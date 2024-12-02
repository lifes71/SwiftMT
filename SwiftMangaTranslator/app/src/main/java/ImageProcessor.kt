import android.content.Context
import android.graphics.*
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.util.Log
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.mlkit.vision.common.InputImage

class ImageProcessor(private val context: Context) {
    private val textRecognizer = TextRecognition.getClient(
        TextRecognizerOptions.Builder()
            .setExecutor(Dispatchers.Default.asExecutor())
            .build()
    )

    suspend fun processImage(bitmap: Bitmap): ProcessingResult {
        return try {
            // 1. Preprocess the image
            val preprocessedImage = preprocessImage(bitmap)
            
            // 2. Detect text regions
            val textBlocks = detectTextBlocks(preprocessedImage)
            
            // 3. Process each text region separately
            val processedRegions = processTextRegions(preprocessedImage, textBlocks)
            
            // 4. Create final image
            val finalImage = combineProcessedRegions(preprocessedImage, processedRegions)
            
            ProcessingResult.Success(finalImage, textBlocks)
        } catch (e: Exception) {
            ProcessingResult.Error(e)
        }
    }

    private fun preprocessImage(bitmap: Bitmap): Bitmap {
        return bitmap.copy(Bitmap.Config.ARGB_8888, true).apply {
            // 1. Contrast enhancement
            enhanceContrast(this)
            
            // 2. Noise reduction
            reduceNoise(this)
            
            // 3. Global image adjustments
            adjustImageGlobally(this)
        }
    }

    private fun enhanceContrast(bitmap: Bitmap) {
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply {
                // Increased contrast for better text detection
                setScale(1.3f, 1.3f, 1.3f, 1f)
            })
        }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
    }

    private fun reduceNoise(bitmap: Bitmap) {
        val renderScript = RenderScript.create(context)
        try {
            val blurScript = ScriptIntrinsicBlur.create(renderScript, Element.U8_4(renderScript))
            val alloc = Allocation.createFromBitmap(renderScript, bitmap)
            
            // Adjust blur radius based on image size
            val blurRadius = (bitmap.width * 0.005f).coerceIn(0.5f, 2.5f)
            blurScript.setRadius(blurRadius)
            blurScript.setInput(alloc)
            blurScript.forEach(alloc)
            alloc.copyTo(bitmap)
        } finally {
            renderScript.destroy()
        }
    }

    private fun adjustImageGlobally(bitmap: Bitmap) {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        
        // Apply adaptive thresholding for better text extraction
        val threshold = calculateAdaptiveThreshold(pixels)
        applyThreshold(pixels, threshold)
        
        bitmap.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }

    private suspend fun detectTextBlocks(bitmap: Bitmap): List<TextBlock> {
        val image = InputImage.fromBitmap(bitmap, 0)
        return withContext(Dispatchers.Default) {
            try {
                textRecognizer.process(image)
                    .await()
                    .textBlocks
                    .map { TextBlock(it) }
                    .let { TextBlock.mergeOverlappingBlocks(it) }
            } catch (e: Exception) {
                Log.e("ImageProcessor", "Text detection failed", e)
                emptyList()
            }
        }
    }

    private fun processTextRegions(bitmap: Bitmap, textBlocks: List<TextBlock>): List<ProcessedRegion> {
        return textBlocks.map { block ->
            val region = extractRegion(bitmap, block.bounds)
            val processedRegion = enhanceTextRegion(region)
            ProcessedRegion(processedRegion, block)
        }
    }

    private fun extractRegion(bitmap: Bitmap, bounds: Rect): Bitmap {
        // Add padding around text region
        val padding = (bounds.width() * 0.1f).toInt()
        val paddedBounds = Rect(
            (bounds.left - padding).coerceAtLeast(0),
            (bounds.top - padding).coerceAtLeast(0),
            (bounds.right + padding).coerceAtMost(bitmap.width),
            (bounds.bottom + padding).coerceAtMost(bitmap.height)
        )
        
        return Bitmap.createBitmap(
            bitmap,
            paddedBounds.left,
            paddedBounds.top,
            paddedBounds.width(),
            paddedBounds.height()
        )
    }

    private fun enhanceTextRegion(region: Bitmap): Bitmap {
        return region.copy(Bitmap.Config.ARGB_8888, true).apply {
            val pixels = IntArray(width * height)
            getPixels(pixels, 0, width, 0, 0, width, height)
            
            // Local adaptive thresholding for text region
            val threshold = calculateOtsuThreshold(pixels)
            for (i in pixels.indices) {
                val pixel = pixels[i]
                val gray = (Color.red(pixel) * 0.299 + 
                           Color.green(pixel) * 0.587 + 
                           Color.blue(pixel) * 0.114).toInt()
                pixels[i] = if (gray > threshold) Color.WHITE else Color.BLACK
            }
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }

    private fun calculateAdaptiveThreshold(pixels: IntArray): Int {
        // Implementation of adaptive thresholding
        return calculateOtsuThreshold(pixels)
    }

    private fun applyThreshold(pixels: IntArray, threshold: Int) {
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val gray = (Color.red(pixel) * 0.299 + 
                       Color.green(pixel) * 0.587 + 
                       Color.blue(pixel) * 0.114).toInt()
            pixels[i] = if (gray > threshold) Color.WHITE else Color.BLACK
        }
    }

    private fun combineProcessedRegions(originalImage: Bitmap, regions: List<ProcessedRegion>): Bitmap {
        return originalImage.copy(Bitmap.Config.ARGB_8888, true).apply {
            val canvas = Canvas(this)
            regions.forEach { region ->
                canvas.drawBitmap(
                    region.bitmap,
                    region.textBlock.bounds.left.toFloat(),
                    region.textBlock.bounds.top.toFloat(),
                    null
                )
            }
        }
    }

    fun drawTranslations(bitmap: Bitmap, translatedBlocks: List<TextBlock>): Bitmap {
        return bitmap.copy(Bitmap.Config.ARGB_8888, true).apply {
            val canvas = Canvas(this)
            val paint = Paint().apply {
                color = Color.WHITE
                style = Paint.Style.FILL
                setShadowLayer(3f, 0f, 0f, Color.BLACK)
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }

            translatedBlocks.forEach { block ->
                // Draw background for better readability
                paint.color = Color.WHITE
                canvas.drawRect(block.bounds, paint)

                // Draw translated text
                paint.color = Color.BLACK
                paint.textSize = block.estimateTranslatedFontSize()
                
                val x = block.bounds.centerX().toFloat()
                val y = block.bounds.centerY().toFloat()
                
                block.translatedText?.let { text ->
                    canvas.drawText(text, x, y, paint)
                }
            }
        }
    }

    sealed class ProcessingResult {
        data class Success(
            val processedImage: Bitmap,
            val textBlocks: List<TextBlock>
        ) : ProcessingResult()
        
        data class Error(val exception: Exception) : ProcessingResult()
    }

    private data class ProcessedRegion(
        val bitmap: Bitmap,
        val textBlock: TextBlock
    )
}
