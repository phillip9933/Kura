package app.kura.nativecore

import androidx.room.*

@Entity(tableName = "identities", indices = [Index(value = ["orderIndex"], name = "idx_identities_order")])
data class IdentityRow(
    @PrimaryKey(autoGenerate = true) val id: Long? = null,
    val name: String? = null,
    val value: String? = null,
    val cardType: String? = null,
    val frontImagePath: String? = null,
    val backImagePath: String? = null,
    val color: String? = null,
    val expiry_date: String? = null,
    val customFields: String? = null,
    @ColumnInfo(defaultValue = "0") val orderIndex: Int? = 0,
    @ColumnInfo(defaultValue = "0") val isArchived: Int = 0
)

@Dao
interface IdentityRowDao {
    @Query("SELECT * FROM identities ORDER BY orderIndex ASC") suspend fun all(): List<IdentityRow>
    @Insert suspend fun insert(row: IdentityRow): Long
    @Update suspend fun update(row: IdentityRow): Int
    @Query("DELETE FROM identities WHERE id = :id") suspend fun delete(id: Long): Int
}

@Database(entities = [IdentityRow::class], version = 6, exportSchema = true)
abstract class IdentityDatabase : RoomDatabase() {
    abstract fun rows(): IdentityRowDao
}