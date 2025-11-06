package com.maverde.crunchybadges

import android.os.Bundle
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity

/**
 * Main Activity - Crunchyroll Language Badges
 *
 * Displays Crunchyroll in a WebView with language badge overlay.
 * When user selects an anime, launches the official Crunchyroll app.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var webViewManager: WebViewManager
    private lateinit var intentLauncher: IntentLauncher

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        intentLauncher = IntentLauncher(this)

        // Initialize WebView with badge injection
        webViewManager = WebViewManager(
            webView = webView,
            activity = this,
            intentLauncher = intentLauncher
        )

        // Load Crunchyroll
        webViewManager.loadCrunchyroll()
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        webView.destroy()
    }
}
