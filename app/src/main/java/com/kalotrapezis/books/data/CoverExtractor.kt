package com.kalotrapezis.books.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Xml
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream
import org.xmlpull.v1.XmlPullParser

private const val MAX_COVER_BYTES = 8 * 1024 * 1024
private const val MAX_COVER_PIXELS = 512

/**
 * Reads the cover image straight from the EPUB zip and stores a bounded JPEG thumbnail in
 * app-private storage. Nothing goes through the WebView, so untrusted book content never
 * reaches Base64 bridge messages.
 */
object CoverExtractor {
    /** Returns the stored thumbnail path, or null when the book has no usable cover. */
    fun extract(context: Context, uri: Uri, bookId: String): String? {
        val opfPath = readEntry(context, uri, "META-INF/container.xml")
            ?.let { rootfilePath(it) } ?: return null
        val opf = readEntry(context, uri, opfPath) ?: return null
        val coverHref = coverHref(opf) ?: return null
        val coverPath = resolve(opfPath, coverHref)
        val bytes = readEntry(context, uri, coverPath) ?: return null
        val bitmap = decodeBounded(bytes) ?: return null

        val file = File(context.filesDir, "covers").apply { mkdirs() }
            .let { File(it, "$bookId.jpg") }
        return runCatching {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            file.absolutePath
        }.getOrNull().also { bitmap.recycle() }
    }

    fun delete(path: String?) {
        path?.let { runCatching { File(it).delete() } }
    }

    private fun readEntry(context: Context, uri: Uri, path: String): ByteArray? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                generateSequence { zip.nextEntry }
                    .firstOrNull { it.name == path }
                    ?: return@use null
                zip.readBounded(MAX_COVER_BYTES)
            }
        }
    }.getOrNull()

    private fun InputStream.readBounded(limit: Int): ByteArray? {
        val buffer = ByteArray(16 * 1024)
        val out = java.io.ByteArrayOutputStream()
        while (true) {
            val read = read(buffer)
            if (read < 0) return out.toByteArray()
            if (out.size() + read > limit) return null
            out.write(buffer, 0, read)
        }
    }

    private fun rootfilePath(container: ByteArray): String? =
        parse(container) { parser ->
            if (parser.name == "rootfile") parser.getAttributeValue(null, "full-path")
            else null
        }

    /** EPUB 3 `properties="cover-image"`, else the EPUB 2 `<meta name="cover">` manifest id. */
    private fun coverHref(opf: ByteArray): String? {
        var metaCoverId: String? = null
        val byProperty = parse(opf) { parser ->
            when (parser.name) {
                "meta" -> {
                    if (parser.getAttributeValue(null, "name") == "cover") {
                        metaCoverId = parser.getAttributeValue(null, "content")
                    }
                    null
                }
                "item" -> parser.getAttributeValue(null, "properties")
                    ?.split(' ')?.contains("cover-image")
                    ?.takeIf { it }
                    ?.let { parser.getAttributeValue(null, "href") }
                else -> null
            }
        }
        if (byProperty != null) return byProperty
        val byMetaId = metaCoverId?.let { id ->
            parse(opf) { parser ->
                if (parser.name == "item" && parser.getAttributeValue(null, "id") == id) {
                    parser.getAttributeValue(null, "href")
                } else null
            }
        }
        if (byMetaId != null) return byMetaId
        // Many real EPUBs point <meta name="cover"> at an id that no item declares, so fall
        // back to the first image whose id or href is named like a cover, as Foliate does.
        return parse(opf) { parser ->
            if (parser.name != "item") return@parse null
            val href = parser.getAttributeValue(null, "href") ?: return@parse null
            val named = listOf(parser.getAttributeValue(null, "id"), href)
                .any { it?.contains("cover", ignoreCase = true) == true }
            val isImage = parser.getAttributeValue(null, "media-type")
                ?.startsWith("image/") == true
            if (named && isImage) href else null
        }
    }

    private fun parse(xml: ByteArray, onStartTag: (XmlPullParser) -> String?): String? =
        runCatching {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            parser.setInput(ByteArrayInputStream(xml), null)
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG) continue
                onStartTag(parser)?.let { return@runCatching it }
            }
            null
        }.getOrNull()

    /** Resolves an OPF-relative href against the OPF's own zip path, without leaving the zip. */
    private fun resolve(opfPath: String, href: String): String {
        val decoded = Uri.decode(href)
        val base = opfPath.substringBeforeLast('/', "")
        val segments = mutableListOf<String>()
        if (base.isNotEmpty()) segments += base.split('/')
        for (segment in decoded.split('/')) when (segment) {
            "", "." -> Unit
            ".." -> segments.removeLastOrNull()
            else -> segments += segment
        }
        return segments.joinToString("/")
    }

    private fun decodeBounded(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val largest = maxOf(bounds.outWidth, bounds.outHeight)
        if (largest <= 0) return null
        var sample = 1
        while (largest / sample > MAX_COVER_PIXELS) sample *= 2
        return BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        )
    }
}
