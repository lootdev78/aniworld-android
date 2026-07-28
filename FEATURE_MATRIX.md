# Feature-Matrix – 1.7.0 Production-Debug Candidate

Diese Matrix ordnet die konsolidierten Funktionen den maßgeblichen Quellmodulen zu. Sie dient als Schutz gegen versehentlich verlorene Funktionen bei späteren Änderungen.

| Bereich | Enthaltene Funktionen | Hauptquellen |
|---|---|---|
| Startseite | Live-Feed, Anime-News, Hero, neue Episoden, beliebte/neue/meistgesehene Sammlungen, Weiterschauen, Vollansichten | `AniWorldRepository.kt`, `UiScreens.kt`, `AppViewModel.kt` |
| Katalog | Offline-Metadaten, WorkManager-Aktualisierung, Skeleton-/Sperrzustand vor Erstimport, Suche, drei Vorschläge, Löschen-X, Genre/A–Z, Endlosliste, Alphabetleiste, drei Ansichten | `CatalogMetadataWorker.kt`, `AppDatabase.kt`, `AppStore.kt`, `UiScreens.kt` |
| Cover | DOM-nahe Zuordnung pro Serienlink, Logo-/Placeholderfilter, anime- und URL-spezifische Coil-Schlüssel, Reset/Migration | `AniWorldRepository.kt`, `RepositoryCache.kt`, `AniWorldApplication.kt` |
| Anime/Staffeln/Filme | Live-Detaildaten, Staffelbeschreibung, Film-/Folgenbeschreibung einmalig, Staffel-/Filmnavigation, kompakte Episodenlisten | `AniWorldRepository.kt`, `UiScreens.kt`, `AppViewModel.kt` |
| Favoriten | Raster/Kompakt/Detail, Suche/Vorschläge, alphabetisch/aktualisiert/eigene Reihenfolge, Schnellnavigation, Mehrfachauswahl und bestätigtes Löschen | `UiScreens.kt`, `AppStore.kt`, `AppDatabase.kt` |
| Verlauf | Begonnen/gesehen/favorisiert, Zeitfortschritt, dieselben Ansichten/Sortierungen wie Favoriten, Mehrfachauswahl, Gesehen-Markierung setzen/entfernen | `UiScreens.kt`, `Models.kt`, `AppStore.kt`, `AppDatabase.kt` |
| Resolver | Sprach-/Hosterpriorität, Verfügbarkeitsprüfung, Hoster-Fallback, Referer/User-Agent/Cookies, Diagnose | `AniWorldRepository.kt`, `AppViewModel.kt`, `Models.kt` |
| Stream-Crawler | Erkennung sichtbarer HLS-, DASH-, SmoothStreaming- und progressiver HTTP(S)-Anfragen nach Seitenabschluss | `MediaDetection.kt`, `ChallengeScreen.kt` |
| WebView-Challenge | Manuelle CAPTCHA-/Turnstile-Prüfung ohne Bypass, Session-/Cookie-Übernahme, erneuter Resolverversuch | `ChallengeSession.kt`, `ChallengeScreen.kt`, `AppViewModel.kt` |
| Adblocker | Werbung/Tracking/Popups/Weiterleitungen, Domain-Ausnahme, einklappbare Panels, standardmäßig deaktiviert | `WebAdBlocker.kt`, `ChallengeScreen.kt`, `AppStore.kt`, `UiScreens.kt` |
| Interner Player | MediaSessionService, Systembenachrichtigung, Timeline-Scrubbing, ±10 s, Doppeltipp, von Anfang, Auto-Hide, Gesten, Auto-Next, vorherige/nächste Folge | `PlaybackService.kt`, `PlayerScreen.kt`, `ExoPlayerComposable.kt` |
| Player-Auswahl | Sprache und Hoster im Player wechseln, optional externer Player, bevorzugter Hoster automatisch starten | `PlayerScreen.kt`, `ExternalPlayback.kt`, `AppViewModel.kt`, `AppStore.kt` |
| Einstellungen | Material-3-Akzentpalette, dynamische Farben, Start-Tab, Host-/Sprachpriorität, Adblocker, Cache/Metadaten, Playeroptionen, Credits | `UiScreens.kt`, `AppStore.kt`, `MainActivity.kt` |
| Persistenz | Room für strukturierte Metadaten/Favoriten/Verlauf/Fortschritt; DataStore für Einstellungen und Reihenfolgen | `AppDatabase.kt`, `AppStore.kt` |
| Build/Qualität | Paket `io.github.lootdev78.aniworld`, Debug-Suffix, JDK 17, statischer Release-Gate, CI-Build, Lint, APK-Prüfsumme | `app/build.gradle.kts`, `tools/verify_project.py`, `.github/workflows/android-debug.yml` |

## Bewusste Grenzen

- Web-Challenges werden nicht automatisiert umgangen.
- Der native WebView-Filter ist keine 1:1-Ausführung der Browser-Erweiterung uBlock Origin.
- Externe Player können Streams ablehnen, wenn die Ziel-App HTTP-Header oder Cookies nicht übernehmen kann.
- Ein veröffentlichbares Release-APK benötigt einen privaten Signing-Key außerhalb dieses Projekts.
