package com.maverde.crunchybadges.scraping

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.serialization.json.Json
import com.maverde.crunchybadges.data.models.BrowseResponse

/**
 * WebView-based scraper that intercepts Crunchyroll API responses
 * Bypasses Cloudflare by using a real browser engine
 */
class WebViewScraper(private val context: Context) {

    private lateinit var webView: WebView
    private val responseChannel = Channel<ScrapingResult>(Channel.UNLIMITED)

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val CRUNCHYROLL_URL = "https://www.crunchyroll.com/videos/new"

        // JavaScript to intercept API responses
        private const val INTERCEPTOR_SCRIPT = """
            (function() {
                console.log('[Scraper] Injecting API interceptor');

                const originalFetch = window.fetch;
                window.fetch = function(...args) {
                    return originalFetch.apply(this, args).then(response => {
                        const url = args[0];
                        if (url.includes('/content/v2/discover/browse')) {
                            response.clone().json().then(data => {
                                console.log('[Scraper] Intercepted browse API:', data.total);
                                Android.onApiResponse(JSON.stringify(data));
                            }).catch(e => console.error('[Scraper] Error parsing JSON:', e));
                        }
                        return response;
                    });
                };

                const originalOpen = XMLHttpRequest.prototype.open;
                const originalSend = XMLHttpRequest.prototype.send;

                XMLHttpRequest.prototype.open = function(method, url) {
                    this._url = url;
                    return originalOpen.apply(this, arguments);
                };

                XMLHttpRequest.prototype.send = function() {
                    this.addEventListener('load', function() {
                        if (this._url && this._url.includes('/content/v2/discover/browse')) {
                            try {
                                const data = JSON.parse(this.responseText);
                                console.log('[Scraper] Intercepted XHR browse API:', data.total);
                                Android.onApiResponse(this.responseText);
                            } catch(e) {
                                console.error('[Scraper] Error parsing XHR:', e);
                            }
                        }
                    });
                    return originalSend.apply(this, arguments);
                };

                console.log('[Scraper] API interceptor ready');
            })();
        """
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun initialize() {
        webView = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true

            // Add JavaScript interface
            addJavascriptInterface(ScraperBridge(), "Android")

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    if (url.contains("crunchyroll.com")) {
                        // Inject interceptor script
                        view.evaluateJavascript(INTERCEPTOR_SCRIPT) {
                            android.util.Log.d("WebViewScraper", "Interceptor injected")
                        }
                    }
                }
            }
        }
    }

    /**
     * Start scraping by loading Crunchyroll
     */
    fun startScraping() {
        webView.loadUrl(CRUNCHYROLL_URL)
    }

    /**
     * Get flow of scraping results
     */
    fun getResultsFlow(): Flow<ScrapingResult> = responseChannel.receiveAsFlow()

    /**
     * Clean up resources
     */
    fun destroy() {
        webView.destroy()
        responseChannel.close()
    }

    /**
     * JavaScript bridge to receive API responses
     */
    private inner class ScraperBridge {
        @JavascriptInterface
        fun onApiResponse(jsonString: String) {
            try {
                val response = json.decodeFromString<BrowseResponse>(jsonString)
                android.util.Log.d(
                    "WebViewScraper",
                    "Parsed response: ${response.data.size} items, total: ${response.total}"
                )
                responseChannel.trySend(ScrapingResult.Success(response))
            } catch (e: Exception) {
                android.util.Log.e("WebViewScraper", "Error parsing JSON", e)
                responseChannel.trySend(ScrapingResult.Error(e.message ?: "Parse error"))
            }
        }

        @JavascriptInterface
        fun log(message: String) {
            android.util.Log.d("WebViewScraper", "JS: $message")
        }
    }
}

/**
 * Sealed class for scraping results
 */
sealed class ScrapingResult {
    data class Success(val response: BrowseResponse) : ScrapingResult()
    data class Error(val message: String) : ScrapingResult()
    object Complete : ScrapingResult()
}
