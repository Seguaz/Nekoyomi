package eu.kanade.tachiyomi.ui.library.manga

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.data.track.TrackerManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.category.manga.interactor.SetMangaDisplayMode
import tachiyomi.domain.category.manga.interactor.SetSortModeForMangaCategory
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.manga.model.MangaLibrarySort
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.service.LibraryPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Duration.Companion.seconds

class MangaLibrarySettingsScreenModel(
    val preferences: BasePreferences = Injekt.get(),
    val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val setMangaDisplayMode: SetMangaDisplayMode = Injekt.get(),
    private val setSortModeForCategory: SetSortModeForMangaCategory = Injekt.get(),
    trackerManager: TrackerManager = Injekt.get(),
    // When true this backs the Novel library, so category-tab visibility uses the novel-only pref.
    val novelOnly: Boolean = false,
) : ScreenModel {

    /** Category-tabs toggle for the active library (novels keep their own, independent of manga). */
    fun categoryTabsPref() = if (novelOnly) {
        libraryPreferences.categoryTabsNovel()
    } else {
        libraryPreferences.categoryTabs()
    }

    val trackersFlow = trackerManager.loggedInTrackersFlow()
        .stateIn(
            scope = screenModelScope,
            started = SharingStarted.WhileSubscribed(5.seconds.inWholeMilliseconds),
            initialValue = trackerManager.loggedInTrackers(),
        )

    fun toggleFilter(preference: (LibraryPreferences) -> Preference<TriState>) {
        preference(libraryPreferences).getAndSet {
            it.next()
        }
    }

    fun toggleTracker(id: Int) {
        toggleFilter { libraryPreferences.filterTrackedManga(id) }
    }

    /** Cycles a tag through neutral -> included -> excluded -> neutral for the library tag filter. */
    fun cycleGenreFilter(genre: String) {
        val include = libraryPreferences.filterGenresIncludeManga()
        val exclude = libraryPreferences.filterGenresExcludeManga()
        when (genre) {
            in include.get() -> {
                include.set(include.get() - genre)
                exclude.set(exclude.get() + genre)
            }
            in exclude.get() -> exclude.set(exclude.get() - genre)
            else -> include.set(include.get() + genre)
        }
    }

    fun clearGenreFilters() {
        libraryPreferences.filterGenresIncludeManga().set(emptySet())
        libraryPreferences.filterGenresExcludeManga().set(emptySet())
    }

    fun setDisplayMode(mode: LibraryDisplayMode) {
        setMangaDisplayMode.await(mode)
    }

    fun setSort(
        category: Category?,
        mode: MangaLibrarySort.Type,
        direction: MangaLibrarySort.Direction,
    ) {
        screenModelScope.launchIO {
            setSortModeForCategory.await(category, mode, direction)
        }
    }
}
