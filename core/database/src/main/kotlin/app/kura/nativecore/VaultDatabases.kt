package app.kura.nativecore

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import net.zetetic.database.DatabaseErrorHandler
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteOpenHelper
import java.io.File

object VaultDatabases {
    val versions = linkedMapOf("walletbox.db" to 8, "passes.db" to 7, "identities.db" to 6)
    init { System.loadLibrary("sqlcipher") }

    /** Loads executable code only: no Context, database path, key or connection is needed. */
    fun prepareRuntime() {
        Class.forName(SQLiteDatabase::class.java.name, true, SQLiteDatabase::class.java.classLoader)
    }

    fun openReadOnly(file: File, password: ByteArray): SQLiteDatabase {
        require(file.isFile) { "Missing vault database" }
        // Never delegate corruption to SQLCipher's deleting default handler.
        return SQLiteDatabase.openDatabase(file.absolutePath, password, null, SQLiteDatabase.OPEN_READONLY,
            DatabaseErrorHandler { _, exception -> throw exception }, null)
    }

    suspend fun open(context: Context, snapshot: File, password: SensitiveBytes): RoomVault {
        var completed: RoomVault? = null
        try {
            return withContext(Dispatchers.IO) {
                require(File(snapshot, "snapshot.complete").isFile) { "Only verified snapshots can be adopted" }
                val opened = java.util.Collections.synchronizedList(mutableListOf<RoomDatabase>())
                val copies = java.util.Collections.synchronizedList(mutableListOf<ByteArray>())
                val verified = java.util.concurrent.CompletableFuture<Unit>()
                val remaining = java.util.concurrent.atomic.AtomicInteger(3)
                fun <T : RoomDatabase> build(type: Class<T>, name: String): T {
                    val file = File(snapshot, name)
                    require(file.isFile)
                    val bytes = password.useBytes { it.copyOf() }.also(copies::add)
                    val room = Room.databaseBuilder(context, type, file.absolutePath)
                        .openHelperFactory(checkedFactory(bytes, versions.getValue(name)) {
                            if (remaining.decrementAndGet() == 0) verified.complete(Unit)
                            verified.join()
                        })
                        .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                        .build().also(opened::add)
                    // Force validation now, so a failed adoption never returns partially opened handles.
                    android.os.Trace.beginSection("Kura.open.$name")
                    try {
                        room.openHelper.writableDatabase
                    } catch (failure: Throwable) {
                        verified.completeExceptionally(failure)
                        throw failure
                    } finally {
                        android.os.Trace.endSection()
                    }
                    return room
                }
                try {
                    // The scope joins every opener before failure cleanup can close handles/wipe keys.
                    coroutineScope {
                        fun <T : RoomDatabase> start(type: Class<T>, name: String) = async {
                            try { build(type, name) } catch (failure: Throwable) {
                                verified.completeExceptionally(failure)
                                throw failure
                            }
                        }.also { task ->
                            // Also releases waiters if cancellation prevents an opener from starting.
                            task.invokeOnCompletion { failure ->
                                if (failure != null) verified.completeExceptionally(failure)
                            }
                        }
                        val wallets = start(WalletDatabase::class.java, "walletbox.db")
                        val passes = start(PassDatabase::class.java, "passes.db")
                        val identities = start(IdentityDatabase::class.java, "identities.db")
                        RoomVault(wallets.await(), passes.await(), identities.await(), copies)
                    }.also { completed = it }
                } catch (e: Throwable) {
                    opened.forEach { runCatching { it.close() } }
                    copies.forEach { it.fill(0) }
                    throw e
                }
            }
        } catch (e: CancellationException) {
            withContext(NonCancellable) { completed?.close() }
            throw e
        }
    }

    /** Validate on Room's own connection, before it can create or migrate any tables. */
    private fun checkedFactory(password: ByteArray, expectedVersion: Int, verified: () -> Unit) =
        SupportSQLiteOpenHelper.Factory { configuration ->
            val callback = configuration.callback
            object : SQLiteOpenHelper(
                configuration.context, configuration.name, password, null, callback.version,
                0, DatabaseErrorHandler { _, exception -> throw exception }, null, false
            ) {
                override fun onConfigure(db: SQLiteDatabase) {
                    check(db.version == expectedVersion) { "Unsupported schema version" }
                    verified()
                    callback.onConfigure(db)
                }

                override fun onCreate(db: SQLiteDatabase) = callback.onCreate(db)

                override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) =
                    callback.onUpgrade(db, oldVersion, newVersion)

                override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) =
                    callback.onDowngrade(db, oldVersion, newVersion)

                override fun onOpen(db: SQLiteDatabase) = callback.onOpen(db)
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
