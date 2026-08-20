package eu.kanade.tachiyomi.extension.manga.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.extension.InstallStep
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.extension.manga.installer.InstallerManga
import eu.kanade.tachiyomi.extension.manga.model.MangaExtension
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.util.storage.getUriCompat
import eu.kanade.tachiyomi.util.system.isPackageInstalled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * The installer which installs, updates and uninstalls the extensions.
 *
 * The APK is downloaded with the app's own network client (OkHttp) rather than the system
 * DownloadManager, which is unreliable on some devices (it can stall at STATUS_PENDING forever).
 * Once downloaded, it's handed to the same installer path (PackageInstaller/Shizuku/private) as
 * anime and manga extensions.
 *
 * @param context The application context.
 */
internal class MangaExtensionInstaller(private val context: Context) {

    private val network: NetworkHelper by injectLazy()

    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * The currently requested downloads, with the package name (unique id) as key, and a synthetic
     * download id as value.
     */
    private val activeDownloads = hashMapOf<String, Long>()

    private val downloadsStateFlows = hashMapOf<Long, MutableStateFlow<InstallStep>>()

    private val downloadJobs = hashMapOf<Long, Job>()

    private val idCounter = AtomicLong(1)

    private val extensionInstaller = Injekt.get<BasePreferences>().extensionInstaller()

    /**
     * Downloads the given extension's APK (via OkHttp) and installs it, returning a flow of its step
     * in the installation process.
     *
     * @param url The url of the apk.
     * @param extension The extension to install.
     */
    fun downloadAndInstall(url: String, extension: MangaExtension): Flow<InstallStep> {
        val pkgName = extension.pkgName

        if (activeDownloads[pkgName] != null) {
            deleteDownload(pkgName)
        }

        val id = idCounter.getAndIncrement()
        activeDownloads[pkgName] = id
        val stateFlow = MutableStateFlow(InstallStep.Pending)
        downloadsStateFlows[id] = stateFlow

        downloadJobs[id] = downloadScope.launch {
            try {
                stateFlow.value = InstallStep.Downloading
                val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
                val apkFile = File(dir, "$pkgName.apk")
                network.client.newCall(GET(url)).awaitSuccess().use { response ->
                    response.body!!.byteStream().use { input ->
                        apkFile.outputStream().use { output -> input.copyTo(output) }
                    }
                }
                withUIContext { installApk(id, apkFile.getUriCompat(context)) }
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e) { "Failed to download extension APK from $url" }
                stateFlow.value = InstallStep.Error
            }
        }

        return stateFlow.transformWhile {
            emit(it)
            // Stop when the application is installed or errors
            !it.isCompleted()
        }.onCompletion {
            withUIContext { deleteDownload(pkgName) }
        }
    }

    /**
     * Starts an intent to install the extension at the given uri.
     *
     * @param uri The uri of the extension to install.
     */
    fun installApk(downloadId: Long, uri: Uri) {
        when (val installer = extensionInstaller.get()) {
            BasePreferences.ExtensionInstaller.PRIVATE -> {
                val extensionManager = Injekt.get<MangaExtensionManager>()
                val tempFile = File(context.cacheDir, "temp_$downloadId")

                if (tempFile.exists() && !tempFile.delete()) {
                    // Unlikely but just in case
                    extensionManager.updateInstallStep(downloadId, InstallStep.Error)
                    return
                }

                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }

                    if (MangaExtensionLoader.installPrivateExtensionFile(context, tempFile)) {
                        extensionManager.updateInstallStep(downloadId, InstallStep.Installed)
                    } else {
                        extensionManager.updateInstallStep(downloadId, InstallStep.Error)
                    }
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Failed to read downloaded extension file." }
                    extensionManager.updateInstallStep(downloadId, InstallStep.Error)
                }

                tempFile.delete()
            }
            else -> {
                val intent =
                    MangaExtensionInstallService.getIntent(context, downloadId, uri, installer)
                ContextCompat.startForegroundService(context, intent)
            }
        }
    }

    /**
     * Cancels extension install and removes the download.
     */
    fun cancelInstall(pkgName: String) {
        val downloadId = activeDownloads.remove(pkgName) ?: return
        downloadJobs.remove(downloadId)?.cancel()
        downloadsStateFlows.remove(downloadId)
        InstallerManga.cancelInstallQueue(context, downloadId)
    }

    /**
     * Starts an intent to uninstall the extension by the given package name.
     *
     * @param pkgName The package name of the extension to uninstall
     */
    fun uninstallApk(pkgName: String) {
        if (context.isPackageInstalled(pkgName)) {
            @Suppress("DEPRECATION")
            val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE, "package:$pkgName".toUri())
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } else {
            MangaExtensionLoader.uninstallPrivateExtension(context, pkgName)
            MangaExtensionInstallReceiver.notifyRemoved(context, pkgName)
        }
    }

    /**
     * Sets the step of the installation of an extension.
     *
     * @param downloadId The id of the download.
     * @param step New install step.
     */
    fun updateInstallStep(downloadId: Long, step: InstallStep) {
        downloadsStateFlows[downloadId]?.let { it.value = step }
    }

    /**
     * Deletes the download for the given package name.
     *
     * @param pkgName The package name of the download to delete.
     */
    private fun deleteDownload(pkgName: String) {
        val downloadId = activeDownloads.remove(pkgName)
        if (downloadId != null) {
            downloadJobs.remove(downloadId)?.cancel()
            downloadsStateFlows.remove(downloadId)
        }
    }

    companion object {
        const val APK_MIME = "application/vnd.android.package-archive"
        const val EXTRA_DOWNLOAD_ID = "ExtensionInstaller.extra.DOWNLOAD_ID"
        const val FILE_SCHEME = "file://"
    }
}
