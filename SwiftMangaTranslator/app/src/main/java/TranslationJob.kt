// TranslationJob.kt
import java.util.UUID

data class TranslationJob(
    val id: String = UUID.randomUUID().toString(),
    val inputPath: String,
    val outputPath: String,
    val targetLanguage: String,
    var status: Status = Status.PENDING,
    var progress: Progress = Progress(),
    val timestamp: Long = System.currentTimeMillis()
) {
    data class Progress(
        var totalImages: Int = 0,
        var processedImages: Int = 0,
        var successfulImages: Int = 0,
        var currentChapter: String = "",
        var error: String? = null
    )

    enum class Status {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED
    }
}
