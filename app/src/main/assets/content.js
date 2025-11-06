// =============================================================================
// CRUNCHYROLL ITA BADGE - Content Script
// =============================================================================
// Questa estensione intercetta le chiamate API di Crunchyroll per identificare
// i contenuti con audio italiano e aggiunge un badge visivo agli item HTML.
// =============================================================================

console.log('🎌 [Badge] Content script caricato');

// =============================================================================
// CONFIGURATION
// =============================================================================

const STORAGE_KEY_PREFIX = 'dubbedIDs_'; // Sarà dubbedIDs_it-IT, dubbedIDs_en-US, etc.
const SELECTED_LANGUAGES_KEY = 'selectedLanguages';
const DEFAULT_LANGUAGES = ['it-IT'];

// Language configuration (sync with options.js)
const LANGUAGE_CONFIG = {
  'it-IT': { label: 'ITA', flag: '🇮🇹', color: 'rgba(0, 128, 0, 0.9)' },
  'en-US': { label: 'ENG', flag: '🇺🇸', color: 'rgba(0, 84, 166, 0.9)' },
  'es-ES': { label: 'ESP', flag: '🇪🇸', color: 'rgba(198, 0, 0, 0.9)' },
  'es-419': { label: 'LAT', flag: '🌎', color: 'rgba(255, 136, 0, 0.9)' },
  'de-DE': { label: 'DEU', flag: '🇩🇪', color: 'rgba(0, 0, 0, 0.85)' },
  'fr-FR': { label: 'FRA', flag: '🇫🇷', color: 'rgba(0, 85, 164, 0.9)' },
  'pt-BR': { label: 'POR', flag: '🇧🇷', color: 'rgba(0, 155, 58, 0.9)' },
  'ja-JP': { label: 'JAP', flag: '🇯🇵', color: 'rgba(188, 0, 45, 0.9)' },
  'ru-RU': { label: 'RUS', flag: '🇷🇺', color: 'rgba(0, 57, 166, 0.9)' }
};

let selectedLanguages = DEFAULT_LANGUAGES;

// =============================================================================
// INJECT SCRIPT INTO PAGE CONTEXT
// =============================================================================

function injectScript() {
  const script = document.createElement('script');
  script.src = chrome.runtime.getURL('injected.js');
  script.onload = function() {
    console.log('✅ [Badge] Injected script loaded');
    this.remove();
  };
  (document.head || document.documentElement).appendChild(script);
}

// Inietta lo script il prima possibile
injectScript();

// =============================================================================
// LANGUAGE CONFIGURATION
// =============================================================================

/**
 * Carica le lingue selezionate dall'utente
 */
async function loadSelectedLanguages() {
  try {
    const result = await chrome.storage.sync.get(SELECTED_LANGUAGES_KEY);
    selectedLanguages = result[SELECTED_LANGUAGES_KEY] || DEFAULT_LANGUAGES;
    console.log(`[Badge] Lingue configurate:`, selectedLanguages);
  } catch (error) {
    console.error('[Badge] Errore caricamento lingue:', error);
    selectedLanguages = DEFAULT_LANGUAGES;
  }
}

// Carica subito le lingue
loadSelectedLanguages();

// Listen for language updates from options page
chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  if (message.type === 'LANGUAGES_UPDATED') {
    selectedLanguages = message.languages;
    console.log('[Badge] Lingue aggiornate:', selectedLanguages);
    // Re-scan della pagina con nuove lingue
    scanAndApplyBadges([document.body]);
  }
});

// =============================================================================
// STORAGE MANAGEMENT
// =============================================================================

/**
 * Salva gli ID delle serie con audio per una specifica lingua.
 * Utilizza un Set per evitare duplicati.
 */
async function saveIDsToStorage(locale, newIDs) {
  if (!newIDs || newIDs.length === 0) return;

  const storageKey = STORAGE_KEY_PREFIX + locale;

  try {
    const result = await chrome.storage.local.get(storageKey);
    const existingIDs = result[storageKey] || [];
    const combinedSet = new Set([...existingIDs, ...newIDs]);

    await chrome.storage.local.set({
      [storageKey]: Array.from(combinedSet)
    });

    console.log(`[Badge] Salvati ${newIDs.length} ID per ${locale}. Totale: ${combinedSet.size}`);
  } catch (error) {
    console.error(`[Badge] Errore nel salvare gli ID per ${locale}:`, error);
  }
}

/**
 * Recupera tutti gli ID salvati per tutte le lingue configurate.
 * Ritorna un oggetto: { locale: [IDs], locale2: [IDs], ... }
 */
async function getAllStoredIDs() {
  const result = {};

  try {
    const keys = selectedLanguages.map(locale => STORAGE_KEY_PREFIX + locale);
    const storedData = await chrome.storage.local.get(keys);

    selectedLanguages.forEach(locale => {
      const key = STORAGE_KEY_PREFIX + locale;
      result[locale] = storedData[key] || [];
    });

    return result;
  } catch (error) {
    console.error('[Badge] Errore nel recuperare gli ID:', error);
    return {};
  }
}

// =============================================================================
// API RESPONSE LISTENER
// =============================================================================
// Ascolta gli eventi inviati dall'injected script che intercetta le API
// =============================================================================

document.addEventListener('crunchyroll-api-response', (event) => {
  const { url, data } = event.detail;
  console.log('📨 [ITA Badge] Evento ricevuto da injected script');
  processAPIResponse(data).then(() => {
    // Dopo aver salvato i nuovi IDs, fai una re-scan della pagina
    console.log('[ITA Badge] Re-scanning pagina dopo aggiornamento storage');
    scanAndApplyBadges([document.body]);
  });
});

/**
 * Processa la risposta API ed estrae gli ID per ogni lingua configurata.
 */
async function processAPIResponse(responseData) {
  // Mappa per raggruppare IDs per lingua: { locale: [IDs], ... }
  const foundIDsByLanguage = {};

  // Inizializza array per ogni lingua configurata
  selectedLanguages.forEach(locale => {
    foundIDsByLanguage[locale] = [];
  });

  try {
    // La struttura reale del JSON ha un array "data"
    if (responseData && Array.isArray(responseData.data)) {
      responseData.data.forEach(item => {
        // Controlla se esiste series_metadata.audio_locales
        if (item?.series_metadata?.audio_locales && item.id) {
          const audioLocales = item.series_metadata.audio_locales;

          if (Array.isArray(audioLocales)) {
            // Per ogni lingua configurata, vedi se è disponibile per questa serie
            selectedLanguages.forEach(locale => {
              if (audioLocales.includes(locale)) {
                foundIDsByLanguage[locale].push(item.id);
              }
            });
          }
        }
      });

      // Salva gli IDs per ogni lingua
      for (const [locale, ids] of Object.entries(foundIDsByLanguage)) {
        if (ids.length > 0) {
          await saveIDsToStorage(locale, ids);
        }
      }
    }
  } catch (error) {
    console.error('[Badge] Errore nel parsing della risposta:', error);
  }
}

// =============================================================================
// DOM MANIPULATION
// =============================================================================

/**
 * Estrae l'ID della serie dall'URL.
 * Es: "/it/series/GP5HJ84P7/gachiakuta" -> "GP5HJ84P7"
 */
function extractSeriesID(url) {
  try {
    const match = url.match(/\/series\/([A-Z0-9]+)/i);
    return match ? match[1] : null;
  } catch (error) {
    return null;
  }
}

/**
 * Scansiona i nodi del DOM e aggiunge i badge agli item con audio italiano.
 */
async function scanAndApplyBadges(nodeList) {
  const allIDsByLanguage = await getAllStoredIDs();

  // Se non ci sono ID salvati, skip
  const hasAnyIDs = Object.values(allIDsByLanguage).some(ids => ids.length > 0);
  if (!hasAnyIDs) return;

  // Traccia quali series abbiamo già processato in questo scan
  const processedSeriesInThisScan = new Set();

  nodeList.forEach(node => {
    // Assicurati che sia un elemento HTML
    if (node.nodeType !== Node.ELEMENT_NODE) return;

    // Trova tutti i link alle serie all'interno del nodo
    const seriesLinks = node.querySelectorAll('a[href*="/series/"]');

    seriesLinks.forEach(link => {
      const seriesID = extractSeriesID(link.href);

      if (seriesID && !processedSeriesInThisScan.has(seriesID)) {
        // Trova quali lingue sono disponibili per questa serie
        const availableLanguages = [];
        for (const [locale, ids] of Object.entries(allIDsByLanguage)) {
          if (ids.includes(seriesID)) {
            availableLanguages.push(locale);
          }
        }

        // Se ha almeno una lingua configurata disponibile
        if (availableLanguages.length > 0) {
          // Cerca solo link del poster (evita title link e altri duplicati)
          if (link.classList.contains('browse-card__poster-wrapper--pU-AW') ||
              link.classList.contains('browse-card-hover__poster-wrapper--Yf-IK')) {

            // Verifica che non ci sia già un badge nel parent container
            const container = link.closest('[class*="browse-card"]');
            if (container && !container.querySelector('.lang-badge')) {
              addBadgesToElement(link, availableLanguages);
              processedSeriesInThisScan.add(seriesID);
            }
          }
        }
      }
    });
  });
}

/**
 * Crea e aggiunge badge multipli per le lingue disponibili.
 */
function addBadgesToElement(element, availableLanguages) {
  try {
    // Trova il contenitore appropriato per i badge
    const container = element.querySelector('figure') ||
                     element.querySelector('div[class*="image"]') ||
                     element.querySelector('.content-image__figure--7vume') ||
                     element;

    // Assicurati che il contenitore abbia position: relative
    const computedStyle = window.getComputedStyle(container);
    if (computedStyle.position === 'static') {
      container.style.position = 'relative';
    }

    // Crea un contenitore per tutti i badge
    const badgesContainer = document.createElement('div');
    badgesContainer.className = 'lang-badges-container';

    // Crea un badge per ogni lingua disponibile
    availableLanguages.forEach((locale, index) => {
      const config = LANGUAGE_CONFIG[locale];
      if (!config) return;

      const badge = document.createElement('div');
      badge.className = 'lang-badge';
      badge.setAttribute('data-locale', locale);
      badge.style.background = config.color;
      badge.style.top = `${8 + (index * 28)}px`; // Stacca verticalmente i badge

      // Icona bandiera
      const flag = document.createElement('span');
      flag.className = 'lang-badge__flag';
      flag.textContent = config.flag;

      // Testo del badge
      const text = document.createElement('span');
      text.className = 'lang-badge__text';
      text.textContent = config.label;

      badge.appendChild(flag);
      badge.appendChild(text);
      badgesContainer.appendChild(badge);
    });

    container.appendChild(badgesContainer);
  } catch (error) {
    console.error('[Badge] Errore nell\'aggiungere i badge:', error);
  }
}

// =============================================================================
// MUTATION OBSERVER
// =============================================================================

/**
 * MutationObserver per rilevare contenuti caricati dinamicamente.
 * Crunchyroll usa un'architettura SPA, quindi i contenuti vengono aggiunti
 * al DOM in modo dinamico senza reload della pagina.
 */
const observer = new MutationObserver((mutationsList) => {
  for (const mutation of mutationsList) {
    if (mutation.type === 'childList' && mutation.addedNodes.length > 0) {
      // Applica i badge ai nuovi elementi
      scanAndApplyBadges(mutation.addedNodes);
    }
  }
});

// =============================================================================
// INITIALIZATION
// =============================================================================

/**
 * Inizializza l'estensione quando il DOM è pronto.
 */
function init() {
  console.log('[ITA Badge] Inizializzazione estensione...');

  // Esegui uno scan iniziale della pagina
  if (document.body) {
    scanAndApplyBadges([document.body]);

    // Avvia l'observer per monitorare i cambiamenti
    observer.observe(document.body, {
      childList: true,
      subtree: true
    });

    console.log('[ITA Badge] Estensione inizializzata con successo');
  } else {
    // Se il body non è ancora pronto, riprova
    setTimeout(init, 100);
  }
}

// Avvia l'inizializzazione
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', init);
} else {
  init();
}
