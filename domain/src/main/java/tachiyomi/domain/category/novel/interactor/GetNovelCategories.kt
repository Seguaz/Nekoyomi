package tachiyomi.domain.category.novel.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.category.novel.repository.NovelCategoryRepository
import tachiyomi.domain.category.model.Category

class GetNovelCategories(
    private val categoryRepository: NovelCategoryRepository,
) {
    fun subscribe(): Flow<List<Category>> {
        return categoryRepository.getAllNovelCategoriesAsFlow()
    }

    fun subscribe(mangaId: Long): Flow<List<Category>> {
        return categoryRepository.getCategoriesByNovelIdAsFlow(mangaId)
    }

    suspend fun await(): List<Category> {
        return categoryRepository.getAllNovelCategories()
    }

    suspend fun await(mangaId: Long): List<Category> {
        return categoryRepository.getCategoriesByNovelId(mangaId)
    }
}
