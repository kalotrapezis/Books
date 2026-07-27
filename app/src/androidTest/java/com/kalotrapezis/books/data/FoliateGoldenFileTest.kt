package com.kalotrapezis.books.data

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A real file written by Foliate on Linux. It guards the parts of the format we cannot
 * see from our own writer: the author object, an empty `modified`, and the fact that
 * `progress` counts locations.
 */
class FoliateGoldenFileTest {
    private val golden: String = InstrumentationRegistry.getInstrumentation().context.assets
        .open("foliate-export.json").use { it.readBytes().decodeToString() }

    private fun book() = BookEntity(
        id = "id",
        uri = "content://book",
        title = "ΚΑΙΝΗ ΔΙΑΘΗΚΗ (κείμενο - μετάφραση)",
        author = "",
        metadataIdentifier = "urn:uuid:f1644b5f-5bb6-4afe-b5e3-f3687ba5caa4",
        foliateKey = "urn:uuid:f1644b5f-5bb6-4afe-b5e3-f3687ba5caa4",
        sha256 = "hash",
        lastCfi = null,
        progressFraction = null,
        addedAt = 0,
        lastOpenedAt = 0,
    )

    @Test fun importsEveryAnnotationWithItsColourAndNote() {
        val merged = FoliateJson.merge(book(), golden)
        val items = JSONArray(merged.annotations)
        assertEquals(5, items.length())
        assertTrue(merged.identifierMatches)

        val colors = (0 until items.length()).map { items.getJSONObject(it).getString("color") }
        assertTrue(colors.contains("yellow"))
        assertTrue(colors.contains("lime"))

        val withNote = (0 until items.length())
            .map { items.getJSONObject(it) }
            .first { it.optString("note").isNotBlank() }
        assertTrue(withNote.getString("value").startsWith("epubcfi("))
    }

    @Test fun roundTripKeepsFoliateOnlyFields() {
        val merged = FoliateJson.merge(book(), golden)
        val exported = JSONObject(
            FoliateJson.export(
                book().copy(annotations = merged.annotations),
                merged.extras,
                page = 1110,
                pages = 4721,
            )
        )
        val metadata = exported.getJSONObject("metadata")
        assertEquals("el", metadata.getString("language"))
        assertEquals("2019-06-24", metadata.getString("published"))
        // Foliate writes the author as an object; ours must not flatten it.
        assertEquals("aut", metadata.getJSONObject("author").getString("role"))
        assertEquals(1110, exported.getJSONArray("progress").getInt(0))
        assertEquals(4721, exported.getJSONArray("progress").getInt(1))
        assertEquals(5, exported.getJSONArray("annotations").length())
    }

    @Test fun ourOwnAnnotationSurvivesAMergeWithTheGoldenFile() {
        val mine = JSONArray(
            listOf(
                JSONObject()
                    .put("value", "epubcfi(/6/2!/4/2/6/2,/1:0,/1:5)")
                    .put("color", "aqua")
                    .put("text", "δικό μας")
                    .put("note", "")
                    .put("created", "2026-07-27T10:00:00Z")
                    .put("modified", "2026-07-27T10:00:00Z"),
            )
        ).toString()
        val merged = FoliateJson.merge(book().copy(annotations = mine), golden)
        assertEquals(6, JSONArray(merged.annotations).length())
    }
}
