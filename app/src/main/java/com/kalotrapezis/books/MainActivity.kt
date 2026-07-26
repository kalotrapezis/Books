package com.kalotrapezis.books

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
private const val READER_URL = "https://$READER_ORIGIN/assets/reader/index.html?v=26"
private const val EPUB_MIME_TYPE = "application/epub+zip"
private const val PREFERENCES_NAME = "reader-state"
private const val BOOK_URI_KEY = "book-uri"
private const val LAST_CFI_KEY = "last-cfi"
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
    var scrolled by remember { mutableStateOf(false) }

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
                error = "Could not keep access to this EPUB."
            } else {
                scope.launch {
                    runCatching { addOrOpenBook(context, dao, uri) }
                        .onSuccess {
                            selectedBookId = it.id
                            error = ""
                        }
                        .onFailure {
                            error = "Could not add EPUB: ${it.message ?: "unknown error"}"
                        }
                }
            }
        }
    }
    val launchPicker = { openBook.launch(arrayOf(EPUB_MIME_TYPE)) }
    val selectBook: (BookEntity) -> Unit = { book ->
        selectedBookId = book.id
        scope.launch { dao.markOpened(book.id, System.currentTimeMillis()) }
    }
    val selectedBook = library.firstOrNull { it.id == selectedBookId }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
                if (maxWidth >= 600.dp) {
                    Row(Modifier.fillMaxSize()) {
                        LibraryPane(
                            books = library,
                            selectedBookId = selectedBookId,
                            error = error,
                            scrolled = scrolled,
                            onSetScrolled = { scrolled = it },
                            onAddBook = launchPicker,
                            onSelectBook = selectBook,
                            modifier = Modifier.width(280.dp).fillMaxHeight(),
                        )
                        if (selectedBook == null) {
                            EmptyReader(Modifier.weight(1f).fillMaxHeight())
                        } else {
                            ReaderScreen(
                                book = selectedBook,
                                dao = dao,
                                persistenceScope = scope,
                                scrolled = scrolled,
                                onBack = null,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                    }
                } else if (selectedBook == null) {
                    LibraryPane(
                        books = library,
                        selectedBookId = null,
                        error = error,
                        scrolled = scrolled,
                        onSetScrolled = { scrolled = it },
                        onAddBook = launchPicker,
                        onSelectBook = selectBook,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    ReaderScreen(
                        book = selectedBook,
                        dao = dao,
                        persistenceScope = scope,
                        scrolled = scrolled,
                        onBack = { selectedBookId = null },
                        modifier = Modifier.fillMaxSize(),
                    )
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
    onAddBook: () -> Unit,
    onSelectBook: (BookEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSettings by remember { mutableStateOf(false) }
    if (showSettings) {
        SettingsDialog(
            scrolled = scrolled,
            onSetScrolled = onSetScrolled,
            onDismiss = { showSettings = false },
        )
    }
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Books", style = MaterialTheme.typography.headlineMedium)
            IconButton(onClick = { showSettings = true }) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings")
            }
        }
        Button(onClick = onAddBook) { Text("Add EPUB") }
        if (error.isNotBlank()) {
            Text(error, color = MaterialTheme.colorScheme.error)
        }
        if (books.isEmpty()) {
            Text("Your local library is empty.")
            Text("Add an EPUB to read it offline.")
        } else {
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                items(books, key = { it.id }) { book ->
                    LibraryBookRow(
                        book = book,
                        selected = book.id == selectedBookId,
                        onClick = { onSelectBook(book) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun LibraryBookRow(book: BookEntity, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CoverThumbnail(book.coverPath)
            Column {
                Text(
                    text = book.title.ifBlank { "Opening EPUB…" },
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

@Composable
private fun SettingsDialog(
    scrolled: Boolean,
    onSetScrolled: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        title = { Text("Settings") },
        text = {
            Row(
                Modifier.fillMaxWidth().clickable { onSetScrolled(!scrolled) },
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Scrolled reading", modifier = Modifier.padding(top = 12.dp))
                Switch(checked = scrolled, onCheckedChange = onSetScrolled)
            }
        },
    )
}

@Composable
private fun EmptyReader(modifier: Modifier = Modifier) {
    Column(modifier.padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Select a book from your library.", style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun ReaderScreen(
    book: BookEntity,
    dao: BookDao,
    persistenceScope: CoroutineScope,
    scrolled: Boolean,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var progress by remember(book.id) { mutableStateOf(book.progressFraction) }
    var currentCfi by remember(book.id) { mutableStateOf(book.lastCfi) }
    var pages by remember(book.id) { mutableStateOf<Int?>(null) }
    var error by remember(book.id) { mutableStateOf("") }
    var bridge by remember(book.id) { mutableStateOf<JavaScriptReplyProxy?>(null) }
    var readerReady by remember(book.id) { mutableStateOf(false) }
    var chromeVisible by remember(book.id) { mutableStateOf(true) }
    var toc by remember(book.id) { mutableStateOf(emptyList<TocEntry>()) }
    var showChapters by remember(book.id) { mutableStateOf(false) }
    var showBookmarks by remember(book.id) { mutableStateOf(false) }
    var selectable by remember(book.id) { mutableStateOf(false) }
    val send: (JSONObject) -> Unit = { command ->
        runCatching { bridge?.postMessage(command.toString()) }
            .onFailure { error = "Reader command failed: ${it.message ?: "unknown error"}" }
    }
    val sendCommand: (String) -> Unit = { type -> send(JSONObject().put("type", type)) }
    val bookmarks = remember(book.bookmarks) { book.bookmarks.toCfiList() }
    // ponytail: bookmarks match on the exact CFI string; compare with epubcfi.js in the
    // reader if a bookmark ever needs to survive a re-render that shifts the CFI.
    val bookmarked = currentCfi != null && currentCfi in bookmarks

    LaunchedEffect(readerReady, scrolled) {
        if (readerReady) {
            send(
                JSONObject()
                    .put("type", "SetFlow")
                    .put("flow", if (scrolled) "scrolled" else "paginated"),
            )
        }
    }
    BackHandler(enabled = showChapters) { showChapters = false }
    BackHandler(enabled = !showChapters && onBack != null) { onBack?.invoke() }

    LaunchedEffect(readerReady, selectable) {
        if (readerReady) {
            send(JSONObject().put("type", "SetSelectable").put("enabled", selectable))
        }
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
                onBookReady = { title, author, identifier, chapters ->
                    error = ""
                    toc = chapters
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
                onRelocated = { cfi, fraction, total ->
                    progress = fraction
                    currentCfi = cfi
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
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Centre tap target: everything else on the page stays reachable by the reader.
        Box(
            Modifier.align(Alignment.Center)
                .size(120.dp)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { chromeVisible = !chromeVisible },
        )

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

        if (chromeVisible) {
            ReaderTopBar(
                book = book,
                error = error,
                bookmarked = bookmarked,
                onBack = onBack,
                onToggleBookmark = {
                    val cfi = currentCfi ?: return@ReaderTopBar
                    val updated = if (bookmarked) bookmarks - cfi else bookmarks + cfi
                    persistenceScope.launch {
                        dao.updateBookmarks(book.id, JSONArray(updated).toString())
                    }
                },
                onShowBookmarks = { showBookmarks = true },
                modifier = Modifier.align(Alignment.TopCenter),
            )
            if (scrolled) {
                ScrubHandle(
                    progress = progress,
                    pages = pages,
                    enabled = bridge != null && readerReady,
                    onSeek = {
                        send(JSONObject().put("type", "GoToFraction").put("fraction", it))
                    },
                    modifier = Modifier.align(Alignment.CenterStart),
                )
            } else {
                ReaderControls(
                    progress = progress,
                    enabled = bridge != null && readerReady,
                    onPrevious = { sendCommand("Previous") },
                    onNext = { sendCommand("Next") },
                    onSeek = {
                        send(JSONObject().put("type", "GoToFraction").put("fraction", it))
                    },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
            Row(
                Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = if (scrolled) 8.dp else 72.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = { showChapters = true }, enabled = toc.isNotEmpty()) {
                    Text("Chapters")
                }
                TextButton(onClick = { selectable = !selectable }) {
                    Text(if (selectable) "Reading" else "Annotate")
                }
            }
        }
    }
}

@Composable
private fun ChaptersScreen(
    toc: List<TocEntry>,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier) {
        Column {
            Row(Modifier.padding(8.dp)) {
                TextButton(onClick = onBack) { Text("‹ Back") }
                Text(
                    "Chapters",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 8.dp, top = 8.dp),
                )
            }
            LazyColumn(Modifier.fillMaxSize()) {
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
    error: String,
    bookmarked: Boolean,
    onBack: (() -> Unit)?,
    onToggleBookmark: () -> Unit,
    onShowBookmarks: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            if (onBack != null) {
                TextButton(onClick = onBack) { Text("‹", style = MaterialTheme.typography.headlineMedium) }
            }
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(
                    text = book.title.ifBlank { "Opening EPUB…" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (book.author.isNotBlank()) {
                    Text(
                        book.author,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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

    Row(modifier.padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(
            Modifier.height(120.dp)
                .width(24.dp)
                .onSizeChanged { trackHeight = it.height.toFloat().coerceAtLeast(1f) }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            dragFraction = (offset.y / trackHeight).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            dragFraction?.let { onSeek(it.toDouble()) }
                            dragFraction = null
                        },
                        onDragCancel = { dragFraction = null },
                    ) { change, _ ->
                        dragFraction = (change.position.y / trackHeight).coerceIn(0f, 1f)
                    }
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
    enabled: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragged by remember { mutableStateOf<Float?>(null) }
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onPrevious, enabled = enabled) { Text("‹") }
            Slider(
                value = dragged ?: progress?.toFloat()?.coerceIn(0f, 1f) ?: 0f,
                onValueChange = { dragged = it },
                onValueChangeFinished = {
                    dragged?.let { onSeek(it.toDouble()) }
                    dragged = null
                },
                enabled = enabled,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onNext, enabled = enabled) { Text("›") }
        }
    }
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
    onBookReady: (String, String, String, List<TocEntry>) -> Unit,
    onRelocated: (String, Double?, Int?) -> Unit,
    onReaderError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            val bookUri = Uri.parse(book.uri)
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
                    if (path != "selected.epub") {
                        blockedResponse()
                    } else {
                        val stream = runCatching {
                            context.contentResolver.openInputStream(bookUri)
                        }.getOrNull()
                        if (stream == null) blockedResponse()
                        else WebResourceResponse(EPUB_MIME_TYPE, null, stream)
                    }
                }
                .build()

            WebView(context).apply {
                setBackgroundColor(Color.TRANSPARENT)
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
                            data.optString("title").normalizedText(),
                            data.optString("author").normalizedText(),
                            data.optString("identifier").normalizedText(),
                            data.optJSONArray("toc").toTocEntries(),
                        )
                        "Relocated" -> {
                            val cfi = data.optString("cfi")
                            if (cfi.startsWith("epubcfi(") && cfi.length <= 8192) {
                                onRelocated(
                                    cfi,
                                    data.optDouble("fraction", Double.NaN)
                                        .takeIf(Double::isFinite),
                                    data.optInt("pages", 0).takeIf { it > 0 },
                                )
                            }
                        }
                        "ReaderError" -> onReaderError(
                            data.optString("message").normalizedText()
                                .ifBlank { "Reader error" },
                        )
                    }
                }
                loadUrl("$READER_URL&book=selected")
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
