package eu.kanade.tachiyomi.ui.reader.viewer.text

import android.annotation.SuppressLint
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.loader.TextPageLoader
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.viewer.Viewer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.injectLazy

/**
 * A [Viewer] that renders a text (novel) epub. Each spine section is one full-screen page shown in a
 * [TextWebView]; a horizontally-snapping [RecyclerView] turns pages while each WebView scrolls
 * vertically to read within a section, and a single tap toggles the reader menu. Because sections
 * map one-to-one onto [ReaderPage]s, reading progress is tracked by the existing page machinery.
 */
class TextViewer(private val activity: ReaderActivity) : Viewer {

    private val readerPreferences: ReaderPreferences by injectLazy()

    private val scope = MainScope()

    private val adapter = TextViewerAdapter(readerPreferences.novelTextScale().get(), currentStyle())

    private val layoutManager = LinearLayoutManager(activity, RecyclerView.HORIZONTAL, false)

    private val snapHelper = PagerSnapHelper()

    private val recyclerView = HorizontalReaderRecyclerView(activity).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        layoutManager = this@TextViewer.layoutManager
        adapter = this@TextViewer.adapter
        setHasFixedSize(true)
    }

    private var pages: List<ReaderPage> = emptyList()

    init {
        snapHelper.attachToRecyclerView(recyclerView)
        recyclerView.addOnScrollListener(
            object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        reportCurrentPage()
                    }
                }
            },
        )
        // Text size applies live (WebView textZoom), without re-rendering.
        readerPreferences.novelTextScale().changes()
            .onEach { scale ->
                adapter.textScale = scale
                recyclerView.children.filterIsInstance<TextWebView>().forEach { it.setTextScale(scale) }
            }
            .launchIn(scope)
        // Other typography changes re-render the visible sections with the new style.
        combine(
            readerPreferences.novelFontFamily().changes(),
            readerPreferences.novelLineHeight().changes(),
            readerPreferences.novelMargin().changes(),
            readerPreferences.novelJustify().changes(),
        ) { _, _, _, _ -> currentStyle() }
            .onEach { style ->
                adapter.style = style
                recyclerView.children.filterIsInstance<TextWebView>().forEach { it.applyStyle(style) }
            }
            .launchIn(scope)
    }

    override fun getView(): View = recyclerView

    override fun destroy() {
        super.destroy()
        scope.cancel()
    }

    override fun setChapters(chapters: ViewerChapters) {
        val newPages = chapters.currChapter.pages.orEmpty()
        pages = newPages
        adapter.setPages(newPages)
        if (newPages.isEmpty()) return
        val target = chapters.currChapter.requestedPage.coerceIn(0, newPages.lastIndex)
        layoutManager.scrollToPosition(target)
        activity.onPageSelected(newPages[target])
    }

    override fun moveToPage(page: ReaderPage) {
        val index = pages.indexOf(page)
        if (index >= 0) {
            recyclerView.smoothScrollToPosition(index)
        }
    }

    override fun handleKeyEvent(event: KeyEvent): Boolean {
        val isUp = event.action == KeyEvent.ACTION_UP
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_PAGE_DOWN -> if (isUp) moveBy(1)
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_PAGE_UP -> if (isUp) moveBy(-1)
            KeyEvent.KEYCODE_MENU -> if (isUp) activity.toggleMenu()
            else -> return false
        }
        return true
    }

    override fun handleGenericMotionEvent(event: MotionEvent): Boolean = false

    private fun moveBy(delta: Int) {
        val next = layoutManager.findFirstVisibleItemPosition() + delta
        if (next in 0 until adapter.itemCount) {
            recyclerView.smoothScrollToPosition(next)
        }
    }

    private fun reportCurrentPage() {
        val snapView = snapHelper.findSnapView(layoutManager) ?: return
        val position = layoutManager.getPosition(snapView)
        pages.getOrNull(position)?.let(activity::onPageSelected)
    }

    /**
     * Reports that the reader reached the end of a section's text. Guarded to the section actually
     * on screen because the RecyclerView may lay out neighbouring sections off-screen.
     */
    private fun onPageReachedEnd(page: ReaderPage) {
        val snapView = snapHelper.findSnapView(layoutManager) ?: return
        if (pages.getOrNull(layoutManager.getPosition(snapView)) == page) {
            activity.onTextPageReachedEnd(page)
        }
    }

    private fun currentStyle() = NovelStyle(
        fontFamily = when (readerPreferences.novelFontFamily().get()) {
            1 -> "sans-serif"
            2 -> "monospace"
            else -> "serif"
        },
        lineHeight = readerPreferences.novelLineHeight().get() / 100f,
        marginDp = readerPreferences.novelMargin().get(),
        justify = readerPreferences.novelJustify().get(),
    )

    private inner class TextViewerAdapter(
        var textScale: Int,
        var style: NovelStyle,
    ) : RecyclerView.Adapter<TextPageHolder>() {

        private var pages: List<ReaderPage> = emptyList()

        @SuppressLint("NotifyDataSetChanged")
        fun setPages(pages: List<ReaderPage>) {
            this.pages = pages
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = pages.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TextPageHolder {
            val webView = TextWebView(activity, activity::toggleMenu).apply {
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.MATCH_PARENT,
                )
            }
            return TextPageHolder(webView)
        }

        override fun onBindViewHolder(holder: TextPageHolder, position: Int) {
            holder.bind(pages[position], textScale, style, scope, ::onPageReachedEnd)
        }

        override fun onViewRecycled(holder: TextPageHolder) {
            holder.cancel()
        }
    }
}

private class TextPageHolder(
    private val webView: TextWebView,
) : RecyclerView.ViewHolder(webView) {

    private var job: Job? = null

    fun bind(
        page: ReaderPage,
        textScale: Int,
        style: NovelStyle,
        scope: CoroutineScope,
        onReachedEnd: (ReaderPage) -> Unit,
    ) {
        job?.cancel()
        webView.setTextScale(textScale)
        webView.onReachedBottom = { onReachedEnd(page) }
        val loader = page.chapter.pageLoader as? TextPageLoader
        if (loader == null) {
            webView.load("", style, trackReading = false)
            return
        }
        // Show a placeholder (not tracked), then load the (possibly network-fetched) text and only
        // then arm end-of-text tracking — and only when there's actual text, so a failed/empty fetch
        // isn't counted as read.
        webView.load("<p style=\"opacity:0.5\">…</p>", style, trackReading = false)
        job = scope.launch {
            val html = try {
                withIOContext { loader.getPageText(page) }
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                logcat(LogPriority.ERROR, e) { "TextViewer: failed to load page text" }
                ""
            }
            val hasText = html.isNotBlank()
            webView.load(
                html.ifBlank { "<p style=\"opacity:0.5\">(empty)</p>" },
                style,
                trackReading = hasText,
            )
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
    }
}
