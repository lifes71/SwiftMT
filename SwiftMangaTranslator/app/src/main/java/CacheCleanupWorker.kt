import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class CacheCleanupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val databaseManager = DatabaseManager.getInstance(applicationContext)
        databaseManager.cleanupOldCache()
        return Result.success()
    }
}
