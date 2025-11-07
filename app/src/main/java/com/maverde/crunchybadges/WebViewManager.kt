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
        // Enable cookie manager for persistent login
        val cookieManager = android.webkit.CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.settings.apply {
            // JavaScript
            javaScriptEnabled = true

            // Storage & Database
            domStorageEnabled = true
            databaseEnabled = true

            // Cache - Aggressive caching for better performance
            // LOAD_CACHE_ELSE_NETWORK prioritizes cache, loads from network only if needed
            cacheMode = android.webkit.WebSettings.LOAD_CACHE_ELSE_NETWORK

            // Media & Content
            mediaPlaybackRequiresUserGesture = false
            loadsImagesAutomatically = true
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

            // Zoom
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false

            // Viewport
            useWideViewPort = true
            loadWithOverviewMode = true

            // Allow file access for cookies/storage
            allowFileAccess = true
            allowContentAccess = true
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

                    // Force cookie flush to ensure they're saved
                    android.webkit.CookieManager.getInstance().flush()
                }
            }
        }
    }

    fun loadCrunchyroll() {
        webView.loadUrl(CRUNCHYROLL_URL)
    }

    private fun injectJavaScript(view: WebView) {
        // 1. Inject injected.js (API interceptor - must be first)
        val injectedJs = loadAsset("injected.js")
        view.evaluateJavascript(injectedJs) { result ->
            android.util.Log.d("CrunchyBadges", "Injected.js loaded: $result")
        }

        // 2. Inject content-android.js (badge logic - uses Android bridge)
        val contentAndroidJs = loadAsset("content-android.js")
        view.evaluateJavascript(contentAndroidJs) { result ->
            android.util.Log.d("CrunchyBadges", "Content-android.js loaded: $result")
        }

        // 3. Inject click-interceptor.js (handles clicks on anime cards)
        val clickInterceptorJs = loadAsset("click-interceptor.js")
        view.evaluateJavascript(clickInterceptorJs) { result ->
            android.util.Log.d("CrunchyBadges", "Click-interceptor.js loaded: $result")
        }

        android.util.Log.d("CrunchyBadges", "All scripts injected successfully")
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
