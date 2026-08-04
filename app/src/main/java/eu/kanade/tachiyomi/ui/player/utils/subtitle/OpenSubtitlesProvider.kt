package eu.kanade.tachiyomi.ui.player.utils.subtitle

import androidx.core.net.toUri
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

/**
 * [SubtitleProvider] backed by the OpenSubtitles.com REST API
 * (https://api.opensubtitles.com/api/v1).
 *
 * Search is anonymous (only the [API_KEY] + [USER_AGENT] headers are required). Downloading goes
 * through the `/download` endpoint which, for a consumer with "anonymous downloads" enabled, works
 * without a user login up to the consumer's daily quota. See [API_KEY].
 */
class OpenSubtitlesProvider(
    private val client: OkHttpClient = Injekt.get<NetworkHelper>().client,
) : SubtitleProvider {

    private val json: Json by injectLazy()

    private fun headers(withBody: Boolean): Headers = Headers.Builder().apply {
        add("Api-Key", API_KEY)
        // OpenSubtitles requires an app-identifying User-Agent ("<name> vX"). Also send it as
        // X-User-Agent in case a network interceptor overrides the standard header.
        add("User-Agent", USER_AGENT)
        add("X-User-Agent", USER_AGENT)
        add("Accept", "application/json")
        if (withBody) add("Content-Type", "application/json")
    }.build()

    override suspend fun search(
        query: String,
        languages: List<String>,
        season: Int?,
        episode: Int?,
    ): List<RemoteSubtitle> {
        // The REST API wants comma-separated, lowercase, alphabetically-sorted 2-letter codes.
        val langParam = languages.map { it.lowercase() }.distinct().sorted().joinToString(",")
        val url = "$API_URL/subtitles".toUri().buildUpon()
            .appendQueryParameter("query", query.trim())
            .apply {
                if (langParam.isNotBlank()) appendQueryParameter("languages", langParam)
                if (season != null) appendQueryParameter("season_number", season.toString())
                if (episode != null) appendQueryParameter("episode_number", episode.toString())
            }
            .build()
            .toString()

        val response = with(json) {
            client.newCall(GET(url, headers(withBody = false)))
                .awaitSuccess()
                .parseAs<SearchResponse>()
        }

        return response.data.mapNotNull { item ->
            val attributes = item.attributes ?: return@mapNotNull null
            val file = attributes.files.firstOrNull() ?: return@mapNotNull null
            val name = file.fileName?.takeIf { it.isNotBlank() }
                ?: attributes.release?.takeIf { it.isNotBlank() }
                ?: "subtitle"
            RemoteSubtitle(
                fileName = name,
                language = attributes.language?.uppercase().orEmpty(),
                languageCode = attributes.language.orEmpty(),
                downloads = attributes.downloadCount,
                release = attributes.release.orEmpty(),
                format = name.substringAfterLast('.', "").lowercase().ifBlank { "srt" },
                fileId = file.fileId,
            )
        }.sortedByDescending { it.downloads }
    }

    override suspend fun download(subtitle: RemoteSubtitle): ByteArray {
        val payload = buildJsonObject { put("file_id", subtitle.fileId) }
            .toString()
            .toRequestBody(jsonMime)

        val download = with(json) {
            client.newCall(POST("$API_URL/download", headers(withBody = true), body = payload))
                .awaitSuccess()
                .parseAs<DownloadResponse>()
        }
        val link = download.link?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException(
                "OpenSubtitles download failed: ${download.message ?: "no link returned"}",
            )

        // The link points to the plain (already decompressed) subtitle file.
        return client.newCall(GET(link)).awaitSuccess().body.byteStream().use { it.readBytes() }
    }

    @Serializable
    private data class SearchResponse(
        val data: List<SubtitleItem> = emptyList(),
    )

    @Serializable
    private data class SubtitleItem(
        val attributes: Attributes? = null,
    )

    @Serializable
    private data class Attributes(
        val language: String? = null,
        @SerialName("download_count") val downloadCount: Int = 0,
        val release: String? = null,
        val files: List<SubtitleFile> = emptyList(),
    )

    @Serializable
    private data class SubtitleFile(
        @SerialName("file_id") val fileId: Int,
        @SerialName("file_name") val fileName: String? = null,
    )

    @Serializable
    private data class DownloadResponse(
        val link: String? = null,
        @SerialName("file_name") val fileName: String? = null,
        val remaining: Int? = null,
        val message: String? = null,
    )

    companion object {
        private const val API_URL = "https://api.opensubtitles.com/api/v1"

        // Public consumer api-key registered for Nekoyomi on opensubtitles.com. It only identifies
        // the app for rate limiting (not a secret); downloads count against each user's daily quota.
        private const val API_KEY = "s1RnR3VUaJwLQrZP7nGq2l20NJLPA1eO"
        private const val USER_AGENT = "Nekoyomi v0.19.1"
    }
}
