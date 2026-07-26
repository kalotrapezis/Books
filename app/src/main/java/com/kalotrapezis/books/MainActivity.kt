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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import java.io.ByteArrayInputStream
import org.json.JSONObject

private const val READER_ORIGIN = "appassets.androidplatform.net"
private const val READER_URL = "https://$READER_ORIGIN/assets/reader/index.html?v=16"
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
            ) {
                Text(text = "Books", style = MaterialTheme.typography.headlineLarge)
                Text(
                    text = if (bookUri == null) {
                        "Offline EPUB reader"
                    } else {
                        "Reading selected book"
                    },
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                )
                Button(
                    onClick = { openBook.launch(arrayOf(EPUB_MIME_TYPE)) },
                    modifier = Modifier.padding(bottom = 12.dp),
                ) {
                    Text(if (bookUri == null) "Open EPUB" else "Open another EPUB")
                }
                key(bookUri) {
                    ReaderView(
                        bookUri = bookUri,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
@SuppressLint("SetJavaScriptEnabled")
private fun ReaderView(
    bookUri: Uri?,
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
                webViewClient = LocalReaderClient(assetLoader)
                WebViewCompat.addWebMessageListener(
                    this,
                    "booksBridge",
                    setOf("https://$READER_ORIGIN"),
                ) { _, message, sourceOrigin, isMainFrame, _ ->
                    if (!isMainFrame || sourceOrigin.host != READER_ORIGIN) return@addWebMessageListener
                    val messageData = message.data ?: return@addWebMessageListener
                    val data =
                        runCatching { JSONObject(messageData) }.getOrNull()
                            ?: return@addWebMessageListener
                    val cfi = data.optString("cfi")
                    if (data.optString("type") == "Relocated"
                        && cfi.startsWith("epubcfi(")
                        && cfi.length <= 8192
                    ) {
                        preferences.edit().putString(LAST_CFI_KEY, cfi).apply()
                    }
                }
                loadUrl(if (bookUri == null) READER_URL else "$READER_URL&book=selected")
            }
        },
        onRelease = WebView::destroy,
    )
}

private class LocalReaderClient(
    private val assetLoader: WebViewAssetLoader,
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
