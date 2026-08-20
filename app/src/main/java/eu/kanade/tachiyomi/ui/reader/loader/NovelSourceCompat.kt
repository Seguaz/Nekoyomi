package eu.kanade.tachiyomi.ui.reader.loader

import eu.kanade.tachiyomi.source.NovelSource
import eu.kanade.tachiyomi.source.model.Page
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.source.manga.service.MangaSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.lang.reflect.Method
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn

/**
 * Detects and calls the `NovelSource` marker on a source across class loaders.
 *
 * Third-party novel extensions (tsundoku / NovelSourcery) **bundle their own copy** of
 * `eu.kanade.tachiyomi.source.NovelSource` in the APK (it isn't compileOnly). Because extensions are
 * loaded with a child-first class loader, their `NovelSource` is a *different* Class than the app's,
 * so a plain `source is NovelSource` / cast fails. We match the interface by fully-qualified name
 * across the class hierarchy and invoke the suspend `fetchPageText` via **Java reflection** (calling
 * the compiler-generated `fetchPageText(Page, Continuation)` method the way the compiler would).
 *
 * First-party sources (compiled against the app's [NovelSource]) still work via the fast `is` path.
 */
object NovelSourceCompat {

    private const val NOVEL_SOURCE_FQN = "eu.kanade.tachiyomi.source.NovelSource"

    // Reflection results are stable per class / source id — cache them so library and source-list
    // filtering (which runs this per entry on every emission) doesn't walk the class hierarchy each time.
    private val interfaceCache = java.util.concurrent.ConcurrentHashMap<Class<*>, Boolean>()
    private val sourceIdCache = java.util.concurrent.ConcurrentHashMap<Long, Boolean>()

    fun isNovelSource(source: Any): Boolean {
        if (source is NovelSource) return true
        return interfaceCache.getOrPut(source.javaClass) { hasInterface(source.javaClass, NOVEL_SOURCE_FQN) }
    }

    /** Whether the installed source with [sourceId] is a novel source (looks up the runtime source). */
    fun isNovelSource(sourceId: Long): Boolean {
        sourceIdCache[sourceId]?.let { return it }
        // Don't cache a miss when the source isn't loaded yet, so it's re-checked once it loads.
        val source = Injekt.get<MangaSourceManager>().get(sourceId) ?: return false
        return isNovelSource(source).also { sourceIdCache[sourceId] = it }
    }

    /**
     * Whether an extension package is a novel extension (tsundoku / NovelSourcery format). Their
     * package namespace is `eu.kanade.tachiyomi.novelextension.*`. Useful for filtering the extension
     * lists (available extensions have no instantiated source to run [isNovelSource] on).
     */
    fun isNovelExtensionPkg(pkgName: String): Boolean {
        return pkgName.contains(".novelextension.") || pkgName.startsWith("eu.kanade.tachiyomi.novelextension")
    }

    suspend fun fetchPageText(source: Any, page: Page): String {
        if (source is NovelSource) return source.fetchPageText(page)

        // Suspend fun compiles to fetchPageText(Page, Continuation): Object
        val method: Method? = source.javaClass.methods.firstOrNull {
            it.name == "fetchPageText" && it.parameterTypes.size == 2
        }
        if (method == null) {
            logcat(LogPriority.ERROR) { "NovelSourceCompat: fetchPageText method not found on ${source.javaClass.name}" }
            return ""
        }
        return try {
            val result = suspendCoroutineUninterceptedOrReturn<Any?> { cont ->
                method.invoke(source, page, cont)
            }
            result as? String ?: ""
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "NovelSourceCompat: fetchPageText call failed" }
            ""
        }
    }

    private fun hasInterface(clazz: Class<*>, name: String): Boolean {
        var current: Class<*>? = clazz
        while (current != null) {
            for (iface in current.interfaces) {
                if (iface.name == name || hasInterface(iface, name)) return true
            }
            current = current.superclass
        }
        return false
    }
}
