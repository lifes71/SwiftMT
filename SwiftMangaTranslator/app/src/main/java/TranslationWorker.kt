import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.File


class TranslationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val translationQueue = Channel<TranslationTask>(Channel.BUFFERED)
    private val resultChannel = Channel<TranslationResult>()
    
    private val translationService = TranslationService()
    private val fileManager = FileManager()
    private val notificationManager = NotificationManagerCompat.from(context)
    private val notificationId = 1001

    data class TranslationTask(
        val file: File,
        val index: Int,
        val chapterInfo: String
    )

    override suspend fun doWork(): Result = withContext(Dispatchers.Default) {
        try {
            val inputPath = inputData.getString("input_path") ?: return@withContext Result.failure()
            
            // Create notification channel and initialize progress
            createNotificationChannel()
            updateNotification(0, 0, 0, "", 0, 0)

            // Create output directory
            val outputPath = fileManager.createOutputDirectories(File(inputPath))

            // Download translation models if needed
            translationService.ensureModelsDownloaded()

            // Initialize workers
            val workers = List(CONCURRENT_WORKERS) {
                launchTranslationWorker(it, outputPath)
            }

            // Launch result processor
            val resultProcessor = launchResultProcessor()

            // Process the folder structure and queue files
            val imageFiles = scanForImages(File(inputPath))
            if (imageFiles.isEmpty()) {
                return@withContext Result.success()
            }

            var currentChapter = ""
            var totalFiles = imageFiles.size
            
            imageFiles.forEachIndexed { index, file ->
                val chapter = file.parentFile?.name ?: ""
                if (chapter != currentChapter) {
                    currentChapter = chapter
                }
                
                // Queue the file for processing
                translationQueue.send(TranslationTask(file, index, chapter))
            }

            // Signal completion
            translationQueue.close()
            
            // Wait for all workers to complete
            workers.forEach { it.join() }
            
            // Close result channel and wait for result processor
            resultChannel.close()
            resultProcessor.join()

            return@withContext Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Batch translation failed", e)
            return@withContext Result.failure()
        }
    }

    private fun CoroutineScope.launchTranslationWorker(workerId: Int, outputPath: String) = launch {
        for (task in translationQueue) {
            try {
                MangaPage.fromFile(task.file, task.index)?.let { page ->
                    val result = translationService.processPage(page)
                    if (result.success && result.translatedImage != null) {
                        saveTranslatedImage(result.translatedImage, task.file, outputPath)
                    }
                    resultChannel.send(
                        TranslationResult(
                            task = task,
                            success = result.success,
                            translatedImage = result.translatedImage,
                            error = result.error
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Worker $workerId failed to process: ${task.file.absolutePath}", e)
                resultChannel.send(
                    TranslationResult(
                        task = task,
                        success = false,
                        error = e.message
                    )
                )
            }
        }
    }

    private fun CoroutineScope.launchResultProcessor() = launch {
        var successCount = 0
        var failureCount = 0
        var currentChapter = ""
        var chapterProgress = 0
        var totalFiles = 0
        
        for (result in resultChannel) {
            if (result.success && result.translatedImage != null) {
                successCount++
            } else {
                failureCount++
            }

            // Update progress
            if (result.task.chapterInfo != currentChapter) {
                currentChapter = result.task.chapterInfo
                chapterProgress = 0
            }
            chapterProgress++
            totalFiles = result.task.index + 1

            // Update progress data
            setProgress(workDataOf(
                "progress" to totalFiles,
                "total" to totalFiles,
                "success_count" to successCount,
                "failure_count" to failureCount,
                "current_chapter" to currentChapter,
                "chapter_progress" to chapterProgress
            ))

            // Update notification
            updateNotification(
                current = totalFiles,
                total = totalFiles,
                successCount = successCount,
                chapter = currentChapter,
                chapterProgress = chapterProgress,
                chapterTotal = totalFiles
            )
        }

        // Show completion notification
        showCompletionNotification(successCount, failureCount, totalFiles)
    }

    private fun scanForImages(folder: File): List<File> {
        return folder.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in SUPPORTED_FORMATS }
            .toList()
    }

    private fun saveTranslatedImage(bitmap: Bitmap, originalFile: File, outputPath: String) {
        val relativePath = originalFile.parentFile?.name ?: ""
        val outputFile = File(outputPath, "$relativePath/${originalFile.name}")
        outputFile.parentFile?.mkdirs()
        
        outputFile.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Batch Translation",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows batch translation progress"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }


    private fun updateNotification(
        current: Int, 
        total: Int, 
        successCount: Int,
        chapter: String,
        chapterProgress: Int,
        chapterTotal: Int
    ) {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_translate)
            .setContentTitle("Translating: $chapter")
            .setContentText("Processing $current of $total images ($successCount successful)")
            .setProgress(total, current, false)
            .setOngoing(true)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Chapter: $chapter ($chapterProgress/$chapterTotal)\n" +
                        "Overall: $current/$total images processed\n" +
                        "Successfully translated: $successCount"))
            .build()

        notificationManager.notify(notificationId, notification)
    }

    private fun showCompletionNotification(successCount: Int, failureCount: Int, total: Int) {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_translate)
            .setContentTitle("Translation Complete")
            .setContentText("Processed $total images ($successCount successful, $failureCount failed)")
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notificationId + 1, notification)
    }

    companion object {
        private const val CONCURRENT_WORKERS = 2
        private const val TAG = "TranslationWorker"
        private const val CHANNEL_ID = "batch_translation_channel"
        private val SUPPORTED_FORMATS = setOf("jpg", "jpeg", "png")
    }
}
