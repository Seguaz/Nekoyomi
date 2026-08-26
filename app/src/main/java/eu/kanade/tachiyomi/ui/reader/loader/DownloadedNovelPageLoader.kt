package eu.kanade.tachiyomi.ui.reader.loader

import eu.kanade.tachiyomi.data.download.manga.MangaDownloadProvider
import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import tachiyomi.domain.entries.manga.model.Manga

/**
 * Loader for a downloaded novel chapter. Its text was saved to a single file by the downloader,
 * so this mirrors the online [NovelHttpPageLoader] (one page = the whole chapter's text) but reads
 * it back from disk, letting novels be read offline. Rendered by the text viewer.
 */
internal class DownloadedNovelPageLoader(
    private val chapter: ReaderChapter,
    private val manga: Manga,
    private val source: MangaSource,
    private val downloadProvider: MangaDownloadProvider,
) : PageLoader(), TextPageLoader {

    override var isLocal: Boolean = true

    override suspend fun getPages(): List<ReaderPage> {
        return listOf(
            ReaderPage(0, chapter.chapter.url).apply {
                status = Page.State.READY
            },
        )
    }

    override suspend fun getPageText(page: ReaderPage): String {
        val dbChapter = chapter.chapter
        val chapterDir = downloadProvider.findChapterDir(
            dbChapter.name,
            dbChapter.scanlator,
            manga.title,
            source,
        ) ?: return ""
        val file = chapterDir.findFile(DOWNLOADED_NOVEL_FILENAME) ?: return ""
        return file.openInputStream().use { it.readBytes().toString(Charsets.UTF_8) }
    }

    override suspend fun loadPage(page: ReaderPage) {
        check(!isRecycled)
    }

    companion object {
        /** Name of the file a downloaded novel chapter's text (HTML) is stored under, in its dir. */
        const val DOWNLOADED_NOVEL_FILENAME = "content.html"
    }
}
