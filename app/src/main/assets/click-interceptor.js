// =============================================================================
// CLICK INTERCEPTOR - Android WebView
// =============================================================================
// Intercetta i click su link delle serie e lancia l'app Crunchyroll
// =============================================================================

console.log('🎯 [Click Interceptor] Script caricato');

/**
 * Estrae l'ID della serie dall'URL
 * Esempi:
 * - /it/series/GP5HJ84P7/gachiakuta -> GP5HJ84P7
 * - /watch/GP5HJ84P7 -> GP5HJ84P7
 * - /series/GP5HJ84P7 -> GP5HJ84P7
 */
function extractSeriesIDFromUrl(url) {
  try {
    // Prova con /series/ID
    let match = url.match(/\/series\/([A-Z0-9]+)/i);
    if (match) return match[1];

    // Prova con /watch/ID
    match = url.match(/\/watch\/([A-Z0-9]+)/i);
    if (match) return match[1];

    return null;
  } catch (error) {
    console.error('[Click Interceptor] Errore nel parsing URL:', error);
    return null;
  }
}

/**
 * Verifica se Android bridge è disponibile
 */
function isAndroidBridgeAvailable() {
  return typeof Android !== 'undefined' && typeof Android.openAnime === 'function';
}

/**
 * Estrae il titolo della serie dal link cliccato
 * Cerca in vari posti nel DOM vicino al link
 */
function extractSeriesTitleFromLink(link) {
  try {
    // Cerca il titolo nell'attributo aria-label del link
    if (link.getAttribute('aria-label')) {
      return link.getAttribute('aria-label');
    }

    // Cerca il titolo in un elemento h4 o h3 vicino
    const card = link.closest('[class*="card"]') || link.closest('li');
    if (card) {
      const heading = card.querySelector('h4, h3, h2, [class*="title"]');
      if (heading && heading.textContent) {
        return heading.textContent.trim();
      }
    }

    // Cerca nei figli del link
    const titleElement = link.querySelector('h4, h3, h2, [class*="title"]');
    if (titleElement && titleElement.textContent) {
      return titleElement.textContent.trim();
    }

    // Cerca nel testo alternativo dell'immagine
    const img = link.querySelector('img');
    if (img && img.alt) {
      return img.alt.trim();
    }

    return null;
  } catch (error) {
    console.error('[Click Interceptor] Errore nell\'estrazione del titolo:', error);
    return null;
  }
}

/**
 * Intercetta i click su link delle serie/watch
 */
function setupClickInterceptor() {
  document.addEventListener('click', (event) => {
    // Trova il link più vicino nell'albero DOM
    const link = event.target.closest('a');

    if (!link || !link.href) return;

    // Controlla se è un link a serie o watch
    if (link.href.includes('/series/') || link.href.includes('/watch/')) {
      console.log('[Click Interceptor] Click su link:', link.href);

      const seriesID = extractSeriesIDFromUrl(link.href);
      const seriesTitle = extractSeriesTitleFromLink(link);

      if (seriesID) {
        console.log('[Click Interceptor] Series ID estratto:', seriesID);
        console.log('[Click Interceptor] Titolo estratto:', seriesTitle);

        // Previeni il comportamento default (navigazione nel WebView)
        event.preventDefault();
        event.stopPropagation();

        // Chiama Android bridge per aprire l'app
        if (isAndroidBridgeAvailable()) {
          console.log('[Click Interceptor] Chiamata Android.openAnime con ID:', seriesID, 'Titolo:', seriesTitle);

          // Passa sia ID che titolo (il titolo è opzionale, usato su Fire TV)
          if (seriesTitle) {
            Android.openAnime(seriesID, seriesTitle);
          } else {
            Android.openAnime(seriesID);
          }
        } else {
          console.error('[Click Interceptor] Android bridge non disponibile!');
          // Fallback: log per debug
          if (typeof Android !== 'undefined' && typeof Android.log === 'function') {
            Android.log('Click Interceptor: seriesID=' + seriesID + ' ma openAnime non disponibile');
          }
        }
      } else {
        console.warn('[Click Interceptor] Impossibile estrarre series ID da:', link.href);
      }
    }
  }, true); // Use capture phase per intercettare prima di altri handler

  console.log('✅ [Click Interceptor] Listener installato con successo');
}

// Installa il click interceptor
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', setupClickInterceptor);
} else {
  setupClickInterceptor();
}
