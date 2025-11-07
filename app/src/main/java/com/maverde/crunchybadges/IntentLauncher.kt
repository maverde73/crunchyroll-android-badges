package com.maverde.crunchybadges

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast

/**
 * Intent Launcher
 *
 * Handles launching Crunchyroll content using the best method available
 * for the current platform (Fire TV vs standard Android TV).
 */
class IntentLauncher(private val activity: Activity) {

    companion object {
        private const val CRUNCHYROLL_PACKAGE = "com.crunchyroll.crunchyroid"
        private const val CRUNCHYROLL_WEB_URL = "https://www.crunchyroll.com/series/"
        private const val CRUNCHYROLL_DEEP_LINK = "crunchyroll://series/"
    }

    /**
     * Launch Crunchyroll content with platform-aware approach
     * @param seriesId - Crunchyroll series ID
     * @param seriesTitle - Title of the series (optional, for user feedback)
     *
     * Fire TV: Uses crunchyroll:// scheme without setPackage() to avoid AppsFilter blocking
     * Other platforms: Uses direct deep link to Crunchyroll app with setPackage()
     */
    fun launchCrunchyrollApp(seriesId: String, seriesTitle: String? = null) {
        if (isFireTV()) {
            // Fire TV: Use crunchyroll:// scheme without setPackage()
            // System automatically chooses Crunchyroll app, avoiding AppsFilter blocking
            openUrlWithoutPackage(seriesId)
        } else {
            // Android TV / Google TV: Use direct deep link with setPackage()
            launchWithDeepLink(seriesId)
        }
    }

    /**
     * Detect if running on Amazon Fire TV
     */
    private fun isFireTV(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val model = Build.MODEL.lowercase()

        return manufacturer == "amazon" ||
               model.contains("aft") || // Amazon Fire TV
               model.contains("amazon")
    }

    /**
     * Open deep link without specifying package (Fire TV workaround)
     * Uses crunchyroll:// scheme without setPackage() to avoid AppsFilter blocking
     * The system automatically routes to Crunchyroll app (only handler for this scheme)
     */
    private fun openUrlWithoutPackage(seriesId: String) {
        try {
            // Use crunchyroll:// scheme (Crunchyroll app is the only handler)
            val deepLinkUrl = "$CRUNCHYROLL_DEEP_LINK$seriesId"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(deepLinkUrl)
                // NO setPackage() - system will choose Crunchyroll automatically
                // This avoids Fire OS AppsFilter blocking
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            activity.startActivity(intent)
            android.util.Log.d("CrunchyBadges", "Opened deep link: $deepLinkUrl (Fire TV mode)")
        } catch (e: Exception) {
            android.util.Log.e("CrunchyBadges", "Failed to open deep link", e)
            Toast.makeText(
                activity,
                "Unable to open Crunchyroll",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Launch with deep link for standard Android TV / Google TV
     * Uses https:// App Links verified by Crunchyroll
     */
    private fun launchWithDeepLink(seriesId: String) {
        try {
            val seriesUrl = "$CRUNCHYROLL_WEB_URL$seriesId"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(seriesUrl)
                setPackage(CRUNCHYROLL_PACKAGE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            activity.startActivity(intent)
            android.util.Log.d("CrunchyBadges", "Launched deep link to series: $seriesId")
        } catch (e: ActivityNotFoundException) {
            android.util.Log.e("CrunchyBadges", "Crunchyroll app not found")
            Toast.makeText(
                activity,
                "Crunchyroll app not installed",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Fallback: Launch Crunchyroll app to home screen
     */
    private fun launchCrunchyrollHome() {
        val intent = activity.packageManager.getLaunchIntentForPackage(CRUNCHYROLL_PACKAGE)
        intent?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                activity.startActivity(it)
                android.util.Log.d("CrunchyBadges", "Launched Crunchyroll home")
            } catch (e: Exception) {
                android.util.Log.e("CrunchyBadges", "Failed to launch Crunchyroll", e)
            }
        }
    }
}
