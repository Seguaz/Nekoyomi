package tachiyomi.data.category.novel

import kotlinx.coroutines.flow.Flow
import tachiyomi.data.Database
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.domain.category.novel.repository.NovelCategoryRepository
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.model.CategoryUpdate

class NovelCategoryRepositoryImpl(
    private val handler: MangaDatabaseHandler,
) : NovelCategoryRepository {

    override suspend fun getNovelCategory(id: Long): Category? {
        return handler.awaitOneOrNull { novel_categoriesQueries.getCategory(id, ::mapCategory) }
    }

    override suspend fun getAllNovelCategories(): List<Category> {
        return handler.awaitList { novel_categoriesQueries.getCategories(::mapCategory) }
    }

    override suspend fun getAllVisibleNovelCategories(): List<Category> {
        return handler.awaitList { novel_categoriesQueries.getVisibleCategories(::mapCategory) }
    }

    override fun getAllNovelCategoriesAsFlow(): Flow<List<Category>> {
        return handler.subscribeToList { novel_categoriesQueries.getCategories(::mapCategory) }
    }

    override fun getAllVisibleNovelCategoriesAsFlow(): Flow<List<Category>> {
        return handler.subscribeToList { novel_categoriesQueries.getVisibleCategories(::mapCategory) }
    }

    override suspend fun getCategoriesByNovelId(mangaId: Long): List<Category> {
        return handler.awaitList {
            novel_categoriesQueries.getCategoriesByNovelId(mangaId, ::mapCategory)
        }
    }

    override suspend fun getVisibleCategoriesByNovelId(mangaId: Long): List<Category> {
        return handler.awaitList {
            novel_categoriesQueries.getVisibleCategoriesByNovelId(mangaId, ::mapCategory)
        }
    }

    override fun getCategoriesByNovelIdAsFlow(mangaId: Long): Flow<List<Category>> {
        return handler.subscribeToList {
            novel_categoriesQueries.getCategoriesByNovelId(mangaId, ::mapCategory)
        }
    }

    override fun getVisibleCategoriesByNovelIdAsFlow(mangaId: Long): Flow<List<Category>> {
        return handler.subscribeToList {
            novel_categoriesQueries.getVisibleCategoriesByNovelId(mangaId, ::mapCategory)
        }
    }

    override suspend fun insertNovelCategory(category: Category) {
        handler.await {
            novel_categoriesQueries.insert(
                name = category.name,
                order = category.order,
                flags = category.flags,
            )
        }
    }

    override suspend fun updatePartialNovelCategory(update: CategoryUpdate) {
        handler.await {
            updatePartialBlocking(update)
        }
    }

    override suspend fun updatePartialNovelCategories(updates: List<CategoryUpdate>) {
        handler.await(inTransaction = true) {
            for (update in updates) {
                updatePartialBlocking(update)
            }
        }
    }

    private fun Database.updatePartialBlocking(update: CategoryUpdate) {
        novel_categoriesQueries.update(
            name = update.name,
            order = update.order,
            flags = update.flags,
            hidden = update.hidden?.let { if (it) 1L else 0L },
            categoryId = update.id,
        )
    }

    override suspend fun updateAllNovelCategoryFlags(flags: Long?) {
        handler.await {
            novel_categoriesQueries.updateAllFlags(flags)
        }
    }

    override suspend fun deleteNovelCategory(categoryId: Long) {
        handler.await {
            novel_categoriesQueries.delete(
                categoryId = categoryId,
            )
        }
    }

    override suspend fun setNovelCategories(mangaId: Long, categoryIds: List<Long>) {
        handler.await(inTransaction = true) {
            mangas_novel_categoriesQueries.deleteNovelCategoryByMangaId(mangaId)
            categoryIds.map { categoryId ->
                mangas_novel_categoriesQueries.insert(mangaId, categoryId)
            }
        }
    }

    private fun mapCategory(
        id: Long,
        name: String,
        order: Long,
        flags: Long,
        hidden: Long,
    ): Category {
        return Category(
            id = id,
            name = name,
            order = order,
            flags = flags,
            hidden = hidden == 1L,
        )
    }
}
