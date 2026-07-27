package com.kalotrapezis.books.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FoliateJsonTest {
    private fun book(annotations: String? = null, bookmarks: String? = null) = BookEntity(
        id = "id",
        uri = "content://book",
        title = "Book",
        author = "Author",
        metadataIdentifier = "urn:uuid:1",
        foliateKey = "urn:uuid:1",
        sha256 = "hash",
        lastCfi = "epubcfi(/6/2!/4/2:0)",
        progressFraction = 0.5,
        bookmarks = bookmarks,
        annotations = annotations,
        addedAt = 0,
        lastOpenedAt = 0,
    )

    @Test fun exportKeepsFoliateShapeAndUnknownFields() {
        val json = JSONObject(
            FoliateJson.export(
                book(annotations = """[{"value":"epubcfi(/6/4!/4/2:1)","color":"yellow"}]"""),
                extras = """{"metadata":{"unknownField":"keep me"}}""",
                page = 1110,
                pages = 4721,
            )
        )
        assertEquals("urn:uuid:1", json.getJSONObject("metadata").getString("identifier"))
        assertEquals("keep me", json.getJSONObject("metadata").getString("unknownField"))
        assertEquals("epubcfi(/6/2!/4/2:0)", json.getString("lastLocation"))
        assertEquals(1110, json.getJSONArray("progress").getInt(0))
        assertEquals(4721, json.getJSONArray("progress").getInt(1))
        assertEquals(1, json.getJSONArray("annotations").length())
    }

    @Test fun mergeKeepsTheNewerAnnotationAndUnionsBookmarks() {
        val mine = """[{"value":"epubcfi(/6/4!/4/2:1)","color":"yellow","modified":"2026-01-01T00:00:00Z"}]"""
        val incoming = """
            {
              "metadata": {"identifier": "urn:uuid:1"},
              "annotations": [
                {"value":"epubcfi(/6/4!/4/2:1)","color":"green","modified":"2026-02-01T00:00:00Z"},
                {"value":"epubcfi(/6/6!/4/2:3)","color":"blue","modified":"2026-02-01T00:00:00Z"}
              ],
              "bookmarks": ["epubcfi(/6/8!/4/2:0)"]
            }
        """.trimIndent()
        val merged = FoliateJson.merge(
            book(annotations = mine, bookmarks = """["epubcfi(/6/2!/4/2:0)"]"""),
            incoming,
        )
        val result = JSONArray(merged.annotations)
        assertEquals(2, result.length())
        assertEquals("green", result.getJSONObject(0).getString("color"))
        assertEquals(2, JSONArray(merged.bookmarks).length())
        assertTrue(merged.identifierMatches)
    }

    @Test fun mergeKeepsTheOlderLocalAnnotationWhenTheFileIsStale() {
        val mine = """[{"value":"epubcfi(/6/4!/4/2:1)","color":"yellow","modified":"2026-03-01T00:00:00Z"}]"""
        val incoming = """{"annotations":[{"value":"epubcfi(/6/4!/4/2:1)","color":"pink","modified":"2026-01-01T00:00:00Z"}]}"""
        val merged = FoliateJson.merge(book(annotations = mine), incoming)
        assertEquals("yellow", JSONArray(merged.annotations).getJSONObject(0).getString("color"))
    }

    @Test fun mergeFlagsAnIdentifierMismatch() {
        val merged = FoliateJson.merge(
            book(),
            """{"metadata":{"identifier":"urn:uuid:other"},"annotations":[]}""",
        )
        assertFalse(merged.identifierMatches)
    }

    @Test fun exportAfterImportStillCarriesTheUnknownFields() {
        val merged = FoliateJson.merge(
            book(),
            """{"metadata":{"identifier":"urn:uuid:1"},"somethingNew":42,"annotations":[]}""",
        )
        val exported = JSONObject(FoliateJson.export(book(), merged.extras))
        assertEquals(42, exported.getInt("somethingNew"))
    }
}
