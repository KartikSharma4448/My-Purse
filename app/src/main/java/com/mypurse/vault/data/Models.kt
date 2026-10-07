package com.mypurse.vault.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "cards")
data class VaultCard(@PrimaryKey val id: String, val title: String, val bank: String,
    val holder: String, val number: String, val expiry: String, val notes: String = "",
    val color: Int = 0, val favorite: Boolean = false, val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue="'BANK'") val kind: String = "BANK",
    @ColumnInfo(defaultValue="'Visa'") val network: String = "Visa",
    @ColumnInfo(defaultValue="''") val cvv: String = "",
    @ColumnInfo(defaultValue="''") val frontImageId: String = "",
    @ColumnInfo(defaultValue="''") val backImageId: String = "")

@Entity(tableName = "documents", indices = [Index(value = ["hash"], unique = true)])
data class VaultDocument(@PrimaryKey val id: String, val title: String, val originalName: String,
    val mime: String, val category: String, val fileName: String, val hash: String, val size: Long,
    val favorite: Boolean = false, val createdAt: Long = System.currentTimeMillis())

@Dao
interface VaultDao {
    @Query("SELECT * FROM cards ORDER BY createdAt DESC") fun cards(): Flow<List<VaultCard>>
    @Query("SELECT * FROM documents WHERE category != 'Card image' ORDER BY createdAt DESC") fun documents(): Flow<List<VaultDocument>>
    @Query("SELECT * FROM documents WHERE id = :id") suspend fun document(id: String): VaultDocument?
    @Query("SELECT * FROM cards WHERE id = :id") suspend fun card(id: String): VaultCard?
    @Query("SELECT * FROM documents WHERE category = 'Card image' AND id NOT IN (SELECT frontImageId FROM cards UNION SELECT backImageId FROM cards)") suspend fun orphanCardImages(): List<VaultDocument>
    @Query("SELECT * FROM documents WHERE hash = :hash LIMIT 1") suspend fun duplicate(hash: String): VaultDocument?
    @Query("SELECT fileName FROM documents") suspend fun documentFiles(): List<String>
    @Query("SELECT COALESCE(SUM(size),0) FROM documents") fun storedBytes(): Flow<Long>
    @Upsert suspend fun save(card: VaultCard)
    @Upsert suspend fun save(document: VaultDocument)
    @Delete suspend fun delete(card: VaultCard)
    @Delete suspend fun delete(document: VaultDocument)
}

@Database(entities = [VaultCard::class, VaultDocument::class], version = 2, exportSchema = true)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun dao(): VaultDao
    companion object {
        val MIGRATION_1_2 = object: androidx.room.migration.Migration(1,2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cards ADD COLUMN kind TEXT NOT NULL DEFAULT 'BANK'")
                db.execSQL("ALTER TABLE cards ADD COLUMN network TEXT NOT NULL DEFAULT 'Visa'")
                listOf("cvv","frontImageId","backImageId").forEach {
                    db.execSQL("ALTER TABLE cards ADD COLUMN $it TEXT NOT NULL DEFAULT ''")
                }
            }
        }
    }
}

object CardValidation {
    fun error(title: String, bank: String, holder: String, number: String, expiry: String): String? = when {
        title.trim().isEmpty() -> "Enter a card title."
        bank.trim().isEmpty() -> "Enter the bank name."
        holder.trim().isEmpty() -> "Enter the holder name."
        number.isNotBlank() && (!number.all(Char::isDigit) || number.length !in 12..19) -> "Use 12 to 19 digits, or leave the number empty."
        expiry.isNotBlank() && !Regex("(0[1-9]|1[0-2])/[0-9]{2}").matches(expiry) -> "Use MM/YY for expiry."
        else -> null
    }
}

fun readableSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1048576 -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / 1048576.0)
}
