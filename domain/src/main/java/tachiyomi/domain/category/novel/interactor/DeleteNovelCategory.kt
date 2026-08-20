package tachiyomi.domain.category.novel.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.model.CategoryUpdate
import tachiyomi.domain.category.novel.repository.NovelCategoryRepository

class DeleteNovelCategory(
    private val categoryRepository: NovelCategoryRepository,
) {

    suspend fun await(categoryId: Long) = withNonCancellableContext {
        try {
            categoryRepository.deleteNovelCategory(categoryId)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            return@withNonCancellableContext Result.InternalError(e)
        }

        val categories = categoryRepository.getAllNovelCategories()
        val updates = categories.mapIndexed { index, category ->
            CategoryUpdate(
                id = category.id,
                order = index.toLong(),
            )
        }

        try {
            categoryRepository.updatePartialNovelCategories(updates)
            Result.Success
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            Result.InternalError(e)
        }
    }

    sealed interface Result {
        data object Success : Result
        data class InternalError(val error: Throwable) : Result
    }
}
