package com.maverde.crunchybadges.scraping

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import com.maverde.crunchybadges.data.models.BrowseResponse

/**
 * WebView-based scraper that intercepts Crunchyroll API responses
 * Bypasses Cloudflare by using a real browser engine
 */
class WebViewScraper(
    private val context: Context,
    private val sortBy: String = "alphabetical"  // alphabetical | newly_added
) {

    private lateinit var webView: WebView
    private val responseChannel = Channel<ScrapingResult>(Channel.UNLIMITED)

    private val json = Json { ignoreUnknownKeys = true }

    private var totalExpected = 0
    private var currentOffset = 0
    private val pageSize = 36  // Crunchyroll default page size

    private var receivedResponses = mutableSetOf<Int>()  // Track received offsets

    // Base URL extracted from first API call
    private var baseUrl: String? = null

    // API client for sequential pagination (initialized after first response)
    private var apiClient: ApiClient? = null
    private val apiScope = CoroutineScope(Dispatchers.IO)

    @Volatile
    private var destroyed = false

    companion object {
        private const val CRUNCHYROLL_URL = "https://www.crunchyroll.com/videos/new"

        // JavaScript to intercept API responses with auth headers
        private const val INTERCEPTOR_SCRIPT = """
            (function() {
                console.log('[Scraper] Injecting API interceptor with auth extraction');

                // Intercept XMLHttpRequest.setRequestHeader to capture auth
                const originalSetRequestHeader = XMLHttpRequest.prototype.setRequestHeader;
                XMLHttpRequest.prototype.setRequestHeader = function(header, value) {
                    if (!this._requestHeaders) {
                        this._requestHeaders = {};
                    }
                    this._requestHeaders[header] = value;
                    return originalSetRequestHeader.apply(this, arguments);
                };

                const originalFetch = window.fetch;
                window.fetch = function(...args) {
                    const url = args[0];
                    const options = args[1] || {};

                    return originalFetch.apply(this, args).then(response => {
                        if (url.includes('/content/v2/discover/browse')) {
                            const requestHeaders = options.headers || {};
                            const cookies = document.cookie;

                            // Extract auth from localStorage/sessionStorage
                            const localStorage = {};
                            const sessionStorage = {};
                            try {
                                for (let i = 0; i < window.localStorage.length; i++) {
                                    const key = window.localStorage.key(i);
                                    if (key.includes('token') || key.includes('auth')) {
                                        localStorage[key] = window.localStorage.getItem(key);
                                    }
                                }
                                for (let i = 0; i < window.sessionStorage.length; i++) {
                                    const key = window.sessionStorage.key(i);
                                    if (key.includes('token') || key.includes('auth')) {
                                        sessionStorage[key] = window.sessionStorage.getItem(key);
                                    }
                                }
                            } catch(e) {}

                            response.clone().json().then(data => {
                                console.log('[Scraper] Intercepted fetch API:', data.total);

                                const payload = {
                                    data: data,
                                    requestHeaders: requestHeaders,
                                    cookies: cookies,
                                    localStorage: localStorage,
                                    sessionStorage: sessionStorage,
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
                    this._requestHeaders = {};
                    return originalOpen.apply(this, arguments);
                };

                XMLHttpRequest.prototype.send = function(body) {
                    const xhr = this;

                    this.addEventListener('load', function() {
                        if (this._url && this._url.includes('/content/v2/discover/browse')) {
                            try {
                                const data = JSON.parse(this.responseText);
                                console.log('[Scraper] Intercepted XHR browse API:', data.total);
                                console.log('[Scraper] Request headers:', xhr._requestHeaders);

                                const cookies = document.cookie;

                                // Extract auth from localStorage/sessionStorage
                                const localStorage = {};
                                const sessionStorage = {};
                                try {
                                    for (let i = 0; i < window.localStorage.length; i++) {
                                        const key = window.localStorage.key(i);
                                        if (key.includes('token') || key.includes('auth')) {
                                            localStorage[key] = window.localStorage.getItem(key);
                                        }
                                    }
                                    for (let i = 0; i < window.sessionStorage.length; i++) {
                                        const key = window.sessionStorage.key(i);
                                        if (key.includes('token') || key.includes('auth')) {
                                            sessionStorage[key] = window.sessionStorage.getItem(key);
                                        }
                                    }
                                } catch(e) {}

                                const payload = {
                                    data: data,
                                    requestHeaders: xhr._requestHeaders || {},
                                    cookies: cookies,
                                    localStorage: localStorage,
                                    sessionStorage: sessionStorage,
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

                console.log('[Scraper] API interceptor with auth ready');
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
     * Clean up resources. Idempotent and safe to call from any thread:
     * WebView.destroy() must run on the main thread, so we post it.
     */
    fun destroy() {
        if (destroyed) return
        destroyed = true

        // Stop any in-flight OkHttp pagination so its results don't reach a closed channel
        apiScope.cancel()

        // WebView must be destroyed on the main looper
        if (this::webView.isInitialized) {
            val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
            mainHandler.post {
                try {
                    webView.destroy()
                } catch (e: Exception) {
                    android.util.Log.w("WebViewScraper", "WebView destroy failed: ${e.message}")
                }
            }
        }

        responseChannel.close()
    }

    /**
     * Trigger next page load using OkHttp (sequential, rate-limited)
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

        // Check if ApiClient is initialized
        if (apiClient == null) {
            android.util.Log.w("WebViewScraper", "Waiting for ApiClient initialization...")
            return
        }

        android.util.Log.d("WebViewScraper", "Loading page via OkHttp: $currentOffset/$totalExpected")

        // Use ApiClient to fetch next page (sequential with delay)
        apiScope.launch {
            val response = apiClient!!.fetchPage(currentOffset, pageSize)

            if (response != null) {
                // Success - send response
                responseChannel.trySend(ScrapingResult.Success(response))

                // Update offset
                currentOffset += response.data.size

                android.util.Log.d(
                    "WebViewScraper",
                    "OkHttp page loaded: ${response.data.size} items. Progress: $currentOffset/$totalExpected"
                )

                // Trigger next page
                loadNextPage()
            } else {
                // Failed - report error
                android.util.Log.e("WebViewScraper", "OkHttp fetch failed at offset $currentOffset")
                responseChannel.trySend(ScrapingResult.Error("Failed to fetch page at $currentOffset"))
            }
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

                // Initialize ApiClient on first response using auth headers + cookies
                if (apiClient == null) {
                    // Get cookies directly from WebView's CookieManager
                    val cookieManager = android.webkit.CookieManager.getInstance()
                    val webViewCookies = cookieManager.getCookie("https://www.crunchyroll.com") ?: ""

                    android.util.Log.d("WebViewScraper", "Initializing ApiClient")
                    android.util.Log.d("WebViewScraper", "  Cookies: ${webViewCookies.take(100)}...")

                    // Extract request headers from payload
                    val headersMap = mutableMapOf<String, String>()
                    try {
                        val requestHeaders = payload["requestHeaders"]?.jsonObject
                        requestHeaders?.forEach { (key, value) ->
                            val headerValue = value.toString().trim('"')
                            headersMap[key] = headerValue
                            if (key.equals("authorization", ignoreCase = true)) {
                                android.util.Log.d("WebViewScraper", "  Found Authorization header: ${headerValue.take(50)}...")
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("WebViewScraper", "Could not extract request headers: ${e.message}")
                    }

                    // Extract auth tokens from localStorage/sessionStorage
                    try {
                        val localStorage = payload["localStorage"]?.jsonObject
                        val sessionStorage = payload["sessionStorage"]?.jsonObject

                        localStorage?.forEach { (key, value) ->
                            val tokenValue = value.toString().trim('"')
                            android.util.Log.d("WebViewScraper", "  localStorage[$key]: ${tokenValue.take(50)}...")

                            // If no Authorization header yet, try to construct one from token
                            if (!headersMap.containsKey("Authorization") && !headersMap.containsKey("authorization")) {
                                if (key.contains("token", ignoreCase = true) && tokenValue.length > 20) {
                                    headersMap["Authorization"] = "Bearer $tokenValue"
                                    android.util.Log.d("WebViewScraper", "  Using localStorage token as Authorization")
                                }
                            }
                        }

                        sessionStorage?.forEach { (key, value) ->
                            val tokenValue = value.toString().trim('"')
                            android.util.Log.d("WebViewScraper", "  sessionStorage[$key]: ${tokenValue.take(50)}...")
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("WebViewScraper", "Could not extract storage data: ${e.message}")
                    }

                    // Use known Crunchyroll API endpoint (always the same)
                    val apiUrl = "https://www.crunchyroll.com/content/v2/discover/browse"

                    if (webViewCookies.isNotEmpty()) {
                        android.util.Log.d("WebViewScraper", "  API URL: $apiUrl")
                        android.util.Log.d("WebViewScraper", "  Headers count: ${headersMap.size}")

                        apiClient = ApiClient(apiUrl, headersMap, webViewCookies, sortBy)
                        android.util.Log.d("WebViewScraper", "✅ ApiClient initialized! Switching to OkHttp for pagination.")
                    } else {
                        android.util.Log.e("WebViewScraper", "❌ Failed to init ApiClient: no cookies available")
                    }
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

                // Trigger next page if ApiClient is ready
                if (apiClient != null) {
                    // Use OkHttp for subsequent pages
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
