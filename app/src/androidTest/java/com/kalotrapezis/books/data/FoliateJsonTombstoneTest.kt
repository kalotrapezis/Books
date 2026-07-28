package com.kalotrapezis.books.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deleting an annotation has to survive a merge. The other side still holds it, and
 * without a record of the deletion "has it" always beats "does not have it".
 */
class FoliateJsonTombstoneTest {
    private val cfi = "epubcfi(/6/4!/4/2:0)"

    private fun book(annotations: String?, extras: String? = null) = BookEntity(
        id = "id",
        uri = "content://books/1",
        title = "A Book",
        author = "",
        metadataIdentifier = "urn:book",
        foliateKey = "urn:book",
        sha256 = "sha",
        lastCfi = null,
        progressFraction = null,
        annotations = annotations,
        foliateExtras = extras,
        addedAt = 0L,
        lastOpenedAt = 0L,
    )

    private fun annotation(modified: String) = JSONArray().put(
        JSONObject()
            .put("value", cfi)
            .put("color", "yellow")
            .put("text", "a highlight")
            .put("created", "2026-01-01T00:00:00Z")
            .put("modified", modified),
    ).toString()

    private fun file(annotations: String) =
        """{"metadata":{"identifier":"urn:book"},"annotations":$annotations,"bookmarks":[]}"""

    @Test fun aDeletionSurvivesAMergeWithAFileThatStillHasIt() {
        val extras = FoliateJson.withTombstone(null, cfi, "2026-02-01T00:00:00Z")
        val merged = FoliateJson.merge(
            book(annotations = "[]", extras = extras),
            file(annotation("2026-01-15T00:00:00Z")),
        )
        assertEquals("[]", merged.annotations.let { JSONArray(it) }.toString())
        // And it is still remembered afterwards, for the merge after this one.
        assertTrue(JSONObject(merged.extras).getJSONObject(FoliateJson.TOMBSTONES).has(cfi))
    }

    @Test fun anAnnotationTouchedAfterTheDeletionComesBack() {
        val extras = FoliateJson.withTombstone(null, cfi, "2026-02-01T00:00:00Z")
        val merged = FoliateJson.merge(
            book(annotations = "[]", extras = extras),
            file(annotation("2026-03-01T00:00:00Z")),
        )
        assertEquals(1, JSONArray(merged.annotations).length())
    }

    @Test fun theOtherSidesDeletionRemovesWhatWeStillHold() {
        val theirs = """
            {"metadata":{"identifier":"urn:book"},"annotations":[],"bookmarks":[],
             "${FoliateJson.TOMBSTONES}":{"$cfi":"2026-02-01T00:00:00Z"}}
        """.trimIndent()
        val merged = FoliateJson.merge(
            book(annotations = annotation("2026-01-15T00:00:00Z")),
            theirs,
        )
        assertEquals(0, JSONArray(merged.annotations).length())
    }

    @Test fun tombstonesDoNotTouchAnythingElseInTheFile() {
        val merged = FoliateJson.merge(
            book(annotations = "[]"),
            """{"metadata":{"identifier":"urn:book"},"annotations":[],"bookmarks":[],
                "somethingFoliateWrote":{"keep":"me"}}""".trimIndent(),
        )
        val extras = JSONObject(merged.extras)
        assertEquals("me", extras.getJSONObject("somethingFoliateWrote").getString("keep"))
        assertTrue(extras.has(FoliateJson.TOMBSTONES))
    }
}
