package eu.kanade.presentation.more.settings.screen.browse

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import mihon.domain.extensionrepo.model.ExtensionRepo
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.core.common.util.lang.launchIO
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Manages the novel extension repos, stored as a simple set of base URLs in
 * [SourcePreferences.novelExtensionRepos]. Novel extensions are manga extensions in the tsundoku /
 * NovelSourcery format, so they install through the manga extension system: [MangaExtensionApi]
 * fetches these repos too, and refreshing here re-fetches the available extensions.
 */
class NovelExtensionReposScreenModel(
    private val preferences: SourcePreferences = Injekt.get(),
    private val extensionManager: MangaExtensionManager = Injekt.get(),
) : StateScreenModel<RepoScreenState>(RepoScreenState.Loading) {

    private val _events: Channel<RepoEvent> = Channel(Int.MAX_VALUE)
    val events = _events.receiveAsFlow()

    init {
        screenModelScope.launchIO {
            preferences.novelExtensionRepos().changes().collectLatest { repos ->
                mutableState.update {
                    RepoScreenState.Success(
                        repos = repos.sorted().map { it.toRepoShim() }.toImmutableSet(),
                    )
                }
            }
        }
    }

    fun createRepo(baseUrl: String) {
        val url = baseUrl.trim().trimEnd('/')
        screenModelScope.launchIO {
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                _events.send(RepoEvent.InvalidUrl)
                return@launchIO
            }
            if (url in preferences.novelExtensionRepos().get()) {
                _events.send(RepoEvent.RepoAlreadyExists)
                return@launchIO
            }
            preferences.novelExtensionRepos().getAndSet { it + url }
            runCatching { extensionManager.findAvailableExtensions() }
        }
    }

    fun refreshRepos() {
        screenModelScope.launchIO {
            runCatching { extensionManager.findAvailableExtensions() }
        }
    }

    fun deleteRepo(baseUrl: String) {
        screenModelScope.launchIO {
            preferences.novelExtensionRepos().getAndSet { it - baseUrl }
            runCatching { extensionManager.findAvailableExtensions() }
        }
    }

    fun showDialog(dialog: RepoDialog) {
        mutableState.update {
            when (it) {
                RepoScreenState.Loading -> it
                is RepoScreenState.Success -> it.copy(dialog = dialog)
            }
        }
    }

    fun dismissDialog() {
        mutableState.update {
            when (it) {
                RepoScreenState.Loading -> it
                is RepoScreenState.Success -> it.copy(dialog = null)
            }
        }
    }

    private fun String.toRepoShim() = ExtensionRepo(
        baseUrl = this,
        name = this.substringAfter("://").trimEnd('/'),
        shortName = null,
        website = this,
        signingKeyFingerprint = "",
    )
}
