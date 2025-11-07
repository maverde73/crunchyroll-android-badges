# Prompt per Agente di Ricerca: Integrazione Deep Link Crunchyroll

## Obiettivo

Trovare il metodo corretto per aprire contenuti specifici (serie/episodi) direttamente nell'app Crunchyroll ufficiale su Fire TV/Android TV, partendo dalla nostra app companion.

## Contesto

- Abbiamo un'app Android che mostra Crunchyroll in WebView con badge delle lingue
- Quando l'utente clicca su una serie, vogliamo aprire quella serie/episodio nell'app Crunchyroll ufficiale
- L'app Crunchyroll è installata su Fire TV Stick (package: `com.crunchyroll.crunchyroid`)
- Abbiamo già verificato che l'app supporta lo scheme `crunchyroll://` ma non abbiamo trovato il formato esatto
- Test eseguiti che NON hanno funzionato:
  - `crunchyroll://series/{SERIES_ID}` - apre l'app ma va alla home
  - `crunchyroll://watch/{SERIES_ID}` - apre l'app ma va alla home
  - `https://www.crunchyroll.com/series/{SERIES_ID}` con package - errore, non risolve

## Informazioni da cercare

### 1. Documentazione ufficiale Crunchyroll

- Esiste documentazione per sviluppatori sui deep link Crunchyroll?
- Quali URI schemes supporta l'app? (crunchyroll://, https://...)
- Formato esatto per aprire una serie: `crunchyroll://series/{ID}` o altro?
- Formato per aprire un episodio specifico: `crunchyroll://watch/{ID}` o `crunchyroll://episode/{ID}`?
- Parametri aggiuntivi necessari (locale, season, episode number)?
- Host o path specifici richiesti?

### 2. Esempi da altre app

- Altre app che si integrano con Crunchyroll (esempio: Discord rich presence, Trakt, MAL, AniList)
- Forum, GitHub issues, o discussioni su Reddit/Stack Overflow su integrazione Crunchyroll
- App simili per altri servizi streaming (Netflix, Prime Video, Disney+) su Fire TV
- Plugin/estensioni browser che lanciano l'app mobile

### 3. Analisi tecnica dell'app

- AndroidManifest.xml dell'app Crunchyroll (se accessibile pubblicamente o tramite APKMirror)
- Activity names per player/dettaglio serie (es: PlayerActivity, SeriesActivity, DetailActivity)
- Intent filters dichiarati nell'app con host, scheme, pathPattern
- Possibili parametri extra per Intent:
  - `EXTRA_SERIES_ID`
  - `EXTRA_EPISODE_ID`
  - `EXTRA_MEDIA_ID`
  - `EXTRA_CONTENT_ID`
  - Altri parametri custom

### 4. Reverse engineering / Community findings

- Tool come `apktool` o `jadx` per decompilare l'APK
- Analisi di file `strings.xml` per trovare URI patterns
- Ricerca nei file di risorse per deep link schemes
- Log di sistema quando si apre contenuto dall'interno dell'app

### 5. Soluzioni alternative

- Se il deep link non funziona, esistono altre API/metodi?
- L'app supporta Android App Links (https://www.crunchyroll.com con auto-verify)?
- Custom Tabs per aprire URL che triggherano l'app?
- Broadcast intents o altri meccanismi IPC?
- Content Provider o Bound Service esposti?

### 6. Specifiche Fire TV / Android TV

- Differenze tra Android mobile e Fire TV per deep linking
- Limitazioni note di Fire TV OS per deep link
- Best practices per Fire TV app integration
- Leanback library requirements
- Amazon specific modifications to Android

## Formato output desiderato

Per ogni soluzione trovata, fornire:

```kotlin
// Esempio di Intent funzionante
val intent = Intent(Intent.ACTION_VIEW).apply {
    data = Uri.parse("crunchyroll://???/{SERIES_ID}")
    setPackage("com.crunchyroll.crunchyroid")
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    // Eventuali extra necessari
    putExtra("EXTRA_SERIES_ID", seriesId)
}
```

Includere:
- URL schemes esatti testati e funzionanti
- Esempi di codice Intent Android completi
- Activity/Component names specifici da targetizzare
- Documentazione o link di riferimento
- Screenshot/proof se possibile
- Workaround se il deep link non è supportato

## Priorità di ricerca

1. **Massima priorità:** Formati deep link ufficiali/documentati da Crunchyroll
2. **Alta priorità:** Esempi verificati da community/forum con proof
3. **Media priorità:** Reverse engineering/analisi tecnica APK
4. **Bassa priorità:** Soluzioni alternative/workaround

## Dove cercare

### Forum e Community

- Reddit: r/Crunchyroll, r/androiddev, r/FireTV, r/androidTV
- Stack Overflow con tag: crunchyroll, android-intent, deep-linking, fire-tv
- XDA Developers forum - sezioni Android Development, Fire TV

### Codice open source

- GitHub: ricerca per "crunchyroll" + "intent" / "deep link" / "android"
- GitLab, Bitbucket progetti simili
- Awesome lists per Android TV apps

### Risorse tecniche

- APKMirror / APKPure per scaricare APK e analizzare
- Android Developer documentation su Custom URI Schemes
- Fire TV Developer documentation
- Crunchyroll developer portal (se esiste)

### Tool di analisi

- `adb shell dumpsys package com.crunchyroll.crunchyroid` (già eseguito)
- `apktool d crunchyroll.apk` per decompilare
- `jadx-gui` per vedere codice sorgente
- Frida / Xposed per hooking runtime

## Informazioni già disponibili

Da `adb shell dumpsys package`:
```
Action: "android.intent.action.VIEW"
Category: "android.intent.category.DEFAULT"
Scheme: "crunchyroll"
```

Package name: `com.crunchyroll.crunchyroid`

Activity trovate:
- `com.crunchyroll.crunchyroid/.main.ui.MainActivity`
- `com.crunchyroll.crunchyroid/.player.ui.PlayerActivity`
- `com.crunchyroll.crunchyroid/.startup.ui.StartupActivity`
- `com.crunchyroll.crunchyroid/.splash.ui.SplashActivity`

## Risultato atteso

Una soluzione funzionante che permetta di:

```kotlin
// Aprire una serie specifica
launchSeries("GY8VM8MWY") // One Piece

// Aprire un episodio specifico (ideale)
launchEpisode("G14U41KE4") // One Piece Episode 1
```

E che l'app Crunchyroll:
1. Si apra
2. Navighi automaticamente alla serie/episodio
3. Sia pronta per la riproduzione

## Note aggiuntive

- Se non esiste un modo per deep link diretto, cercare workaround creativi
- Considerare se è possibile usare automazione (AccessibilityService) come ultima risorsa
- Verificare se esistono API Crunchyroll non ufficiali che potrebbero aiutare
- Controllare se app terze (es: Kodi addon) hanno risolto questo problema
