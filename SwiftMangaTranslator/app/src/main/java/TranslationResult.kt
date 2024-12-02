// TranslationResult.kt
data class TranslationResult(
    val original: MangaPage,
    val translatedImage: Bitmap? = null,
    val textBlocks: List<TextBlock> = emptyList(),
    val success: Boolean = false,
    val error: String? = null
)
