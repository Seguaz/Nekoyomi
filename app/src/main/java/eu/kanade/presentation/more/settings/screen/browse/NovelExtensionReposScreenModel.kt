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
        val url = baseUrl.trim().normalizeRepoUrl()
        screenModelScope.launchIO {
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                _events.send(RepoEvent.InvalidUrl)
                return@launchIO
            }
            if (url.isIncompatibleNovelRepo()) {
                _events.send(RepoEvent.IncompatibleFormat)
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

    /**
     * Strips a trailing index file (index.min.json / index.json / index.pb) and slashes, so pasting
     * the full index URL still stores the repo base — matching how anime/manga repos normalize.
     */
    private fun String.normalizeRepoUrl(): String = trimEnd('/')
        .removeSuffix("/index.min.json")
        .removeSuffix("/index.json")
        .removeSuffix("/index.pb")
        .trimEnd('/')

    /**
     * Detects the popular Mangayomi / LNReader novel repos users keep trying to add. Those are
     * JavaScript/Dart plugin repos for a different app and can never work here (this app runs
     * compiled Kotlin extensions), so we reject them upfront with a clear message instead of adding
     * an empty repo. tsundoku / NovelSourcery repos (index.min.json / index.pb) are unaffected.
     */
    private fun String.isIncompatibleNovelRepo(): Boolean {
        val u = lowercase()
        return "mangayomi-extensions" in u ||
            "lnreader" in u ||
            "novel_index.json" in u ||
            "plugins.min.json" in u
    }

    private fun String.toRepoShim() = ExtensionRepo(
        baseUrl = this,
        name = repoDisplayName(this),
        shortName = null,
        website = this,
        signingKeyFingerprint = "",
    )

    /**
     * A short, readable name for a novel repo URL. Novel repos are stored as plain URLs (no fetched
     * metadata), so we derive the owner segment for GitHub/jsDelivr hosts (e.g. "yuzono",
     * "novelsourcery") and fall back to the host for anything else.
     */
    private fun repoDisplayName(url: String): String {
        val withoutScheme = url.substringAfter("://").trimEnd('/')
        val host = withoutScheme.substringBefore('/')
        val segments = withoutScheme.substringAfter('/', "").split('/').filter { it.isNotBlank() }
        return when {
            // raw.githubusercontent.com/<owner>/<repo>/... or github.com/<owner>/<repo>
            host.contains("githubusercontent.com") || host.contains("github.com") ->
                segments.firstOrNull() ?: host
            // cdn.jsdelivr.net/gh/<owner>/<repo>@<ref>/...
            host.contains("jsdelivr") ->
                segments.getOrNull(1) ?: segments.firstOrNull() ?: host
            else -> host
        }
    }
}
