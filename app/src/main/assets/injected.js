// =============================================================================
// INJECTED SCRIPT - Runs in page context, not extension context
// =============================================================================
// Questo script viene iniettato direttamente nella pagina per intercettare
// window.fetch prima che Crunchyroll lo modifichi.
// =============================================================================

(function() {
  'use strict';

  console.log('🚀 [ITA Badge] Injected script started');

  // Salva riferimento originale
  const originalFetch = window.fetch;

  // Override di fetch
  window.fetch = async function(...args) {
    const response = await originalFetch.apply(this, args);
    const requestUrl = args[0];

    // Endpoint da monitorare (AGGIORNATI con quelli reali!)
    const targetEndpoints = [
      '/content/v1/browse',
      '/content/v2/discover/browse',
      '/content/v2/discover/search',
      '/content/v2/cms/series',
      '/content/v2/cms/seasons',
      '/content/v1/search'
    ];

    const shouldIntercept = targetEndpoints.some(endpoint =>
      typeof requestUrl === 'string' && requestUrl.includes(endpoint)
    );

    if (shouldIntercept) {
      // Clone e processa la risposta
      response.clone().json()
        .then(data => {
          // Invia i dati al content script via CustomEvent
          const event = new CustomEvent('crunchyroll-api-response', {
            detail: { url: requestUrl, data: data }
          });
          document.dispatchEvent(event);
        })
        .catch(error => {
          console.error('[ITA Badge] Errore processing response:', error);
        });
    }

    return response;
  };

  console.log('✅ [ITA Badge] window.fetch successfully hooked');

  // =============================================================================
  // INTERCETTA ANCHE XMLHttpRequest
  // =============================================================================

  const originalOpen = XMLHttpRequest.prototype.open;
  const originalSend = XMLHttpRequest.prototype.send;

  XMLHttpRequest.prototype.open = function(method, url, ...rest) {
    this._url = url;
    return originalOpen.apply(this, [method, url, ...rest]);
  };

  XMLHttpRequest.prototype.send = function(...args) {
    this.addEventListener('load', function() {
      const url = this._url;
      if (typeof url === 'string') {
        // Check se è un endpoint target
        const targetEndpoints = [
          '/content/v1/browse',
          '/content/v2/discover/browse',
          '/content/v2/discover/search',
          '/content/v2/cms/series',
          '/content/v2/cms/seasons',
          '/content/v1/search'
        ];

        if (targetEndpoints.some(endpoint => url.includes(endpoint))) {
          try {
            const data = JSON.parse(this.responseText);
            const event = new CustomEvent('crunchyroll-api-response', {
              detail: { url, data }
            });
            document.dispatchEvent(event);
          } catch (e) {
            console.error('[ITA Badge] Error parsing XHR response:', e);
          }
        }
      }
    });

    return originalSend.apply(this, args);
  };

  console.log('✅ [ITA Badge] XMLHttpRequest successfully hooked');
})();
