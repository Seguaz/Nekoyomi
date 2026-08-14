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
                val dx = abs(e.x - startX)
                val dy = abs(e.y - startY)
                // Only page on a decidedly horizontal swipe; otherwise let the WebView scroll.
                if (dx > touchSlop && dx > dy * HORIZONTAL_BIAS) {
                    return super.onInterceptTouchEvent(e)
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
