package eu.kanade.tachiyomi.ui.category.novel

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.ui.category.manga.MangaCategoryDialog
import eu.kanade.tachiyomi.ui.category.manga.MangaCategoryEvent
import eu.kanade.tachiyomi.ui.category.manga.MangaCategoryScreenState
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.novel.interactor.CreateNovelCategoryWithName
import tachiyomi.domain.category.novel.interactor.DeleteNovelCategory
import tachiyomi.domain.category.novel.interactor.GetNovelCategories
import tachiyomi.domain.category.novel.interactor.RenameNovelCategory
import tachiyomi.domain.category.novel.interactor.ReorderNovelCategory
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Manages the novel category space (separate from manga/anime). Reuses the manga category state/UI
 * types; novel-only differences: no per-category reading mode (novels are text) and no auto-hide.
 */
class NovelCategoryScreenModel(
    private val getAllCategories: GetNovelCategories = Injekt.get(),
    private val createCategoryWithName: CreateNovelCategoryWithName = Injekt.get(),
    private val deleteCategory: DeleteNovelCategory = Injekt.get(),
    private val reorderCategory: ReorderNovelCategory = Injekt.get(),
    private val renameCategory: RenameNovelCategory = Injekt.get(),
) : StateScreenModel<MangaCategoryScreenState>(MangaCategoryScreenState.Loading) {

    private val _events: Channel<MangaCategoryEvent> = Channel()
    val events = _events.receiveAsFlow()

    init {
        screenModelScope.launch {
            getAllCategories.subscribe().collectLatest { categories ->
                mutableState.update {
                    MangaCategoryScreenState.Success(
                        categories = categories
                            .filterNot(Category::isSystemCategory)
                            .toImmutableList(),
                        autoHideCategoryIds = persistentSetOf(),
                    )
                }
            }
        }
    }

    fun createCategory(name: String) {
        screenModelScope.launch {
            if (createCategoryWithName.await(name) is CreateNovelCategoryWithName.Result.InternalError) {
                _events.send(MangaCategoryEvent.InternalError)
            }
        }
    }

    fun deleteCategory(categoryId: Long) {
        screenModelScope.launch {
            if (deleteCategory.await(categoryId = categoryId) is DeleteNovelCategory.Result.InternalError) {
                _events.send(MangaCategoryEvent.InternalError)
            }
        }
    }

    fun changeOrder(category: Category, newIndex: Int) {
        screenModelScope.launch {
            if (reorderCategory.await(category, newIndex) is ReorderNovelCategory.Result.InternalError) {
                _events.send(MangaCategoryEvent.InternalError)
            }
        }
    }

    fun renameCategory(category: Category, name: String) {
        screenModelScope.launch {
            if (renameCategory.await(category, name) is RenameNovelCategory.Result.InternalError) {
                _events.send(MangaCategoryEvent.InternalError)
            }
        }
    }

    fun showDialog(dialog: MangaCategoryDialog) {
        mutableState.update {
            when (it) {
                MangaCategoryScreenState.Loading -> it
                is MangaCategoryScreenState.Success -> it.copy(dialog = dialog)
            }
        }
    }

    fun dismissDialog() {
        mutableState.update {
            when (it) {
                MangaCategoryScreenState.Loading -> it
                is MangaCategoryScreenState.Success -> it.copy(dialog = null)
            }
        }
    }
}
