
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever
import android.content.Context
import android.graphics.Bitmap

class TranslationServiceTest {
    @Mock
    private lateinit var context: Context
    
    @Mock
    private lateinit var translatorManager: TranslatorManager
    
    @Mock
    private lateinit var imageProcessor: ImageProcessor
    
    @Mock
    private lateinit var fileManager: FileManager
    
    private lateinit var translationService: TranslationService

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        translationService = TranslationService(
            context,
            translatorManager,
            imageProcessor,
            fileManager
        )
    }

    @Test
    fun `test processPage success`() = runTest {
        // Create test data
        val testBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val mangaPage = MangaPage(testBitmap, "test/path", 1)
        
        // Mock dependencies
        whenever(imageProcessor.processImage(testBitmap))
            .thenReturn(ImageProcessor.ProcessingResult.Success(testBitmap, emptyList()))
        
        whenever(translatorManager.translate(any(), any()))
            .thenReturn("Translated Text")

        // Execute test
        val result = translationService.processPage(
            mangaPage,
            "en",
            TranslationService.OcrModel.ML_KIT,
            TranslationService.TranslatorType.ML_KIT
        )

        // Verify results
        assertTrue(result.success)
        assertNotNull(result.translatedImage)
    }

    @Test
    fun `test processPage handles error`() = runTest {
        // Create test data
        val testBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val mangaPage = MangaPage(testBitmap, "test/path", 1)
        
        // Mock error scenario
        whenever(imageProcessor.processImage(testBitmap))
            .thenReturn(ImageProcessor.ProcessingResult.Error(Exception("Test error")))

        // Execute test
        val result = translationService.processPage(
            mangaPage,
            "en",
            TranslationService.OcrModel.ML_KIT,
            TranslationService.TranslatorType.ML_KIT
        )

        // Verify results
        assertFalse(result.success)
        assertNotNull(result.error)
    }
}
