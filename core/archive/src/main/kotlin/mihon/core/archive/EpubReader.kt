package mihon.core.archive

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.util.Base64

/**
 * Wrapper over ArchiveReader to load files in epub format.
 */
class EpubReader(private val reader: ArchiveReader) : Closeable by reader {

    /**
     * Path separator used by this epub.
     */
    private val pathSeparator = getPathSeparator()

    /**
     * Returns an input stream for reading the contents of the specified zip file entry.
     */
    fun getInputStream(entryName: String): InputStream? {
        return reader.getInputStream(entryName)
    }

    /**
     * Returns the path of all the images found in the epub file.
     */
    fun getImagesFromPages(): List<String> {
        val ref = getPackageHref()
        val doc = getPackageDocument(ref)
        val pages = getPagesFromDocument(doc)
        return getImagesFromPages(pages, ref)
    }

    /**
     * Returns the zip-entry paths of the spine's XHTML documents, in reading order. Used to render a
     * text (novel) epub: each entry can be read as HTML with [getInputStream].
     */
    fun getSpinePaths(): List<String> {
        val ref = getPackageHref()
        val doc = getPackageDocument(ref)
        val basePath = getParentDirectory(ref)
        return getPagesFromDocument(doc).map { resolveZipPath(basePath, it) }
    }

    /**
     * Whether this epub is a text novel rather than an image (manga) epub. Image epubs wrap each
     * page in an `<img>` with virtually no text, while a novel carries prose. We accumulate the
     * plain-text length of the spine documents and stop as soon as it crosses [TEXT_NOVEL_THRESHOLD].
     */
    fun isTextNovel(): Boolean {
        var textLength = 0
        for (path in getSpinePaths()) {
            val html = getInputStream(path)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: continue
            textLength += Jsoup.parse(html).body().text().length
            if (textLength >= TEXT_NOVEL_THRESHOLD) return true
        }
        return false
    }

    /**
     * Reads a spine XHTML document and returns its `<body>` HTML with every `<img>`/`<image>` source
     * rewritten to an inline `data:` URI, so referenced illustrations render without a base URL.
     */
    fun getSectionHtml(path: String): String {
        val html = getInputStream(path)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: return ""
        val document = Jsoup.parse(html)
        val basePath = getParentDirectory(path)
        document.select("img[src]").forEach { element ->
            inlineImage(basePath, element.attr("src"))?.let { element.attr("src", it) }
        }
        document.select("image").forEach { element ->
            val href = element.attr("xlink:href").ifEmpty { element.attr("href") }
            inlineImage(basePath, href)?.let { element.attr("xlink:href", it) }
        }
        return document.body().html()
    }

    /** Encodes the epub entry referenced by [src] (relative to [basePath]) as a `data:` URI. */
    private fun inlineImage(basePath: String, src: String): String? {
        if (src.isEmpty() || src.startsWith("data:")) return null
        val entryPath = resolveZipPath(basePath, src)
        val bytes = getInputStream(entryPath)?.use { it.readBytes() } ?: return null
        val mimeType = when (entryPath.substringAfterLast('.', "").lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "svg" -> "image/svg+xml"
            "webp" -> "image/webp"
            else -> "image/*"
        }
        val encoded = Base64.getEncoder().encodeToString(bytes)
        return "data:$mimeType;base64,$encoded"
    }

    /**
     * Returns the path to the package document.
     */
    fun getPackageHref(): String {
        val meta = getInputStream(resolveZipPath("META-INF", "container.xml"))
        if (meta != null) {
            val metaDoc = meta.use { Jsoup.parse(it, null, "") }
            val path = metaDoc.getElementsByTag("rootfile").first()?.attr("full-path")
            if (path != null) {
                return path
            }
        }
        return resolveZipPath("OEBPS", "content.opf")
    }

    /**
     * Returns the package document where all the files are listed.
     */
    fun getPackageDocument(ref: String): Document {
        return getInputStream(ref)!!.use { Jsoup.parse(it, null, "") }
    }

    /**
     * Returns all the pages from the epub.
     */
    private fun getPagesFromDocument(document: Document): List<String> {
        val pages = document.select("manifest > item")
            .filter { node -> "application/xhtml+xml" == node.attr("media-type") }
            .associateBy { it.attr("id") }

        val spine = document.select("spine > itemref").map { it.attr("idref") }
        return spine.mapNotNull { pages[it] }.map { it.attr("href") }
    }

    /**
     * Returns all the images contained in every page from the epub.
     */
    private fun getImagesFromPages(pages: List<String>, packageHref: String): List<String> {
        val result = mutableListOf<String>()
        val basePath = getParentDirectory(packageHref)
        pages.forEach { page ->
            val entryPath = resolveZipPath(basePath, page)
            val document = getInputStream(entryPath)!!.use { Jsoup.parse(it, null, "") }
            val imageBasePath = getParentDirectory(entryPath)

            document.allElements.forEach {
                when (it.tagName()) {
                    "img" -> result.add(resolveZipPath(imageBasePath, it.attr("src")))
                    "image" -> result.add(resolveZipPath(imageBasePath, it.attr("xlink:href")))
                }
            }
        }

        return result
    }

    /**
     * Returns the path separator used by the epub file.
     */
    private fun getPathSeparator(): String {
        val meta = getInputStream("META-INF\\container.xml")
        return if (meta != null) {
            meta.close()
            "\\"
        } else {
            "/"
        }
    }

    /**
     * Resolves a zip path from base and relative components and a path separator.
     */
    private fun resolveZipPath(basePath: String, relativePath: String): String {
        if (relativePath.startsWith(pathSeparator)) {
            // Path is absolute, so return as-is.
            return relativePath
        }

        var fixedBasePath = basePath.replace(pathSeparator, File.separator)
        if (!fixedBasePath.startsWith(File.separator)) {
            fixedBasePath = "${File.separator}$fixedBasePath"
        }

        val fixedRelativePath = relativePath.replace(pathSeparator, File.separator)
        val resolvedPath = File(fixedBasePath, fixedRelativePath).canonicalPath
        return resolvedPath.replace(File.separator, pathSeparator).substring(1)
    }

    /**
     * Gets the parent directory of a path.
     */
    private fun getParentDirectory(path: String): String {
        val separatorIndex = path.lastIndexOf(pathSeparator)
        return if (separatorIndex >= 0) {
            path.substring(0, separatorIndex)
        } else {
            ""
        }
    }

    companion object {
        // Plain-text characters of prose needed to treat an epub as a text novel rather than manga.
        private const val TEXT_NOVEL_THRESHOLD = 500
    }
}
