package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.domain.category.novel.interactor.GetNovelCategories
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelCategoriesRestorer(
    private val mangaHandler: MangaDatabaseHandler = Injekt.get(),
    private val getNovelCategories: GetNovelCategories = Injekt.get(),
) {

    suspend operator fun invoke(backupCategories: List<BackupCategory>) {
        if (backupCategories.isNotEmpty()) {
            val dbCategories = getNovelCategories.await()
            val dbCategoriesByName = dbCategories.associateBy { it.name }
            var nextOrder = dbCategories.maxOfOrNull { it.order }?.plus(1) ?: 0

            backupCategories
                .sortedBy { it.order }
                .forEach {
                    if (dbCategoriesByName[it.name] != null) return@forEach
                    val order = nextOrder++
                    mangaHandler.await {
                        novel_categoriesQueries.insert(it.name, order, it.flags)
                    }
                }
        }
    }
}
