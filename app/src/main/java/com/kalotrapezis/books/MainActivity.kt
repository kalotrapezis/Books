package com.kalotrapezis.books

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.LocalContentColor
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import com.kalotrapezis.books.data.BookDao
import com.kalotrapezis.books.data.BookEntity
import com.kalotrapezis.books.data.BookIdentifiers
import com.kalotrapezis.books.data.BooksDatabase
import com.kalotrapezis.books.data.CoverExtractor
import com.kalotrapezis.books.data.AnnotationExport
import com.kalotrapezis.books.data.ExportFormat
import com.kalotrapezis.books.data.FoliateJson
import com.kalotrapezis.books.data.toAnnotations
import java.io.ByteArrayInputStream
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val READER_ORIGIN = "appassets.androidplatform.net"
private const val READER_URL = "https://$READER_ORIGIN/assets/reader/index.html?v=55"
private const val EPUB_MIME_TYPE = "application/epub+zip"

/**
 * foliate-js sniffs zip and PDF headers itself, but tells CBZ, FBZ and FB2 apart by file
 * name, so a book is served to the reader under its own extension. Anything else is
 * offered to the EPUB loader, which fails with a clean message.
 */
private val BOOK_MIME_TYPES = mapOf(
    "epub" to EPUB_MIME_TYPE,
    "pdf" to "application/pdf",
    "cbz" to "application/vnd.comicbook+zip",
    "fb2" to "application/x-fictionbook+xml",
    "fbz" to "application/x-zip-compressed-fb2",
    "mobi" to "application/x-mobipocket-ebook",
    "azw" to "application/vnd.amazon.ebook",
    "azw3" to "application/vnd.amazon.ebook",
    "kf8" to "application/vnd.amazon.ebook",
    "prc" to "application/x-mobipocket-ebook",
)

// SAF greys out anything outside the filter, and CBZ, FB2 and MOBI usually reach the
// picker with no registered MIME type at all, so the filter would hide real books. The
// reader rejects what it cannot parse with a clear message instead.
private val PICKER_MIME_TYPES = arrayOf("*/*")

/** `selected.<ext>`: the name the reader asks for, and the only one we will serve. */
private fun bookFileName(context: Context, uri: Uri): String {
    val extension = displayName(context, uri).substringAfterLast('.', "").lowercase()
    return "selected." + if (extension in BOOK_MIME_TYPES) extension else "epub"
}

/** The file's own name; PDFs and comics rarely carry a title, so it stands in for one. */
private fun displayName(context: Context, uri: Uri): String {
    val name = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull() ?: uri.lastPathSegment.orEmpty()
    return name
}

private const val PREFERENCES_NAME = "reader-state"
private const val BOOK_URI_KEY = "book-uri"
private const val LAST_CFI_KEY = "last-cfi"
private const val SCROLLED_KEY = "scrolled"
private const val PINNED_KEY = "libraryPinned"
private const val DARK_KEY = "dark"
private const val FONT_SCALE_KEY = "font-scale"
private const val LINE_HEIGHT_KEY = "line-height"
private const val MARGIN_KEY = "margin"
private const val FONT_KEY = "font"
private const val SYNC_FILE_PREFIX = "sync-file-"
private const val KEEP_COLORS_KEY = "keep-colors"

/** Reader typography, shared by every book. */
private data class Typography(
    val fontScale: Int = 100,
    val lineHeight: Float = 1.5f,
    val margin: Int = 48,
    val font: String = "book",
) {
    fun toJson(): JSONObject = JSONObject()
        .put("type", "SetTypography")
        .put("fontScale", fontScale)
        .put("lineHeight", lineHeight.toDouble())
        .put("margin", margin)
        .put("font", font)
}

/** Two low-glare reading themes: grey on white, and white on grey. */
private enum class ReaderTheme(
    val label: String,
    val background: Color,
    val foreground: Color,
    val link: Color,
    /** libadwaita keeps the sidebar a step away from the page it sits next to. */
    val sidebar: Color,
) {
    GREY_ON_WHITE(
        "Grey on white",
        Color(0xFFFAFAFA),
        Color(0xFF3A3A3A),
        Color(0xFF1A5FB4),
        Color(0xFFEBEBEB),
    ),
    WHITE_ON_GREY(
        "White on grey",
        Color(0xFF303234),
        Color(0xFFE4E4E4),
        Color(0xFF8AB4F8),
        Color(0xFF3B3D40),
    );

    fun hex(color: Color) = String.format("#%06X", color.toArgb() and 0xFFFFFF)

    fun colorScheme() = if (this == GREY_ON_WHITE) {
        lightColorScheme(
            primary = foreground,
            onPrimary = background,
            secondary = foreground,
            onSecondary = background,
            background = background,
            surface = background,
            onBackground = foreground,
            onSurface = foreground,
            surfaceVariant = foreground.copy(alpha = 0.10f),
            onSurfaceVariant = foreground,
            secondaryContainer = foreground.copy(alpha = 0.10f),
            onSecondaryContainer = foreground,
            surfaceContainerHighest = foreground.copy(alpha = 0.08f),
        )
    } else {
        darkColorScheme(
            primary = foreground,
            onPrimary = background,
            secondary = foreground,
            onSecondary = background,
            background = background,
            surface = background,
            onBackground = foreground,
            onSurface = foreground,
            surfaceVariant = foreground.copy(alpha = 0.16f),
            onSurfaceVariant = foreground,
            secondaryContainer = foreground.copy(alpha = 0.16f),
            onSecondaryContainer = foreground,
            surfaceContainerHighest = foreground.copy(alpha = 0.12f),
        )
    }
}
private val LOCAL_SCHEMES = setOf("blob", "data")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { BooksApp() }
    }
}

@Composable
private fun BooksApp() {
    val context = LocalContext.current
    val database = remember { BooksDatabase.getInstance(context.applicationContext) }
    val dao = remember(database) { database.bookDao() }
    val scope = rememberCoroutineScope()
    var library by remember { mutableStateOf(emptyList<BookEntity>()) }
    var selectedBookId by remember { mutableStateOf<String?>(null) }
    var initialized by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val coverAttempts = remember { mutableSetOf<String>() }
    val preferences = remember {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }
    var scrolled by remember { mutableStateOf(preferences.getBoolean(SCROLLED_KEY, false)) }
    // Pinned keeps the sidebar beside the book; unpinned it steps aside while you read
    // and the reader's back arrow brings it back.
    var pinned by remember { mutableStateOf(preferences.getBoolean(PINNED_KEY, true)) }
    var showLibrary by remember { mutableStateOf(false) }
    var panels by remember { mutableStateOf<ReaderPanels?>(null) }
    val setPinned: (Boolean) -> Unit = {
        pinned = it
        showLibrary = false
        preferences.edit().putBoolean(PINNED_KEY, it).apply()
    }
    var theme by remember {
        mutableStateOf(
            if (preferences.getBoolean(DARK_KEY, false)) ReaderTheme.WHITE_ON_GREY
            else ReaderTheme.GREY_ON_WHITE
        )
    }
    var typography by remember {
        mutableStateOf(
            Typography(
                fontScale = preferences.getInt(FONT_SCALE_KEY, 100),
                lineHeight = preferences.getFloat(LINE_HEIGHT_KEY, 1.5f),
                margin = preferences.getInt(MARGIN_KEY, 48),
                font = preferences.getString(FONT_KEY, "book") ?: "book",
            )
        )
    }
    val setTypography: (Typography) -> Unit = {
        typography = it
        preferences.edit()
            .putInt(FONT_SCALE_KEY, it.fontScale)
            .putFloat(LINE_HEIGHT_KEY, it.lineHeight)
            .putInt(MARGIN_KEY, it.margin)
            .putString(FONT_KEY, it.font)
            .apply()
    }
    var keepColors by remember { mutableStateOf(preferences.getBoolean(KEEP_COLORS_KEY, true)) }
    val setKeepColors: (Boolean) -> Unit = {
        keepColors = it
        preferences.edit().putBoolean(KEEP_COLORS_KEY, it).apply()
    }
    val setTheme: (ReaderTheme) -> Unit = {
        theme = it
        preferences.edit().putBoolean(DARK_KEY, it == ReaderTheme.WHITE_ON_GREY).apply()
    }
    val setScrolled: (Boolean) -> Unit = {
        scrolled = it
        preferences.edit().putBoolean(SCROLLED_KEY, it).apply()
    }

    LaunchedEffect(dao) {
        migrateLegacyBook(context, dao)?.let { error = it }
        dao.observeLibrary().collect { books ->
            library = books
            // ponytail: books added before covers existed, and books whose cover extraction
            // failed, are retried once per session; add a persisted "tried" flag if the
            // repeated zip scan ever shows up on startup.
            books.filter { it.coverPath == null && it.id !in coverAttempts }.forEach { book ->
                coverAttempts += book.id
                launch(Dispatchers.IO) {
                    CoverExtractor.extract(context, Uri.parse(book.uri), book.id)
                        ?.let { dao.updateCover(book.id, it) }
                }
            }
            if (!initialized) {
                selectedBookId = books.firstOrNull()?.id
                initialized = true
            } else if (selectedBookId != null && books.none { it.id == selectedBookId }) {
                selectedBookId = null
            }
        }
    }

    val openBook = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val permissionFailure = runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }.exceptionOrNull()
            if (permissionFailure != null) {
                error = "Could not keep access to this book."
            } else {
                scope.launch {
                    runCatching { addOrOpenBook(context, dao, uri) }
                        .onSuccess {
                            selectedBookId = it.id
                            error = ""
                        }
                        .onFailure {
                            error = "Could not add book: ${it.message ?: "unknown error"}"
                        }
                }
            }
        }
    }
    val launchPicker = { openBook.launch(PICKER_MIME_TYPES) }
    val selectBook: (BookEntity) -> Unit = { book ->
        selectedBookId = book.id
        scope.launch { dao.markOpened(book.id, System.currentTimeMillis()) }
    }
    val removeBook: (BookEntity) -> Unit = { book ->
        scope.launch {
            withContext(Dispatchers.IO) { CoverExtractor.delete(book.coverPath) }
            dao.delete(book.id)
        }
    }
    val selectedBook = library.firstOrNull { it.id == selectedBookId }

    MaterialTheme(colorScheme = theme.colorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
                if (maxWidth >= 600.dp) {
                    // Unpinned, the sidebar steps aside as soon as a book is open, and
                    // comes back floating over the page — dragged in from the left edge,
                    // or with the reader's back arrow.
                    val docked = pinned || selectedBook == null
                    val sidebar = @Composable { floating: Boolean ->
                      Sidebar(
                        panels = panels,
                        theme = theme,
                        floating = floating,
                        modifier = Modifier.width(300.dp).fillMaxHeight(),
                      ) { paneModifier -> LibraryPane(
                            books = library,
                            selectedBookId = selectedBookId,
                            error = error,
                            scrolled = scrolled,
                            onSetScrolled = setScrolled,
                            theme = theme,
                            onSetTheme = setTheme,
                            keepColors = keepColors,
                            onSetKeepColors = setKeepColors,
                            typography = typography,
                            onSetTypography = setTypography,
                            onAddBook = launchPicker,
                            onSelectBook = {
                                showLibrary = false
                                selectBook(it)
                            },
                            onRemoveBook = removeBook,
                            onSetPinned = { setPinned(it) },
                            pinned = pinned,
                            paneColor = Color.Transparent,
                            modifier = paneModifier,
                        ) }
                    }
                    Row(Modifier.fillMaxSize()) {
                        if (docked) sidebar(false)
                        if (selectedBook == null) {
                            panels = null
                            EmptyReader(Modifier.weight(1f).fillMaxHeight())
                        } else {
                            ReaderScreen(
                                book = selectedBook,
                                dao = dao,
                                persistenceScope = scope,
                                scrolled = scrolled,
                                theme = theme,
                                keepColors = keepColors,
                                typography = typography,
                                onBack = if (pinned) null else ({ showLibrary = true }),
                                onPanels = { panels = it },
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                    }
                    // A strip along the left edge opens the sidebar by drag. It has to
                    // claim the gesture, or Android's own back swipe eats it first.
                    if (!docked && !showLibrary) {
                        Box(
                            Modifier.fillMaxHeight().width(24.dp)
                                .align(Alignment.CenterStart)
                                .systemGestureExclusion()
                                .draggableFromLeftEdge { showLibrary = true },
                        )
                    }
                    if (!docked && showLibrary) {
                        // Tap the page to send it away again.
                        Box(
                            Modifier.fillMaxSize()
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                ) { showLibrary = false },
                        )
                        sidebar(true)
                    }
                } else if (selectedBook == null) {
                    LibraryPane(
                        books = library,
                        selectedBookId = null,
                        error = error,
                        scrolled = scrolled,
                        onSetScrolled = setScrolled,
                        theme = theme,
                        onSetTheme = setTheme,
                        keepColors = keepColors,
                        onSetKeepColors = setKeepColors,
                        typography = typography,
                        onSetTypography = setTypography,
                        onAddBook = launchPicker,
                        onSelectBook = selectBook,
                        onRemoveBook = removeBook,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    ReaderScreen(
                        book = selectedBook,
                        dao = dao,
                        persistenceScope = scope,
                        scrolled = scrolled,
                        theme = theme,
                        keepColors = keepColors,
                        typography = typography,
                        onBack = { selectedBookId = null },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

/** Library, chapters and annotations side by side with the page, one tab each. */
@Composable
private fun Sidebar(
    panels: ReaderPanels?,
    theme: ReaderTheme,
    /** Floating over the page (unpinned) it lets the text show through; pinned it is solid. */
    floating: Boolean = false,
    modifier: Modifier = Modifier,
    library: @Composable (Modifier) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(0) }
    // A closed book has nothing to show in the other two tabs.
    val tabs = if (panels == null) listOf("Library") else listOf("Library", "Chapters", "Notes")
    val current = tab.coerceIn(0, tabs.lastIndex)
    // Floating, it is the same island as the reader chrome: barely translucent, thin
    // border, rounded — legibility first.
    Surface(
        modifier,
        color = if (floating) theme.sidebar.copy(alpha = 0.94f) else theme.sidebar,
        shape = if (floating) RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
        else RoundedCornerShape(0.dp),
        border = if (floating) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
        } else null,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Box {
            // The lists sit in a rounded well, so they read as a panel, not a wall.
            Box(Modifier.fillMaxSize().padding(4.dp).clip(RoundedCornerShape(16.dp))) {
                when {
                    current == 1 && panels != null -> ChaptersScreen(
                        toc = panels.toc,
                        onBack = null,
                        onOpen = panels.openHref,
                        color = Color.Transparent,
                        modifier = Modifier.fillMaxSize(),
                    )
                    current == 2 && panels != null -> AnnotationsScreen(
                        annotations = panels.annotations,
                        onBack = null,
                        onExport = panels.onExport,
                        onImport = panels.onImport,
                        onSync = panels.onSync,
                        onSyncWrite = panels.onSyncWrite,
                        syncLabel = panels.syncLabel,
                        notice = panels.notice,
                        onOpen = panels.openCfi,
                        onRemove = panels.removeAnnotation,
                        color = Color.Transparent,
                        modifier = Modifier.fillMaxSize(),
                    )
                    else -> library(Modifier.fillMaxSize())
                }
            }
            if (tabs.size > 1) {
                // ponytail: translucency, not a real backdrop blur — Compose has no
                // backdrop RenderEffect. A blur needs a third-party layer (haze).
                ChromeIsland(
                    Modifier.align(Alignment.BottomCenter).padding(8.dp).fillMaxWidth(),
                ) {
                Row(
                    Modifier.fillMaxWidth().padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    tabs.forEachIndexed { index, label ->
                        val selected = index == current
                        Surface(
                            onClick = { tab = index },
                            shape = CircleShape,
                            color = if (selected) MaterialTheme.colorScheme.secondaryContainer
                            else Color.Transparent,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelLarge,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                modifier = Modifier.padding(vertical = 10.dp),
                            )
                        }
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun LibraryPane(
    books: List<BookEntity>,
    selectedBookId: String?,
    error: String,
    scrolled: Boolean,
    onSetScrolled: (Boolean) -> Unit,
    theme: ReaderTheme,
    onSetTheme: (ReaderTheme) -> Unit,
    keepColors: Boolean,
    onSetKeepColors: (Boolean) -> Unit,
    typography: Typography,
    onSetTypography: (Typography) -> Unit,
    onAddBook: () -> Unit,
    onSelectBook: (BookEntity) -> Unit,
    onRemoveBook: (BookEntity) -> Unit,
    /** Null on a phone, where the library is a screen of its own and never pinned. */
    onSetPinned: ((Boolean) -> Unit)? = null,
    pinned: Boolean = true,
    paneColor: Color = Color.Unspecified,
    modifier: Modifier = Modifier,
) {
    var showSettings by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf<BookEntity?>(null) }
    details?.let { book ->
        BookDetailsDialog(
            book = book,
            onRemove = {
                onRemoveBook(book)
                details = null
            },
            onDismiss = { details = null },
        )
    }
    if (showSettings) {
        SettingsDialog(
            scrolled = scrolled,
            onSetScrolled = onSetScrolled,
            theme = theme,
            onSetTheme = onSetTheme,
            keepColors = keepColors,
            onSetKeepColors = onSetKeepColors,
            typography = typography,
            onSetTypography = onSetTypography,
            onDismiss = { showSettings = false },
        )
    }
    Surface(modifier, color = paneColor.takeOrElse { theme.sidebar }) {
      Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Books", style = MaterialTheme.typography.headlineMedium)
            Row {
                if (onSetPinned != null) {
                    IconButton(onClick = { onSetPinned(!pinned) }) {
                        Icon(
                            painterResource(R.drawable.ic_pin),
                            contentDescription = if (pinned) {
                                "Unpin the library, it hides while you read"
                            } else {
                                "Pin the library open"
                            },
                            // Askew and faded is the unpinned pin, as in GNOME.
                            modifier = Modifier.rotate(if (pinned) 0f else 45f),
                            tint = LocalContentColor.current
                                .copy(alpha = if (pinned) 1f else 0.5f),
                        )
                    }
                }
                IconButton(onClick = { showSettings = true }) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                }
            }
        }
        Button(onClick = onAddBook) { Text("Add book") }
        if (error.isNotBlank()) {
            Text(error, color = MaterialTheme.colorScheme.error)
        }
        if (books.isEmpty()) {
            Text("Your local library is empty.")
            Text("Add a book to read it offline.")
        } else {
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(bottom = 72.dp),
            ) {
                items(books, key = { it.id }) { book ->
                    LibraryBookRow(
                        book = book,
                        selected = book.id == selectedBookId,
                        onClick = { onSelectBook(book) },
                        onShowInfo = { details = book },
                    )
                    HorizontalDivider()
                }
            }
        }
      }
    }
}

@Composable
private fun LibraryBookRow(
    book: BookEntity,
    selected: Boolean,
    onClick: () -> Unit,
    onShowInfo: () -> Unit,
) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
        else Color.Transparent,
        // Long press opens the book's details, the way the ribbon opens bookmarks.
        modifier = Modifier.fillMaxWidth().combinedClickable(
            onClick = onClick,
            onLongClick = onShowInfo,
            onLongClickLabel = "Book details",
        ),
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CoverThumbnail(book.coverPath)
            Column {
                Text(
                    text = book.title.ifBlank { "Opening book…" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (book.author.isNotBlank()) {
                    Text(book.author, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(book.progressFraction.asPercent())
            }
        }
    }
}

@Composable
private fun CoverThumbnail(coverPath: String?) {
    val bitmap by produceState<ImageBitmap?>(null, coverPath) {
        value = coverPath?.let {
            withContext(Dispatchers.IO) {
                runCatching { BitmapFactory.decodeFile(it)?.asImageBitmap() }.getOrNull()
            }
        }
    }
    val modifier = Modifier.width(48.dp).height(72.dp)
    bitmap?.let {
        Image(
            bitmap = it,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    } ?: Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = modifier) {}
}

/** What Foliate shows in its book properties, with the identifiers we keep. */
@Composable
private fun BookDetailsDialog(
    book: BookEntity,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    var confirming by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = {
            TextButton(onClick = { if (confirming) onRemove() else confirming = true }) {
                Text(if (confirming) "Remove, keep the file" else "Remove")
            }
        },
        title = { Text(book.title.ifBlank { "Untitled book" }) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (book.author.isNotBlank()) DetailRow("Author", book.author)
                DetailRow("Progress", book.progressFraction.asPercent())
                DetailRow("Bookmarks", book.bookmarks.toCfiList().size.toString())
                DetailRow("Annotations", book.annotations.toAnnotations().size.toString())
                DetailRow("Identifier", book.metadataIdentifier ?: "—")
                DetailRow("Foliate key", book.foliateKey)
                DetailRow("SHA-256", book.sha256)
                DetailRow("Added", book.addedAt.asDate())
                DetailRow("Last opened", book.lastOpenedAt.asDate())
                DetailRow("File", Uri.decode(book.uri).substringAfterLast('/'))
            }
        },
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

private fun Long.asDate(): String =
    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(this))

@Composable
private fun SettingsDialog(
    scrolled: Boolean,
    onSetScrolled: (Boolean) -> Unit,
    theme: ReaderTheme,
    onSetTheme: (ReaderTheme) -> Unit,
    keepColors: Boolean,
    onSetKeepColors: (Boolean) -> Unit,
    typography: Typography,
    onSetTypography: (Typography) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        title = { Text("Settings") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().clickable { onSetScrolled(!scrolled) },
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Scrolled reading", modifier = Modifier.padding(top = 12.dp))
                    Switch(checked = scrolled, onCheckedChange = onSetScrolled)
                }
                SettingSlider(
                    label = "Text size",
                    value = typography.fontScale.toFloat(),
                    range = 70f..200f,
                    display = "${typography.fontScale}%",
                ) { onSetTypography(typography.copy(fontScale = it.toInt())) }
                SettingSlider(
                    label = "Line spacing",
                    value = typography.lineHeight,
                    range = 1.1f..2.2f,
                    display = String.format("%.1f", typography.lineHeight),
                ) { onSetTypography(typography.copy(lineHeight = it)) }
                SettingSlider(
                    label = "Margins",
                    value = typography.margin.toFloat(),
                    range = 0f..96f,
                    display = "${typography.margin}",
                ) { onSetTypography(typography.copy(margin = it.toInt())) }
                Text("Font", style = MaterialTheme.typography.titleSmall)
                Row {
                    listOf("book" to "Book's own", "serif" to "Serif", "sans" to "Sans")
                        .forEach { (key, label) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = typography.font == key,
                                    onClick = { onSetTypography(typography.copy(font = key)) },
                                )
                                Text(label, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                }
                Row(
                    Modifier.fillMaxWidth().clickable { onSetKeepColors(!keepColors) },
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "Book colours as greys",
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Switch(checked = keepColors, onCheckedChange = onSetKeepColors)
                }
                Text("Theme", style = MaterialTheme.typography.titleSmall)
                ReaderTheme.entries.forEach { option ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onSetTheme(option) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = theme == option, onClick = { onSetTheme(option) })
                        Text(option.label)
                    }
                }
            }
        },
    )
}

@Composable
private fun SettingSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: String,
    onChange: (Float) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(display, style = MaterialTheme.typography.bodySmall)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun EmptyReader(modifier: Modifier = Modifier) {
    Column(modifier.padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Select a book from your library.", style = MaterialTheme.typography.titleLarge)
    }
}

/**
 * What the open book can offer a panel outside the reader. On a tablet the sidebar draws
 * these itself, so the chapters and annotations live next to the page instead of over it.
 */
private class ReaderPanels(
    val toc: List<TocEntry>,
    val annotations: List<JSONObject>,
    val openHref: (String) -> Unit,
    val openCfi: (String) -> Unit,
    val removeAnnotation: (String) -> Unit,
    val onExport: () -> Unit,
    val onImport: () -> Unit,
    val onSync: () -> Unit,
    val onSyncWrite: () -> Unit,
    val syncLabel: String,
    val notice: String,
)

@Composable
private fun ReaderScreen(
    book: BookEntity,
    dao: BookDao,
    persistenceScope: CoroutineScope,
    scrolled: Boolean,
    theme: ReaderTheme,
    keepColors: Boolean,
    typography: Typography,
    onBack: (() -> Unit)?,
    /** Set on a tablet: the sidebar takes over chapters and annotations. */
    onPanels: ((ReaderPanels) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var progress by remember(book.id) { mutableStateOf(book.progressFraction) }
    var currentCfi by remember(book.id) { mutableStateOf(book.lastCfi) }
    var pages by remember(book.id) { mutableStateOf<Int?>(null) }
    var page by remember(book.id) { mutableStateOf<Int?>(null) }
    var chapter by remember(book.id) { mutableStateOf("") }
    var printPage by remember(book.id) { mutableStateOf("") }
    var error by remember(book.id) { mutableStateOf("") }
    var bridge by remember(book.id) { mutableStateOf<JavaScriptReplyProxy?>(null) }
    var readerReady by remember(book.id) { mutableStateOf(false) }
    var chromeVisible by remember(book.id) { mutableStateOf(true) }
    var toc by remember(book.id) { mutableStateOf(emptyList<TocEntry>()) }
    // Comics and PDF keep their own fixed layout: scrolled mode does not apply to them,
    // and pretending it does leaves them with no way to turn a page at all.
    var fixedLayout by remember(book.id) { mutableStateOf(false) }
    var showChapters by remember(book.id) { mutableStateOf(false) }
    var showBookmarks by remember(book.id) { mutableStateOf(false) }
    var showAnnotations by remember(book.id) { mutableStateOf(false) }
    var selectionLower by remember(book.id) { mutableStateOf(true) }
    var openedAnnotation by remember(book.id) { mutableStateOf<JSONObject?>(null) }
    val readerContext = LocalContext.current
    val bookmarks = remember(book.bookmarks) { book.bookmarks.toCfiList() }
    var selection by remember(book.id) { mutableStateOf<Pair<String, String>?>(null) }
    val annotations = remember(book.annotations) { book.annotations.toAnnotations() }
    val clipboard = LocalClipboardManager.current
    val send: (JSONObject) -> Unit = { command ->
        runCatching { bridge?.postMessage(command.toString()) }
            .onFailure { error = "Reader command failed: ${it.message ?: "unknown error"}" }
    }
    val sendCommand: (String) -> Unit = { type -> send(JSONObject().put("type", type)) }
    var transferNotice by remember(book.id) { mutableStateOf("") }
    val preferences = remember(readerContext) {
        readerContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }
    var pendingImport by remember(book.id) { mutableStateOf<FoliateJson.Merged?>(null) }
    var exportFormat by remember(book.id) { mutableStateOf(ExportFormat.JSON) }
    var choosingFormat by remember(book.id) { mutableStateOf(false) }
    val exportFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        persistenceScope.launch {
            transferNotice = runCatching {
                withContext(Dispatchers.IO) {
                    val text = AnnotationExport.render(
                        format = exportFormat,
                        title = book.title,
                        annotations = annotations,
                    ) { FoliateJson.export(book, book.foliateExtras, page, pages) }
                    readerContext.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(text.toByteArray())
                    } ?: error("Could not write the file")
                }
                "Exported ${exportFormat.label}."
            }.getOrElse { "Export failed: ${it.message ?: "unknown error"}" }
        }
    }
    // Sync against one file the user picks per book: Syncthing folders hold files
    // with their own names, so matching by folder + key was too fragile.
    val syncKey = "$SYNC_FILE_PREFIX${book.id}"
    var syncUri by remember(book.id) {
        mutableStateOf(preferences.getString(syncKey, null)?.let(Uri::parse))
    }
    val syncWithFile: (Uri) -> Unit = { target ->
        persistenceScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val text = readerContext.contentResolver.openInputStream(target)
                        ?.use { it.readBytes().decodeToString() }
                        ?: error("Could not read the file")
                    FoliateJson.merge(book, text)
                }
            }.onSuccess { pendingImport = it }
                .onFailure { transferNotice = "Sync failed: ${it.message ?: "unknown error"}" }
        }
    }
    val writeSyncFile: (Uri) -> Unit = { target ->
        persistenceScope.launch {
            transferNotice = runCatching {
                withContext(Dispatchers.IO) {
                    val updated = dao.findByUri(book.uri) ?: book
                    readerContext.contentResolver.openOutputStream(target, "wt")?.use { out ->
                        out.write(
                            FoliateJson.export(updated, updated.foliateExtras, page, pages)
                                .toByteArray(),
                        )
                    } ?: error("Could not write the file")
                }
                "Wrote this book's annotations to the synced file."
            }.getOrElse { "Sync write failed: ${it.message ?: "unknown error"}" }
        }
    }
    val pickSyncFile = rememberLauncherForActivityResult(OpenReadWriteDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            readerContext.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        preferences.edit().putString(syncKey, uri.toString()).apply()
        syncUri = uri
        syncWithFile(uri)
    }
    val applyMerge: (FoliateJson.Merged) -> Unit = { merged ->
        persistenceScope.launch {
            dao.applyImport(book.id, merged.annotations, merged.bookmarks, merged.extras)
            merged.annotations.toAnnotations().forEach {
                send(
                    JSONObject().put("type", "Annotate")
                        .put("cfi", it.optString("value"))
                        .put("color", it.optString("color").ifBlank { "yellow" }),
                )
            }
            transferNotice = if (merged.identifierMatches) "Imported Foliate data."
            else "Imported, but this file is from a different book identifier."
            syncUri?.let(writeSyncFile)
        }
    }
    val importFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        persistenceScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val text = readerContext.contentResolver.openInputStream(uri)
                        ?.use { it.readBytes().decodeToString() }
                        ?: error("Could not read the file")
                    FoliateJson.merge(book, text)
                }
            }.onSuccess { pendingImport = it }
                .onFailure { transferNotice = "Import failed: ${it.message ?: "unknown error"}" }
        }
    }
    // ponytail: bookmarks match on the exact CFI string; compare with epubcfi.js in the
    // reader if a bookmark ever needs to survive a re-render that shifts the CFI.
    val bookmarked = currentCfi != null && currentCfi in bookmarks
    val scrolling = scrolled && !fixedLayout

    LaunchedEffect(readerReady, scrolling) {
        if (readerReady) {
            send(
                JSONObject()
                    .put("type", "SetFlow")
                    .put("flow", if (scrolling) "scrolled" else "paginated"),
            )
        }
    }
    BackHandler(enabled = showChapters) { showChapters = false }
    BackHandler(enabled = showAnnotations) { showAnnotations = false }
    BackHandler(enabled = selection != null) {
        sendCommand("ClearSelection")
        selection = null
    }
    BackHandler(
        enabled = !showChapters && !showAnnotations && selection == null && onBack != null,
    ) { onBack?.invoke() }

    LaunchedEffect(readerReady, theme, keepColors) {
        if (readerReady) {
            send(
                JSONObject()
                    .put("type", "SetTheme")
                    .put("foreground", theme.hex(theme.foreground))
                    .put("background", theme.hex(theme.background))
                    .put("link", theme.hex(theme.link))
                    .put("keepColors", keepColors),
            )
        }
    }
    LaunchedEffect(readerReady, book.annotations) {
        if (readerReady) annotations.forEach { annotation ->
            send(
                JSONObject()
                    .put("type", "Annotate")
                    .put("cfi", annotation.optString("value"))
                    .put("color", annotation.optString("color").ifBlank { "yellow" }),
            )
        }
    }
    LaunchedEffect(readerReady, typography) {
        if (readerReady) send(typography.toJson())
    }

    Box(modifier) {
        key(book.id) {
            ReaderView(
                book = book,
                onBridgeReady = { bridge = it },
                onBridgeClosed = { closedBridge ->
                    if (bridge === closedBridge) {
                        bridge = null
                        readerReady = false
                    }
                },
                onBookReady = { title, author, identifier, chapters, fixed ->
                    error = ""
                    toc = chapters
                    fixedLayout = fixed
                    persistenceScope.launch {
                        dao.updateMetadata(
                            id = book.id,
                            title = title,
                            author = author,
                            metadataIdentifier = identifier.ifBlank { null },
                            foliateKey = identifier.ifBlank { book.foliateKey },
                        )
                    }
                },
                onRelocated = { cfi, fraction, current, total, chapterLabel, printPageLabel ->
                    chapter = chapterLabel
                    printPage = printPageLabel
                    progress = fraction
                    currentCfi = cfi
                    if (current != null) page = current
                    if (total != null) pages = total
                    readerReady = true
                    persistenceScope.launch {
                        dao.updateProgress(
                            id = book.id,
                            cfi = cfi,
                            fraction = fraction,
                            timestamp = System.currentTimeMillis(),
                        )
                    }
                },
                onReaderError = {
                    error = it
                    readerReady = false
                },
                onTapped = {
                    chromeVisible = !chromeVisible
                    selection = null
                },
                onAnnotationTapped = { cfi ->
                    openedAnnotation = annotations.firstOrNull { it.optString("value") == cfi }
                },
                onSelected = { cfi, selectedText, lower ->
                    selection = cfi to selectedText
                    selectionLower = lower
                },
                onSelectionCleared = { selection = null },
                onTapPage = { forward -> sendCommand(if (forward) "Next" else "Previous") },
                // No inset: the chrome floats over the page, so the text never reflows
                // when it appears.
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (showChapters) {
            ChaptersScreen(
                toc = toc,
                onBack = { showChapters = false },
                onOpen = { href ->
                    showChapters = false
                    send(JSONObject().put("type", "GoToHref").put("href", href))
                },
                modifier = Modifier.fillMaxSize(),
            )
            return@Box
        }

        // Big books take a while to parse and lay out; say so instead of showing blank.
        if (!readerReady && error.isBlank()) {
            Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SpinningPage()
                Text(
                    "Opening ${book.title.ifBlank { "book" }}…",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }

        openedAnnotation?.let { item ->
            AnnotationNoteDialog(annotation = item, onDismiss = { openedAnnotation = null })
        }

        if (choosingFormat) {
            AlertDialog(
                onDismissRequest = { choosingFormat = false },
                confirmButton = {
                    TextButton(onClick = { choosingFormat = false }) { Text("Cancel") }
                },
                title = { Text("Export as") },
                text = {
                    Column {
                        ExportFormat.entries.forEach { format ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    exportFormat = format
                                    choosingFormat = false
                                    exportFile.launch(exportFileName(book, format))
                                }.padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(format.label)
                            }
                        }
                    }
                },
            )
        }

        pendingImport?.let { merged ->
            ImportPreviewDialog(
                merged = merged,
                onImport = {
                    applyMerge(merged)
                    pendingImport = null
                },
                onCancel = { pendingImport = null },
            )
        }

        // Hand the panels to whoever draws them outside the reader (the tablet sidebar).
        if (onPanels != null) {
            val panels = ReaderPanels(
                toc = toc,
                annotations = annotations,
                openHref = { send(JSONObject().put("type", "GoToHref").put("href", it)) },
                openCfi = { send(JSONObject().put("type", "GoToCfi").put("cfi", it)) },
                removeAnnotation = { cfi ->
                    persistenceScope.launch {
                        dao.updateAnnotations(book.id, removeAnnotation(annotations, cfi).toString())
                    }
                    send(JSONObject().put("type", "Unannotate").put("cfi", cfi))
                },
                onExport = { choosingFormat = true },
                onImport = { importFile.launch(arrayOf("application/json", "text/plain", "*/*")) },
                onSync = {
                    val target = syncUri
                    if (target == null) {
                        pickSyncFile.launch(arrayOf("application/json", "text/plain", "*/*"))
                    } else {
                        syncWithFile(target)
                    }
                },
                onSyncWrite = { syncUri?.let(writeSyncFile) },
                syncLabel = if (syncUri == null) "Choose sync file" else "Sync now",
                notice = transferNotice,
            )
            SideEffect { onPanels(panels) }
        }

        if (showAnnotations) {
            AnnotationsScreen(
                annotations = annotations,
                onBack = { showAnnotations = false },
                onExport = { choosingFormat = true },
                onImport = { importFile.launch(arrayOf("application/json", "text/plain", "*/*")) },
                onSync = {
                    val target = syncUri
                    if (target == null) {
                        pickSyncFile.launch(arrayOf("application/json", "text/plain", "*/*"))
                    } else {
                        syncWithFile(target)
                    }
                },
                onSyncWrite = { syncUri?.let(writeSyncFile) },
                syncLabel = if (syncUri == null) "Choose sync file" else "Sync now",
                notice = transferNotice,
                onOpen = { cfi ->
                    showAnnotations = false
                    send(JSONObject().put("type", "GoToCfi").put("cfi", cfi))
                },
                onRemove = { cfi ->
                    persistenceScope.launch {
                        dao.updateAnnotations(book.id, removeAnnotation(annotations, cfi).toString())
                    }
                    send(JSONObject().put("type", "Unannotate").put("cfi", cfi))
                },
                modifier = Modifier.fillMaxSize(),
            )
            return@Box
        }

        if (showBookmarks) {
            BookmarksDialog(
                bookmarks = bookmarks,
                onOpen = { cfi ->
                    showBookmarks = false
                    send(JSONObject().put("type", "GoToCfi").put("cfi", cfi))
                },
                onRemove = { cfi ->
                    persistenceScope.launch {
                        dao.updateBookmarks(book.id, JSONArray(bookmarks - cfi).toString())
                    }
                },
                onDismiss = { showBookmarks = false },
            )
        }

        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            ReaderTopBar(
                book = book,
                chapter = chapter,
                error = error,
                bookmarked = bookmarked,
                onBack = onBack,
                backIsSidebar = onPanels != null,
                onToggleBookmark = {
                    val cfi = currentCfi ?: return@ReaderTopBar
                    val updated = if (bookmarked) bookmarks - cfi else bookmarks + cfi
                    persistenceScope.launch {
                        dao.updateBookmarks(book.id, JSONArray(updated).toString())
                    }
                },
                onShowBookmarks = { showBookmarks = true },
            )
        }
        val current = selection
        if (current != null) {
            Box(
                Modifier.align(
                    if (selectionLower) Alignment.TopCenter else Alignment.BottomCenter,
                ).padding(vertical = 72.dp),
            ) {
                SelectionPanel(
                    excerpt = current.second,
                    note = annotations.noteFor(current.first),
                    onHighlight = { color, note ->
                        val (cfi, selected) = current
                        persistenceScope.launch {
                            dao.updateAnnotations(
                                book.id,
                                addAnnotation(annotations, cfi, color, selected, note).toString(),
                            )
                        }
                        send(
                            if (color == null) {
                                JSONObject().put("type", "Unannotate").put("cfi", cfi)
                            } else {
                                JSONObject().put("type", "Annotate")
                                    .put("cfi", cfi).put("color", color)
                            },
                        )
                        sendCommand("ClearSelection")
                        selection = null
                    },
                    onCopy = {
                        clipboard.setText(AnnotatedString(current.second))
                        sendCommand("ClearSelection")
                        selection = null
                    },
                    onCite = {
                        clipboard.setText(
                            AnnotatedString(
                                "“${current.second}”\n— ${book.title}" +
                                    (if (book.author.isBlank()) "" else ", ${book.author}") +
                                    "\n${current.first}",
                            ),
                        )
                        sendCommand("ClearSelection")
                        selection = null
                    },
                    onLookUp = { service ->
                        // No network permission here: the selection is handed to whichever
                        // app the user already trusts with the web.
                        val query = Uri.encode(current.second.take(200))
                        val language = java.util.Locale.getDefault().language
                        val url = when (service) {
                            "wikipedia" ->
                                "https://$language.wikipedia.org/wiki/Special:Search?search=$query"
                            "translate" ->
                                "https://translate.google.com/?sl=auto&tl=$language&text=$query"
                            else -> "https://$language.wiktionary.org/wiki/Special:Search?search=$query"
                        }
                        runCatching {
                            readerContext.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }.onFailure { error = "No app can open links." }
                    },
                    onShare = {
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, current.second)
                            putExtra(Intent.EXTRA_SUBJECT, book.title)
                        }
                        readerContext.startActivity(Intent.createChooser(share, null))
                    },
                    onDismiss = {
                        sendCommand("ClearSelection")
                        selection = null
                    },
                )
            }
        }

        AnimatedVisibility(
            visible = chromeVisible && selection == null,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (onPanels == null) ChromeIsland {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { showChapters = true }, enabled = toc.isNotEmpty()) {
                        Text("Chapters")
                    }
                    TextButton(onClick = { showAnnotations = true }) { Text("Annotations") }
                }
            }
            if (!scrolling) {
                ReaderControls(
                    progress = progress,
                    printPage = printPage,
                    page = page,
                    pages = pages,
                    enabled = bridge != null && readerReady,
                    onPrevious = { sendCommand("Previous") },
                    onNext = { sendCommand("Next") },
                    onSeek = {
                        send(JSONObject().put("type", "GoToFraction").put("fraction", it))
                    },
                )
            }
          }
        }

        // Scrolled mode scrubs from the side, so the toolbar keeps the bottom.
        AnimatedVisibility(
            visible = chromeVisible && selection == null && scrolling,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            ScrubHandle(
                progress = progress,
                pages = pages,
                enabled = bridge != null && readerReady,
                onSeek = { send(JSONObject().put("type", "GoToFraction").put("fraction", it)) },
            )
        }
    }
}

/** Colours match Foliate's annotation palette so exported JSON stays compatible. */
/** Foliate's own palette, by name, so imported colours land on the right swatch. */
private val HIGHLIGHT_COLORS = listOf(
    "yellow" to Color(0xFFF6E58D),
    "orange" to Color(0xFFF9C784),
    "red" to Color(0xFFF3A0A0),
    "magenta" to Color(0xFFEBA6D8),
    "aqua" to Color(0xFF9BD8E0),
    "lime" to Color(0xFFB6E29B),
)

/** Any other Foliate value is a CSS colour name or #rrggbb; show it as it draws. */
private fun highlightSwatch(name: String): Color? =
    HIGHLIGHT_COLORS.toMap()[name]
        ?: runCatching { Color(android.graphics.Color.parseColor(name)) }.getOrNull()

/** Colours, note and copy for the current selection; `null` colour clears the highlight. */
@Composable
private fun SelectionPanel(
    excerpt: String,
    note: String,
    onHighlight: (String?, String) -> Unit,
    onCopy: () -> Unit,
    onCite: () -> Unit,
    onShare: () -> Unit,
    onLookUp: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(excerpt) { mutableStateOf(note) }
    var writing by remember(excerpt) { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(writing) { if (writing) focus.requestFocus() }

    ChromeIsland(Modifier.padding(bottom = 8.dp).widthIn(max = 420.dp).fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                excerpt,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HIGHLIGHT_COLORS.forEach { (name, color) ->
                    Box(
                        Modifier.size(30.dp)
                            .background(color, CircleShape)
                            .clickable { onHighlight(name, draft) },
                    )
                }
                // "None" erases an existing highlight: a struck-through swatch, so it
                // does not read as yet another colour.
                val outline = MaterialTheme.colorScheme.onSurface
                Canvas(
                    Modifier.size(30.dp).clickable { onHighlight(null, draft) },
                ) {
                    drawCircle(outline, style = Stroke(width = 3f))
                    drawLine(
                        Color(0xFFE01B24),
                        start = Offset(size.width * 0.2f, size.height * 0.8f),
                        end = Offset(size.width * 0.8f, size.height * 0.2f),
                        strokeWidth = 5f,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { onLookUp("dictionary") }) { Text("Dictionary") }
                TextButton(onClick = { onLookUp("wikipedia") }) { Text("Wikipedia") }
                TextButton(onClick = { onLookUp("translate") }) { Text("Translate") }
            }
            if (writing) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text("Note") },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(onClick = onCopy) { Text("Copy") }
                TextButton(onClick = onCite) { Text("Cite") }
                TextButton(onClick = onShare) { Text("Share") }
                TextButton(onClick = { writing = true }) {
                    Text(if (note.isBlank()) "Note" else "Edit note")
                }
                Spacer(Modifier.weight(1f))
                if (writing) {
                    TextButton(onClick = { onHighlight("yellow", draft) }) { Text("Save") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    }
}

/** Quick look at a highlight's note. Deleting lives in the annotations list. */
@Composable
private fun AnnotationNoteDialog(
    annotation: JSONObject,
    onDismiss: () -> Unit,
) {
    val note = annotation.optString("note")
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(14.dp).background(
                        highlightSwatch(annotation.optString("color"))
                            ?: MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape,
                    ),
                )
                Text(
                    annotation.optString("text"),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
        },
        text = {
            Text(note.ifBlank { "No note on this highlight." })
        },
    )
}

/** Foliate shows what a file holds before importing it; so do we. */
@Composable
private fun ImportPreviewDialog(
    merged: FoliateJson.Merged,
    onImport: () -> Unit,
    onCancel: () -> Unit,
) {
    val items = remember(merged) { merged.annotations.toAnnotations() }
    AlertDialog(
        onDismissRequest = onCancel,
        confirmButton = { TextButton(onClick = onImport) { Text("Import") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
        title = { Text("Import annotations") },
        text = {
            Column {
                if (!merged.identifierMatches) {
                    Text(
                        "This file is from a different book identifier.",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    "${items.size} annotations, ${merged.bookmarks.toCfiList().size} bookmarks " +
                        "after merging.",
                    style = MaterialTheme.typography.bodySmall,
                )
                LazyColumn(Modifier.padding(top = 8.dp)) {
                    items(items.size) { index ->
                        val item = items[index]
                        Row(Modifier.padding(vertical = 6.dp)) {
                            Box(
                                Modifier.size(14.dp).background(
                                    highlightSwatch(item.optString("color"))
                                        ?: MaterialTheme.colorScheme.surfaceVariant,
                                    CircleShape,
                                ),
                            )
                            Column(Modifier.padding(start = 10.dp)) {
                                Text(
                                    item.optString("text"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val note = item.optString("note")
                                if (note.isNotBlank()) {
                                    Text(
                                        note,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
    )
}

@Composable
private fun AnnotationsScreen(
    annotations: List<JSONObject>,
    onBack: (() -> Unit)?,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onSync: () -> Unit,
    onSyncWrite: () -> Unit,
    syncLabel: String,
    notice: String,
    onOpen: (String) -> Unit,
    onRemove: (String) -> Unit,
    color: Color = Color.Unspecified,
    modifier: Modifier = Modifier,
) {
    Surface(modifier, color = color.takeOrElse { MaterialTheme.colorScheme.surface }) {
        Column {
            ScreenHeader("Annotations", onBack) {
                // Half and half on the first line, sync across the whole second one:
                // the sync labels are too wordy to share a row in a 300 dp sidebar.
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PillButton("Import", onImport, Modifier.weight(1f))
                    PillButton("Export", onExport, Modifier.weight(1f))
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PillButton(syncLabel, onSync, Modifier.weight(1f))
                    if (syncLabel == "Sync now") PillButton("Write", onSyncWrite)
                }
            }
            if (notice.isNotBlank()) {
                Text(notice, modifier = Modifier.padding(horizontal = 16.dp))
            }
            if (annotations.isEmpty()) {
                Text(
                    "Select text in the book to highlight it or add a note.",
                    modifier = Modifier.padding(16.dp),
                )
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 72.dp)) {
                items(annotations.size) { index ->
                    val item = annotations[index]
                    val cfi = item.optString("value")
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpen(cfi) }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(16.dp).background(
                                highlightSwatch(item.optString("color"))
                                    ?: MaterialTheme.colorScheme.surfaceVariant,
                                CircleShape,
                            ),
                        )
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(
                                item.optString("text"),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val note = item.optString("note")
                            if (note.isNotBlank()) {
                                Text(
                                    note,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        TextButton(onClick = { onRemove(cfi) }) { Text("Remove") }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

/** `Title-Books-Export-2026-07-27-1215.md`: sorts by book, then by when. */
private fun exportFileName(book: BookEntity, format: ExportFormat): String =
    foliateFileName(book).removeSuffix(".json") + "." + format.extension

private fun foliateFileName(book: BookEntity): String {
    val title = book.title.ifBlank { "book" }
        .replace(Regex("[/\\\\:*?\"<>|]"), "")
        .trim()
        .take(60)
    val stamp = java.text.SimpleDateFormat("yyyy-MM-dd-HHmm", java.util.Locale.US)
        .format(java.util.Date())
    return "$title-Books-Export-$stamp.json"
}

private fun removeAnnotation(existing: List<JSONObject>, cfi: String): JSONArray =
    JSONArray(existing.filterNot { it.optString("value") == cfi }.toList())

private fun List<JSONObject>.noteFor(cfi: String): String =
    firstOrNull { it.optString("value") == cfi }?.optString("note").orEmpty()

/** A page with a folded corner, tumbling while the book loads. */
@Composable
private fun SpinningPage() {
    val spin = rememberInfiniteTransition(label = "page")
    val angle by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
        label = "angle",
    )
    val ink = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.size(56.dp)) {
        rotate(angle) {
            val w = size.width * 0.62f
            val h = size.height * 0.82f
            val left = (size.width - w) / 2f
            val top = (size.height - h) / 2f
            val fold = w * 0.3f
            val page = Path().apply {
                moveTo(left, top)
                lineTo(left + w - fold, top)
                lineTo(left + w, top + fold)
                lineTo(left + w, top + h)
                lineTo(left, top + h)
                close()
            }
            drawPath(page, ink, style = Stroke(width = 4f))
            // the folded corner
            drawPath(
                Path().apply {
                    moveTo(left + w - fold, top)
                    lineTo(left + w - fold, top + fold)
                    lineTo(left + w, top + fold)
                },
                ink,
                style = Stroke(width = 4f),
            )
            repeat(3) { line ->
                val y = top + h * (0.45f + line * 0.16f)
                drawLine(
                    ink.copy(alpha = 0.6f),
                    start = Offset(left + w * 0.18f, y),
                    end = Offset(left + w * 0.82f, y),
                    strokeWidth = 3f,
                )
            }
        }
    }
}

/** Grey pill, dark label; the same shape the sidebar uses. */
@Composable
private fun PillButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = CircleShape,
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun ScreenHeader(
    title: String,
    onBack: (() -> Unit)?,
    actions: @Composable () -> Unit = {},
) {
    Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Chevron only: the label was pushing the actions off screen, and the
            // system back button does the same thing. In the sidebar there is nothing
            // to go back to — the tabs underneath are the navigation.
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Text("‹", style = MaterialTheme.typography.headlineMedium)
                }
            } else {
                Spacer(Modifier.width(8.dp))
            }
            Text(title, style = MaterialTheme.typography.titleLarge)
        }
        FlowRow(
            Modifier.padding(start = 8.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) { actions() }
    }
}

@Composable
private fun ChaptersScreen(
    toc: List<TocEntry>,
    onBack: (() -> Unit)?,
    onOpen: (String) -> Unit,
    color: Color = Color.Unspecified,
    modifier: Modifier = Modifier,
) {
    Surface(modifier, color = color.takeOrElse { MaterialTheme.colorScheme.surface }) {
        Column {
            ScreenHeader("Chapters", onBack)
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 72.dp)) {
                items(toc.size) { index ->
                    val entry = toc[index]
                    Text(
                        text = entry.label,
                        modifier = Modifier.fillMaxWidth()
                            .clickable { onOpen(entry.href) }
                            .padding(
                                start = (16 + entry.depth * 16).dp,
                                end = 16.dp,
                                top = 12.dp,
                                bottom = 12.dp,
                            ),
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun BookmarksDialog(
    bookmarks: List<String>,
    onOpen: (String) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Bookmarks") },
        text = {
            if (bookmarks.isEmpty()) {
                Text("No saved pages yet. Tap the ribbon to save this one.")
            } else {
                LazyColumn {
                    items(bookmarks.size) { index ->
                        val cfi = bookmarks[index]
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "Saved page ${index + 1}",
                                modifier = Modifier.weight(1f)
                                    .clickable { onOpen(cfi) }
                                    .padding(vertical = 12.dp),
                            )
                            TextButton(onClick = { onRemove(cfi) }) { Text("Remove") }
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
    )
}

@Composable
private fun ReaderTopBar(
    book: BookEntity,
    chapter: String,
    error: String,
    bookmarked: Boolean,
    onBack: (() -> Unit)?,
    /** On a tablet the arrow does not leave the book, it calls the sidebar over. */
    backIsSidebar: Boolean,
    onToggleBookmark: () -> Unit,
    onShowBookmarks: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ChromeIsland(modifier.padding(12.dp)) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                if (backIsSidebar) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.Menu, contentDescription = "Show the library")
                    }
                } else {
                    TextButton(onClick = onBack) {
                        Text("‹", style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
            Column(
                Modifier.weight(1f).padding(vertical = 8.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = book.title.ifBlank { "Opening book…" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = chapter.ifBlank { book.author },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                if (error.isNotBlank()) {
                    Text(error, color = MaterialTheme.colorScheme.error, maxLines = 2)
                }
            }
            Ribbon(
                filled = bookmarked,
                // Long press opens the saved pages instead of costing another icon.
                modifier = Modifier
                    .combinedClickable(
                        onClick = onToggleBookmark,
                        onLongClick = onShowBookmarks,
                        onLongClickLabel = "Show saved pages",
                    )
                    .padding(horizontal = 12.dp)
                    .size(width = 24.dp, height = 40.dp),
            )
        }
    }
}

/** Bookmark ribbon: a rectangle with a notch cut out of the bottom edge. */
@Composable
private fun Ribbon(filled: Boolean, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val notch = size.height * 0.25f
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height)
            lineTo(size.width / 2f, size.height - notch)
            lineTo(0f, size.height)
            close()
        }
        drawPath(path, color, style = if (filled) Fill else Stroke(width = 4f))
    }
}

@Composable
private fun ScrubHandle(
    progress: Double?,
    pages: Int?,
    enabled: Boolean,
    onSeek: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    var trackHeight by remember { mutableStateOf(1f) }
    val haptics = LocalHapticFeedback.current
    var lastTickedPage by remember { mutableStateOf<Int?>(null) }
    // Same feel as the paginated seek bar: a third of finger speed, one tick per page.
    val scrub: (Float) -> Unit = { target ->
        val current = dragFraction ?: progress?.toFloat() ?: 0f
        val next = (current + (target - current) * 0.33f).coerceIn(0f, 1f)
        dragFraction = next
        if (pages != null && pages > 0) {
            val tick = (next * pages).toInt()
            if (tick != lastTickedPage) {
                lastTickedPage = tick
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }

    Row(modifier.padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(
            Modifier.height(180.dp)
                .width(36.dp)
                .onSizeChanged { trackHeight = it.height.toFloat().coerceAtLeast(1f) }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectVerticalDragGestures(
                        onDragStart = { offset -> scrub(offset.y / trackHeight) },
                        onDragEnd = {
                            dragFraction?.let { onSeek(it.toDouble()) }
                            dragFraction = null
                            lastTickedPage = null
                        },
                        onDragCancel = {
                            dragFraction = null
                            lastTickedPage = null
                        },
                    ) { change, _ -> scrub(change.position.y / trackHeight) }
                },
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            repeat(3) {
                Box(
                    Modifier.size(6.dp)
                        .background(MaterialTheme.colorScheme.outline, CircleShape),
                )
            }
        }
        val shown = dragFraction?.toDouble() ?: progress
        if (dragFraction != null) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = CircleShape,
            ) {
                Text(
                    text = pages?.let { "${(shown!! * it).toInt() + 1} / $it" }
                        ?: shown.asPercent(),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ReaderControls(
    progress: Double?,
    printPage: String,
    page: Int?,
    pages: Int?,
    enabled: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragged by remember { mutableStateOf<Float?>(null) }
    val haptics = LocalHapticFeedback.current
    var lastTickedPage by remember { mutableStateOf<Int?>(null) }
    val shown = dragged?.toDouble() ?: progress
    ChromeIsland(modifier.padding(12.dp)) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(
                text = if (printPage.isNotBlank() && dragged == null) "Page $printPage"
                    else pageLabel(shown, page, pages, dragged != null),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onPrevious, enabled = enabled) {
                    Text("‹", style = MaterialTheme.typography.headlineMedium)
                }
                Slider(
                    value = dragged ?: progress?.toFloat()?.coerceIn(0f, 1f) ?: 0f,
                    // The thumb follows the finger at a third of its speed: a long book
                    // needs a slow scrub to be usable, and each page ticks under the thumb.
                    onValueChange = { target ->
                        val current = dragged ?: progress?.toFloat()?.coerceIn(0f, 1f) ?: 0f
                        val next = (current + (target - current) * 0.33f).coerceIn(0f, 1f)
                        dragged = next
                        if (pages != null && pages > 0) {
                            val tick = (next * pages).toInt()
                            if (tick != lastTickedPage) {
                                lastTickedPage = tick
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    },
                    onValueChangeFinished = {
                        dragged?.let { onSeek(it.toDouble()) }
                        dragged = null
                        lastTickedPage = null
                    },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onNext, enabled = enabled) {
                    Text("›", style = MaterialTheme.typography.headlineMedium)
                }
            }
        }
    }
}

/**
 * "12 / 340" once foliate reports locations, percent otherwise. While dragging the
 * page is estimated from the fraction, since the reader has not moved yet.
 */
private fun pageLabel(fraction: Double?, page: Int?, pages: Int?, dragging: Boolean): String {
    if (pages == null || pages <= 0) return fraction.asPercent()
    val current = if (dragging || page == null) {
        ((fraction ?: 0.0).coerceIn(0.0, 1.0) * pages).toInt()
    } else {
        page
    }
    return "${current + 1} / $pages"
}

/** Floating translucent island, so the page stays readable underneath. */
@Composable
private fun ChromeIsland(
    modifier: Modifier = Modifier,
    alpha: Float = 0.94f,
    content: @Composable () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = alpha),
        shape = RoundedCornerShape(24.dp),
        // No shadow: a shadow cast over the WebView leaves white artefacts on it.
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
        modifier = modifier,
        content = content,
    )
}

private data class TocEntry(val label: String, val href: String, val depth: Int)

private fun JSONArray?.toTocEntries(): List<TocEntry> {
    val array = this ?: return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        val href = item.optString("href").take(2048).takeIf(String::isNotBlank)
            ?: return@mapNotNull null
        TocEntry(
            label = item.optString("label").normalizedText().ifBlank { "Untitled" },
            href = href,
            depth = item.optInt("depth", 0).coerceIn(0, 5),
        )
    }
}

/** Foliate annotation records, kept as JSON so unknown fields survive round trips. */
private fun String?.toAnnotations(): List<JSONObject> = runCatching {
    val array = JSONArray(this ?: "[]")
    (0 until array.length()).mapNotNull { array.optJSONObject(it) }
}.getOrDefault(emptyList())

/** Upsert one Foliate-shaped annotation; a null colour drops it. */
private fun addAnnotation(
    existing: List<JSONObject>,
    cfi: String,
    color: String?,
    text: String,
    note: String = "",
): JSONArray {
    // ISO-8601 UTC like Foliate writes; java.time needs API 26, this works on API 24.
    val now = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
        .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        .format(java.util.Date())
    val previous = existing.firstOrNull { it.optString("value") == cfi }
    val kept = existing.filterNot { it.optString("value") == cfi }
    if (color == null) return JSONArray(kept.toList())
    val record = JSONObject()
        .put("value", cfi)
        .put("color", color)
        .put("text", text)
        .put("note", note)
        .put("created", previous?.optString("created")?.takeIf(String::isNotBlank) ?: now)
        .put("modified", now)
    return JSONArray((kept + record).toList())
}

private fun String?.toCfiList(): List<String> = runCatching {
    val array = JSONArray(this ?: "[]")
    (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
}.getOrDefault(emptyList())

@Composable
@SuppressLint("SetJavaScriptEnabled")
private fun ReaderView(
    book: BookEntity,
    onBridgeReady: (JavaScriptReplyProxy) -> Unit,
    onBridgeClosed: (JavaScriptReplyProxy?) -> Unit,
    onBookReady: (String, String, String, List<TocEntry>, Boolean) -> Unit,
    onTapped: () -> Unit,
    onAnnotationTapped: (String) -> Unit,
    onSelected: (String, String, Boolean) -> Unit,
    onSelectionCleared: () -> Unit,
    onTapPage: (Boolean) -> Unit,
    onRelocated: (String, Double?, Int?, Int?, String, String) -> Unit,
    onReaderError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            val bookUri = Uri.parse(book.uri)
            val bookFile = bookFileName(context, bookUri)
            val fallbackTitle = displayName(context, bookUri)
                .substringBeforeLast('.').normalizedText()
                .ifBlank { "Untitled book" }
            val assetLoader = WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                .addPathHandler("/state/") { path ->
                    if (path == "last-location.json") {
                        jsonResponse(JSONObject().put("cfi", book.lastCfi).toString())
                    } else {
                        blockedResponse()
                    }
                }
                .addPathHandler("/book/") { path ->
                    if (path != bookFile) {
                        blockedResponse()
                    } else {
                        val stream = runCatching {
                            context.contentResolver.openInputStream(bookUri)
                        }.getOrNull()
                        if (stream == null) blockedResponse()
                        else WebResourceResponse(
                            BOOK_MIME_TYPES[bookFile.substringAfterLast('.')] ?: EPUB_MIME_TYPE,
                            null,
                            stream,
                        )
                    }
                }
                .build()

            // Keep the selection handles, drop the system Copy/Share items: the
            // action mode still runs, it just gets an empty menu.
            object : WebView(context) {
                override fun startActionMode(callback: ActionMode.Callback?): ActionMode? =
                    super.startActionMode(EmptyMenu(callback, onSelectionCleared))

                override fun startActionMode(
                    callback: ActionMode.Callback?,
                    type: Int,
                ): ActionMode? =
                    super.startActionMode(EmptyMenu(callback, onSelectionCleared), type)
            }.apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = WebView.OVER_SCROLL_NEVER
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.domStorageEnabled = false
                settings.javaScriptCanOpenWindowsAutomatically = false
                settings.setSupportMultipleWindows(false)
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                webViewClient = LocalReaderClient(assetLoader, onBridgeClosed)
                WebViewCompat.addWebMessageListener(
                    this,
                    "booksBridge",
                    setOf("https://$READER_ORIGIN"),
                ) { view, message, sourceOrigin, isMainFrame, replyProxy ->
                    if (!isMainFrame || sourceOrigin.host != READER_ORIGIN) {
                        return@addWebMessageListener
                    }
                    view.tag = replyProxy
                    onBridgeReady(replyProxy)
                    val data = message.data
                        ?.let { runCatching { JSONObject(it) }.getOrNull() }
                        ?: return@addWebMessageListener
                    when (data.optString("type")) {
                        "BookReady" -> onBookReady(
                            data.optString("title").normalizedText().ifBlank { fallbackTitle },
                            data.optString("author").normalizedText(),
                            data.optString("identifier").normalizedText(),
                            data.optJSONArray("toc").toTocEntries(),
                            data.optBoolean("fixedLayout"),
                        )
                        "Relocated" -> {
                            val cfi = data.optString("cfi")
                            if (cfi.startsWith("epubcfi(") && cfi.length <= 8192) {
                                onRelocated(
                                    cfi,
                                    data.optDouble("fraction", Double.NaN)
                                        .takeIf(Double::isFinite),
                                    data.optInt("page", -1).takeIf { it >= 0 },
                                    data.optInt("pages", 0).takeIf { it > 0 },
                                    data.optString("chapter").normalizedText(),
                                    data.optString("printPage").normalizedText(),
                                )
                            }
                        }
                        "Tapped" -> onTapped()
                        "AnnotationTapped" -> onAnnotationTapped(data.optString("cfi"))
                        "Selected" -> {
                            val cfi = data.optString("cfi")
                            val selected = data.optString("text").normalizedText()
                            if (cfi.startsWith("epubcfi(") && selected.isNotBlank()) {
                                onSelected(cfi, selected, data.optBoolean("lower", true))
                            }
                        }
                        "SelectionCleared" -> onSelectionCleared()
                        "TappedPrevious" -> onTapPage(false)
                        "TappedNext" -> onTapPage(true)
                        "ReaderError" -> onReaderError(
                            data.optString("message").normalizedText()
                                .ifBlank { "Reader error" },
                        )
                    }
                }
                loadUrl("$READER_URL&book=$bookFile")
            }
        },
        onRelease = { view ->
            onBridgeClosed(view.tag as? JavaScriptReplyProxy)
            view.destroy()
        },
    )
}

private suspend fun addOrOpenBook(
    context: Context,
    dao: BookDao,
    uri: Uri,
    legacyCfi: String? = null,
): BookEntity = withContext(Dispatchers.IO) {
    val uriString = uri.toString()
    dao.findByUri(uriString)?.let {
        dao.markOpened(it.id, System.currentTimeMillis())
        return@withContext it
    }
    val hashes = context.contentResolver.openInputStream(uri)?.let(BookIdentifiers::calculate)
        ?: error("File unavailable")
    val now = System.currentTimeMillis()
    val book = BookEntity(
        id = UUID.randomUUID().toString(),
        uri = uriString,
        title = "",
        author = "",
        metadataIdentifier = null,
        foliateKey = BookIdentifiers.foliateKey(null, hashes.foliateMd5),
        sha256 = hashes.sha256,
        lastCfi = legacyCfi,
        progressFraction = null,
        addedAt = now,
        lastOpenedAt = now,
    )
    dao.upsert(book)
    val coverPath = CoverExtractor.extract(context, uri, book.id)
    if (coverPath != null) dao.updateCover(book.id, coverPath)
    book.copy(coverPath = coverPath)
}

private suspend fun migrateLegacyBook(context: Context, dao: BookDao): String? {
    val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    val legacyUri = preferences.getString(BOOK_URI_KEY, null) ?: return null
    val legacyCfi = preferences.getString(LAST_CFI_KEY, null)
        ?.takeIf { it.startsWith("epubcfi(") && it.length <= 8192 }
    return runCatching {
        val existing = dao.findByUri(legacyUri)
        if (existing == null) {
            addOrOpenBook(context, dao, Uri.parse(legacyUri), legacyCfi)
        } else if (existing.lastCfi == null && legacyCfi != null) {
            dao.updateProgress(
                existing.id,
                legacyCfi,
                existing.progressFraction,
                existing.lastOpenedAt,
            )
        }
        preferences.edit().remove(BOOK_URI_KEY).remove(LAST_CFI_KEY).apply()
    }.exceptionOrNull()?.let { "Could not import the previous book: ${it.message}" }
}

private fun Double?.asPercent(): String =
    this?.takeIf(Double::isFinite)?.let { "${(it.coerceIn(0.0, 1.0) * 100).toInt()}%" }
        ?: "Reading"

private fun String.normalizedText() = replace(Regex("\\s+"), " ").trim().take(512)

private class LocalReaderClient(
    private val assetLoader: WebViewAssetLoader,
    private val onBridgeClosed: (JavaScriptReplyProxy?) -> Unit,
) : WebViewClient() {
    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ): WebResourceResponse? {
        val url = request.url
        return when {
            url.scheme == "https" && url.host == READER_ORIGIN ->
                assetLoader.shouldInterceptRequest(url) ?: blockedResponse()
            // Book sections and their resources are handed to the WebView as blob:/data:
            // URLs the reader itself created; blocking those leaves an empty page. The
            // app has no INTERNET permission, so nothing else can reach the network.
            url.scheme in LOCAL_SCHEMES -> null
            else -> blockedResponse()
        }
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
        !(request.url.scheme == "https" && request.url.host == READER_ORIGIN)
            && request.url.scheme !in LOCAL_SCHEMES

    override fun onRenderProcessGone(
        view: WebView,
        detail: RenderProcessGoneDetail,
    ): Boolean {
        onBridgeClosed(view.tag as? JavaScriptReplyProxy)
        view.destroy()
        return true
    }
}

/**
 * Passes the selection action mode through with no menu items of its own. Its lifetime is
 * the selection's: tapping away drops the selection inside the WebView without firing a
 * click or a selectionchange in the book document, so this is the only place the app hears
 * that the selection is gone.
 */
private class EmptyMenu(
    private val inner: ActionMode.Callback?,
    private val onDestroyed: () -> Unit = {},
) : ActionMode.Callback {
    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        inner?.onCreateActionMode(mode, menu)
        menu.clear()
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        inner?.onPrepareActionMode(mode, menu)
        menu.clear()
        return true
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean = false

    override fun onDestroyActionMode(mode: ActionMode) {
        inner?.onDestroyActionMode(mode)
        onDestroyed()
    }
}

/** ACTION_OPEN_DOCUMENT that also asks for write access, so sync can save back. */
private class OpenReadWriteDocument : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent =
        super.createIntent(context, input).addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
        )
}

private fun blockedResponse() = WebResourceResponse(
    "text/plain",
    "UTF-8",
    ByteArrayInputStream(ByteArray(0)),
)

private fun jsonResponse(json: String) = WebResourceResponse(
    "application/json",
    "UTF-8",
    ByteArrayInputStream(json.toByteArray()),
)

/** A drag that starts within a finger's width of the left edge, as GNOME opens a sidebar. */
private fun Modifier.draggableFromLeftEdge(onOpen: () -> Unit): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (down.position.x > 24.dp.toPx()) continue
            var travelled = 0f
            do {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                travelled += change.position.x - change.previousPosition.x
                if (travelled > 40.dp.toPx()) {
                    onOpen()
                    break
                }
            } while (change.pressed)
        }
    }
}
