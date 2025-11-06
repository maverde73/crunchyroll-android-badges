package com.maverde.crunchybadges

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * Intent Launcher
 *
 * Handles launching the official Crunchyroll app with deep links.
 * Falls back to web browser if app is not installed.
 */
class IntentLauncher(private val activity: Activity) {

    companion object {
        private const val CRUNCHYROLL_PACKAGE = "com.crunchyroll.crunchyroid"
        private const val CRUNCHYROLL_WEB_URL = "https://www.crunchyroll.com/series/"
    }

    /**
     * Launch Crunchyroll app with specific anime series
     * @param seriesId - Crunchyroll series ID
     */
    fun launchCrunchyrollApp(seriesId: String) {
        // Try multiple approaches

        // Approach 1: Try deep link with crunchyroll:// scheme
        if (tryDeepLink(seriesId)) {
            return
        }

        // Approach 2: Try opening with package name and web URL
        if (tryPackageIntent(seriesId)) {
            return
        }

        // Approach 3: Fallback to web browser
        openInBrowser(seriesId)
    }

    private fun tryDeepLink(seriesId: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("crunchyroll://series/$seriesId")
                setPackage(CRUNCHYROLL_PACKAGE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (intent.resolveActivity(activity.packageManager) != null) {
                activity.startActivity(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun tryPackageIntent(seriesId: String): Boolean {
        return try {
            val webUrl = "$CRUNCHYROLL_WEB_URL$seriesId"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(webUrl)
                setPackage(CRUNCHYROLL_PACKAGE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (intent.resolveActivity(activity.packageManager) != null) {
                activity.startActivity(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun openInBrowser(seriesId: String) {
        try {
            val webUrl = "$CRUNCHYROLL_WEB_URL$seriesId"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(webUrl))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            activity.startActivity(intent)

            Toast.makeText(
                activity,
                "Crunchyroll app not found. Opening in browser...",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(
                activity,
                "No browser found to open Crunchyroll",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Check if Crunchyroll app is installed
     */
    fun isCrunchyrollInstalled(): Boolean {
        return try {
            activity.packageManager.getPackageInfo(CRUNCHYROLL_PACKAGE, 0)
            true
        } catch (e: Exception) {
            false
        }
    }
}
