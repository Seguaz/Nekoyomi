package tachiyomi.data.release

import android.os.Build
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.json.Json
import tachiyomi.domain.release.interactor.GetApplicationRelease
import tachiyomi.domain.release.model.Release
import tachiyomi.domain.release.service.ReleaseService

class ReleaseServiceImpl(
    private val networkService: NetworkHelper,
    private val json: Json,
) : ReleaseService {

    override suspend fun latest(arguments: GetApplicationRelease.Arguments): Release? {
        return try {
            latestFromApi(arguments)
        } catch (e: HttpException) {
            // GitHub's unauthenticated REST API is capped at 60 requests/hour per IP. Mobile carriers
            // route many users through a shared IP (CGNAT), so this endpoint 403s in bursts — typically
            // right after a release, when lots of users check at once. The web releases feed isn't tied
            // to that API quota, so fall back to it to keep update checks working.
            if (e.code == 403 || e.code == 429) {
                latestFromAtom(arguments)
            } else {
                throw e
            }
        }
    }

    /** Primary path: the GitHub REST API, which gives the tag, notes and per-asset download links. */
    private suspend fun latestFromApi(arguments: GetApplicationRelease.Arguments): Release? {
        val release = with(json) {
            networkService.client
                .newCall(GET("https://api.github.com/repos/${arguments.repository}/releases/latest"))
                .awaitSuccess()
                .parseAs<GithubRelease>()
        }
        val downloadLink = getDownloadLink(release) ?: return null

        return Release(
            version = release.version,
            info = release.info.linkifyMentions(),
            releaseLink = release.releaseLink,
            downloadLink = downloadLink,
        )
    }

    /**
     * Fallback path used when the REST API is rate-limited. The releases atom feed is served by the
     * web front-end (not the API), so it isn't subject to the 60/hour quota. It only exposes the tag
     * and notes, so the download link is reconstructed from the tag using the CI's asset naming.
     */
    private suspend fun latestFromAtom(arguments: GetApplicationRelease.Arguments): Release? {
        val atom = networkService.client
            .newCall(GET("https://github.com/${arguments.repository}/releases.atom"))
            .awaitSuccess()
            .use { it.body.string() }

        // The feed lists newest first, so the first <entry> is the latest release.
        val entry = ATOM_ENTRY_REGEX.find(atom)?.groupValues?.get(1) ?: return null
        val tag = ATOM_TAG_REGEX.find(entry)?.groupValues?.get(1)?.takeIf { it.isNotBlank() } ?: return null
        val downloadLink = getDownloadLink(arguments.repository, tag)

        return Release(
            version = tag,
            info = parseAtomInfo(entry).linkifyMentions(),
            releaseLink = "https://github.com/${arguments.repository}/releases/tag/$tag",
            downloadLink = downloadLink,
        )
    }

    /** Picks the ABI-specific asset published by the API, falling back to the universal one. */
    private fun getDownloadLink(release: GithubRelease): String? {
        val map = release.assets.associate { asset ->
            BUILD_TYPES.find { "-$it" in asset.name } to asset.downloadLink
        }

        return map[Build.SUPPORTED_ABIS[0]] ?: map[null]
    }

    /**
     * Reconstructs the download URL from a tag when only the feed is available. Mirrors the release
     * asset naming from `.github/workflows/build_push.yml` (`nekoyomi-<abi>-<tag>.apk`, or the
     * universal `nekoyomi-<tag>.apk` when the device ABI has no dedicated build).
     */
    private fun getDownloadLink(repository: String, tag: String): String {
        val abi = Build.SUPPORTED_ABIS.getOrNull(0)?.takeIf { it in BUILD_TYPES }
        val fileName = if (abi != null) "nekoyomi-$abi-$tag.apk" else "nekoyomi-$tag.apk"
        return "https://github.com/$repository/releases/download/$tag/$fileName"
    }

    /** Turns the atom entry's HTML notes into plain text (the update prompt renders them as markdown). */
    private fun parseAtomInfo(entry: String): String {
        val html = ATOM_CONTENT_REGEX.find(entry)?.groupValues?.get(1)
            ?: return ATOM_TITLE_REGEX.find(entry)?.groupValues?.get(1).orEmpty().unescapeHtml()
        return html
            .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("""</(p|li|h\d|div)>""", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("""<[^>]+>"""), "")
            .unescapeHtml()
            .lines()
            .joinToString("\n") { it.trim() }
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()
    }

    private fun String.unescapeHtml(): String = this
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&amp;", "&")

    private fun String.linkifyMentions(): String = replace(gitHubUsernameMentionRegex) { mention ->
        "[${mention.value}](https://github.com/${mention.value.substring(1)})"
    }

    companion object {
        private val BUILD_TYPES = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")

        private val ATOM_ENTRY_REGEX = Regex("""<entry>([\s\S]*?)</entry>""")
        private val ATOM_TAG_REGEX = Regex("""/releases/tag/([^"]+)"""")
        private val ATOM_CONTENT_REGEX = Regex("""<content[^>]*>([\s\S]*?)</content>""")
        private val ATOM_TITLE_REGEX = Regex("""<title[^>]*>([\s\S]*?)</title>""")

        /**
         * Regular expression that matches a mention to a valid GitHub username, like it's
         * done in GitHub Flavored Markdown. It follows these constraints:
         *
         * - Alphanumeric with single hyphens (no consecutive hyphens)
         * - Cannot begin or end with a hyphen
         * - Max length of 39 characters
         *
         * Reference: https://stackoverflow.com/a/30281147
         */
        private val gitHubUsernameMentionRegex = """\B@([a-z0-9](?:-(?=[a-z0-9])|[a-z0-9]){0,38}(?<=[a-z0-9]))"""
            .toRegex(RegexOption.IGNORE_CASE)
    }
}
