package com.maverde.crunchybadges

import android.annotation.SuppressLint
import android.app.Activity
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * WebView Manager
 *
 * Manages the WebView, injects JavaScript from extension, and sets up
 * communication bridge with Android.
 */
class WebViewManager(
    private val webView: WebView,
    private val activity: Activity,
    private val intentLauncher: IntentLauncher
) {

    companion object {
        private const val CRUNCHYROLL_URL = "https://www.crunchyroll.com/videos/new"
    }

    init {
        setupWebView()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
        }

        // Add JavaScript interface for communication
        webView.addJavascriptInterface(
            JavaScriptBridge(activity, intentLauncher),
            "Android"
        )

        // Set up WebViewClient to inject code when page loads
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)

                // Only inject on Crunchyroll pages
                if (url.contains("crunchyroll.com")) {
                    injectJavaScript(view)
                    injectCSS(view)
                }
            }
        }
    }

    fun loadCrunchyroll() {
        webView.loadUrl(CRUNCHYROLL_URL)
    }

    private fun injectJavaScript(view: WebView) {
        // Inject injected.js (API interceptor)
        val injectedJs = loadAsset("injected.js")
        view.evaluateJavascript(injectedJs, null)

        // Inject content.js (badge logic)
        val contentJs = loadAsset("content.js")
        // Modify content.js to use Android bridge
        val modifiedContentJs = contentJs.replace(
            "// Add badges to element",
            """
            // Intercept anime clicks for Android
            document.addEventListener('click', (e) => {
                const link = e.target.closest('a[href*="/series/"]');
                if (link) {
                    e.preventDefault();
                    const match = link.href.match(/\/series\/([A-Z0-9]+)/i);
                    if (match) {
                        Android.openAnime(match[1]);
                    }
                }
            }, true);

            // Add badges to element
            """.trimIndent()
        )
        view.evaluateJavascript(modifiedContentJs, null)
    }

    private fun injectCSS(view: WebView) {
        val css = loadAsset("style.css")
        val cssInjection = """
            (function() {
                var style = document.createElement('style');
                style.textContent = `$css`;
                document.head.appendChild(style);
            })();
        """.trimIndent()
        view.evaluateJavascript(cssInjection, null)
    }

    private fun loadAsset(fileName: String): String {
        return try {
            val inputStream = activity.assets.open(fileName)
            val reader = BufferedReader(InputStreamReader(inputStream))
            val text = reader.use { it.readText() }
            text
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }
}
