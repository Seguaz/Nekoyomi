package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import tachiyomi.domain.category.model.Category

class PreferenceRestorerTest {

    // Local categories on the device being restored to. "Favorites" exists in BOTH manga and anime,
    // and manga/anime category ids share the same numeric space (separate tables).
    private val mangaCategoriesByName = mapOf(
        "Favorites" to category(10, "Favorites"),
        "Reading" to category(11, "Reading"),
    )
    private val animeCategoriesByName = mapOf(
        "Favorites" to category(20, "Favorites"),
        "Watching" to category(21, "Watching"),
    )

    // Backup categories from the source device. Manga id 1 and anime id 1 collide numerically.
    private val backupMangaCategoriesById = mapOf(
        "1" to backupCategory(1, "Favorites"),
        "2" to backupCategory(2, "Reading"),
    )
    private val backupAnimeCategoriesById = mapOf(
        "1" to backupCategory(1, "Favorites"),
        "3" to backupCategory(3, "Watching"),
    )

    @Test
    fun mangaCategoryKeyResolvesOnlyMangaIds() {
        val ids = mapCategoryPreferenceIds(
            key = "library_update_categories",
            backupIds = setOf("1", "2"),
            backupMangaCategoriesById = backupMangaCategoriesById,
            backupAnimeCategoriesById = backupAnimeCategoriesById,
            mangaCategoriesByName = mangaCategoriesByName,
            animeCategoriesByName = animeCategoriesByName,
        )

        // Only manga ids: the same-named anime "Favorites" (id 20) must NOT leak in.
        assertEquals(setOf("10", "11"), ids?.toSet())
    }

    @Test
    fun animeCategoryKeyResolvesOnlyAnimeIds() {
        val ids = mapCategoryPreferenceIds(
            key = "animelib_update_categories",
            backupIds = setOf("1", "3"),
            backupMangaCategoriesById = backupMangaCategoriesById,
            backupAnimeCategoriesById = backupAnimeCategoriesById,
            mangaCategoriesByName = mangaCategoriesByName,
            animeCategoriesByName = animeCategoriesByName,
        )

        // Resolved via the anime backup + anime local categories, never via the colliding manga id.
        assertEquals(setOf("20", "21"), ids?.toSet())
    }

    @Test
    fun nonCategoryKeyReturnsNull() {
        val ids = mapCategoryPreferenceIds(
            key = "some_unrelated_pref",
            backupIds = setOf("1"),
            backupMangaCategoriesById = backupMangaCategoriesById,
            backupAnimeCategoriesById = backupAnimeCategoriesById,
            mangaCategoriesByName = mangaCategoriesByName,
            animeCategoriesByName = animeCategoriesByName,
        )

        assertNull(ids)
    }

    private fun category(id: Long, name: String) = Category(
        id = id,
        name = name,
        order = 0,
        flags = 0,
        hidden = false,
    )

    private fun backupCategory(id: Long, name: String) = BackupCategory(
        name = name,
        id = id,
    )
}
