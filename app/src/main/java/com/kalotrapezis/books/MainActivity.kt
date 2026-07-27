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
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
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
private const val READER_URL = "https://$READER_ORIGIN/assets/reader/index.html?v=41"
private const val EPUB_MIME_TYPE = "application/epub+zip"
private const val PREFERENCES_NAME = "reader-state"
private const val BOOK_URI_KEY = "book-uri"
private const val LAST_CFI_KEY = "last-cfi"
private const val SCROLLED_KEY = "scrolled"
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
) {
    GREY_ON_WHITE("Grey on white", Color(0xFFFAFAFA), Color(0xFF3A3A3A), Color(0xFF1A5FB4)),
    WHITE_ON_GREY("White on grey", Color(0xFF303234), Color(0xFFE4E4E4), Color(0xFF8AB4F8));

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

    MaterialTheme(colorScheme = theme.colorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
                if (maxWidth >= 600.dp) {
                    Row(Modifier.fillMaxSize()) {
                        LibraryPane(
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
                                theme = theme,
                                keepColors = keepColors,
                                typography = typography,
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
                        onSetScrolled = setScrolled,
                        theme = theme,
                        onSetTheme = setTheme,
                        keepColors = keepColors,
                        onSetKeepColors = setKeepColors,
                        typography = typography,
                        onSetTypography = setTypography,
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
    modifier: Modifier = Modifier,
) {
    var showSettings by remember { mutableStateOf(false) }
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
    var showChapters by remember(book.id) { mutableStateOf(false) }
    var showBookmarks by remember(book.id) { mutableStateOf(false) }
    var showAnnotations by remember(book.id) { mutableStateOf(false) }
    var selectionLower by remember(book.id) { mutableStateOf(true) }
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
    val exportFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        persistenceScope.launch {
            transferNotice = runCatching {
                withContext(Dispatchers.IO) {
                    readerContext.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(
                            FoliateJson.export(book, book.foliateExtras, page, pages).toByteArray(),
                        )
                    } ?: error("Could not write the file")
                }
                "Exported Foliate JSON."
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
                onSelected = { cfi, selectedText, lower ->
                    selection = cfi to selectedText
                    selectionLower = lower
                },
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

        if (showAnnotations) {
            AnnotationsScreen(
                annotations = annotations,
                onBack = { showAnnotations = false },
                onExport = { exportFile.launch(foliateFileName(book)) },
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
            ChromeIsland {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { showChapters = true }, enabled = toc.isNotEmpty()) {
                        Text("Chapters")
                    }
                    TextButton(onClick = { showAnnotations = true }) { Text("Annotations") }
                }
            }
            if (!scrolled) {
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
            visible = chromeVisible && selection == null && scrolled,
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
    onShare: () -> Unit,
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
    onBack: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onSync: () -> Unit,
    onSyncWrite: () -> Unit,
    syncLabel: String,
    notice: String,
    onOpen: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier) {
        Column {
            ScreenHeader("Annotations", onBack) {
                PillButton(syncLabel, onSync)
                if (syncLabel == "Sync now") PillButton("Write", onSyncWrite)
                PillButton("Import", onImport)
                PillButton("Export", onExport)
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
            LazyColumn(Modifier.fillMaxSize()) {
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

/**
 * Foliate keeps one file per book in its data directory, named
 * `encodeURIComponent(key).json`. Matching that means a synced folder just works.
 */
private fun foliateFileName(book: BookEntity): String =
    Uri.encode(book.foliateKey, null).replace("+", "%20") + ".json"

private fun removeAnnotation(existing: List<JSONObject>, cfi: String): JSONArray =
    JSONArray(existing.filterNot { it.optString("value") == cfi }.toList())

private fun List<JSONObject>.noteFor(cfi: String): String =
    firstOrNull { it.optString("value") == cfi }?.optString("note").orEmpty()

/** Grey pill, dark label; the same shape the sidebar uses. */
@Composable
private fun PillButton(label: String, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = CircleShape,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun ScreenHeader(
    title: String,
    onBack: () -> Unit,
    actions: @Composable () -> Unit = {},
) {
    Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Chevron only: the label was pushing the actions off screen, and the
            // system back button does the same thing.
            IconButton(onClick = onBack) {
                Text("‹", style = MaterialTheme.typography.headlineMedium)
            }
            Text(title, style = MaterialTheme.typography.titleLarge)
        }
        Row(
            Modifier.padding(start = 8.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) { actions() }
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
            ScreenHeader("Chapters", onBack)
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
    chapter: String,
    error: String,
    bookmarked: Boolean,
    onBack: (() -> Unit)?,
    onToggleBookmark: () -> Unit,
    onShowBookmarks: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ChromeIsland(modifier.padding(12.dp)) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            if (onBack != null) {
                TextButton(onClick = onBack) {
                    Text("‹", style = MaterialTheme.typography.titleLarge)
                }
            }
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(
                    text = book.title.ifBlank { "Opening EPUB…" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = chapter.ifBlank { book.author },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
private fun ChromeIsland(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
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
    onBookReady: (String, String, String, List<TocEntry>) -> Unit,
    onTapped: () -> Unit,
    onSelected: (String, String, Boolean) -> Unit,
    onTapPage: (Boolean) -> Unit,
    onRelocated: (String, Double?, Int?, Int?, String, String) -> Unit,
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

            // Keep the selection handles, drop the system Copy/Share items: the
            // action mode still runs, it just gets an empty menu.
            object : WebView(context) {
                override fun startActionMode(callback: ActionMode.Callback?): ActionMode? =
                    super.startActionMode(EmptyMenu(callback))

                override fun startActionMode(
                    callback: ActionMode.Callback?,
                    type: Int,
                ): ActionMode? = super.startActionMode(EmptyMenu(callback), type)
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
                                    data.optInt("page", -1).takeIf { it >= 0 },
                                    data.optInt("pages", 0).takeIf { it > 0 },
                                    data.optString("chapter").normalizedText(),
                                    data.optString("printPage").normalizedText(),
                                )
                            }
                        }
                        "Tapped" -> onTapped()
                        "Selected" -> {
                            val cfi = data.optString("cfi")
                            val selected = data.optString("text").normalizedText()
                            if (cfi.startsWith("epubcfi(") && selected.isNotBlank()) {
                                onSelected(cfi, selected, data.optBoolean("lower", true))
                            }
                        }
                        "TappedPrevious" -> onTapPage(false)
                        "TappedNext" -> onTapPage(true)
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

/** Passes the selection action mode through with no menu items of its own. */
private class EmptyMenu(private val inner: ActionMode.Callback?) : ActionMode.Callback {
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
