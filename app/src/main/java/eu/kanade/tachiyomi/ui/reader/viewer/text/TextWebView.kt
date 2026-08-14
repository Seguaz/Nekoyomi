package eu.kanade.tachiyomi.ui.reader.viewer.text

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.GestureDetector
import android.view.MotionEvent
import android.webkit.WebView
import com.google.android.material.color.MaterialColors
import org.jsoup.Jsoup

/**
 * A [WebView] that renders one XHTML section of a text (novel) epub. It applies theme-aware colors
 * and comfortable reading typography, and toggles the reader menu on a single tap while still
 * allowing normal vertical scrolling within the section.
 */
@SuppressLint("SetJavaScriptEnabled", "ViewConstructor")
class TextWebView(
    context: Context,
    private val onSingleTap: () -> Unit,
) : WebView(context) {

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                onSingleTap()
                return true
            }
        },
    )

    private val backgroundColor = MaterialColors.getColor(
        context,
        com.google.android.material.R.attr.colorSurface,
        Color.BLACK,
    )
    private val textColor = MaterialColors.getColor(
        context,
        com.google.android.material.R.attr.colorOnSurface,
        Color.WHITE,
    )

    init {
        isVerticalScrollBarEnabled = true
        isHorizontalScrollBarEnabled = false
        settings.javaScriptEnabled = false
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        setBackgroundColor(backgroundColor)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        return super.onTouchEvent(event)
    }

    /** Sets the reading text size as a percentage (100 = default). */
    fun setTextScale(scale: Int) {
        settings.textZoom = scale
    }

    /** Renders the body of the given XHTML section with the reader's theme and typography. */
    fun loadSection(xhtml: String) {
        val body = runCatching { Jsoup.parse(xhtml).body().html() }.getOrDefault(xhtml)
        val bg = cssColor(backgroundColor)
        val fg = cssColor(textColor)
        val document = buildString {
            append("<html><head>")
            append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
            append("<style>")
            append("html,body{margin:0;padding:16px 20px;")
            append("background:").append(bg).append(';')
            append("color:").append(fg).append(';')
            append("font-size:19px;line-height:1.7;font-family:serif;")
            append("word-wrap:break-word;overflow-wrap:break-word;}")
            append("img{max-width:100%;height:auto;}")
            append("a{color:").append(fg).append(";}")
            append("</style></head><body>")
            append(body)
            append("</body></html>")
        }
        loadDataWithBaseURL(null, document, "text/html", "utf-8", null)
    }

    private fun cssColor(color: Int): String = "#%06X".format(0xFFFFFF and color)
}
