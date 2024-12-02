
object TestUtils {
    fun createTestBitmap(width: Int = 100, height: Int = 100): Bitmap {
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    }

    fun createTestMangaPage(
        pageNumber: Int = 1,
        chapterInfo: MangaPage.ChapterInfo? = null
    ): MangaPage {
        return MangaPage(
            image = createTestBitmap(),
            path = "test/path/page$pageNumber.jpg",
            pageNumber = pageNumber,
            chapterInfo = chapterInfo
        )
    }
}
