package tachiyomi.domain.category.novel.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.category.novel.repository.NovelCategoryRepository
import tachiyomi.domain.category.model.Category

class GetVisibleNovelCategories(
    private val categoryRepository: NovelCategoryRepository,
) {
    fun subscribe(): Flow<List<Category>> {
        return categoryRepository.getAllVisibleNovelCategoriesAsFlow()
    }

    fun subscribe(mangaId: Long): Flow<List<Category>> {
        return categoryRepository.getVisibleCategoriesByNovelIdAsFlow(mangaId)
    }

    suspend fun await(): List<Category> {
        return categoryRepository.getAllVisibleNovelCategories()
    }

    suspend fun await(mangaId: Long): List<Category> {
        return categoryRepository.getVisibleCategoriesByNovelId(mangaId)
    }
}
