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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
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
import org.json.JSONObject

private const val READER_ORIGIN = "appassets.androidplatform.net"
private const val READER_URL = "https://$READER_ORIGIN/assets/reader/index.html?v=19"
private const val EPUB_MIME_TYPE = "application/epub+zip"
private const val PREFERENCES_NAME = "reader-state"
private const val BOOK_URI_KEY = "book-uri"
private const val LAST_CFI_KEY = "last-cfi"

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
                                onAddBook = launchPicker,
                                showLibraryAction = false,
                                onShowLibrary = {},
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                    }
                } else if (selectedBook == null) {
                    LibraryPane(
                        books = library,
                        selectedBookId = null,
                        error = error,
                        onAddBook = launchPicker,
                        onSelectBook = selectBook,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    ReaderScreen(
                        book = selectedBook,
                        dao = dao,
                        persistenceScope = scope,
                        onAddBook = launchPicker,
                        showLibraryAction = true,
                        onShowLibrary = { selectedBookId = null },
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
    onAddBook: () -> Unit,
    onSelectBook: (BookEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Books", style = MaterialTheme.typography.headlineMedium)
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
    onAddBook: () -> Unit,
    showLibraryAction: Boolean,
    onShowLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var progress by remember(book.id) { mutableStateOf(book.progressFraction) }
    var error by remember(book.id) { mutableStateOf("") }
    var bridge by remember(book.id) { mutableStateOf<JavaScriptReplyProxy?>(null) }
    var readerReady by remember(book.id) { mutableStateOf(false) }
    var scrolled by remember(book.id) { mutableStateOf(false) }
    val send: (JSONObject) -> Unit = { command ->
        runCatching { bridge?.postMessage(command.toString()) }
            .onFailure { error = "Reader command failed: ${it.message ?: "unknown error"}" }
    }
    val sendCommand: (String) -> Unit = { type -> send(JSONObject().put("type", type)) }

    Column(modifier) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (showLibraryAction) {
                    TextButton(onClick = onShowLibrary) { Text("Library") }
                } else {
                    Text("Books", style = MaterialTheme.typography.titleLarge)
                }
                Row {
                    TextButton(
                        onClick = {
                            scrolled = !scrolled
                            send(
                                JSONObject()
                                    .put("type", "SetFlow")
                                    .put("flow", if (scrolled) "scrolled" else "paginated"),
                            )
                        },
                        enabled = bridge != null && readerReady,
                    ) { Text(if (scrolled) "Paginated" else "Scrolled") }
                    TextButton(onClick = onAddBook) { Text("Add EPUB") }
                }
            }
            Text(
                text = book.title.ifBlank { "Opening EPUB…" },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (book.author.isNotBlank()) {
                Text(book.author, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (error.isNotBlank()) {
                Text(error, color = MaterialTheme.colorScheme.error, maxLines = 2)
            }
        }
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
                onBookReady = { title, author, identifier ->
                    error = ""
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
                onRelocated = { cfi, fraction ->
                    progress = fraction
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
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
        ReaderControls(
            progress = progress,
            enabled = bridge != null && readerReady,
            onPrevious = { sendCommand("Previous") },
            onNext = { sendCommand("Next") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun ReaderControls(
    progress: Double?,
    enabled: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.SpaceBetween) {
        Button(onClick = onPrevious, enabled = enabled) { Text("Previous") }
        Text(progress.asPercent(), modifier = Modifier.padding(top = 12.dp))
        Button(onClick = onNext, enabled = enabled) { Text("Next") }
    }
}

@Composable
@SuppressLint("SetJavaScriptEnabled")
private fun ReaderView(
    book: BookEntity,
    onBridgeReady: (JavaScriptReplyProxy) -> Unit,
    onBridgeClosed: (JavaScriptReplyProxy?) -> Unit,
    onBookReady: (String, String, String) -> Unit,
    onRelocated: (String, Double?) -> Unit,
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
                        )
                        "Relocated" -> {
                            val cfi = data.optString("cfi")
                            if (cfi.startsWith("epubcfi(") && cfi.length <= 8192) {
                                onRelocated(
                                    cfi,
                                    data.optDouble("fraction", Double.NaN)
                                        .takeIf(Double::isFinite),
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
        return if (url.scheme == "https" && url.host == READER_ORIGIN) {
            assetLoader.shouldInterceptRequest(url) ?: blockedResponse()
        } else {
            blockedResponse()
        }
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
        request.url.scheme != "https" || request.url.host != READER_ORIGIN

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
