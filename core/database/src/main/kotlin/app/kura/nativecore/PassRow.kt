package app.kura.nativecore

import androidx.room.*

@Entity(tableName = "passes", indices = [Index(value = ["orderIndex"], name = "idx_passes_order")])
data class PassRow(
    @PrimaryKey(autoGenerate = true) val id: Long? = null,
    val type: String? = null,
    val organizationName: String? = null,
    val description: String? = null,
    val logoText: String? = null,
    val backgroundColor: String? = null,
    val foregroundColor: String? = null,
    val labelColor: String? = null,
    val barcodeValue: String? = null,
    val barcodeFormat: String? = null,
    val barcodeAltText: String? = null,
    val transitType: String? = null,
    val relevantDate: String? = null,
    val expiry_date: String? = null,
    val frontImagePath: String? = null,
    val backImagePath: String? = null,
    val stripImagePath: String? = null,
    val thumbnailImagePath: String? = null,
    val iconImagePath: String? = null,
    val logoImagePath: String? = null,
    val footerImagePath: String? = null,
    val sourceType: String? = null,
    val fields: String? = null,
    @ColumnInfo(defaultValue = "0") val orderIndex: Int? = 0,
    @ColumnInfo(defaultValue = "0") val isArchived: Int = 0
)

@Dao
interface PassRowDao {
    @Query("SELECT * FROM passes ORDER BY orderIndex ASC") suspend fun all(): List<PassRow>
    @Insert suspend fun insert(row: PassRow): Long
    @Update suspend fun update(row: PassRow): Int
    @Query("DELETE FROM passes WHERE id = :id") suspend fun delete(id: Long): Int
}

@Database(entities = [PassRow::class], version = 7, exportSchema = true)
abstract class PassDatabase : RoomDatabase() {
    abstract fun rows(): PassRowDao
}