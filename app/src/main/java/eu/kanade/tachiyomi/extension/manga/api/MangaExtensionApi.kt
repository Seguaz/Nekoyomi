package eu.kanade.tachiyomi.extension.manga.api

import android.content.Context
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.ExtensionUpdateNotifier
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.extension.manga.model.MangaExtension
import eu.kanade.tachiyomi.extension.manga.model.MangaLoadResult
import eu.kanade.tachiyomi.extension.manga.util.MangaExtensionLoader
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.decodeFromJsonResponse
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import logcat.LogPriority
import mihon.domain.extensionrepo.manga.interactor.GetMangaExtensionRepo
import mihon.domain.extensionrepo.manga.interactor.UpdateMangaExtensionRepo
import mihon.domain.extensionrepo.model.ExtensionRepo
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.injectLazy
import java.time.Instant
import kotlin.time.Duration.Companion.days

private val JSDELIVR_GH_REGEX =
    Regex("""^https?://cdn\.jsdelivr\.net/gh/([^/]+)/([^@/]+)@([^/]+)/(.+)$""")

internal class MangaExtensionApi {

    private val networkService: NetworkHelper by injectLazy()
    private val preferenceStore: PreferenceStore by injectLazy()
    private val sourcePreferences: SourcePreferences by injectLazy()
    private val getExtensionRepo: GetMangaExtensionRepo by injectLazy()
    private val updateExtensionRepo: UpdateMangaExtensionRepo by injectLazy()
    private val extensionManager: MangaExtensionManager by injectLazy()
    private val json: Json by injectLazy()

    private val lastExtCheck: Preference<Long> by lazy {
        preferenceStore.getLong("last_ext_check", 0)
    }

    suspend fun findExtensions(): List<MangaExtension.Available> {
        return withIOContext {
            // Novel extensions (tsundoku / NovelSourcery) install through the manga extension system,
            // so their repos are fetched here too. Novel repos are stored as plain URLs in a pref.
            val mangaRepos = getExtensionRepo.getAll()
            val novelRepos = sourcePreferences.novelExtensionRepos().get().map { url ->
                // Defensive: older entries may have been stored with a trailing index file (e.g.
                // ".../repo/index.min.json"); strip it so we fetch ".../repo/index.json" correctly.
                val base = url.trimEnd('/')
                    .removeSuffix("/index.min.json")
                    .removeSuffix("/index.json")
                    .removeSuffix("/index.pb")
                    .trimEnd('/')
                ExtensionRepo(
                    baseUrl = base,
                    name = base,
                    shortName = null,
                    website = base,
                    signingKeyFingerprint = "",
                )
            }
            (mangaRepos + novelRepos)
                .distinctBy { it.baseUrl }
                .map { async { getExtensions(it) } }
                .awaitAll()
                .flatten()
        }
    }

    private suspend fun getExtensions(extRepo: ExtensionRepo): List<MangaExtension.Available> {
        val repoBaseUrl = extRepo.baseUrl
        // Prefer the newer "tachiyomix 1.6" index format, falling back to the classic one.
        return getNewFormatExtensions(repoBaseUrl)?.takeIf { it.isNotEmpty() }
            ?: getClassicExtensions(repoBaseUrl)
    }

    private suspend fun getNewFormatExtensions(repoBaseUrl: String): List<MangaExtension.Available>? {
        return try {
            val response = networkService.client
                .newCall(GET("$repoBaseUrl/index.json"))
                .awaitSuccess()

            with(json) {
                // Use the explicit compiler-generated serializer rather than the reified serializer<T>()
                // lookup, which R8 full mode can break for a nested @Serializable graph like this one.
                decodeFromJsonResponse(ExtensionRepoJsonObject.serializer(), response)
                    .extensionList
                    .extensions
                    .toExtensions(repoBaseUrl)
            }
        } catch (e: Throwable) {
            // Repo doesn't use the new format (or is unreachable); fall back to the classic index.
            logcat(LogPriority.DEBUG) { "New-format index unavailable for $repoBaseUrl (${e.message})" }
            null
        }
    }

    private suspend fun getClassicExtensions(repoBaseUrl: String): List<MangaExtension.Available> {
        return try {
            val response = networkService.client
                .newCall(GET("$repoBaseUrl/index.min.json"))
                .awaitSuccess()

            with(json) {
                response
                    .parseAs<List<ExtensionJsonObject>>()
                    .toExtensions(repoBaseUrl)
            }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "Failed to get extensions from $repoBaseUrl" }
            emptyList()
        }
    }

    suspend fun checkForUpdates(
        context: Context,
        fromAvailableExtensionList: Boolean = false,
    ): List<MangaExtension.Installed>? {
        // Limit checks to once a day at most
        if (fromAvailableExtensionList &&
            Instant.now().toEpochMilli() < lastExtCheck.get() + 1.days.inWholeMilliseconds
        ) {
            return null
        }

        // Update extension repo details
        updateExtensionRepo.awaitAll()

        val extensions = if (fromAvailableExtensionList) {
            extensionManager.availableExtensionsFlow.value
        } else {
            findExtensions().also { lastExtCheck.set(Instant.now().toEpochMilli()) }
        }

        val installedExtensions = MangaExtensionLoader.loadMangaExtensions(context)
            .filterIsInstance<MangaLoadResult.Success>()
            .map { it.extension }

        val extensionsWithUpdate = mutableListOf<MangaExtension.Installed>()
        for (installedExt in installedExtensions) {
            val pkgName = installedExt.pkgName
            val availableExt = extensions.find { it.pkgName == pkgName } ?: continue
            val hasUpdatedVer = availableExt.versionCode > installedExt.versionCode
            val hasUpdatedLib = availableExt.libVersion > installedExt.libVersion
            val hasUpdate = hasUpdatedVer || hasUpdatedLib
            if (hasUpdate) {
                extensionsWithUpdate.add(installedExt)
            }
        }

        if (extensionsWithUpdate.isNotEmpty()) {
            ExtensionUpdateNotifier(context).promptUpdates(extensionsWithUpdate.map { it.name })
        }

        return extensionsWithUpdate
    }

    private fun List<ExtensionJsonObject>.toExtensions(repoUrl: String): List<MangaExtension.Available> {
        return this
            .filter {
                val libVersion = it.extractLibVersion()
                libVersion >= MangaExtensionLoader.LIB_VERSION_MIN && libVersion <= MangaExtensionLoader.LIB_VERSION_MAX
            }
            .map {
                MangaExtension.Available(
                    name = it.name.substringAfter("Tachiyomi: "),
                    pkgName = it.pkg,
                    versionName = it.version,
                    versionCode = it.code,
                    libVersion = it.extractLibVersion(),
                    lang = it.lang,
                    isNsfw = it.nsfw == 1,
                    sources = it.sources?.map(extensionSourceMapper).orEmpty(),
                    apkName = it.apk,
                    iconUrl = "$repoUrl/icon/${it.pkg}.png",
                    repoUrl = repoUrl,
                )
            }
    }

    @JvmName("toExtensionsNewFormat")
    private fun List<NewExtensionJsonObject>.toExtensions(repoUrl: String): List<MangaExtension.Available> {
        return this
            .filter {
                val libVersion = it.extensionLib.toDoubleOrNull() ?: return@filter false
                libVersion >= MangaExtensionLoader.LIB_VERSION_MIN && libVersion <= MangaExtensionLoader.LIB_VERSION_MAX
            }
            .map {
                MangaExtension.Available(
                    name = it.name.substringAfter("Tachiyomi: "),
                    pkgName = it.packageName,
                    versionName = it.versionName,
                    versionCode = it.versionCode.toLongOrNull() ?: 0,
                    libVersion = it.extensionLib.toDouble(),
                    lang = it.packageName.substringAfterLast("extension.").substringBefore('.'),
                    // Some repos (e.g. NovelSourcery) set contentWarning to a "CONTENT_WARNING_SAFE"
                    // marker for safe extensions; only treat an actual warning as NSFW.
                    isNsfw = it.contentWarning != null &&
                        !it.contentWarning.equals("CONTENT_WARNING_SAFE", ignoreCase = true),
                    sources = it.sources?.map(newExtensionSourceMapper).orEmpty(),
                    apkName = it.resources.apkUrl.substringAfterLast('/'),
                    iconUrl = it.resources.iconUrl ?: "$repoUrl/icon/${it.packageName}.png",
                    repoUrl = repoUrl,
                    // The new format hosts APKs at an arbitrary URL, not "$repoUrl/apk/$apkName";
                    // keep the real one so the download doesn't 404.
                    apkUrl = it.resources.apkUrl,
                )
            }
    }

    fun getApkUrl(extension: MangaExtension.Available): String {
        // New-format repos give the real download URL directly; classic repos build it from the repo.
        val url = extension.apkUrl ?: "${extension.repoUrl}/apk/${extension.apkName}"
        return rewriteJsDelivrToRaw(url)
    }

    /**
     * The system DownloadManager stalls on jsDelivr GitHub URLs (the `@branch` in the path);
     * NovelSourcery serves APKs via jsDelivr. Map `cdn.jsdelivr.net/gh/user/repo@branch/path` to the
     * equivalent `raw.githubusercontent.com/user/repo/branch/path`, which the DownloadManager handles.
     */
    private fun rewriteJsDelivrToRaw(url: String): String {
        val match = JSDELIVR_GH_REGEX.find(url) ?: return url
        val (user, repo, branch, path) = match.destructured
        return "https://raw.githubusercontent.com/$user/$repo/$branch/$path"
    }

    private fun ExtensionJsonObject.extractLibVersion(): Double {
        return version.substringBeforeLast('.').toDouble()
    }
}

@Serializable
private data class ExtensionJsonObject(
    val name: String,
    val pkg: String,
    val apk: String,
    val lang: String,
    val code: Long,
    val version: String,
    val nsfw: Int,
    val sources: List<ExtensionSourceJsonObject>?,
)

@Serializable
private data class ExtensionSourceJsonObject(
    val id: Long,
    val lang: String,
    val name: String,
    val baseUrl: String,
)

private val extensionSourceMapper: (ExtensionSourceJsonObject) -> MangaExtension.Available.MangaSource = {
    MangaExtension.Available.MangaSource(
        id = it.id,
        lang = it.lang,
        name = it.name,
        baseUrl = it.baseUrl,
    )
}

// Newer "tachiyomix 1.6" repo index format (Mihon 0.20.0+). Unknown fields such as the repo
// signing key and jar URLs are ignored by the JSON parser.
@Serializable
private data class ExtensionRepoJsonObject(
    val extensionList: ExtensionListJsonObject,
)

@Serializable
private data class ExtensionListJsonObject(
    val extensions: List<NewExtensionJsonObject>,
)

@Serializable
private data class NewExtensionJsonObject(
    val name: String,
    val packageName: String,
    val resources: ExtensionResourcesJsonObject,
    val extensionLib: String,
    val versionCode: String,
    val versionName: String,
    val contentWarning: String? = null,
    val sources: List<NewExtensionSourceJsonObject>?,
)

@Serializable
private data class ExtensionResourcesJsonObject(
    val apkUrl: String,
    val iconUrl: String? = null,
)

@Serializable
private data class NewExtensionSourceJsonObject(
    val id: String,
    val name: String,
    val language: String,
    val homeUrl: String? = null,
)

private val newExtensionSourceMapper: (NewExtensionSourceJsonObject) -> MangaExtension.Available.MangaSource = {
    MangaExtension.Available.MangaSource(
        id = it.id.toLongOrNull() ?: 0,
        lang = it.language,
        name = it.name,
        baseUrl = it.homeUrl.orEmpty(),
    )
}
