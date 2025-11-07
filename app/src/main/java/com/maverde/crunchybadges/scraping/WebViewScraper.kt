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
import kotlinx.serialization.json.jsonObject
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

    // Extracted authentication data
    private var authHeaders: Map<String, String>? = null
    private var cookies: String? = null
    private var baseUrl: String? = null
    private var useDirectApi = false  // Switch to direct API after first response

    companion object {
        private const val CRUNCHYROLL_URL = "https://www.crunchyroll.com/videos/new"

        // JavaScript to intercept API responses with full request details
        private const val INTERCEPTOR_SCRIPT = """
            (function() {
                console.log('[Scraper] Injecting API interceptor with headers extraction');

                const originalFetch = window.fetch;
                window.fetch = function(...args) {
                    const url = args[0];
                    const options = args[1] || {};

                    return originalFetch.apply(this, args).then(response => {
                        if (url.includes('/content/v2/discover/browse')) {
                            // Extract headers from request
                            const requestHeaders = options.headers || {};
                            const cookies = document.cookie;

                            response.clone().json().then(data => {
                                console.log('[Scraper] Intercepted fetch API:', data.total);

                                // Send data + headers to Android
                                const payload = {
                                    data: data,
                                    requestHeaders: requestHeaders,
                                    cookies: cookies,
                                    url: url
                                };
                                Android.onApiResponseWithHeaders(JSON.stringify(payload));
                            }).catch(e => console.error('[Scraper] Error parsing JSON:', e));
                        }
                        return response;
                    });
                };

                const originalOpen = XMLHttpRequest.prototype.open;
                const originalSend = XMLHttpRequest.prototype.send;

                XMLHttpRequest.prototype.open = function(method, url) {
                    this._url = url;
                    this._method = method;
                    return originalOpen.apply(this, arguments);
                };

                XMLHttpRequest.prototype.send = function(body) {
                    const xhr = this;

                    this.addEventListener('load', function() {
                        if (this._url && this._url.includes('/content/v2/discover/browse')) {
                            try {
                                const data = JSON.parse(this.responseText);
                                console.log('[Scraper] Intercepted XHR browse API:', data.total);

                                // Extract all request headers
                                const requestHeaders = {};
                                const headerStr = xhr.getAllResponseHeaders();

                                // Get cookies
                                const cookies = document.cookie;

                                const payload = {
                                    data: data,
                                    requestHeaders: requestHeaders,
                                    cookies: cookies,
                                    url: this._url,
                                    method: this._method
                                };

                                Android.onApiResponseWithHeaders(JSON.stringify(payload));
                            } catch(e) {
                                console.error('[Scraper] Error parsing XHR:', e);
                            }
                        }
                    });
                    return originalSend.call(this, body);
                };

                console.log('[Scraper] API interceptor with headers ready');
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
     * Trigger next page load by direct API fetch with extracted auth data
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

        // Check if we have auth data
        if (!useDirectApi || authHeaders == null) {
            android.util.Log.w("WebViewScraper", "Waiting for auth data...")
            return
        }

        // Make direct API call with extracted auth headers
        val start = currentOffset
        val apiUrl = "/content/v2/discover/browse?start=$start&n=36&sort_by=alphabetical"

        // Build headers object from extracted data
        val headersJson = authHeaders!!.entries.joinToString(",\n") { (key, value) ->
            // Escape value for JSON
            val escapedValue = value.replace("\"", "\\\"").replace("\n", "\\n")
            "        '$key': \"$escapedValue\""
        }

        android.util.Log.d("WebViewScraper", "Fetching offset $start/$totalExpected")

        webView.post {
            val fetchScript = """
                (function() {
                    console.log('[Scraper] Direct API fetch at offset $start');
                    fetch('$apiUrl', {
                        method: 'GET',
                        headers: {
$headersJson,
                            'Content-Type': 'application/json'
                        },
                        credentials: 'include'
                    })
                    .then(response => {
                        if (!response.ok) {
                            throw new Error('HTTP ' + response.status);
                        }
                        return response.json();
                    })
                    .then(data => {
                        console.log('[Scraper] Success for offset $start:', data.data.length, 'items');
                        const payload = {
                            data: data,
                            requestHeaders: {},
                            cookies: document.cookie,
                            url: '$apiUrl'
                        };
                        Android.onApiResponseWithHeaders(JSON.stringify(payload));
                    })
                    .catch(error => {
                        console.error('[Scraper] Fetch error at $start:', error);
                        console.error('[Scraper] Error details:', error.message);
                    });
                })();
            """.trimIndent()

            webView.evaluateJavascript(fetchScript, null)
        }
    }

    /**
     * JavaScript bridge to receive API responses with headers
     */
    private inner class ScraperBridge {
        @JavascriptInterface
        fun onApiResponseWithHeaders(jsonString: String) {
            try {
                // Parse the payload containing data + headers
                val payload = json.parseToJsonElement(jsonString).jsonObject
                val dataJsonString = payload["data"]!!.toString()
                val response = json.decodeFromString<BrowseResponse>(dataJsonString)

                android.util.Log.d(
                    "WebViewScraper",
                    "Parsed response: ${response.data.size} items, total: ${response.total}"
                )

                // Extract headers and cookies from first response
                if (authHeaders == null) {
                    val headersObj = payload["requestHeaders"]?.jsonObject
                    authHeaders = headersObj?.mapValues { it.value.toString().trim('"') } ?: emptyMap()
                    cookies = payload["cookies"]?.toString()?.trim('"') ?: ""
                    baseUrl = payload["url"]?.toString()?.trim('"')?.substringBefore("?") ?: ""

                    android.util.Log.d("WebViewScraper", "Extracted auth data:")
                    android.util.Log.d("WebViewScraper", "  Headers: ${authHeaders?.keys}")
                    android.util.Log.d("WebViewScraper", "  Cookies: ${cookies?.take(50)}...")
                    android.util.Log.d("WebViewScraper", "  Base URL: $baseUrl")

                    useDirectApi = true
                }

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
                android.util.Log.e("WebViewScraper", "Error parsing response with headers", e)
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
