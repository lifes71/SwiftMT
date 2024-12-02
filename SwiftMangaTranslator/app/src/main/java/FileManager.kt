import android.util.Log
import java.io.File

class FileManager {
    fun generateOutputPath(inputPath: String): String {
        val inputDir = File(inputPath)
        val parentDir = inputDir.parentFile
        val baseName = inputDir.name
        
        return when {
            // If it's a chapter folder (contains images directly)
            containsImages(inputDir) -> {
                "${inputDir.absolutePath}-translated"
            }
            // If it's a manga folder (contains chapter folders)
            hasValidStructure(inputDir) -> {
                "${parentDir.absolutePath}/${baseName}-translated"
            }
            else -> throw IllegalArgumentException("Invalid folder structure")
        }
    }

    fun createOutputDirectories(inputPath: String): Boolean {
        try {
            val inputDir = File(inputPath)
            val outputBasePath = generateOutputPath(inputPath)
            
            // If input is a single chapter folder
            if (containsImages(inputDir)) {
                return File(outputBasePath).mkdirs()
            }
            
            // If input contains multiple chapter folders
            inputDir.listFiles()?.forEach { chapterDir ->
                if (chapterDir.isDirectory && hasValidStructure(chapterDir)) {
                    val relativePath = chapterDir.name
                    val outputChapterPath = "$outputBasePath/$relativePath"
                    File(outputChapterPath).mkdirs()
                }
            }
            return true
        } catch (e: Exception) {
            Log.e("FileManager", "Error creating output directories", e)
            return false
        }
    }

    fun validateInputOutputPaths(inputPath: String): Triple<Boolean, String?, String?> {
        try {
            val inputDir = File(inputPath)
            val outputPath = generateOutputPath(inputPath)
            val outputDir = File(outputPath)

            return when {
                !inputDir.exists() -> 
                    Triple(false, null, "Input directory does not exist")
                !inputDir.isDirectory -> 
                    Triple(false, null, "Input path is not a directory")
                outputDir.exists() && outputDir.list()?.isNotEmpty() == true -> 
                    Triple(false, null, "Output directory already exists and is not empty")
                !hasValidStructure(inputDir) -> 
                    Triple(false, null, "Invalid folder structure")
                else -> 
                    Triple(true, outputPath, null)
            }
        } catch (e: Exception) {
            return Triple(false, null, "Error validating paths: ${e.message}")
        }
    }

    /**
     * Scans for chapters in the root path
     * Returns a list of chapter paths sorted numerically
     */
    fun scanForChapters(rootPath: String): List<String> {
        return File(rootPath)
            .listFiles()
            ?.filter { it.isDirectory }
            ?.sortedBy { dir -> 
                // Extract numbers from directory name for natural sorting
                dir.name.filter { it.isDigit() }.toIntOrNull() ?: Int.MAX_VALUE
            }
            ?.map { it.absolutePath }
            ?: emptyList()
    }

    /**
     * Checks if the given path contains nested chapter folders
     */
    private fun hasNestedChapters(path: String): Boolean {
        return File(path)
            .listFiles()
            ?.any { it.isDirectory && it.name.matches(Regex(".*chapter.*\\d+.*", RegexOption.IGNORE_CASE)) }
            ?: false
    }

    /**
     * Copies the directory structure from input to output path
     * without copying the actual files
     */
    private fun copyDirectoryStructure(inputPath: String, outputPath: String) {
        File(inputPath).listFiles()
            ?.filter { it.isDirectory }
            ?.forEach { sourceDir ->
                val destDir = File(outputPath, sourceDir.name)
                if (!destDir.exists()) {
                    destDir.mkdirs()
                }
                // Recursively copy nested structure
                copyDirectoryStructure(sourceDir.absolutePath, destDir.absolutePath)
            }
    }

    fun validatePath(path: String): Boolean {
        val dir = File(path)
        return when {
            !dir.exists() -> false
            !dir.isDirectory -> false
            !hasValidStructure(dir) -> false
            !containsImages(dir) -> false
            else -> true
        }
    }

    private fun hasValidStructure(dir: File): Boolean {
        // Check if it's a single chapter directory
        if (containsImages(dir)) {
            return true
        }
        
        // Check if it contains chapter subdirectories
        return dir.listFiles()?.any { subDir ->
            subDir.isDirectory && (
                subDir.name.contains("chapter", ignoreCase = true) ||
                subDir.name.matches(Regex("ch[0-9]+.*", RegexOption.IGNORE_CASE))
            )
        } ?: false
    }

    /**
     * Checks if the directory contains image files
     */
    private fun containsImages(dir: File): Boolean {
        val imageExtensions = setOf("jpg", "jpeg", "png", "webp")
        return dir.listFiles()?.any { file ->
            file.isFile && file.extension.toLowerCase() in imageExtensions
        } ?: false
    }
}
