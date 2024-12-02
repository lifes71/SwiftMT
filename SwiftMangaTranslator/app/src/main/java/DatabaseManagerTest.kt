
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseManagerTest {
    private lateinit var databaseManager: DatabaseManager

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        databaseManager = DatabaseManager.getInstance(context)
    }

    @Test
    fun testCacheTranslation() = runTest {
        // Test data
        val sourceText = "テスト"
        val translatedText = "Test"
        val sourceLanguage = "ja"
        val targetLanguage = "en"

        // Cache translation
        databaseManager.cacheTranslation(
            sourceText,
            translatedText,
            sourceLanguage,
            targetLanguage
        )

        // Retrieve cached translation
        val cached = databaseManager.getCachedTranslation(
            sourceText,
            sourceLanguage,
            targetLanguage
        )

        assertEquals(translatedText, cached)
    }

    @Test
    fun testCleanupOldCache() = runTest {
        // Add test data
        databaseManager.cacheTranslation(
            "テスト1",
            "Test1",
            "ja",
            "en"
        )

        // Perform cleanup
        databaseManager.cleanupOldCache(0) // Immediate cleanup

        // Verify cleanup
        val cached = databaseManager.getCachedTranslation(
            "テスト1",
            "ja",
            "en"
        )

        assertNull(cached)
    }
}
