package eu.kanade.tachiyomi.ui.library

/**
 * How the library is grouped into pager sections. [NONE] uses the user's real categories; the other
 * modes ignore categories and generate synthetic ones on the fly (one per source / per status), so
 * the existing category-tab pager renders them unchanged.
 */
enum class LibraryGroupMode {
    NONE,
    BY_SOURCE,
    BY_STATUS,
    BY_LANGUAGE,
    BY_GENRE,
    ;

    companion object {
        fun fromInt(value: Int): LibraryGroupMode = entries.getOrElse(value) { NONE }
    }
}
