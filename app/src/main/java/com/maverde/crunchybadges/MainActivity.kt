package com.maverde.crunchybadges

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity

/**
 * Main Activity - Crunchyroll Language Badges
 *
 * Displays Crunchyroll in a WebView with language badge overlay.
 * When user selects an anime, launches the official Crunchyroll app.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val SETTINGS_REQUEST_CODE = 1001
    }

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

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Open settings when MENU button is pressed (Fire TV remote)
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            openSettings()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun openSettings() {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivityForResult(intent, SETTINGS_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == SETTINGS_REQUEST_CODE && resultCode == RESULT_OK) {
            // Settings changed, reload the page to apply new filters
            webView.reload()
        }
    }

    override fun onPause() {
        super.onPause()
        // Save WebView state and flush cookies
        webView.onPause()
        android.webkit.CookieManager.getInstance().flush()
    }

    override fun onResume() {
        super.onResume()
        // Resume WebView
        webView.onResume()
    }

    override fun onSaveInstanceState(outState: android.os.Bundle) {
        super.onSaveInstanceState(outState)
        // Save WebView state for restoration after configuration changes
        webView.saveState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: android.os.Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        // Restore WebView state
        webView.restoreState(savedInstanceState)
    }

    override fun onDestroy() {
        super.onDestroy()
        webView.destroy()
    }
}
