package app.kura.nativecore

import android.content.Context
import android.util.Base64
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class DatabaseCompatibilityTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val assets get() = InstrumentationRegistry.getInstrumentation().context.assets
    private val password = Base64.encode(ByteArray(32) { (it + 1).toByte() }, Base64.NO_WRAP)
    init { System.loadLibrary("sqlcipher") }
    private fun value(name: String): String = if (name == "customFields" || name == "fields") "{\"label\":\"秘密\",\"value\":\"123\"}" else "${name}_日本語"
    private fun fileFor(table: String) = when(table) { "wallets" -> "walletbox.db"; "passes" -> "passes.db"; else -> "identities.db" }
    private fun fixture(): File {
        val root = File(context.cacheDir, "source-${UUID.randomUUID()}")
        File(root, "app_flutter").mkdirs()
        for (table in listOf("wallets", "passes", "identities")) {
            val file = File(root, "app_flutter/${fileFor(table)}")
            val db = SQLiteDatabase.openOrCreateDatabase(file, password, null, null)
            try {
                val ddl = assets.open("$table.sql").bufferedReader().use { it.readText() }
                ddl.split(';').filter { it.isNotBlank() }.forEach { db.execSQL(it) }
                val columns = mutableListOf<String>()
                db.rawQuery("PRAGMA table_info($table)", null).use { cursor -> while(cursor.moveToNext()) columns.add(cursor.getString(1)) }
                val bindings: Array<Any?> = columns.map { when(it) { "id" -> 41L; "orderIndex" -> 7; "isArchived" -> 1; else -> value(it) } }.toTypedArray()
                db.execSQL("INSERT INTO $table (${columns.joinToString()}) VALUES (${columns.joinToString { "?" }})", bindings)
            } finally { db.close() }
        }
        return root
    }
    private fun copyFixture(root: File, destination: File): File {
        check(destination.mkdirs())
        VaultDatabases.versions.keys.forEach { name ->
            File(root, "app_flutter/$name").copyTo(File(destination, name))
        }
        File(destination, "snapshot.complete").writeText("synthetic-fixture")
        return destination
    }
    private fun hashes(root: File) = root.walkTopDown().filter { it.isFile }.associate { it.relativeTo(root).path to java.security.MessageDigest.getInstance("SHA-256").digest(it.readBytes()).toList() }
    private fun schema(file: File): List<String> = VaultDatabases.openReadOnly(file, password).use { db ->
        val rows = mutableListOf<String>()
        db.rawQuery("SELECT type,name,sql FROM sqlite_master WHERE name NOT LIKE 'room_%' AND name NOT LIKE 'sqlite_%' ORDER BY name", null).use { cursor ->
            while(cursor.moveToNext()) rows.add("${cursor.getString(0)}|${cursor.getString(1)}|${cursor.getString(2)}")
        }; rows
    }
    @Test fun allThreeSqlcipherConnectionsUseBase64TextAndStayReadOnly() {
        val root = fixture(); val before = hashes(root)
        for ((name, version) in VaultDatabases.versions) {
            VaultDatabases.openReadOnly(File(root, "app_flutter/$name"), password).use { db ->
                assertEquals(version, db.version)
                db.rawQuery("PRAGMA cipher_version", null).use { cursor -> assertTrue(cursor.moveToFirst()); assertTrue(cursor.getString(0).startsWith("4.19.0")) }
                assertThrows(Exception::class.java) { db.execSQL("CREATE TABLE forbidden(x)") }
            }
            assertThrows(Exception::class.java) {
                VaultDatabases.openReadOnly(File(root, "app_flutter/$name"), Base64.decode(password, Base64.DEFAULT)).use { it.version }
            }
        }
        assertEquals(before, hashes(root))
    }
    @Test fun wrongVersionOrSchemaNeverModifiesOriginals() = runBlocking {
        val root = fixture(); val before = hashes(root)
        for (badVersion in listOf(true, false)) {
            val snapshot = copyFixture(root, File(context.cacheDir, UUID.randomUUID().toString()))
            SQLiteDatabase.openOrCreateDatabase(File(snapshot, "passes.db"), password, null, null).use {
                if (badVersion) it.version = 99 else it.execSQL("ALTER TABLE passes ADD COLUMN unexpected TEXT")
            }
            assertTrue(runCatching { VaultDatabases.open(context, snapshot, SensitiveBytes(password.copyOf())).close() }.isFailure)
            assertEquals(before, hashes(root))
        }
    }
    @Test fun unsupportedVersionsCannotCreateOrMigrateTables() = runBlocking {
        val root = fixture()
        for (version in listOf(0, 6, 99)) {
            val snapshot = copyFixture(root, File(context.cacheDir, UUID.randomUUID().toString()))
            val file = File(snapshot, "walletbox.db")
            SQLiteDatabase.openOrCreateDatabase(file, password, null, null).use { it.version = version }
            val before = file.readBytes()
            SensitiveBytes(password.copyOf()).use { key ->
                assertTrue(runCatching { VaultDatabases.open(context, snapshot, key).close() }.isFailure)
            }
            assertArrayEquals(before, file.readBytes())
        }
    }

    @Test fun wrongKeyAndCorruptionDoNotDeleteDatabaseFiles() = runBlocking {
        val root = fixture()
        for (corrupt in listOf(false, true)) {
            val snapshot = copyFixture(root, File(context.cacheDir, UUID.randomUUID().toString()))
            val file = File(snapshot, "walletbox.db")
            if (corrupt) file.writeBytes(ByteArray(4096) { 0x5a })
            val before = hashes(snapshot)
            val supplied = if (corrupt) password.copyOf() else ByteArray(password.size) { 42 }
            SensitiveBytes(supplied).use { key ->
                assertTrue(runCatching { VaultDatabases.open(context, snapshot, key).close() }.isFailure)
            }
            assertEquals(before, hashes(snapshot))
        }
    }

    @Test fun cancelledParallelOpenCanBeReopened() = runBlocking {
        val root = fixture()
        for (wait in listOf(0L, 50L, 200L)) {
            val snapshot = copyFixture(root, File(context.cacheDir, UUID.randomUUID().toString()))
            SensitiveBytes(password.copyOf()).use { key ->
                withTimeout(10000) {
                    val opening = async { VaultDatabases.open(context, snapshot, key).close() }
                    delay(wait)
                    opening.cancelAndJoin()
                    val reopened = VaultDatabases.open(context, snapshot, key)
                    try {
                        assertEquals(1, reopened.wallets.rows().all().size)
                        assertEquals(1, reopened.passes.rows().all().size)
                        assertEquals(1, reopened.identities.rows().all().size)
                    } finally { reopened.close() }
                }
            }
        }
    }

    @Test fun missingParallelDatabaseFailsWithoutHanging() = runBlocking {
        val snapshot = copyFixture(fixture(), File(context.cacheDir, UUID.randomUUID().toString()))
        assertTrue(File(snapshot, "passes.db").delete())
        val before = hashes(snapshot)
        withTimeout(10000) {
            SensitiveBytes(password.copyOf()).use { key ->
                assertTrue(runCatching { VaultDatabases.open(context, snapshot, key).close() }.isFailure)
            }
        }
        assertEquals(before, hashes(snapshot))
    }

    @Test fun walletsAllColumnsReadWriteParity() = runBlocking {
        val fixture = fixture()
        val before = hashes(fixture)
        val snapshot = copyFixture(fixture, File(context.cacheDir, UUID.randomUUID().toString()))
        val originalSchema = schema(File(snapshot, fileFor("wallets")))
        val vault = VaultDatabases.open(context, snapshot, SensitiveBytes(password.copyOf()))
        try {
            val dao = vault.wallets.rows()
            val expected = WalletRow(id = 41, name = value("name"), number = value("number"), expiry = value("expiry"), network = value("network"), issuer = value("issuer"), customFields = value("customFields"), spends = value("spends"), rewards = value("rewards"), annualFeeWaiver = value("annualFeeWaiver"), maxlimit = value("maxlimit"), cardtype = value("cardtype"), billdate = value("billdate"), category = value("category"), color = value("color"), frontImagePath = value("frontImagePath"), backImagePath = value("backImagePath"), orderIndex = 7, isArchived = 1)
            assertEquals(expected, dao.all().single())
            val nullRow = WalletRow(id = 42, orderIndex = null)
            dao.insert(nullRow)
            assertEquals(nullRow, dao.all().first { it.id == 42L })
            assertEquals(1, dao.update(expected.copy(id = 42)))
            assertEquals(expected.copy(id = 42), dao.all().first { it.id == 42L })
            assertEquals(1, dao.delete(42))
            assertEquals(expected, dao.all().single())
        } finally { vault.close() }
        assertEquals(originalSchema, schema(File(snapshot, fileFor("wallets"))))
        assertEquals(before, hashes(fixture))
        // Reopen with a fresh factory: a cleared prior passphrase must not be reused.
        val reopened = VaultDatabases.open(context, snapshot, SensitiveBytes(password.copyOf()))
        try { assertEquals(1, reopened.wallets.rows().all().size) } finally { reopened.close() }
    }
    @Test fun passesAllColumnsReadWriteParity() = runBlocking {
        val fixture = fixture()
        val before = hashes(fixture)
        val snapshot = copyFixture(fixture, File(context.cacheDir, UUID.randomUUID().toString()))
        val originalSchema = schema(File(snapshot, fileFor("passes")))
        val vault = VaultDatabases.open(context, snapshot, SensitiveBytes(password.copyOf()))
        try {
            val dao = vault.passes.rows()
            val expected = PassRow(id = 41, type = value("type"), organizationName = value("organizationName"), description = value("description"), logoText = value("logoText"), backgroundColor = value("backgroundColor"), foregroundColor = value("foregroundColor"), labelColor = value("labelColor"), barcodeValue = value("barcodeValue"), barcodeFormat = value("barcodeFormat"), barcodeAltText = value("barcodeAltText"), transitType = value("transitType"), relevantDate = value("relevantDate"), expiry_date = value("expiry_date"), frontImagePath = value("frontImagePath"), backImagePath = value("backImagePath"), stripImagePath = value("stripImagePath"), thumbnailImagePath = value("thumbnailImagePath"), iconImagePath = value("iconImagePath"), logoImagePath = value("logoImagePath"), footerImagePath = value("footerImagePath"), sourceType = value("sourceType"), fields = value("fields"), orderIndex = 7, isArchived = 1)
            assertEquals(expected, dao.all().single())
            val nullRow = PassRow(id = 42, orderIndex = null)
            dao.insert(nullRow)
            assertEquals(nullRow, dao.all().first { it.id == 42L })
            assertEquals(1, dao.update(expected.copy(id = 42)))
            assertEquals(expected.copy(id = 42), dao.all().first { it.id == 42L })
            assertEquals(1, dao.delete(42))
            assertEquals(expected, dao.all().single())
        } finally { vault.close() }
        assertEquals(originalSchema, schema(File(snapshot, fileFor("passes"))))
        assertEquals(before, hashes(fixture))
        // Reopen with a fresh factory: a cleared prior passphrase must not be reused.
        val reopened = VaultDatabases.open(context, snapshot, SensitiveBytes(password.copyOf()))
        try { assertEquals(1, reopened.passes.rows().all().size) } finally { reopened.close() }
    }
    @Test fun identitiesAllColumnsReadWriteParity() = runBlocking {
        val fixture = fixture()
        val before = hashes(fixture)
        val snapshot = copyFixture(fixture, File(context.cacheDir, UUID.randomUUID().toString()))
        val originalSchema = schema(File(snapshot, fileFor("identities")))
        val vault = VaultDatabases.open(context, snapshot, SensitiveBytes(password.copyOf()))
        try {
            val dao = vault.identities.rows()
            val expected = IdentityRow(id = 41, name = value("name"), value = value("value"), cardType = value("cardType"), frontImagePath = value("frontImagePath"), backImagePath = value("backImagePath"), color = value("color"), expiry_date = value("expiry_date"), customFields = value("customFields"), orderIndex = 7, isArchived = 1)
            assertEquals(expected, dao.all().single())
            val nullRow = IdentityRow(id = 42, orderIndex = null)
            dao.insert(nullRow)
            assertEquals(nullRow, dao.all().first { it.id == 42L })
            assertEquals(1, dao.update(expected.copy(id = 42)))
            assertEquals(expected.copy(id = 42), dao.all().first { it.id == 42L })
            assertEquals(1, dao.delete(42))
            assertEquals(expected, dao.all().single())
        } finally { vault.close() }
        assertEquals(originalSchema, schema(File(snapshot, fileFor("identities"))))
        assertEquals(before, hashes(fixture))
        // Reopen with a fresh factory: a cleared prior passphrase must not be reused.
        val reopened = VaultDatabases.open(context, snapshot, SensitiveBytes(password.copyOf()))
        try { assertEquals(1, reopened.identities.rows().all().size) } finally { reopened.close() }
    }
    @Test fun columnCiphertextRoomAndSessionLockEndToEnd() = runBlocking {
        val root = fixture()
        val master = Base64.decode(password, Base64.DEFAULT)
        fun encrypt(key: ByteArray, bytes: ByteArray, gcm: Boolean): ByteArray {
            val iv = ByteArray(if (gcm) 12 else 16) { (it + 5).toByte() }
            val cipher = javax.crypto.Cipher.getInstance(if(gcm) "AES/GCM/NoPadding" else "AES/CBC/PKCS7Padding")
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, javax.crypto.spec.SecretKeySpec(key, "AES"),
                if (gcm) javax.crypto.spec.GCMParameterSpec(128, iv) else javax.crypto.spec.IvParameterSpec(iv))
            return iv + cipher.doFinal(bytes)
        }
        fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
        fun column(text: String, gcm: Boolean): String {
            val data = encrypt(master, text.toByteArray(), gcm); val size = if(gcm) 12 else 16
            return b64(data.copyOfRange(0, size)) + ":" + b64(data.copyOfRange(size, data.size))
        }
        SQLiteDatabase.openOrCreateDatabase(File(root, "app_flutter/walletbox.db"), password, null, null).use {
            it.execSQL("UPDATE wallets SET name=?, customFields=? WHERE id=41", arrayOf(column("秘密の財布", true), column("{\"label\":\"legacy JSON\"}", false)))
        }
        val before = hashes(root)
        val snapshot = copyFixture(root, File(context.cacheDir, UUID.randomUUID().toString()))
        SensitiveBytes(master.copyOf()).use { columnKey ->
            val coordinator = VaultSessionCoordinator()
            val bytes = password.copyOf()
            lateinit var vault: RoomVault
            val token = coordinator.unlock({ bytes }) { key -> VaultDatabases.open(context, snapshot, key).also { vault = it } }
            val row = coordinator.run(token) { vault.wallets.rows().all().single() }
            columnKey.use { key ->
                val plaintext = key.useBytes { LegacyColumnCodec.decrypt(row.name, it)!! }
                assertEquals("秘密の財布", String(plaintext))
                coordinator.own(token, plaintext)
                val json = key.useBytes { LegacyColumnCodec.decrypt(row.customFields, it)!! }
                assertEquals("{\"label\":\"legacy JSON\"}", String(json)); coordinator.own(token, json)
                coordinator.lock()
                assertTrue(plaintext.all { it == 0.toByte() }); assertTrue(json.all { it == 0.toByte() })
            }
            assertTrue(bytes.all { it == 0.toByte() })
            assertFalse(vault.wallets.isOpen); assertFalse(vault.passes.isOpen); assertFalse(vault.identities.isOpen)
            assertTrue(runCatching { coordinator.run(token) { vault.wallets.rows().all() } }.isFailure)
        }
        assertEquals(before, hashes(root))
        master.fill(0)
    }    @Test fun alteredColumnOrderFromOlderSchemasIsAccepted() = runBlocking {
        val root = fixture()
        for ((table, tail) in mapOf("wallets" to listOf("isArchived"), "passes" to listOf("logoImagePath", "footerImagePath"), "identities" to listOf("expiry_date"))) {
            val file = File(root, "app_flutter/${fileFor(table)}")
            SQLiteDatabase.openOrCreateDatabase(file, password, null, null).use { db ->
                db.execSQL("DROP TABLE $table")
                val definitions = assets.open("$table.sql").bufferedReader().use { it.readText() }.substringAfter('(').substringBefore(')')
                    .split(',').map { it.trim() }
                val first = definitions.filterNot { it.substringBefore(' ') in tail }
                db.execSQL("CREATE TABLE $table (${first.joinToString()})")
                tail.forEach { name -> db.execSQL("ALTER TABLE $table ADD COLUMN ${definitions.single { it.substringBefore(' ') == name }}") }
                db.execSQL("CREATE INDEX idx_${table}_order ON $table(orderIndex)")
            }
        }
        val before = hashes(root)
        val snapshot = copyFixture(root, File(context.cacheDir, UUID.randomUUID().toString()))
        val vault = VaultDatabases.open(context, snapshot, SensitiveBytes(password.copyOf()))
        try {
            assertTrue(vault.wallets.rows().all().isEmpty())
            assertTrue(vault.passes.rows().all().isEmpty())
            assertTrue(vault.identities.rows().all().isEmpty())
        } finally { vault.close() }
        assertEquals(before, hashes(root))
    }
    @Test fun interruptedSnapshotCannotBeAdoptedOrOverwritten() = runBlocking {
        val root = fixture(); val before = hashes(root)
        val incomplete = File(context.cacheDir, UUID.randomUUID().toString()).apply { mkdirs() }
        File(root, "app_flutter/walletbox.db").copyTo(File(incomplete, "walletbox.db"))
        assertTrue(runCatching { VaultDatabases.open(context, incomplete, SensitiveBytes(password.copyOf())) }.isFailure)
        assertEquals(before, hashes(root))
    }}
