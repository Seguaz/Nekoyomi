package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.model.Page

/**
 * Marker interface for manga [MangaSource]s that actually provide **novel (text)** content.
 *
 * This matches the established tsundoku / NovelSourcery / Tadami novel-extension ABI: a novel
 * extension is a normal manga [eu.kanade.tachiyomi.source.online.HttpSource] that also implements
 * this interface, returns a normal page list, and delivers each page's text via [fetchPageText].
 * The reader renders the text (instead of loading an image) when the source `is NovelSource`.
 *
 * Do NOT change this contract — it is a binary-compatibility surface shared with third-party
 * extensions compiled against it.
 */
interface NovelSource {

    /**
     * Whether this source provides novel (text-based) content. Always true for implementations.
     */
    val isNovelSource: Boolean
        get() = true

    /**
     * Fetches the text content for a page.
     *
     * @param page the page to fetch text for.
     * @return the text content of the page (may be HTML).
     */
    suspend fun fetchPageText(page: Page): String
}
