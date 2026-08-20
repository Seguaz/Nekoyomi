package tachiyomi.domain.category.novel.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.novel.repository.NovelCategoryRepository

class SetNovelCategories(
    private val categoryRepository: NovelCategoryRepository,
) {

    suspend fun await(mangaId: Long, categoryIds: List<Long>) {
        try {
            categoryRepository.setNovelCategories(mangaId, categoryIds)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }
}
