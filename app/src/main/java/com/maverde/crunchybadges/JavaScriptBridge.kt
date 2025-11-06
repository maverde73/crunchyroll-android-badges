package com.maverde.crunchybadges

import android.app.Activity
import android.webkit.JavascriptInterface
import android.widget.Toast

/**
 * JavaScript Bridge
 *
 * Provides communication between JavaScript in WebView and Android native code.
 * Allows JavaScript to call Android methods.
 */
class JavaScriptBridge(
    private val activity: Activity,
    private val intentLauncher: IntentLauncher
) {

    /**
     * Called from JavaScript when user clicks on an anime
     * @param seriesId - Crunchyroll series ID (e.g., "GP5HJ84P7")
     */
    @JavascriptInterface
    fun openAnime(seriesId: String) {
        activity.runOnUiThread {
            // Show toast feedback
            Toast.makeText(
                activity,
                "Opening anime in Crunchyroll app...",
                Toast.LENGTH_SHORT
            ).show()

            // Launch Crunchyroll app with series ID
            intentLauncher.launchCrunchyrollApp(seriesId)
        }
    }

    /**
     * Log messages from JavaScript (for debugging)
     */
    @JavascriptInterface
    fun log(message: String) {
        android.util.Log.d("CrunchyBadges", "JS: $message")
    }

    /**
     * Get saved language preferences from Android storage
     * @return JSON string of selected languages
     */
    @JavascriptInterface
    fun getLanguagePreferences(): String {
        val prefs = activity.getSharedPreferences("crunchybadges", Activity.MODE_PRIVATE)
        return prefs.getString("selectedLanguages", "[\"it-IT\"]") ?: "[\"it-IT\"]"
    }

    /**
     * Save language preferences to Android storage
     * @param languagesJson - JSON array of selected language codes
     */
    @JavascriptInterface
    fun saveLanguagePreferences(languagesJson: String) {
        val prefs = activity.getSharedPreferences("crunchybadges", Activity.MODE_PRIVATE)
        prefs.edit().putString("selectedLanguages", languagesJson).apply()

        activity.runOnUiThread {
            Toast.makeText(
                activity,
                "Language preferences saved",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
