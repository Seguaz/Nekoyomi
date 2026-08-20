package eu.kanade.tachiyomi.ui.reader.loader

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage

/**
 * Loader for online novel chapters. The [source] is a manga [HttpSource] that also implements the
 * `NovelSource` marker (tsundoku / NovelSourcery format): it returns a normal page list, and each
 * page's text is fetched on demand via `fetchPageText`. Rendered by the text viewer.
 *
 * Detection + the text fetch go through [NovelSourceCompat] because third-party extensions bundle
 * their own `NovelSource` class (different class loader), so a direct cast/`is` would fail.
 */
internal class NovelHttpPageLoader(
    private val chapter: ReaderChapter,
    private val source: HttpSource,
) : PageLoader(), TextPageLoader {

    override var isLocal: Boolean = false

    override suspend fun getPages(): List<ReaderPage> {
        // Novel sources short-circuit the page list: a single page whose url is the chapter, fetched
        // as text on demand via fetchPageText. This matches the tsundoku / NovelSourcery contract
        // ("the app's getPageList short-circuit returns the stub without calling pageListParse") and
        // avoids a redundant chapter fetch that can break sources which redirect.
        return listOf(
            ReaderPage(0, chapter.chapter.url).apply {
                status = Page.State.READY
            },
        )
    }

    override suspend fun getPageText(page: ReaderPage): String {
        // ReaderPage is-a Page, so it can be passed straight to the source.
        return NovelSourceCompat.fetchPageText(source, page)
    }

    override suspend fun loadPage(page: ReaderPage) {
        check(!isRecycled)
    }
}
