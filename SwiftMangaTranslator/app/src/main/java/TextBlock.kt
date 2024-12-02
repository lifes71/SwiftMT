// TextBlock.kt

import android.graphics.Rect
import com.google.mlkit.vision.text.Text
import java.util.UUID

/**
 * Model class representing a block of detected text and its translation
 * Works with ML Kit's text recognition output and handles translation data
 */
data class TextBlock(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val bounds: Rect,
    var translatedText: String? = null,
    val confidence: Float? = null,
    val language: String? = null,
    val lines: List<Line> = emptyList()
) {
    /**
     * Secondary constructor to create TextBlock from ML Kit's Text.TextBlock
     */
    constructor(mlKitBlock: Text.TextBlock) : this(
        text = mlKitBlock.text,
        bounds = mlKitBlock.boundingBox ?: Rect(),
        lines = mlKitBlock.lines.map { Line(it) },
        language = mlKitBlock.recognizedLanguage
    )

    /**
     * Nested data class representing a line of text within the block
     */
    data class Line(
        val text: String,
        val bounds: Rect,
        val confidence: Float? = null,
        val elements: List<Element> = emptyList()
    ) {
        constructor(mlKitLine: Text.Line) : this(
            text = mlKitLine.text,
            bounds = mlKitLine.boundingBox ?: Rect(),
            elements = mlKitLine.elements.map { Element(it) }
        )
    }

    /**
     * Nested data class representing individual text elements (words/characters)
     */
    data class Element(
        val text: String,
        val bounds: Rect,
        val confidence: Float? = null
    ) {
        constructor(mlKitElement: Text.Element) : this(
            text = mlKitElement.text,
            bounds = mlKitElement.boundingBox ?: Rect()
        )
    }

    /**
     * Calculate the font size based on bounding box height
     */
    fun estimateFontSize(): Float {
        return if (lines.isNotEmpty()) {
            val firstLine = lines.first()
            firstLine.bounds.height().toFloat()
        } else {
            bounds.height().toFloat()
        }
    }

    /**
     * Calculate appropriate font size for translated text
     */
    fun estimateTranslatedFontSize(targetLanguage: String = "en"): Float {
        val originalFontSize = estimateFontSize()
        val translatedLength = translatedText?.length ?: return originalFontSize
        val originalLength = text.length

        // Adjust font size based on text length difference and target language
        return when (targetLanguage) {
            "en" -> {
                // English typically needs more space than Japanese
                val scaleFactor = 0.8f * (originalLength.toFloat() / translatedLength)
                originalFontSize * scaleFactor.coerceIn(0.5f, 1.2f)
            }
            "ko", "zh" -> {
                // Korean and Chinese can maintain similar size
                originalFontSize
            }
            else -> {
                // Default scaling for other languages
                val scaleFactor = originalLength.toFloat() / translatedLength
                originalFontSize * scaleFactor.coerceIn(0.6f, 1.1f)
            }
        }
    }

    /**
     * Check if the block likely contains Japanese text
     */
    fun isJapaneseText(): Boolean {
        val japanesePattern = Regex("[\\p{IsHiragana}\\p{IsKatakana}\\p{IsCJKUnifiedIdeographs}]")
        return text.contains(japanesePattern)
    }

    /**
     * Get the block's center point
     */
    val center: Pair<Int, Int>
        get() = Pair(
            bounds.left + bounds.width() / 2,
            bounds.top + bounds.height() / 2
        )

    /**
     * Check if this block overlaps with another block
     */
    fun overlapsWith(other: TextBlock): Boolean {
        return bounds.intersect(other.bounds)
    }

    /**
     * Calculate the overlap percentage with another block
     */
    fun calculateOverlapPercentage(other: TextBlock): Float {
        if (!overlapsWith(other)) return 0f

        val intersectionRect = Rect(bounds)
        intersectionRect.intersect(other.bounds)

        val intersectionArea = intersectionRect.width() * intersectionRect.height()
        val thisArea = bounds.width() * bounds.height()

        return (intersectionArea.toFloat() / thisArea).coerceIn(0f, 1f)
    }

    companion object {
        /**
         * Merge overlapping text blocks
         */
        fun mergeOverlappingBlocks(blocks: List<TextBlock>, overlapThreshold: Float = 0.5f): List<TextBlock> {
            val mergedBlocks = mutableListOf<TextBlock>()
            val processedBlocks = blocks.toMutableList()

            while (processedBlocks.isNotEmpty()) {
                val current = processedBlocks.removeAt(0)
                val overlapping = processedBlocks.filter { 
                    current.calculateOverlapPercentage(it) > overlapThreshold 
                }

                if (overlapping.isEmpty()) {
                    mergedBlocks.add(current)
                } else {
                    // Create merged block
                    val allBlocks = listOf(current) + overlapping
                    processedBlocks.removeAll(overlapping)

                    // Calculate combined bounds
                    val minLeft = allBlocks.minOf { it.bounds.left }
                    val minTop = allBlocks.minOf { it.bounds.top }
                    val maxRight = allBlocks.maxOf { it.bounds.right }
                    val maxBottom = allBlocks.maxOf { it.bounds.bottom }

                    mergedBlocks.add(
                        TextBlock(
                            text = allBlocks.joinToString(" ") { it.text },
                            bounds = Rect(minLeft, minTop, maxRight, maxBottom),
                            lines = allBlocks.flatMap { it.lines }
                        )
                    )
                }
            }

            return mergedBlocks
        }
    }
}
