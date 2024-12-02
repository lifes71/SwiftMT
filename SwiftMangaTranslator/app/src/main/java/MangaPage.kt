import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.File

data class MangaPage(
    val image: Bitmap,
    val path: String,
    val pageNumber: Int,
    val chapterInfo: ChapterInfo? = null
) {
    data class ChapterInfo(
        val chapterNumber: Int,
        val seriesName: String,
        val volumeNumber: Int? = null
    )

    companion object {
        fun fromFile(file: File, pageNumber: Int): MangaPage? {
            return try {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                MangaPage(
                    image = bitmap,
                    path = file.absolutePath,
                    pageNumber = pageNumber
                )
            } catch (e: Exception) {
                Log.e("MangaPage", "Failed to load image: ${file.absolutePath}", e)
                null
            }
        }
    }
}
