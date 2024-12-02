import android.content.Context
import android.util.Log
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException


class TranslatorManager(private val context: Context) {
    private val translators = mutableMapOf<String, Translator>()
    private val databaseManager = DatabaseManager.getInstance(context)
    
    init {
        initializeMLKit()
    }

    private fun initializeMLKit() {
        try {
            // Initialize supported language pairs
            val supportedLanguages = listOf("ja", "en", "ko", "zh")
            supportedLanguages.forEach { sourceLanguage ->
                supportedLanguages.filter { it != sourceLanguage }.forEach { targetLanguage ->
                    getTranslator(sourceLanguage, targetLanguage)
                }
            }
        } catch (e: Exception) {
            handleTranslationError(TranslationError.InitializationError(e))
        }
    }

    private fun handleTranslationError(error: TranslationError) {
        when (error) {
            is TranslationError.InitializationError -> {
                Log.e("TranslatorManager", "ML Kit initialization failed", error.exception)
            }
            is TranslationError.DownloadError -> {
                Log.e("TranslatorManager", "Model download failed: ${error.language}", error.exception)
            }
            is TranslationError.TranslationError -> {
                Log.e("TranslatorManager", "Translation failed for text: ${error.text}", error.exception)
            }
            is TranslationError.NetworkError -> {
                Log.e("TranslatorManager", "Network error during translation", error.exception)
            }
        }
    }

    suspend fun translate(text: String, targetLanguage: String): String {
        if (text.isBlank()) return text
        
        try {
            // Check cache first
            val cached = databaseManager.getCachedTranslation(text, "ja", targetLanguage)
            if (cached != null) {
                return cached
            }
            
            // Perform translation if not cached
            val translated = performTranslation(text, targetLanguage)
            
            // Cache the result
            databaseManager.cacheTranslation(text, translated, "ja", targetLanguage)
            
            return translated
        } catch (e: Exception) {
            handleTranslationError(TranslationError.TranslationError(text, e))
            return text // Return original text on failure
        }
    }

    private suspend fun performTranslation(text: String, targetLanguage: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val translator = getTranslator("ja", targetLanguage)
                translator.translate(text).await()
            } catch (e: Exception) {
                when {
                    isNetworkError(e) -> handleTranslationError(TranslationError.NetworkError(e))
                    else -> handleTranslationError(TranslationError.TranslationError(text, e))
                }
                text // Return original text on failure
            }
        }
    }

    suspend fun translateBlocks(textBlocks: List<TextBlock>, targetLanguage: String): List<TextBlock> {
        return textBlocks.map { block ->
            block.apply {
                translatedText = translate(block.text, targetLanguage)
            }
        }
    }

    private fun getTranslator(sourceLanguage: String, targetLanguage: String): Translator {
        val key = "${sourceLanguage}_$targetLanguage"
        return translators.getOrPut(key) {
            Translation.getClient(
                TranslatorOptions.Builder()
                    .setSourceLanguage(sourceLanguage)
                    .setTargetLanguage(targetLanguage)
                    .build()
            )
        }
    }

    suspend fun downloadModelIfNeeded(sourceLanguage: String, targetLanguage: String) {
        try {
            val translator = getTranslator(sourceLanguage, targetLanguage)
            val conditions = DownloadConditions.Builder()
                .requireWifi()
                .build()
            
            translator.downloadModelIfNeeded(conditions).await()
        } catch (e: Exception) {
            handleTranslationError(TranslationError.DownloadError("${sourceLanguage}_$targetLanguage", e))
            throw e
        }
    }

    private fun isNetworkError(error: Exception): Boolean {
        return error is IOException || 
               error.cause is IOException ||
               error.message?.contains("network", ignoreCase = true) == true
    }

    sealed class TranslationError {
        data class InitializationError(val exception: Exception) : TranslationError()
        data class DownloadError(val language: String, val exception: Exception) : TranslationError()
        data class TranslationError(val text: String, val exception: Exception) : TranslationError()
        data class NetworkError(val exception: Exception) : TranslationError()
    }

    companion object {
        private const val RETRY_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 1000L
    }
}
