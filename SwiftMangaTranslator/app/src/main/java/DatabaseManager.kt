import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// DatabaseManager.kt
class DatabaseManager private constructor(context: Context) {
    private val database: SQLiteDatabase
    
    companion object {
        private const val DATABASE_NAME = "manga_translations.db"
        private const val DATABASE_VERSION = 1
        
        @Volatile
        private var instance: DatabaseManager? = null
        
        fun getInstance(context: Context): DatabaseManager {
            return instance ?: synchronized(this) {
                instance ?: DatabaseManager(context).also { instance = it }
            }
        }
    }
    
    init {
        database = DatabaseHelper(context).writableDatabase
    }
    
    private class DatabaseHelper(context: Context) : 
        SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
        
        override fun onCreate(db: SQLiteDatabase) {
            // Create translations table
            db.execSQL("""
                CREATE TABLE translations (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    source_text TEXT NOT NULL,
                    translated_text TEXT NOT NULL,
                    source_language TEXT NOT NULL,
                    target_language TEXT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    UNIQUE(source_text, source_language, target_language)
                )
            """)
            
            // Create manga_pages table
            db.execSQL("""
                CREATE TABLE manga_pages (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    file_path TEXT NOT NULL UNIQUE,
                    chapter_number INTEGER,
                    page_number INTEGER NOT NULL,
                    series_name TEXT,
                    processing_status TEXT NOT NULL,
                    last_processed INTEGER
                )
            """)
        }
        
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // Handle database upgrades here
        }
    }
    
    // Cache translation
    suspend fun cacheTranslation(
        sourceText: String,
        translatedText: String,
        sourceLanguage: String,
        targetLanguage: String
    ) {
        withContext(Dispatchers.IO) {
            try {
                val values = ContentValues().apply {
                    put("source_text", sourceText)
                    put("translated_text", translatedText)
                    put("source_language", sourceLanguage)
                    put("target_language", targetLanguage)
                    put("timestamp", System.currentTimeMillis())
                }
                
                database.insertWithOnConflict(
                    "translations",
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            } catch (e: Exception) {
                Log.e("DatabaseManager", "Failed to cache translation", e)
            }
        }
    }
    
    // Get cached translation
    suspend fun getCachedTranslation(
        sourceText: String,
        sourceLanguage: String,
        targetLanguage: String
    ): String? {
        return withContext(Dispatchers.IO) {
            try {
                val cursor = database.query(
                    "translations",
                    arrayOf("translated_text"),
                    "source_text = ? AND source_language = ? AND target_language = ?",
                    arrayOf(sourceText, sourceLanguage, targetLanguage),
                    null,
                    null,
                    null
                )
                
                cursor.use {
                    if (it.moveToFirst()) {
                        it.getString(0)
                    } else null
                }
            } catch (e: Exception) {
                Log.e("DatabaseManager", "Failed to get cached translation", e)
                null
            }
        }
    }
    
    // Track manga page processing
    suspend fun trackMangaPage(mangaPage: MangaPage, status: ProcessingStatus) {
        withContext(Dispatchers.IO) {
            try {
                val values = ContentValues().apply {
                    put("file_path", mangaPage.path)
                    put("page_number", mangaPage.pageNumber)
                    mangaPage.chapterInfo?.let {
                        put("chapter_number", it.chapterNumber)
                        put("series_name", it.seriesName)
                    }
                    put("processing_status", status.name)
                    put("last_processed", System.currentTimeMillis())
                }
                
                database.insertWithOnConflict(
                    "manga_pages",
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            } catch (e: Exception) {
                Log.e("DatabaseManager", "Failed to track manga page", e)
            }
        }
    }
    
    // Get processing status of manga page
    suspend fun getMangaPageStatus(filePath: String): ProcessingStatus? {
        return withContext(Dispatchers.IO) {
            try {
                val cursor = database.query(
                    "manga_pages",
                    arrayOf("processing_status"),
                    "file_path = ?",
                    arrayOf(filePath),
                    null,
                    null,
                    null
                )
                
                cursor.use {
                    if (it.moveToFirst()) {
                        ProcessingStatus.valueOf(it.getString(0))
                    } else null
                }
            } catch (e: Exception) {
                Log.e("DatabaseManager", "Failed to get manga page status", e)
                null
            }
        }
    }
    
    // Clean up old cached translations
    suspend fun cleanupOldCache(maxAgeMs: Long = 7 * 24 * 60 * 60 * 1000) { // 7 days
        withContext(Dispatchers.IO) {
            try {
                val cutoffTime = System.currentTimeMillis() - maxAgeMs
                database.delete(
                    "translations",
                    "timestamp < ?",
                    arrayOf(cutoffTime.toString())
                )
            } catch (e: Exception) {
                Log.e("DatabaseManager", "Failed to cleanup cache", e)
            }
        }
    }
    
    enum class ProcessingStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED
    }
}
