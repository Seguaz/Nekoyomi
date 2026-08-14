package eu.kanade.tachiyomi.ui.reader.viewer.text

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.GestureDetector
import android.view.MotionEvent
import android.webkit.WebView
import com.google.android.material.color.MaterialColors

/** Typography options for the novel reader, applied as CSS. */
data class NovelStyle(
    val fontFamily: String,
    val lineHeight: Float,
    val marginDp: Int,
    val justify: Boolean,
)

/**
 * A [WebView] that renders one section of a text (novel) epub. It applies theme-aware colors and the
 * user's typography ([NovelStyle]), and toggles the reader menu on a single tap while still allowing
 * normal vertical scrolling within the section. Text size is applied live via [setTextScale]; other
 * typography changes are applied with [applyStyle], which re-renders the current section.
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

    private var body: String = ""
    private var style: NovelStyle = DEFAULT_STYLE

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

    /** Sets the reading text size as a percentage (100 = default). Applies live, without re-rendering. */
    fun setTextScale(scale: Int) {
        settings.textZoom = scale
    }

    /** Renders the given section [body] HTML with the given typography. */
    fun load(body: String, style: NovelStyle) {
        this.body = body
        this.style = style
        render()
    }

    /** Re-renders the current section with new typography (font, spacing, margins, alignment). */
    fun applyStyle(style: NovelStyle) {
        this.style = style
        render()
    }

    private fun render() {
        val bg = cssColor(backgroundColor)
        val fg = cssColor(textColor)
        val align = if (style.justify) "justify" else "start"
        val document = buildString {
            append("<html><head>")
            append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
            append("<style>")
            append("html,body{margin:0;padding:16px ").append(style.marginDp).append("px;")
            append("background:").append(bg).append(';')
            append("color:").append(fg).append(';')
            append("font-size:19px;")
            append("line-height:").append(style.lineHeight).append(';')
            append("font-family:").append(style.fontFamily).append(';')
            append("text-align:").append(align).append(';')
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

    companion object {
        val DEFAULT_STYLE = NovelStyle(fontFamily = "serif", lineHeight = 1.7f, marginDp = 20, justify = false)
    }
}
