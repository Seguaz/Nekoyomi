package eu.kanade.tachiyomi.ui.player.loader

import eu.kanade.domain.entries.anime.model.toDomainAnime
import eu.kanade.domain.entries.anime.model.toSAnime
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SEpisode
import kotlinx.coroutines.withTimeoutOrNull
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.items.episode.service.EpisodeRecognition
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.Normalizer

/**
 * When the primary source can't return any hosters for an episode, this searches the SAME anime in
 * OTHER installed sources (by exact title) and the SAME episode (by number), returning the first
 * source that yields non-empty hosters.
 *
 * This is a fallback only — it must never run on the normal (working) path. It is additive and gated
 * behind [eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences.autoSourceFallback]. Nothing here is
 * persisted to the DB (searched anime are kept in-memory), so it has no side effects on the library.
 */
class EpisodeSourceFallback(
    private val sourceManager: AnimeSourceManager = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
) {

    /** The alternative source + its matched anime/episode + the hosters it produced. */
    data class Result(
        val source: AnimeCatalogueSource,
        val anime: Anime,
        val episode: Episode,
        val hosters: List<Hoster>,
    )

    private val enabledLanguages by lazy { sourcePreferences.enabledLanguages().get() }
    private val disabledSources by lazy { sourcePreferences.disabledAnimeSources().get() }
    private val pinnedSources by lazy { sourcePreferences.pinnedAnimeSources().get() }
    private val migrationPriority by lazy {
        sourcePreferences.migrationSourcePriorityAnime().get()
            .split(",").mapNotNull { it.trim().toLongOrNull() }
    }
    private val excludedSources by lazy { sourcePreferences.migrationExcludedSourcesAnime().get() }
    private val excludedLanguages by lazy { sourcePreferences.migrationExcludedLanguagesAnime().get() }

    /**
     * The ordered list of alternative sources to try for [oldAnime] (best first), capped so the search
     * can't run forever. The caller consumes them one at a time via [tryOne], advancing to the next only
     * when the chosen one fails to actually play.
     */
    fun orderedCandidates(oldAnime: Anime): List<AnimeCatalogueSource> {
        val preferredLang = sourceManager.getOrStub(oldAnime.source).lang
        val candidates = candidateSources(oldAnime.source, preferredLang).take(MAX_SOURCES)
        logcat {
            "F1 fallback: candidates for '${oldAnime.title}' (lang=$preferredLang): " +
                candidates.joinToString { "${it.name}[${it.lang}]" }
        }
        return candidates
    }

    /**
     * Tries a single [source] for [oldAnime]/[episode]: exact-title search, same-numbered episode,
     * non-empty hosters. Returns the playable [Result] or null (with a reason logged) if this source
     * doesn't have it. Bounded by a per-source timeout so a slow source can't hang the player.
     */
    suspend fun tryOne(source: AnimeCatalogueSource, oldAnime: Anime, episode: Episode): Result? {
        val targetNumber = EpisodeRecognition.parseEpisodeNumber(oldAnime.title, episode.name, episode.episodeNumber)
        val normalizedQuery = oldAnime.title.normalizedForMatch()
        if (normalizedQuery.isEmpty()) return null
        val result = withTimeoutOrNull(PER_SOURCE_TIMEOUT_MS) {
            trySource(source, oldAnime.title, normalizedQuery, targetNumber)
        }
        if (result != null) {
            logcat { "F1 fallback: SUCCESS from '${source.name}' (${result.hosters.size} hosters)" }
        }
        return result
    }

    private suspend fun trySource(
        source: AnimeCatalogueSource,
        query: String,
        normalizedQuery: String,
        targetNumber: Double,
    ): Result? {
        val searchResults = try {
            source.getSearchAnime(1, query, source.getFilterList()).animes
        } catch (e: Throwable) {
            logcat { "F1 fallback: search failed on '${source.name}': ${e.message}" }
            return null
        }

        // Only trust an EXACT title match for automatic fallback, so we never silently play the wrong show.
        val match = searchResults.firstOrNull { it.title.normalizedForMatch() == normalizedQuery }
        if (match == null) {
            logcat {
                "F1 fallback: no exact title match in '${source.name}' (${searchResults.size} results): " +
                    searchResults.take(5).joinToString { it.title }
            }
            return null
        }
        val anime = match.toDomainAnime(source.id)

        val sEpisodes = try {
            source.getEpisodeList(anime.toSAnime())
        } catch (e: Throwable) {
            logcat { "F1 fallback: episode list failed on '${source.name}': ${e.message}" }
            return null
        }

        val targetEpisode = sEpisodes.firstOrNull {
            EpisodeRecognition.parseEpisodeNumber(query, it.name, it.episode_number.toDouble()) == targetNumber
        }
        if (targetEpisode == null) {
            logcat { "F1 fallback: '${source.name}' matched but has no episode $targetNumber (${sEpisodes.size} eps)" }
            return null
        }

        val domainEpisode = targetEpisode.toFallbackEpisode()

        val hosters = try {
            EpisodeLoader.getHosters(domainEpisode, anime, source)
        } catch (e: Throwable) {
            logcat { "F1 fallback: getHosters failed on '${source.name}': ${e.message}" }
            return null
        }
        if (hosters.isEmpty()) {
            logcat { "F1 fallback: '${source.name}' returned empty hosters for ep $targetNumber" }
            return null
        }

        return Result(source, anime, domainEpisode, hosters)
    }

    /**
     * Installed catalogue sources to search, ordered by (reusing the migration "source priority"
     * config so one setting drives both): the user's manual position, then same-language-first, then
     * pinned, then alphabetical. Excludes the failing source and anything the user disabled/excluded.
     */
    private fun candidateSources(fromSourceId: Long, preferredLang: String): List<AnimeCatalogueSource> {
        return sourceManager.getCatalogueSources()
            .filter {
                it.lang in enabledLanguages &&
                    "${it.id}" !in disabledSources &&
                    it.id != fromSourceId &&
                    "${it.id}" !in excludedSources &&
                    it.lang !in excludedLanguages
            }
            .sortedWith(
                compareBy(
                    // 1. User-defined position priority (Migration → source priority screen).
                    { priorityRank(it.id) },
                    // 2. Same language as the failing source first (don't try EN before ES, etc.).
                    { it.lang != preferredLang },
                    // 3. Pinned sources next.
                    { "${it.id}" !in pinnedSources },
                    // 4. Alphabetical for a stable final order.
                    { "${it.name.lowercase()} (${it.lang})" },
                ),
            )
            .distinctBy { it.id }
    }

    private fun priorityRank(id: Long): Int =
        migrationPriority.indexOf(id).let { if (it < 0) Int.MAX_VALUE else it }

    private fun SEpisode.toFallbackEpisode(): Episode = Episode.create().copy(
        url = url,
        name = name,
        dateUpload = date_upload,
        episodeNumber = episode_number.toDouble(),
        scanlator = scanlator,
    )

    companion object {
        private const val MAX_SOURCES = 5
        private const val PER_SOURCE_TIMEOUT_MS = 20_000L
    }
}

private fun String.normalizedForMatch(): String {
    // Fold accents, treat the multiplication sign as a plain "x" (SPY×FAMILY == Spy x Family), then
    // keep only ascii letters/digits so punctuation/spacing/casing don't break exact matching.
    return Normalizer.normalize(trim(), Normalizer.Form.NFD)
        .lowercase()
        .replace('×', 'x')
        .replace(Regex("[^a-z0-9]"), "")
}
