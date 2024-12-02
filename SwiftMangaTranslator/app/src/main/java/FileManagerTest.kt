
import org.junit.Test
import org.junit.Assert.*
import java.io.File

class FileManagerTest {
    private val fileManager = FileManager()

    @Test
    fun `test generateOutputPath for single chapter`() {
        val inputPath = "/storage/manga/chapter1"
        val expectedPath = "$inputPath-translated"
        
        // Create temporary test directory structure
        createTestDirectory(inputPath, true)
        
        val result = fileManager.generateOutputPath(inputPath)
        assertEquals(expectedPath, result)
        
        // Cleanup
        cleanupTestDirectory(inputPath)
    }

    @Test
    fun `test validatePath with invalid directory`() {
        val invalidPath = "/nonexistent/path"
        assertFalse(fileManager.validatePath(invalidPath))
    }

    @Test
    fun `test hasValidStructure with proper manga folder`() {
        val testPath = "/storage/manga/series1"
        createTestMangaStructure(testPath)
        
        assertTrue(fileManager.validatePath(testPath))
        
        cleanupTestDirectory(testPath)
    }

    private fun createTestDirectory(path: String, withImages: Boolean = false) {
        val dir = File(path)
        dir.mkdirs()
        if (withImages) {
            File(dir, "page1.jpg").createNewFile()
            File(dir, "page2.jpg").createNewFile()
        }
    }

    private fun createTestMangaStructure(basePath: String) {
        val baseDir = File(basePath)
        baseDir.mkdirs()
        
        // Create chapter folders
        File(baseDir, "chapter1").also { 
            it.mkdirs()
            File(it, "page1.jpg").createNewFile()
        }
        
        File(baseDir, "chapter2").also {
            it.mkdirs()
            File(it, "page1.jpg").createNewFile()
        }
    }

    private fun cleanupTestDirectory(path: String) {
        File(path).deleteRecursively()
    }
}
