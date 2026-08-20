package eu.kanade.tachiyomi.ui.reader.loader

import eu.kanade.tachiyomi.ui.reader.model.ReaderPage

/**
 * Implemented by [PageLoader]s whose pages carry text (novel) content instead of images. The
 * [eu.kanade.tachiyomi.ui.reader.viewer.text.TextViewer] renders [getPageText] for each page.
 *
 * [getPageText] is suspend so online novel sources can fetch the text on demand.
 */
interface TextPageLoader {
    suspend fun getPageText(page: ReaderPage): String
}
