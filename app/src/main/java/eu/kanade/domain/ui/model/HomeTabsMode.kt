package eu.kanade.domain.ui.model

/**
 * Controls which of the Manga/Anime sub-tabs are shown (and which is the default) in the split home
 * sections: Updates, History and Browse.
 */
enum class HomeTabsMode {
    /** Both tabs, Anime selected first (the classic default). */
    ANIME_FIRST,

    /** Both tabs, Manga selected first. */
    MANGA_FIRST,

    /** Only the Anime tab. */
    ANIME_ONLY,

    /** Only the Manga tab. */
    MANGA_ONLY,
    ;

    val showAnime: Boolean get() = this != MANGA_ONLY
    val showManga: Boolean get() = this != ANIME_ONLY

    /** Index to select first in the resulting [showAnime]/[showManga] tab list. */
    val defaultIndex: Int get() = if (this == MANGA_FIRST) 1 else 0
}
