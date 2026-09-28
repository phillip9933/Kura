package app.kura.nativecore

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.*
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File

/** Creates only a previously nonexistent native generation; never receives a legacy path. */
object NewDatabases {
    suspend fun create(context: Context, directory: File, password: SensitiveBytes): RoomVault {
        var completed: RoomVault? = null
        try { return withContext(Dispatchers.IO) {
        require(!directory.exists()); check(directory.mkdirs())
        System.loadLibrary("sqlcipher")
        val copies = mutableListOf<ByteArray>()
        val opened = mutableListOf<RoomDatabase>()
        fun <T : RoomDatabase> build(type: Class<T>, name: String): T {
            val bytes = password.useBytes { it.copyOf() }.also(copies::add)
            return Room.databaseBuilder(context, type, File(directory, name).absolutePath)
                .openHelperFactory(SupportOpenHelperFactory(bytes))
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE).build().also {
                    opened.add(it); it.openHelper.writableDatabase
                }
        }
        try {
            val wallets = build(WalletDatabase::class.java, "walletbox.db")
            val passes = build(PassDatabase::class.java, "passes.db")
            val identities = build(IdentityDatabase::class.java, "identities.db")
            File(directory, "snapshot.complete").writeText("native-generation")
            RoomVault(wallets, passes, identities, copies).also { completed = it }
        } catch (e: Throwable) { opened.forEach { runCatching { it.close() } }; copies.forEach { it.fill(0) }; throw e }
    } } catch (e: CancellationException) { withContext(NonCancellable) { completed?.close() }; throw e }
    }
}