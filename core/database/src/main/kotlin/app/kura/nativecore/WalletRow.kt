package app.kura.nativecore

import androidx.room.*

@Entity(tableName = "wallets", indices = [Index(value = ["orderIndex"], name = "idx_wallets_order")])
data class WalletRow(
    @PrimaryKey val id: Long? = null,
    val name: String? = null,
    val number: String? = null,
    val expiry: String? = null,
    val network: String? = null,
    val issuer: String? = null,
    val customFields: String? = null,
    val spends: String? = null,
    val rewards: String? = null,
    val annualFeeWaiver: String? = null,
    val maxlimit: String? = null,
    val cardtype: String? = null,
    val billdate: String? = null,
    val category: String? = null,
    val color: String? = null,
    val frontImagePath: String? = null,
    val backImagePath: String? = null,
    @ColumnInfo(defaultValue = "0") val orderIndex: Int? = 0,
    @ColumnInfo(defaultValue = "0") val isArchived: Int = 0
)

@Dao
interface WalletRowDao {
    @Query("SELECT * FROM wallets ORDER BY orderIndex ASC") suspend fun all(): List<WalletRow>
    @Insert suspend fun insert(row: WalletRow): Long
    @Update suspend fun update(row: WalletRow): Int
    @Query("DELETE FROM wallets WHERE id = :id") suspend fun delete(id: Long): Int
}

@Database(entities = [WalletRow::class], version = 8, exportSchema = true)
abstract class WalletDatabase : RoomDatabase() {
    abstract fun rows(): WalletRowDao
}