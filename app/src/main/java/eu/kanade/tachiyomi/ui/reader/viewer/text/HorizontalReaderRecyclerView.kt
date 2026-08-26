package eu.kanade.tachiyomi.ui.reader.viewer.text

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

/**
 * A horizontally-paging [RecyclerView] that only takes over a gesture when the drag is clearly
 * horizontal. Vertical (and near-vertical) drags are left to the child [TextWebView] so that
 * scrolling to read a section doesn't accidentally flip to the next one.
 */
class HorizontalReaderRecyclerView(context: Context) : RecyclerView(context) {

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var startX = 0f
    private var startY = 0f

    init {
        // No edge bounce/glow: reading is vertical, and horizontal over-scroll just flickers.
        overScrollMode = OVER_SCROLL_NEVER
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = e.x
                startY = e.y
                // Let the parent set up its touch bookkeeping, but don't claim the gesture yet.
                super.onInterceptTouchEvent(e)
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                val rawDx = e.x - startX
                val dx = abs(rawDx)
                val dy = abs(e.y - startY)
                // Only page on a decidedly horizontal swipe; otherwise let the WebView scroll.
                if (dx > touchSlop && dx > dy * HORIZONTAL_BIAS) {
                    // Swiping left (rawDx < 0) reveals the next section (scroll right, +1); right the
                    // previous (-1). Only take over the gesture if there's actually a section that way,
                    // so a swipe with nowhere to go doesn't bounce/flicker (e.g. single-section chapters).
                    val direction = if (rawDx < 0) 1 else -1
                    if (canScrollHorizontally(direction)) {
                        return super.onInterceptTouchEvent(e)
                    }
                    return false
                }
                return false
            }
        }
        return super.onInterceptTouchEvent(e)
    }

    companion object {
        // How much the horizontal component must dominate the vertical one to count as a page turn.
        private const val HORIZONTAL_BIAS = 1.5f
    }
}
