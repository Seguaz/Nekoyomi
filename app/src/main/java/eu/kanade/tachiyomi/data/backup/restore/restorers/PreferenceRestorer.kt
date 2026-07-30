package eu.kanade.tachiyomi.data.backup.restore.restorers

import android.content.Context
import android.util.Log
import eu.kanade.tachiyomi.data.backup.create.BackupCreateJob
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.BackupSourcePreferences
import eu.kanade.tachiyomi.data.backup.models.BooleanPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.FloatPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.IntPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.LongPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.StringPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.StringSetPreferenceValue
import eu.kanade.tachiyomi.data.library.anime.AnimeLibraryUpdateJob
import eu.kanade.tachiyomi.data.library.manga.MangaLibraryUpdateJob
import eu.kanade.tachiyomi.source.sourcePreferences
import tachiyomi.core.common.preference.AndroidPreferenceStore
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.plusAssign
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.category.manga.interactor.GetMangaCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.library.service.LibraryPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class PreferenceRestorer(
    private val context: Context,
    private val getMangaCategories: GetMangaCategories = Injekt.get(),
    private val getAnimeCategories: GetAnimeCategories = Injekt.get(),
    private val preferenceStore: PreferenceStore = Injekt.get(),
) {
    suspend fun restoreApp(
        preferences: List<BackupPreference>,
        backupCategories: List<BackupCategory>?,
        backupAnimeCategories: List<BackupCategory>?,
    ) {
        restorePreferences(
            preferences,
            preferenceStore,
            backupCategories,
            backupAnimeCategories,
        )

        AnimeLibraryUpdateJob.setupTask(context)
        MangaLibraryUpdateJob.setupTask(context)
        BackupCreateJob.setupTask(context)
    }

    suspend fun restoreSource(preferences: List<BackupSourcePreferences>) {
        preferences.forEach {
            val sourcePrefs = AndroidPreferenceStore(context, sourcePreferences(it.sourceKey))
            restorePreferences(it.prefs, sourcePrefs)
        }
    }

    private suspend fun restorePreferences(
        toRestore: List<BackupPreference>,
        preferenceStore: PreferenceStore,
        backupCategories: List<BackupCategory>? = null,
        backupAnimeCategories: List<BackupCategory>? = null,
    ) {
        val restoreCategories = backupCategories != null || backupAnimeCategories != null
        val allMangaCategories = if (restoreCategories) getMangaCategories.await() else emptyList()
        val allAnimeCategories = if (restoreCategories) getAnimeCategories.await() else emptyList()

        val mangaCategoriesByName = allMangaCategories.associateBy { it.name }
        val animeCategoriesByName = allAnimeCategories.associateBy { it.name }
        val backupMangaCategoriesById = backupCategories?.associateBy { it.id.toString() }.orEmpty()
        val backupAnimeCategoriesById = backupAnimeCategories?.associateBy { it.id.toString() }.orEmpty()

        val prefs = preferenceStore.getAll()
        toRestore.forEach { (key, value) ->
            try {
                when (value) {
                    is IntPreferenceValue -> {
                        if (prefs[key] is Int?) {
                            val newValue = if (key == LibraryPreferences.DEFAULT_MANGA_CATEGORY_PREF_KEY) {
                                backupMangaCategoriesById[value.value.toString()]
                                    ?.let { mangaCategoriesByName[it.name]?.id?.toInt() }
                            } else if (key == LibraryPreferences.DEFAULT_ANIME_CATEGORY_PREF_KEY) {
                                backupAnimeCategoriesById[value.value.toString()]
                                    ?.let { animeCategoriesByName[it.name]?.id?.toInt() }
                            } else {
                                value.value
                            }

                            newValue?.let { preferenceStore.getInt(key).set(it) }
                        }
                    }
                    is LongPreferenceValue -> {
                        if (prefs[key] is Long?) {
                            preferenceStore.getLong(key).set(value.value)
                        }
                    }
                    is FloatPreferenceValue -> {
                        if (prefs[key] is Float?) {
                            preferenceStore.getFloat(key).set(value.value)
                        }
                    }
                    is StringPreferenceValue -> {
                        if (prefs[key] is String?) {
                            preferenceStore.getString(key).set(value.value)
                        }
                    }
                    is BooleanPreferenceValue -> {
                        if (prefs[key] is Boolean?) {
                            preferenceStore.getBoolean(key).set(value.value)
                        }
                    }
                    is StringSetPreferenceValue -> {
                        if (prefs[key] is Set<*>?) {
                            val restored = restoreCategoriesPreference(
                                key,
                                value.value,
                                preferenceStore,
                                backupMangaCategoriesById,
                                backupAnimeCategoriesById,
                                mangaCategoriesByName,
                                animeCategoriesByName,
                            )
                            if (!restored) preferenceStore.getStringSet(key).set(value.value)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("PreferenceRestorer", "Failed to restore preference <$key>", e)
            }
        }
    }

    private fun restoreCategoriesPreference(
        key: String,
        value: Set<String>,
        preferenceStore: PreferenceStore,
        backupMangaCategoriesById: Map<String, BackupCategory>,
        backupAnimeCategoriesById: Map<String, BackupCategory>,
        mangaCategoriesByName: Map<String, Category>,
        animeCategoriesByName: Map<String, Category>,
    ): Boolean {
        val ids = mapCategoryPreferenceIds(
            key = key,
            backupIds = value,
            backupMangaCategoriesById = backupMangaCategoriesById,
            backupAnimeCategoriesById = backupAnimeCategoriesById,
            mangaCategoriesByName = mangaCategoriesByName,
            animeCategoriesByName = animeCategoriesByName,
        ) ?: return false

        if (ids.isNotEmpty()) {
            preferenceStore.getStringSet(key) += ids
        }
        return true
    }
}

/**
 * Resolves the backed-up category ids stored in a category preference [key] to the local category
 * ids of the matching type. Manga keys resolve against manga categories and anime keys against anime
 * categories, matching by category name. Returns null when [key] is not a category preference.
 */
internal fun mapCategoryPreferenceIds(
    key: String,
    backupIds: Set<String>,
    backupMangaCategoriesById: Map<String, BackupCategory>,
    backupAnimeCategoriesById: Map<String, BackupCategory>,
    mangaCategoriesByName: Map<String, Category>,
    animeCategoriesByName: Map<String, Category>,
): List<String>? {
    val isAnimeKey = key in LibraryPreferences.animeCategoryPreferenceKeys ||
        key in DownloadPreferences.animeCategoryPreferenceKeys
    val isMangaKey = key in LibraryPreferences.mangaCategoryPreferenceKeys ||
        key in DownloadPreferences.mangaCategoryPreferenceKeys
    if (!isAnimeKey && !isMangaKey) return null

    val backupCategoriesById = if (isAnimeKey) backupAnimeCategoriesById else backupMangaCategoriesById
    val localCategoriesByName = if (isAnimeKey) animeCategoriesByName else mangaCategoriesByName

    return backupIds.mapNotNull { backupId ->
        backupCategoriesById[backupId]?.name?.let { name ->
            localCategoriesByName[name]?.id?.toString()
        }
    }
}
