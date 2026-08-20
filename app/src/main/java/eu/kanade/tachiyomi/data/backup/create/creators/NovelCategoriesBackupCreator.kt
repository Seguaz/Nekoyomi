package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.backupCategoryMapper
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.novel.interactor.GetNovelCategories
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelCategoriesBackupCreator(
    private val getNovelCategories: GetNovelCategories = Injekt.get(),
) {

    suspend operator fun invoke(): List<BackupCategory> {
        return getNovelCategories.await()
            .filterNot(Category::isSystemCategory)
            .map(backupCategoryMapper)
    }
}
