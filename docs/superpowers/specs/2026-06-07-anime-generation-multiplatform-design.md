# Design — Catalogo multi-piattaforma: Anime Generation (Prime Video) accanto a Crunchyroll

- **Data:** 2026-06-07
- **Stato:** Approvato (brainstorming) — in attesa di review della spec prima del piano di implementazione
- **Branch:** `v3.0-native-api-catalog`
- **Topic:** estendere l'app Fire TV per mostrare anche il catalogo del canale italiano "Anime Generation" (Amazon Prime Video), in una lista unica con badge di piattaforma e filtro.

---

## 1. Contesto e obiettivo

L'app oggi (v3.0) mostra il catalogo anime di **Crunchyroll** con overlay di badge lingua, su Fire TV / Android TV con navigazione D-pad. Il catalogo CR è ottenuto **on-device, login-free**: `WebViewScraper` carica la pagina pubblica `crunchyroll.com/videos/new` in una WebView headless, intercetta l'API `/content/v2/discover/browse`, e `ApiClient` (OkHttp) la pagina; i dati finiscono in un DB Room normalizzato (9 tabelle).

**Obiettivo:** aggiungere il catalogo **Anime Generation** (canale in abbonamento su Prime Video, distribuito da Anime Factory/Yamato Video) con **parità completa di metadati** rispetto a Crunchyroll (descrizione, copertine, lingue audio/sottotitoli, rating, generi, anno), presentandolo in una **lista unica** dove ogni titolo mostra con un **badge** su quale/i piattaforma/e è disponibile, più un **filtro per piattaforma**.

**Vincolo accettato dall'utente:** una lista di titoli Anime Generation **curata e aggiornata periodicamente** va bene, e i metadati ricchi possono essere **arricchiti da fonti esterne**. **Nessun login utente.**

---

## 2. Decisioni chiave

| Tema | Decisione |
|------|-----------|
| Ricchezza dati AG | Parità completa di metadati con Crunchyroll |
| Acquisizione | Lista curata + arricchimento esterno, login-free |
| **Dove gira la pipeline** | **Offline → file JSON** pubblicato a un URL, in versione **auto-update** |
| **Scheduler** | **GitHub Actions** (cron, gratuito); piano B: PC/Raspberry con IP residenziale |
| Aggiornamento on-device | **WorkManager** (job periodico ~12h) + fetch all'avvio |
| **Matching CR↔AG** | Fatto **offline** dalla pipeline; l'app fa solo lookup deterministica per id |
| **Layout UI** | **Lista unica + badge piattaforma + filtro piattaforma** |
| Schema DB | **Migration Room esplicita v3→v4** (no wipe distruttivo) |

### Fonti dati (validate da deep research, 2026-06-06)

- **Lista titoli AG:** JustWatch — "Anime Generation Amazon Channel" è un provider distinto nel catalogo IT (~279 titoli), interrogabile via la libreria non ufficiale `simple-justwatch-python-api` (GraphQL). Storefront Prime Video pubblico come fallback/verifica.
- **Localizzazione IT (descrizione/poster/anno):** TMDB (`language=it-IT` o endpoint `/translations`).
- **Generi / rating / copertura catalogo classico:** Jikan (MyAnimeList, gratis, no key, MIT — sinossi solo in inglese) e/o AniList.
- **Lingue audio/sub per-offerta:** JustWatch è l'**unica** fonte strutturata (`audio_languages`/`subtitle_languages`); TMDB non le espone. Fallback: assunzione "dub ITA + sub ITA".
- **Matching ID cross-database:** dataset `Fribb/anime-lists` (`anime-list-full.json`) collega `mal_id ↔ anilist_id ↔ kitsu_id ↔ themoviedb_id`.

---

## 3. Architettura generale

Due metà nettamente separate che comunicano **solo** tramite un file JSON pubblicato a un URL.

```
┌─ OFFLINE (GitHub Actions, cron ~ogni 6h) ───────────────────┐
│  pipeline Python:                                            │
│    1. CR catalog (API pubblica browse)  ─┐                   │
│    2. AG catalog (JustWatch provider)    ├─ MATCHING ─ ENRICH │
│    3. mapping ID (Fribb/anime-lists)     ┘  (fuzzy+ID)  (TMDB/│
│                                                        Jikan) │
│    └─→ valida → pubblica catalog_anime_generation.json @ URL  │
└───────────────────────────────────────────────────────────────┘
                              │  HTTPS GET
┌─ ON-DEVICE (Fire TV) ───────┼─────────────────────────────────┐
│  CR scraper (invariato)     │   AG sync (NUOVO, WorkManager)   │
│         ↓                    ↓                                 │
│      Room DB  ←── merge per piattaforma (lookup per id) ──     │
│         ↓                                                      │
│      UI: lista unica + badge CR/AG + filtro piattaforma        │
└─────────────────────────────────────────────────────────────────┘
```

**Principio cardine:** tutta la complessità fragile (JustWatch non ufficiale, fuzzy matching, arricchimento multi-fonte) vive **offline**. L'app resta semplice: scarica un JSON e lo fonde nel DB. Se una fonte cambia, si corregge lo script — **l'app non si rompe** e non richiede aggiornamento.

---

## 4. Pipeline offline

Repository/cartella Python separata (può vivere in `tools/anime_generation_pipeline/` nello stesso repo, o in un repo dedicato), eseguita da un workflow GitHub Actions schedulato.

### Stadi

1. **Catalogo AG** — via `simple-justwatch-python-api`, filtrando il provider "Anime Generation Amazon Channel" (regione IT). Per ogni titolo si raccolgono: nome, anno, `audio_languages`, `subtitle_languages`, deep-link Prime Video. Storefront Prime Video pubblico come verifica/fallback.
2. **Catalogo CR** — la pipeline scarica anche il catalogo Crunchyroll dalla stessa API pubblica usata dall'app (login-free), per poter fare il matching.
3. **Matching** — per ogni titolo AG: fuzzy match (titolo normalizzato + anno, es. RapidFuzz) come **aggancio iniziale**, con soglie conservative (score alto + anno coerente); poi join deterministici via `Fribb/anime-lists` per ottenere `mal_id`/`anilist_id`/`tmdb_id`. Se il titolo AG corrisponde a un titolo CR → si registra `matched_crunchyroll_id`; altrimenti `null`.
4. **Arricchimento** — TMDB (`it-IT`) per descrizione/poster/backdrop/anno italiani; Jikan/AniList per generi, rating e copertura dei classici. Fallback poster a Jikan/AniList se TMDB manca (frequente sul back-catalog classico).
5. **Validazione & pubblicazione** — si valida l'output (schema corretto + **soglia minima di titoli** per non pubblicare un catalogo vuoto/rotto); solo se valido si **sovrascrive** il JSON pubblicato. In caso di errore, **l'ultimo JSON buono resta** in linea.

### Scheduler

- **Primario:** GitHub Actions, `on: schedule` (es. `cron: "0 */6 * * *"`). Gratuito (illimitato su repo pubblici; ~2.000 min/mese su privati — il consumo qui è di pochi minuti per run). Pubblica il JSON come GitHub Pages / Release asset / file raw del repo.
- **Avvertenze note:** il cron GHA non è preciso al minuto; i workflow schedulati vengono disattivati dopo 60 giorni di inattività del repo (auto-risolto dai commit periodici del JSON).
- **Piano B:** se JustWatch rate-limita/blocca gli IP dei runner cloud, spostare lo scheduler su PC/Raspberry/VPS con IP residenziale (stesso script, cron locale).

---

## 5. Contratto JSON (interfaccia pipeline ↔ app)

Unico punto di accoppiamento tra le due metà. Schema:

```json
{
  "version": 1,
  "generated_at": "2026-06-07T12:00:00Z",
  "titles": [
    {
      "ag_id": "string",
      "title": "Lamù",
      "year": 1981,
      "matched_crunchyroll_id": null,
      "external_ids": { "mal_id": 1, "anilist_id": 290, "tmdb_id": 26209 },
      "description_it": "string",
      "poster_tall": "https://…",
      "poster_wide": "https://…",
      "genres": ["string"],
      "rating": 7.8,
      "audio_locales": ["it-IT", "ja-JP"],
      "subtitle_locales": ["it-IT"],
      "languages_assumed": false,
      "deep_link_url": "https://www.primevideo.com/…"
    }
  ]
}
```

- `matched_crunchyroll_id`: id CR se la pipeline ha trovato corrispondenza, altrimenti `null`.
- `languages_assumed`: `true` se `audio_locales`/`subtitle_locales` derivano dal fallback "ITA" anziché da dati JustWatch reali.
- `version` / `generated_at`: usati dall'app per saltare il re-processing se il JSON non è cambiato (anche via ETag/Last-Modified HTTP).

---

## 6. Modello dati (Room)

Nessuna modifica alle 9 tabelle esistenti nella loro struttura corrente; si **aggiunge**:

### Nuova tabella `series_platforms` (1 `series` → N piattaforme)

| Colonna | Tipo | Note |
|---------|------|------|
| `series_id` | String (FK → `series.id`) | |
| `platform` | String | `"crunchyroll"` \| `"anime_generation"` |
| `deep_link_url` | String? | URL per "apri nell'app" |
| `audio_locales` | (lista) | lingue audio per-piattaforma |
| `subtitle_locales` | (lista) | lingue sottotitoli per-piattaforma |
| `languages_assumed` | Boolean | per AG: true se lingue da fallback |

Chiave primaria composta `(series_id, platform)`. Un titolo su entrambe le piattaforme ha 2 righe qui → 2 badge.

### Nuove colonne su `series` (matching)

- `mal_id` (Int?), `anilist_id` (Int?), `tmdb_id` (Int?) — nullable.

### Sorgente dei record

- Titoli "solo AG" → normali `Series` popolate dai metadati arricchiti, con una riga `series_platforms` (`anime_generation`).
- Sync CR → continua a popolare `series` come oggi, ma ora inserisce anche/garantisce la riga `series_platforms` (`crunchyroll`) per ogni titolo.

### Migration

- **Migration Room esplicita v3→v4** che crea `series_platforms`, aggiunge le colonne id esterni e **retro-popola** una riga `crunchyroll` in `series_platforms` per ogni `series` esistente. Niente `fallbackToDestructiveMigration` per questo step (evita il wipe del catalogo locale segnalato in `AGENTS.md`).

---

## 7. Lato app

### 7.1 Sync Anime Generation (`AnimeGenerationSyncWorker`)

- `CoroutineWorker` schedulato via **WorkManager**: job periodico ~12h con vincolo "rete disponibile", **più** un fetch all'avvio (dopo il sync CR, orchestrato in `SplashViewModel`).
- Flusso: `GET` JSON dall'URL (OkHttp) → parse (kotlinx.serialization) → upsert nel DB. Salta il lavoro se `version`/ETag invariati.

### 7.2 Ingestion / merge

Per ogni titolo del JSON:
- `matched_crunchyroll_id` presente **e** già nel DB → aggiungi solo la riga `series_platforms` (`anime_generation`) a quella `series`; eventualmente retro-compila gli id esterni.
- altrimenti → upsert di una nuova `Series` "solo AG" dai metadati arricchiti + riga `series_platforms`.

**Rimozioni:** titolo sparito da AG → rimuovi la sua riga `anime_generation`; se la `series` resta senza alcuna piattaforma (era solo-AG) → rimuovi la `series`. **Mai** toccare i dati CR.

**Drift CR/AG:** se `matched_crunchyroll_id` non è (ancora) nel DB locale, tratta il titolo come solo-AG per ora; si fonderà al sync successivo quando il catalogo CR sarà allineato.

### 7.3 UI e filtro

- **Card** (`AnimeListAdapter` + `item_anime_card.xml`): badge piattaforma (CR arancione / AG teal) in un angolo, accanto ai badge lingua esistenti. Focus D-pad invariato.
- **Filtro** (`FilterState` + `FilterBottomSheet` + `FilterPreferences`): nuova dimensione **piattaforma** (Tutte / Crunchyroll / Anime Generation). La query dinamica di `AnimeDao` fa join su `series_platforms`.
- **MainViewModel**: la query carica le piattaforme (per badge) e applica il filtro piattaforma.

### 7.4 Schermata dettaglio (`DetailActivity`)

- Mostra su quali piattaforme è disponibile il titolo.
- `IntentLauncher`: oltre al deep-link Crunchyroll esistente, aggiungi l'azione **"Apri in Prime Video"** (intent `ACTION_VIEW` su `deep_link_url`) per i titoli AG.
- Traduzione Gemini: resta per le descrizioni CR (inglesi); per i titoli AG la descrizione è già IT → i controlli di traduzione si nascondono quando la descrizione è già in italiano.

---

## 8. Gestione errori e rischi

| Rischio | Mitigazione |
|--------|-------------|
| JustWatch (non ufficiale) cambia/si rompe | È offline: retry/backoff; validazione output con soglia minima titoli; **non sovrascrivo** l'ultimo JSON buono in caso di run fallito |
| Runner cloud bloccati da JustWatch | Piano B: scheduler su PC/Raspberry/VPS (IP residenziale) |
| TMDB manca titoli classici | Fallback poster da Jikan/AniList; se zero metadati → card minima (titolo + piattaforma) |
| Lingue AG vuote | Fallback "dub ITA + sub ITA", marcato `languages_assumed` |
| Falsi positivi nel matching | Soglie conservative (score alto + anno); in dubbio resta "solo AG" anziché fondere errato |
| Download JSON fallito on-device | Mantengo i dati esistenti, ritento al ciclo WorkManager successivo |
| JSON malformato | Ignoro l'update, ultimo stato buono resta nel DB |
| Cambio schema | Migration esplicita v3→v4 testata |

---

## 9. Testing

- **Pipeline (Python):** unit test su matching (fuzzy + join ID), fallback arricchimento, validazione schema; golden-file test su fixture ridotta.
- **App (Kotlin/JUnit):**
  - Migration Room v3→v4 (`MigrationTestHelper`).
  - Ingestion/merge: id matchato presente/assente, creazione solo-AG, rimozione, drift CR/AG.
  - Query filtro per piattaforma (test `AnimeDao`).
  - `AnimeGenerationSyncWorker` (WorkManager `TestDriver`): fetch + parse + upsert.
- Esecuzione: `./gradlew test`.

---

## 10. Fuori scope (YAGNI)

- Scraping autenticato di Prime Video / lettura del catalogo personale dell'utente.
- Riproduzione/streaming in-app (l'app indirizza alle app native via deep-link).
- Piattaforme oltre Crunchyroll e Anime Generation (il modello `series_platforms` è però estensibile in futuro).
- Reimplementazione on-device delle API JustWatch/TMDB/Jikan.

---

## 11. Domande aperte / verifiche empiriche da fare in implementazione

1. Match-rate reale del mapping TMDB sul back-catalog classico AG (titoli anni '80-'90): quantificare i buchi sui ~279 titoli per dimensionare il fallback.
2. I campi `audio_languages`/`subtitle_languages` di JustWatch sono effettivamente popolati per il provider AG, o tornano vuoti (→ fallback "ITA" prevalente)?
3. Stabilità della libreria `simple-justwatch-python-api` e tolleranza di JustWatch agli IP dei runner GitHub Actions.
4. Formato e stabilità del `deep_link_url` Prime Video su Fire TV (apertura corretta nell'app Prime Video).

---

## 12. Fonti (deep research 2026-06-06)

- JustWatch provider AG: https://www.justwatch.com/it/provider/anime-generation-amazon-channel
- `simple-justwatch-python-api`: https://github.com/Electronic-Mango/simple-justwatch-python-api · https://pypi.org/project/simple-justwatch-python-api/
- Storefront Prime Video AG: https://www.primevideo.com/-/it/channel/bde40d8f-0116-4b26-8a59-cb79677c9a10
- TMDB translations: https://developer.themoviedb.org/reference/tv-series-translations
- Jikan: https://docs.api.jikan.moe/
- AniList rate limiting: https://docs.anilist.co/guide/rate-limiting
- Mapping ID: https://github.com/Fribb/anime-lists · https://github.com/manami-project/anime-offline-database · https://github.com/nattadasu/animeApi
