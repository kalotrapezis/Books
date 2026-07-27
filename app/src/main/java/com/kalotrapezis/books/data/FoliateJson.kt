package com.kalotrapezis.books.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * The reading-data file Foliate keeps next to a book. Unknown fields are carried through
 * untouched, so a round trip never drops anything Foliate wrote.
 */
object FoliateJson {
    fun export(book: BookEntity, extras: String?): String {
        val root = extras?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()
        val metadata = root.optJSONObject("metadata") ?: JSONObject()
        book.metadataIdentifier?.let { metadata.put("identifier", it) }
        if (book.title.isNotBlank()) metadata.put("title", book.title)
        if (book.author.isNotBlank()) metadata.put("author", book.author)
        root.put("metadata", metadata)
        root.put("lastLocation", book.lastCfi ?: JSONObject.NULL)
        book.progressFraction?.let { root.put("progress", JSONArray(listOf(it))) }
        root.put("bookmarks", JSONArray(book.bookmarks ?: "[]"))
        root.put("annotations", JSONArray(book.annotations ?: "[]"))
        return root.toString(2)
    }

    /**
     * Merges a Foliate file into what we already hold: annotations and bookmarks are
     * matched on `value`, and the newer `modified` wins. Everything else in the file is
     * preserved for the next export.
     */
    fun merge(book: BookEntity, incoming: String): Merged {
        val root = JSONObject(incoming)
        val identifier = root.optJSONObject("metadata")?.optString("identifier")
            ?.takeIf { it.isNotBlank() }

        val mine = (book.annotations ?: "[]").toAnnotations()
        val theirs = root.optJSONArray("annotations").toObjects()
        val byValue = LinkedHashMap<String, JSONObject>()
        for (item in mine) byValue[item.optString("value")] = item
        for (item in theirs) {
            val key = item.optString("value").ifBlank { continue }
            val existing = byValue[key]
            if (existing == null || item.optString("modified") >= existing.optString("modified")) {
                byValue[key] = item
            }
        }

        val bookmarks = LinkedHashSet((book.bookmarks ?: "[]").toCfis())
        bookmarks += root.optJSONArray("bookmarks").toCfis()

        // Keep the parts of the file we do not model, minus what we just merged.
        val extras = JSONObject(root.toString()).apply {
            remove("annotations")
            remove("bookmarks")
            remove("lastLocation")
            remove("progress")
        }
        return Merged(
            annotations = JSONArray(byValue.values.toList()).toString(),
            bookmarks = JSONArray(bookmarks.toList()).toString(),
            identifierMatches = identifier == null || identifier == book.metadataIdentifier,
            extras = extras.toString(),
        )
    }

    data class Merged(
        val annotations: String,
        val bookmarks: String,
        val identifierMatches: Boolean,
        val extras: String,
    )
}

internal fun String.toAnnotations(): List<JSONObject> =
    runCatching { JSONArray(this) }.getOrNull().toObjects()

private fun JSONArray?.toObjects(): List<JSONObject> {
    val array = this ?: return emptyList()
    return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
}

private fun JSONArray?.toCfis(): List<String> {
    val array = this ?: return emptyList()
    return (0 until array.length()).mapNotNull {
        array.optString(it).takeIf { cfi -> cfi.startsWith("epubcfi(") }
    }
}

private fun String.toCfis(): List<String> =
    runCatching { JSONArray(this) }.getOrNull().toCfis()
