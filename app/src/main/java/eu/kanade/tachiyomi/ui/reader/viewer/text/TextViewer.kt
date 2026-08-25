package eu.kanade.tachiyomi.ui.reader.viewer.text

import android.annotation.SuppressLint
import android.graphics.Color
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
import eu.kanade.tachiyomi.util.system.isNightMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
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

    /** Non-null while auto-scroll is running. */
    private var autoScrollJob: Job? = null

    // Whether the user turned auto-scroll on. A manual drag suspends the running job but keeps this
    // true so it resumes when the drag settles; a tap (menu hidden) or the toggle clears it.
    private var autoScrollEnabled = false

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
        // Reader background/text color (white/black/gray/automatic/beige) applies live.
        readerPreferences.readerTheme().changes()
            .onEach {
                val (bg, text) = readerColors()
                recyclerView.children.filterIsInstance<TextWebView>().forEach { it.setColors(bg, text) }
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

    /**
     * Toggles auto-scroll and returns whether it is now running. Scrolls the current section's text
     * vertically at the configured speed; when it reaches the bottom it advances to the next section,
     * and stops once the last section is fully scrolled.
     */
    fun toggleAutoScroll(): Boolean {
        if (autoScrollEnabled) stopAutoScroll() else startAutoScroll()
        return autoScrollEnabled
    }

    private fun startAutoScroll() {
        autoScrollEnabled = true
        launchAutoScroll()
    }

    private fun launchAutoScroll() {
        autoScrollJob?.cancel()
        val density = recyclerView.resources.displayMetrics.density
        autoScrollJob = scope.launch {
            var remainder = 0f
            while (isActive) {
                val level = readerPreferences.webtoonAutoScrollSpeed().get()
                    .coerceIn(1, MAX_AUTO_SCROLL_LEVEL)
                val dpPerSecond = AUTO_SCROLL_MIN_DP_PER_SEC +
                    (level - 1) * (AUTO_SCROLL_MAX_DP_PER_SEC - AUTO_SCROLL_MIN_DP_PER_SEC) /
                    (MAX_AUTO_SCROLL_LEVEL - 1)
                val distance = remainder + dpPerSecond * density * AUTO_SCROLL_FRAME_MS / 1000f
                val dy = distance.toInt()
                remainder = distance - dy
                if (dy != 0) {
                    val webView = snapHelper.findSnapView(layoutManager) as? TextWebView
                    if (webView != null) {
                        if (webView.canScrollVertically(1)) {
                            webView.scrollBy(0, dy)
                        } else {
                            // Bottom of this section reached: advance to the next one, or stop at the end.
                            val position = layoutManager.getPosition(webView)
                            if (position < adapter.itemCount - 1) {
                                recyclerView.smoothScrollToPosition(position + 1)
                                // Let the horizontal snap settle before scrolling the next section.
                                delay(SECTION_ADVANCE_DELAY_MS)
                            } else {
                                stopAutoScroll()
                                activity.viewModel.setAutoScrollActive(false)
                            }
                        }
                    }
                }
                delay(AUTO_SCROLL_FRAME_MS)
            }
        }
    }

    private fun stopAutoScroll() {
        autoScrollEnabled = false
        autoScrollJob?.cancel()
        autoScrollJob = null
    }

    /** A manual drag suspends the running scroll (kept enabled); it resumes when the drag settles. */
    private fun suspendAutoScroll() {
        autoScrollJob?.cancel()
    }

    private fun resumeAutoScroll() {
        if (autoScrollEnabled && autoScrollJob?.isActive != true) launchAutoScroll()
    }

    /**
     * Handles a tap on a section, mirroring the webtoon reader: while the menu is up a tap only
     * dismisses/uses it (e.g. right after pressing play) and doesn't stop the scroll; with the menu
     * hidden while scrolling, a tap pauses auto-scroll and reveals the menu; otherwise it toggles it.
     */
    private fun handleTap() {
        if (!activity.viewModel.state.value.menuVisible && autoScrollEnabled) {
            stopAutoScroll()
            activity.viewModel.setAutoScrollActive(false)
            activity.showMenu()
        } else {
            activity.toggleMenu()
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

    /** Background + text colors for the current reader theme (white/black/gray/automatic/beige). */
    private fun readerColors(): Pair<Int, Int> = when (readerPreferences.readerTheme().get()) {
        0 -> Color.WHITE to READER_TEXT_DARK
        2 -> READER_GRAY_BG to READER_TEXT_LIGHT
        3 -> if (activity.isNightMode()) {
            READER_GRAY_BG to READER_TEXT_LIGHT
        } else {
            Color.WHITE to READER_TEXT_DARK
        }
        4 -> READER_BEIGE_BG to READER_BEIGE_TEXT
        else -> Color.BLACK to READER_TEXT_LIGHT
    }

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
            val webView = TextWebView(activity, this@TextViewer::handleTap).apply {
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.MATCH_PARENT,
                )
                // Manual drag suspends auto-scroll; it resumes when the finger lifts.
                onUserDragStart = { suspendAutoScroll() }
                onUserDragEnd = { resumeAutoScroll() }
            }
            return TextPageHolder(webView)
        }

        override fun onBindViewHolder(holder: TextPageHolder, position: Int) {
            val (bgColor, textColor) = readerColors()
            holder.bind(pages[position], textScale, style, bgColor, textColor, scope, ::onPageReachedEnd)
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
        bgColor: Int,
        textColor: Int,
        scope: CoroutineScope,
        onReachedEnd: (ReaderPage) -> Unit,
    ) {
        job?.cancel()
        webView.setTextScale(textScale)
        webView.setColors(bgColor, textColor)
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

// Auto-scroll tuning (mirrors the webtoon viewer so the same speed slider drives both).
private const val MAX_AUTO_SCROLL_LEVEL = 10
private const val AUTO_SCROLL_MIN_DP_PER_SEC = 25f
private const val AUTO_SCROLL_MAX_DP_PER_SEC = 400f
private const val AUTO_SCROLL_FRAME_MS = 16L
private const val SECTION_ADVANCE_DELAY_MS = 400L

// Reader-theme colors for the novel viewer (gray matches ReaderActivity; beige is a warm sepia).
private val READER_GRAY_BG = Color.rgb(0x20, 0x21, 0x25)
private val READER_BEIGE_BG = Color.rgb(0xF5, 0xEC, 0xD9)
private val READER_BEIGE_TEXT = Color.rgb(0x5B, 0x46, 0x36)
private val READER_TEXT_DARK = Color.rgb(0x1A, 0x1A, 0x1A)
private val READER_TEXT_LIGHT = Color.rgb(0xE0, 0xE0, 0xE0)
