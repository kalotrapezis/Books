package com.kalotrapezis.books.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "books",
    indices = [Index(value = ["uri"], unique = true), Index(value = ["lastOpenedAt"])]
)
data class BookEntity(
    @PrimaryKey val id: String,
    val uri: String,
    val title: String,
    val author: String,
    val metadataIdentifier: String?,
    val foliateKey: String,
    val sha256: String,
    val lastCfi: String?,
    val progressFraction: Double?,
    val coverPath: String? = null,
    /** Foliate-compatible JSON array of CFI strings. */
    val bookmarks: String? = null,
    val addedAt: Long,
    val lastOpenedAt: Long,
)

@Dao
abstract class BookDao {
    @Query("SELECT * FROM books ORDER BY lastOpenedAt DESC")
    abstract fun observeLibrary(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE uri = :uri LIMIT 1")
    abstract suspend fun findByUri(uri: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insert(book: BookEntity)

    @Update
    protected abstract suspend fun update(book: BookEntity)

    @androidx.room.Transaction
    open suspend fun upsert(book: BookEntity) {
        val existing = findByUri(book.uri)
        if (existing == null) {
            insert(book)
        } else {
            update(
                book.copy(
                    id = existing.id,
                    addedAt = existing.addedAt,
                    lastCfi = book.lastCfi ?: existing.lastCfi,
                    progressFraction = book.progressFraction ?: existing.progressFraction,
                    coverPath = book.coverPath ?: existing.coverPath,
                )
            )
        }
    }

    @Query("""
        UPDATE books SET title = :title, author = :author, metadataIdentifier = :metadataIdentifier,
        foliateKey = :foliateKey WHERE id = :id
    """)
    abstract suspend fun updateMetadata(
        id: String,
        title: String,
        author: String,
        metadataIdentifier: String?,
        foliateKey: String,
    )

    @Query("""
        UPDATE books SET lastCfi = :cfi, progressFraction = :fraction,
        lastOpenedAt = :timestamp WHERE id = :id
    """)
    abstract suspend fun updateProgress(id: String, cfi: String?, fraction: Double?, timestamp: Long)

    @Query("UPDATE books SET lastOpenedAt = :timestamp WHERE id = :id")
    abstract suspend fun markOpened(id: String, timestamp: Long)

    @Query("UPDATE books SET coverPath = :coverPath WHERE id = :id")
    abstract suspend fun updateCover(id: String, coverPath: String?)

    @Query("UPDATE books SET bookmarks = :bookmarks WHERE id = :id")
    abstract suspend fun updateBookmarks(id: String, bookmarks: String?)
}

@Database(entities = [BookEntity::class], version = 3, exportSchema = true)
abstract class BooksDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao

    companion object {
        @Volatile private var instance: BooksDatabase? = null

        fun getInstance(context: Context): BooksDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                BooksDatabase::class.java,
                "books.db",
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN coverPath TEXT")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN bookmarks TEXT")
            }
        }
    }
}
