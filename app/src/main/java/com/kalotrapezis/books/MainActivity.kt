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
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import java.io.ByteArrayInputStream
import org.json.JSONObject

private const val READER_ORIGIN = "appassets.androidplatform.net"
private const val READER_URL = "https://$READER_ORIGIN/assets/reader/index.html?v=17"
private const val EPUB_MIME_TYPE = "application/epub+zip"
private const val PREFERENCES_NAME = "reader-state"
private const val BOOK_URI_KEY = "book-uri"
private const val LAST_CFI_KEY = "last-cfi"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BooksApp()
        }
    }
}

@Composable
private fun BooksApp() {
    val context = LocalContext.current
    val preferences = remember {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }
    var bookUri by remember {
        mutableStateOf(preferences.getString(BOOK_URI_KEY, null)?.let(Uri::parse))
    }
    var title by remember(bookUri) { mutableStateOf("") }
    var author by remember(bookUri) { mutableStateOf("") }
    var progress by remember(bookUri) { mutableStateOf<Double?>(null) }
    var error by remember(bookUri) { mutableStateOf("") }
    var bridge by remember(bookUri) { mutableStateOf<JavaScriptReplyProxy?>(null) }
    var readerReady by remember(bookUri) { mutableStateOf(false) }
    val openBook = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            preferences.edit()
                .putString(BOOK_URI_KEY, uri.toString())
                .remove(LAST_CFI_KEY)
                .apply()
            bookUri = uri
        }
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val sendCommand: (String) -> Unit = { type ->
                    runCatching { bridge?.postMessage(JSONObject().put("type", type).toString()) }
                        .onFailure { error = "Reader command failed: ${it.message ?: "unknown error"}" }
                }
                val controls: @Composable () -> Unit = {
                    ReaderControls(
                        progress = progress,
                        enabled = bridge != null && readerReady,
                        onPrevious = { sendCommand("Previous") },
                        onNext = { sendCommand("Next") },
                    )
                }
                val reader: @Composable (Modifier) -> Unit = { modifier ->
                    key(bookUri) {
                        ReaderView(
                            bookUri = bookUri,
                            onBridgeReady = { bridge = it },
                            onBridgeClosed = { closedBridge ->
                                if (bridge === closedBridge) {
                                    bridge = null
                                    readerReady = false
                                }
                            },
                            onBookReady = { newTitle, newAuthor ->
                                title = newTitle
                                author = newAuthor
                                error = ""
                            },
                            onRelocated = { fraction ->
                                progress = fraction
                                readerReady = true
                            },
                            onReaderError = {
                                error = it
                                readerReady = false
                            },
                            modifier = modifier,
                        )
                    }
                }
                if (maxWidth >= 600.dp) {
                    Row(Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .width(260.dp)
                                .fillMaxHeight()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            ReaderDetails(bookUri, title, author, error)
                            Button(onClick = { openBook.launch(arrayOf(EPUB_MIME_TYPE)) }) {
                                Text(if (bookUri == null) "Open EPUB" else "Open another EPUB")
                            }
                            controls()
                        }
                        reader(Modifier.weight(1f).fillMaxHeight())
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(text = "Books", style = MaterialTheme.typography.titleLarge)
                            Text(
                                text = title.ifBlank { if (bookUri == null) "Offline EPUB reader" else "Opening EPUB…" },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (author.isNotBlank()) {
                                Text(author, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (error.isNotBlank()) {
                                Text(error, color = MaterialTheme.colorScheme.error, maxLines = 2)
                            }
                            Button(
                                onClick = { openBook.launch(arrayOf(EPUB_MIME_TYPE)) },
                                modifier = Modifier.padding(top = 8.dp),
                            ) {
                                Text(if (bookUri == null) "Open EPUB" else "Open another EPUB")
                            }
                        }
                        reader(Modifier.fillMaxWidth().weight(1f))
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            controls()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderDetails(bookUri: Uri?, title: String, author: String, error: String) {
    Text(text = "Books", style = MaterialTheme.typography.titleLarge)
    Text(
        text = title.ifBlank { if (bookUri == null) "Offline EPUB reader" else "Opening EPUB…" },
        style = MaterialTheme.typography.titleMedium,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
    if (author.isNotBlank()) Text(author, maxLines = 3, overflow = TextOverflow.Ellipsis)
    if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
}

@Composable
private fun ReaderControls(
    progress: Double?,
    enabled: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Button(onClick = onPrevious, enabled = enabled) {
            Text("Previous")
        }
        Text(
            text = progress?.let { "${(it * 100).toInt()}%" } ?: "Reading",
            modifier = Modifier.padding(top = 12.dp),
        )
        Button(onClick = onNext, enabled = enabled) {
            Text("Next")
        }
    }
}

@Composable
@SuppressLint("SetJavaScriptEnabled")
private fun ReaderView(
    bookUri: Uri?,
    onBridgeReady: (JavaScriptReplyProxy) -> Unit,
    onBridgeClosed: (JavaScriptReplyProxy?) -> Unit,
    onBookReady: (String, String) -> Unit,
    onRelocated: (Double?) -> Unit,
    onReaderError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            val preferences =
                context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            val assetLoaderBuilder = WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                .addPathHandler("/state/") { path ->
                    if (path != "last-location.json") {
                        blockedResponse()
                    } else {
                        val cfi = preferences.getString(LAST_CFI_KEY, null)
                        jsonResponse(JSONObject().put("cfi", cfi).toString())
                    }
                }
            if (bookUri != null) {
                assetLoaderBuilder.addPathHandler("/book/") { path ->
                    if (path != "selected.epub") {
                        blockedResponse()
                    } else {
                        val stream = runCatching {
                            context.contentResolver.openInputStream(bookUri)
                        }.getOrNull()
                        if (stream == null) {
                            blockedResponse()
                        } else {
                            WebResourceResponse(EPUB_MIME_TYPE, null, stream)
                        }
                    }
                }
            }
            val assetLoader = assetLoaderBuilder.build()

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
                    if (!isMainFrame || sourceOrigin.host != READER_ORIGIN) return@addWebMessageListener
                    view.tag = replyProxy
                    onBridgeReady(replyProxy)
                    val messageData = message.data ?: return@addWebMessageListener
                    val data =
                        runCatching { JSONObject(messageData) }.getOrNull()
                            ?: return@addWebMessageListener
                    when (data.optString("type")) {
                        "BookReady" -> onBookReady(
                            data.optString("title").normalizedText(),
                            data.optString("author").normalizedText(),
                        )
                        "Relocated" -> {
                            val cfi = data.optString("cfi")
                            if (cfi.startsWith("epubcfi(") && cfi.length <= 8192) {
                                preferences.edit().putString(LAST_CFI_KEY, cfi).apply()
                                onRelocated(
                                    data.optDouble("fraction", Double.NaN).takeIf(Double::isFinite),
                                )
                            }
                        }
                        "ReaderError" -> onReaderError(
                            data.optString("message").normalizedText().ifBlank { "Reader error" },
                        )
                    }
                }
                loadUrl(if (bookUri == null) READER_URL else "$READER_URL&book=selected")
            }
        },
        onRelease = { view ->
            onBridgeClosed(view.tag as? JavaScriptReplyProxy)
            view.destroy()
        },
    )
}

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
