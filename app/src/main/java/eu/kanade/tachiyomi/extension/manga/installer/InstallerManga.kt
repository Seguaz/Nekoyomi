package eu.kanade.tachiyomi.extension.manga.installer

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.annotation.CallSuper
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import eu.kanade.tachiyomi.extension.InstallStep
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import uy.kohesive.injekt.injectLazy
import java.util.Collections
import java.util.concurrent.atomic.AtomicReference

/**
 * Base implementation class for extension installer. To be used inside a foreground [Service].
 */
abstract class InstallerManga(private val service: Service) {

    private val extensionManager: MangaExtensionManager by injectLazy()

    private var waitingInstall = AtomicReference<Entry>(null)
    private val queue = Collections.synchronizedList(mutableListOf<Entry>())

    private val cancelReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val downloadId = intent.getLongExtra(EXTRA_DOWNLOAD_ID, -1).takeIf { it >= 0 } ?: return
            cancelQueue(downloadId)
        }
    }

    // Lets an out-of-process component (e.g. the legacy install Activity) report a finished install
    // so the queue advances to the next entry — keeping the system prompts serialized.
    private val continueReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val downloadId = intent.getLongExtra(EXTRA_DOWNLOAD_ID, -1).takeIf { it >= 0 } ?: return
            val step = intent.getStringExtra(EXTRA_INSTALL_STEP)
                ?.let { runCatching { InstallStep.valueOf(it) }.getOrNull() } ?: return
            if (waitingInstall.get()?.downloadId == downloadId) {
                continueQueue(step)
            }
        }
    }

    /**
     * Installer readiness. If false, queue check will not run.
     *
     * @see checkQueue
     */
    abstract var ready: Boolean

    /**
     * Add an item to install queue.
     *
     * @param downloadId Download ID as known by [MangaExtensionManager]
     * @param uri Uri of APK to install
     */
    fun addToQueue(downloadId: Long, uri: Uri) {
        // Mark as queued; if it can be processed right away, [processEntry] flips it to Installing.
        extensionManager.updateInstallStep(downloadId, InstallStep.Queued)
        queue.add(Entry(downloadId, uri))
        checkQueue()
    }

    /**
     * Proceeds to install the APK of this entry inside this method. Call [continueQueue]
     * when the install process for this entry is finished to continue the queue.
     *
     * @param entry The [Entry] of item to process
     * @see continueQueue
     */
    @CallSuper
    open fun processEntry(entry: Entry) {
        extensionManager.setInstalling(entry.downloadId)
    }

    /**
     * Called before queue continues. Override this to handle when the removed entry is
     * currently being processed.
     *
     * @return true if this entry can be removed from queue.
     */
    open fun cancelEntry(entry: Entry): Boolean {
        return true
    }

    /**
     * Tells the queue to continue processing the next entry and updates the install step
     * of the completed entry ([waitingInstall]) to [MangaExtensionManager].
     *
     * @param resultStep new install step for the processed entry.
     * @see waitingInstall
     */
    fun continueQueue(resultStep: InstallStep) {
        val completedEntry = waitingInstall.getAndSet(null)
        if (completedEntry != null) {
            extensionManager.updateInstallStep(completedEntry.downloadId, resultStep)
            checkQueue()
        }
    }

    /**
     * Checks the queue. The provided service will be stopped if the queue is empty.
     * Will not be run when not ready.
     *
     * @see ready
     */
    fun checkQueue() {
        if (!ready) {
            return
        }
        if (queue.isEmpty()) {
            service.stopSelf()
            return
        }
        val nextEntry = queue.first()
        if (waitingInstall.compareAndSet(null, nextEntry)) {
            queue.removeAt(0)
            processEntry(nextEntry)
        }
    }

    /**
     * Call this method when the provided service is destroyed.
     */
    @CallSuper
    open fun onDestroy() {
        LocalBroadcastManager.getInstance(service).unregisterReceiver(cancelReceiver)
        LocalBroadcastManager.getInstance(service).unregisterReceiver(continueReceiver)
        queue.forEach { extensionManager.updateInstallStep(it.downloadId, InstallStep.Error) }
        queue.clear()
        waitingInstall.set(null)
    }

    protected fun getActiveEntry(): Entry? = waitingInstall.get()

    /**
     * Cancels queue for the provided download ID if exists.
     *
     * @param downloadId Download ID as known by [MangaExtensionManager]
     */
    private fun cancelQueue(downloadId: Long) {
        val waitingInstall = this.waitingInstall.get()
        val toCancel = queue.find { it.downloadId == downloadId } ?: waitingInstall ?: return
        if (cancelEntry(toCancel)) {
            queue.remove(toCancel)
            if (waitingInstall == toCancel) {
                // Currently processing removed entry, continue queue
                this.waitingInstall.set(null)
                checkQueue()
            }
            extensionManager.updateInstallStep(downloadId, InstallStep.Idle)
        }
    }

    /**
     * Install item to queue.
     *
     * @param downloadId Download ID as known by [MangaExtensionManager]
     * @param uri Uri of APK to install
     */
    data class Entry(val downloadId: Long, val uri: Uri)

    init {
        LocalBroadcastManager.getInstance(service)
            .registerReceiver(cancelReceiver, IntentFilter(ACTION_CANCEL_QUEUE))
        LocalBroadcastManager.getInstance(service)
            .registerReceiver(continueReceiver, IntentFilter(ACTION_CONTINUE_QUEUE))
    }

    companion object {
        private const val ACTION_CANCEL_QUEUE = "Installer.action.CANCEL_QUEUE"
        private const val ACTION_CONTINUE_QUEUE = "Installer.action.CONTINUE_QUEUE"
        private const val EXTRA_DOWNLOAD_ID = "Installer.extra.DOWNLOAD_ID"
        private const val EXTRA_INSTALL_STEP = "Installer.extra.INSTALL_STEP"

        /**
         * Attempts to cancel the installation entry for the provided download ID.
         *
         * @param downloadId Download ID as known by [MangaExtensionManager]
         */
        fun cancelInstallQueue(context: Context, downloadId: Long) {
            val intent = Intent(ACTION_CANCEL_QUEUE)
            intent.putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
        }

        /**
         * Reports that the install for the provided download ID finished with [step], so the queue
         * advances to the next entry. Used by the legacy install Activity.
         */
        fun continueInstallQueue(context: Context, downloadId: Long, step: InstallStep) {
            val intent = Intent(ACTION_CONTINUE_QUEUE)
                .putExtra(EXTRA_DOWNLOAD_ID, downloadId)
                .putExtra(EXTRA_INSTALL_STEP, step.name)
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
        }
    }
}
