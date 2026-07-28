package com.kalotrapezis.books

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kalotrapezis.books.data.BookEntity
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every panel and dialog the reader can open, and the controls inside them that are not
 * obvious enough to be safe: the two-step remove, the note field that appears in place,
 * the sidebar tabs, the colour swatches that carry the draft note with them.
 */
@RunWith(AndroidJUnit4::class)
class ModalsTest {
    @get:Rule
    val compose = createComposeRule()

    private val book = BookEntity(
        id = "id",
        uri = "content://books/1",
        title = "A Book",
        author = "An Author",
        metadataIdentifier = null,
        foliateKey = "foliate:key",
        sha256 = "sha",
        lastCfi = null,
        progressFraction = null,
        addedAt = 0L,
        lastOpenedAt = 0L,
    )

    // A note is written in the selection panel itself, and opening it used to close the
    // panel: the field took focus, the WebView lost it, and the selection went with it.
    @Test
    fun selectionPanelNoteFieldOpensAndKeepsThePanel() {
        var saved: Pair<String?, String>? = null
        compose.setContent {
            MaterialTheme {
                SelectionPanel(
                    excerpt = "some selected words",
                    note = "",
                    onHighlight = { color, note -> saved = color to note },
                    onCopy = {},
                    onCite = {},
                    onShare = {},
                    onLookUp = {},
                    onDismiss = {},
                )
            }
        }

        compose.onNodeWithText("Note").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("a note of mine")
        // The panel is still here, with its excerpt, and Save is now offered.
        compose.onNodeWithText("some selected words").assertIsDisplayed()
        compose.onNodeWithText("Save").assertIsDisplayed().performClick()

        assertEquals("yellow" to "a note of mine", saved)
    }

    @Test
    fun selectionPanelActionsReport() {
        var copied = false
        var cited = false
        var dismissed = false
        compose.setContent {
            MaterialTheme {
                SelectionPanel(
                    excerpt = "words",
                    note = "",
                    onHighlight = { _, _ -> },
                    onCopy = { copied = true },
                    onCite = { cited = true },
                    onShare = {},
                    onLookUp = {},
                    onDismiss = { dismissed = true },
                )
            }
        }

        compose.onNodeWithText("Copy").performClick()
        compose.onNodeWithText("Cite").performClick()
        compose.onNodeWithText("Cancel").performClick()

        assertTrue(copied && cited && dismissed)
    }

    @Test
    fun bookmarksDialogShowsWhereEachOneIs() {
        var opened: String? = null
        var removed: String? = null
        compose.setContent {
            MaterialTheme {
                BookmarksDialog(
                    bookmarks = listOf("epubcfi(/6/4!/4/2:0)", "epubcfi(/6/8!/4/2:0)"),
                    labels = mapOf(
                        "epubcfi(/6/4!/4/2:0)" to "Chapter One · Section 2 of 27 — first words",
                    ),
                    onOpen = { opened = it },
                    onRemove = { removed = it },
                    onDismiss = {},
                )
            }
        }

        compose.onNodeWithText("Chapter One").assertIsDisplayed()
        compose.onNodeWithText("Section 2 of 27 — first words").assertIsDisplayed()
        // The one the reader has not described yet still says something.
        compose.onNodeWithText("Saved page 2").assertIsDisplayed().performClick()
        assertEquals("epubcfi(/6/8!/4/2:0)", opened)

        compose.onAllNodesWithText("Remove")[0].performClick()
        assertEquals("epubcfi(/6/4!/4/2:0)", removed)
    }

    @Test
    fun bookmarksDialogSaysWhenThereAreNone() {
        compose.setContent {
            MaterialTheme {
                BookmarksDialog(emptyList(), emptyMap(), {}, {}, {})
            }
        }
        compose.onNodeWithText("No saved pages yet. Tap the ribbon to save this one.")
            .assertIsDisplayed()
    }

    // Removing a book takes two taps; one tap must not delete anything.
    @Test
    fun bookDetailsRemoveAsksFirst() {
        var removed = false
        compose.setContent {
            MaterialTheme {
                BookDetailsDialog(book = book, onRemove = { removed = true }, onDismiss = {})
            }
        }

        compose.onNodeWithText("A Book").assertIsDisplayed()
        compose.onNodeWithText("Remove").performClick()
        assertTrue("one tap must not remove the book", !removed)
        compose.onNodeWithText("Remove, keep the file").performClick()
        assertTrue(removed)
    }

    @Test
    fun settingsDialogReportsEveryToggle() {
        var scrolled: Boolean? = null
        var theme: ReaderTheme? = null
        var typography: Typography? = null
        compose.setContent {
            MaterialTheme {
                SettingsDialog(
                    scrolled = false,
                    onSetScrolled = { scrolled = it },
                    theme = ReaderTheme.GREY_ON_WHITE,
                    onSetTheme = { theme = it },
                    keepColors = true,
                    onSetKeepColors = {},
                    typography = Typography(),
                    onSetTypography = { typography = it },
                    onDismiss = {},
                )
            }
        }

        compose.onNodeWithText("Settings").assertIsDisplayed()
        compose.onNodeWithText("Scrolled reading").performClick()
        compose.onNodeWithText(ReaderTheme.WHITE_ON_GREY.label).performClick()

        assertEquals(true, scrolled)
        assertEquals(ReaderTheme.WHITE_ON_GREY, theme)
        assertNull("typography is only touched by its own sliders", typography)
    }

    @Test
    fun annotationNoteDialogShowsTheNote() {
        compose.setContent {
            MaterialTheme {
                AnnotationNoteDialog(
                    annotation = JSONObject()
                        .put("value", "epubcfi(/6/4!/4/2:0)")
                        .put("color", "yellow")
                        .put("text", "the highlighted words")
                        .put("note", "what I thought of it"),
                    onDismiss = {},
                )
            }
        }
        compose.onNodeWithText("the highlighted words").assertIsDisplayed()
        compose.onNodeWithText("what I thought of it").assertIsDisplayed()
    }

    @Test
    fun chaptersOpenTheirHref() {
        var opened: String? = null
        compose.setContent {
            MaterialTheme {
                ChaptersScreen(
                    toc = listOf(
                        TocEntry("Chapter One", "one.xhtml", 0),
                        TocEntry("Chapter Two", "two.xhtml", 1),
                    ),
                    onBack = null,
                    onOpen = { opened = it },
                )
            }
        }
        compose.onNodeWithText("Chapter Two").performClick()
        assertEquals("two.xhtml", opened)
    }

    // The tablet sidebar: three tabs, and only one while no book is open.
    @Test
    fun sidebarTabsSwitchAndHideWithNoBook() {
        compose.setContent {
            MaterialTheme {
                Sidebar(panels = null, theme = ReaderTheme.GREY_ON_WHITE) { modifier ->
                    androidx.compose.material3.Text("the library", modifier)
                }
            }
        }
        compose.onNodeWithText("the library").assertIsDisplayed()
        compose.onAllNodesWithText("Chapters").assertCountEquals(0)
        compose.onAllNodesWithText("Notes").assertCountEquals(0)
    }

    @Test
    fun sidebarShowsChaptersAndNotesWithABookOpen() {
        compose.setContent {
            MaterialTheme {
                Sidebar(
                    panels = ReaderPanels(
                        toc = listOf(TocEntry("Chapter One", "one.xhtml", 0)),
                        annotations = listOf(
                            JSONObject().put("value", "epubcfi(/6/4!/4/2:0)")
                                .put("color", "yellow").put("text", "highlighted"),
                        ),
                        openHref = {},
                        openCfi = {},
                        removeAnnotation = {},
                        onExport = {},
                        onImport = {},
                        onSync = {},
                        onSyncWrite = {},
                        syncLabel = "Sync",
                        notice = "",
                    ),
                    theme = ReaderTheme.GREY_ON_WHITE,
                    modifier = Modifier,
                ) { modifier -> androidx.compose.material3.Text("the library", modifier) }
            }
        }

        compose.onNodeWithText("Chapters").performClick()
        compose.onNodeWithText("Chapter One").assertIsDisplayed()
        compose.onNodeWithText("Notes").performClick()
        compose.onNodeWithText("highlighted").assertIsDisplayed()
        compose.onNodeWithText("Library").performClick()
        compose.onNodeWithText("the library").assertIsDisplayed()
    }

    @Test
    fun bookmarkLabelsCarryChapterAndSection() {
        val labels = org.json.JSONArray()
            .put(
                JSONObject()
                    .put("cfi", "epubcfi(/6/4!/4/2:0)")
                    .put("chapter", "Chapter One")
                    .put("section", 2)
                    .put("sections", 27)
                    .put("excerpt", "first words"),
            )
            .put(JSONObject().put("cfi", "not a cfi"))
            .toCfiLabels()

        assertEquals(
            "Chapter One · Section 2 of 27 — first words",
            labels["epubcfi(/6/4!/4/2:0)"],
        )
        assertNull(labels["not a cfi"])
    }
}
