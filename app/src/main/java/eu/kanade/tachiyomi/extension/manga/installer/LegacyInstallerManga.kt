package eu.kanade.tachiyomi.extension.manga.installer

import android.app.Service
import android.content.Intent
import eu.kanade.tachiyomi.extension.manga.util.MangaExtensionInstallActivity
import eu.kanade.tachiyomi.extension.manga.util.MangaExtensionInstaller

/**
 * Drives the legacy `ACTION_INSTALL_PACKAGE` flow through the shared install queue so the system
 * prompts are shown one at a time. [MangaExtensionInstallActivity] reports each result back via
 * [InstallerManga.continueInstallQueue], which advances the queue.
 */
class LegacyInstallerManga(private val service: Service) : InstallerManga(service) {

    // Always ready; the actual prompt is driven by the Activity.
    override var ready = true

    override fun processEntry(entry: Entry) {
        super.processEntry(entry)
        val intent = Intent(service, MangaExtensionInstallActivity::class.java)
            .setDataAndType(entry.uri, MangaExtensionInstaller.APK_MIME)
            .putExtra(MangaExtensionInstaller.EXTRA_DOWNLOAD_ID, entry.downloadId)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        service.startActivity(intent)
    }
}
