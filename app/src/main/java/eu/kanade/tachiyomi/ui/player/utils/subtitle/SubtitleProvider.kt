package eu.kanade.tachiyomi.ui.player.utils.subtitle

/**
 * A single subtitle result returned by a [SubtitleProvider].
 */
data class RemoteSubtitle(
    val fileName: String,
    /** Human readable language name, e.g. "Spanish". */
    val language: String,
    /** ISO code of the language, kept for mpv's `sub-add`. */
    val languageCode: String,
    val downloads: Int,
    /** Release/group name, useful to match the video. */
    val release: String,
    /** Subtitle format, e.g. "srt" or "ass". */
    val format: String,
    /** Provider-internal handle used by [SubtitleProvider.download] (OpenSubtitles REST file id). */
    val fileId: Int,
)

/**
 * Abstraction over a web subtitle service so the concrete backend (currently the
 * OpenSubtitles.com REST API) can be swapped without touching the player/UI.
 */
interface SubtitleProvider {

    /**
     * Searches subtitles matching the given [query] in the requested [languages]
     * (ISO 639-1 codes, e.g. "es", "en"). [season] and [episode] narrow the search
     * for TV content and may be null for movies/unknown.
     */
    suspend fun search(
        query: String,
        languages: List<String>,
        season: Int?,
        episode: Int?,
    ): List<RemoteSubtitle>

    /**
     * Downloads [subtitle] and returns the decompressed subtitle file bytes, ready to be
     * written to disk and handed to the player.
     */
    suspend fun download(subtitle: RemoteSubtitle): ByteArray
}
