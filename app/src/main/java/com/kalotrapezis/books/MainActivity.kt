package com.kalotrapezis.books

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import java.io.ByteArrayInputStream

private const val READER_ORIGIN = "appassets.androidplatform.net"
private const val READER_URL = "https://$READER_ORIGIN/assets/reader/index.html"

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
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
            ) {
                Text(text = "Books", style = MaterialTheme.typography.headlineLarge)
                Text(
                    text = "Offline reader capability check",
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                )
                ReaderCapabilityView(modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
@SuppressLint("SetJavaScriptEnabled")
private fun ReaderCapabilityView(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            val assetLoader = WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
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
                webViewClient = LocalReaderClient(assetLoader)
                loadUrl(READER_URL)
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

    private fun blockedResponse() = WebResourceResponse(
        "text/plain",
        "UTF-8",
        ByteArrayInputStream(ByteArray(0)),
    )
}
