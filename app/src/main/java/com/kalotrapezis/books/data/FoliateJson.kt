package com.kalotrapezis.books.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * The reading-data file Foliate keeps next to a book. Unknown fields are carried through
 * untouched, so a round trip never drops anything Foliate wrote.
 */
object FoliateJson {
    /**
     * Deletions, kept in a field of our own inside the file. Foliate carries unknown
     * fields through untouched, exactly as we do, so a tombstone survives a round trip
     * through it. Without them a merge quietly brings back everything you deleted: the
     * other side still has the annotation, and having it beats not having it.
     */
    const val TOMBSTONES = "booksDeleted"

    fun export(book: BookEntity, extras: String?, page: Int? = null, pages: Int? = null): String {
        val root = extras?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()
        val metadata = root.optJSONObject("metadata") ?: JSONObject()
        book.metadataIdentifier?.let { metadata.put("identifier", it) }
        if (!metadata.has("title") && book.title.isNotBlank()) metadata.put("title", book.title)
        // Foliate writes author as an object; never flatten one it already wrote.
        if (!metadata.has("author") && book.author.isNotBlank()) {
            metadata.put("author", JSONObject().put("name", book.author).put("role", "aut"))
        }
        root.put("metadata", metadata)
        root.put("lastLocation", book.lastCfi ?: JSONObject.NULL)
        // Foliate's progress is [current location, total locations], not a fraction.
        if (page != null && pages != null && pages > 0) {
            root.put("progress", JSONArray(listOf(page, pages)))
        }
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
        // Both sides' tombstones, newest wins, and they outlive the merge itself.
        val graves = LinkedHashMap<String, String>()
        for (side in listOf((book.foliateExtras ?: "{}").toGraves(), root.toGraves())) {
            for ((value, deleted) in side) {
                if (deleted >= (graves[value] ?: "")) graves[value] = deleted
            }
        }
        for (item in theirs) {
            val key = item.optString("value").ifBlank { continue }
            val existing = byValue[key]
            if (existing == null || item.optString("modified") >= existing.optString("modified")) {
                byValue[key] = item
            }
        }
        // A deletion beats an annotation that has not been touched since it was deleted.
        for ((value, deleted) in graves) {
            val kept = byValue[value] ?: continue
            if (kept.optString("modified") <= deleted) byValue.remove(value)
        }

        val bookmarks = LinkedHashSet((book.bookmarks ?: "[]").toCfis())
        bookmarks += root.optJSONArray("bookmarks").toCfis()

        // Keep the parts of the file we do not model, minus what we just merged.
        val extras = JSONObject(root.toString()).apply {
            remove("annotations")
            remove("bookmarks")
            remove("lastLocation")
            remove("progress")
            put(TOMBSTONES, JSONObject(graves.mapValues { it.value }))
        }
        return Merged(
            annotations = JSONArray(byValue.values.toList()).toString(),
            bookmarks = JSONArray(bookmarks.toList()).toString(),
            identifierMatches = identifier == null || identifier == book.metadataIdentifier,
            extras = extras.toString(),
        )
    }

    /** Records a deletion so a later merge does not undo it. */
    fun withTombstone(extras: String?, value: String, deletedAt: String): String {
        val root = extras?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()
        val graves = root.optJSONObject(TOMBSTONES) ?: JSONObject()
        graves.put(value, deletedAt)
        return root.put(TOMBSTONES, graves).toString()
    }

    data class Merged(
        val annotations: String,
        val bookmarks: String,
        val identifierMatches: Boolean,
        val extras: String,
    )
}

/** value → when it was deleted. */
private fun JSONObject.toGraves(): Map<String, String> {
    val graves = optJSONObject(FoliateJson.TOMBSTONES) ?: return emptyMap()
    return graves.keys().asSequence().associateWith { graves.optString(it) }
        .filterValues { it.isNotBlank() }
}

private fun String.toGraves(): Map<String, String> =
    runCatching { JSONObject(this) }.getOrNull()?.toGraves() ?: emptyMap()

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
