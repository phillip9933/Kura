package app.kura.nativecore

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.zetetic.database.DatabaseErrorHandler
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File

object VaultDatabases {
    val versions = linkedMapOf("walletbox.db" to 8, "passes.db" to 7, "identities.db" to 6)
    init { System.loadLibrary("sqlcipher") }

    fun openReadOnly(file: File, password: ByteArray): SQLiteDatabase {
        require(file.isFile) { "Missing vault database" }
        // Never delegate corruption to SQLCipher's deleting default handler.
        return SQLiteDatabase.openDatabase(file.absolutePath, password, null, SQLiteDatabase.OPEN_READONLY,
            DatabaseErrorHandler { _, exception -> throw exception }, null)
    }

    suspend fun open(context: Context, snapshot: File, password: SensitiveBytes): RoomVault {
        var completed: RoomVault? = null
        try { return withContext(Dispatchers.IO) {
        require(File(snapshot, "snapshot.complete").isFile) { "Only verified snapshots can be adopted" }
        val opened = mutableListOf<RoomDatabase>()
        val copies = mutableListOf<ByteArray>()
        fun <T : RoomDatabase> build(type: Class<T>, name: String): T {
            val file = File(snapshot, name)
            require(file.isFile)
            val bytes = password.useBytes { it.copyOf() }.also(copies::add)
            openReadOnly(file, bytes).use { db -> check(db.version == versions.getValue(name)) { "Unsupported schema version" } }
            val room = Room.databaseBuilder(context, type, file.absolutePath)
                .openHelperFactory(SupportOpenHelperFactory(bytes))
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                .build().also(opened::add)
            // Force validation now, so a failed adoption never returns partially opened handles.
            room.openHelper.writableDatabase
            return room
        }
        try {
            val wallets = build(WalletDatabase::class.java, "walletbox.db")
            val passes = build(PassDatabase::class.java, "passes.db")
            val identities = build(IdentityDatabase::class.java, "identities.db")
            RoomVault(wallets, passes, identities, copies).also { completed = it }
        } catch (e: Throwable) {
            opened.forEach { runCatching { it.close() } }
            copies.forEach { it.fill(0) }
            throw e
        }
        } } catch (e: CancellationException) {
            withContext(NonCancellable) { completed?.close() }
            throw e
        }
    }
}

class RoomVault internal constructor(
    val wallets: WalletDatabase,
    val passes: PassDatabase,
    val identities: IdentityDatabase,
    private val passwordCopies: List<ByteArray>,
) : VaultResources {
    override suspend fun close() = withContext(Dispatchers.IO) {
        var failure: Throwable? = null
        listOf(wallets, passes, identities).forEach { db ->
            try { db.close() } catch (e: Throwable) { if (failure == null) failure = e else failure!!.addSuppressed(e) }
        }
        passwordCopies.forEach { it.fill(0) }
        failure?.let { throw it }
        Unit
    }
}
