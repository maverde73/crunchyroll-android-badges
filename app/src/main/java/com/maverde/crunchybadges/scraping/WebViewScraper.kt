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

    private var totalExpected = 0
    private var currentOffset = 0
    private val pageSize = 36  // Crunchyroll default page size

    private var receivedResponses = mutableSetOf<Int>()  // Track received offsets

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
     * Trigger next page load by direct API fetch
     */
    private fun loadNextPage() {
        if (totalExpected == 0) {
            // Wait for first response to know the total
            return
        }

        if (currentOffset >= totalExpected) {
            // All data loaded
            android.util.Log.d("WebViewScraper", "Scraping complete!")
            responseChannel.trySend(ScrapingResult.Complete)
            return
        }

        // Make direct API call with fetch
        val start = currentOffset
        val apiUrl = "/content/v2/discover/browse?start=$start&n=36&sort_by=alphabetical"

        webView.post {
            val fetchScript = """
                (function() {
                    console.log('[Scraper] Fetching page at offset $start');
                    fetch('$apiUrl', {
                        method: 'GET',
                        headers: {
                            'Content-Type': 'application/json'
                        },
                        credentials: 'include'
                    })
                    .then(response => response.json())
                    .then(data => {
                        console.log('[Scraper] API response for offset $start:', data.total);
                        Android.onApiResponse(JSON.stringify(data));
                    })
                    .catch(error => {
                        console.error('[Scraper] Fetch error:', error);
                    });
                })();
            """.trimIndent()

            webView.evaluateJavascript(fetchScript, null)
        }
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

                // Update total if this is first response
                if (totalExpected == 0) {
                    totalExpected = response.total
                    android.util.Log.d("WebViewScraper", "Total items to scrape: $totalExpected")
                }

                // Mark this offset as received
                receivedResponses.add(currentOffset)
                currentOffset += response.data.size

                // Send response
                responseChannel.trySend(ScrapingResult.Success(response))

                // Trigger next page if needed
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    loadNextPage()
                }

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
