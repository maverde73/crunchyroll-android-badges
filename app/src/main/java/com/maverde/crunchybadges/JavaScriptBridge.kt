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
     * @param seriesTitle - Title of the series (optional, for Fire TV search)
     */
    @JavascriptInterface
    fun openAnime(seriesId: String, seriesTitle: String? = null) {
        activity.runOnUiThread {
            // Launch Crunchyroll with platform-aware method
            intentLauncher.launchCrunchyrollApp(seriesId, seriesTitle)
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

    /**
     * Get filter undubbed preference
     * @return true if user wants to show only dubbed anime
     */
    @JavascriptInterface
    fun shouldFilterUndubbed(): Boolean {
        val prefs = activity.getSharedPreferences("crunchybadges", Activity.MODE_PRIVATE)
        return prefs.getBoolean("filterUndubbed", false)
    }

    /**
     * Save filter undubbed preference
     * @param enabled - true to show only dubbed anime
     */
    @JavascriptInterface
    fun saveFilterUndubbed(enabled: Boolean) {
        val prefs = activity.getSharedPreferences("crunchybadges", Activity.MODE_PRIVATE)
        prefs.edit().putBoolean("filterUndubbed", enabled).apply()
    }
}
