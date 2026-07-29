# Feature-Matrix – 1.7.9 Cast-/Hotspot-Erweiterung

Diese Matrix ordnet die konsolidierten Funktionen den maßgeblichen Quellmodulen zu. Sie dient als Schutz gegen versehentlich verlorene Funktionen bei späteren Änderungen.

| Bereich | Enthaltene Funktionen | Hauptquellen |
|---|---|---|
| Startseite | Umschaltbarer Live-/Offline-Feed, unveränderte Anime-News-Karten, isolierter News-WebView, Hero, Favoriten, Weiterschauen, neue Episoden, beliebte/neue/meistgesehene Sammlungen, frei gespeicherte Sichtbarkeit und Reihenfolge aller Bereiche, Vollansichten | `AniWorldRepository.kt`, `UiScreens.kt`, `AppViewModel.kt`, `IsolatedWebPageScreen.kt`, `AppStore.kt` |
| Katalog | Offline-Metadaten, JSON-Import/-Export über Android DocumentProvider, Bestandsprüfung beim Öffnen der Einstellungen, bestätigtes Löschen, WorkManager-Aktualisierung, maximal vier parallele Anime-Detailjobs, Skeleton-/Sperrzustand vor Erstimport, Suche, Genre/A–Z, Endlosliste, Alphabetleiste, drei Ansichten | `CatalogMetadataWorker.kt`, `AniWorldRepository.kt`, `AppDatabase.kt`, `AppStore.kt`, `UiScreens.kt` |
| Cover | DOM-nahe Zuordnung pro Serienlink, Logo-/Placeholderfilter, anime- und URL-spezifische Coil-Schlüssel, Reset/Migration | `AniWorldRepository.kt`, `RepositoryCache.kt`, `AniWorldApplication.kt` |
| Anime/Staffeln/Filme | Live-Detaildaten, Staffelbeschreibung, Film-/Folgenbeschreibung einmalig, Staffel-/Filmnavigation, kompakte Episodenlisten | `AniWorldRepository.kt`, `UiScreens.kt`, `AppViewModel.kt` |
| Favoriten | Startseiten-Reihe, Such-/Sortierleiste nur bei vorhandenen Einträgen, Raster/Kompakt/Detail, Suche/Vorschläge, alphabetisch/aktualisiert/eigene Reihenfolge, vollständige #/A–Z-Leiste, Mehrfachauswahl und bestätigtes Löschen | `UiScreens.kt`, `AppStore.kt`, `AppDatabase.kt` |
| Verlauf | Such-/Filterleiste nur bei vorhandenen Einträgen, begonnen/gesehen/favorisiert, Zeitfortschritt, dieselben Ansichten/Sortierungen und #/A–Z-Leiste wie Favoriten, Mehrfachauswahl, Gesehen-Markierung setzen/entfernen | `UiScreens.kt`, `Models.kt`, `AppStore.kt`, `AppDatabase.kt` |
| Diagnose/Logging | Globaler persistenter Ein-/Aus-Schalter für AppLogger, vollständige Unterdrückung neuer App-Logs bei deaktivierter Diagnose, eigene Vollbild-UI mit Leeren/Kopieren/Teilen | `AppLogger.kt`, `DiagnosticScreen.kt`, `AppViewModel.kt`, `AppStore.kt`, `MainActivity.kt` |
| Resolver | Sprach-/Hosterpriorität, Verfügbarkeitsprüfung, Hoster-Fallback, Referer/User-Agent/Cookies, Diagnose | `AniWorldRepository.kt`, `AppViewModel.kt`, `Models.kt` |
| News-WebView | Gewohnte Startseiten-News-Darstellung; separater normaler WebView ohne Challenge-Cookies, Cloudflare-Verifizierung, Hoster-Retry oder Medienerkennung | `UiScreens.kt`, `IsolatedWebPageScreen.kt`, `MainActivity.kt`, `AppViewModel.kt` |
| Stream-Crawler | Erkennung sichtbarer HLS-, DASH-, SmoothStreaming- und progressiver HTTP(S)-Anfragen nach Seitenabschluss | `MediaDetection.kt`, `ChallengeScreen.kt` |
| WebView-Challenge | Manuelle CAPTCHA-/Turnstile-Prüfung ohne Bypass, Session-/Cookie-Übernahme, erneuter Resolverversuch; getrennt vom News-WebView | `ChallengeSession.kt`, `ChallengeScreen.kt`, `AppViewModel.kt` |
| Adblocker | Werbung/Tracking/Popups/Weiterleitungen, Domain-Ausnahme, einklappbare Panels, standardmäßig deaktiviert | `WebAdBlocker.kt`, `ChallengeScreen.kt`, `AppStore.kt`, `UiScreens.kt` |
| Interner Player | MediaSessionService, Systembenachrichtigung, Timeline-Scrubbing, ±10 s, Doppeltipp, von Anfang, Auto-Hide, Gesten, Auto-Next, vorherige/nächste Folge | `PlaybackService.kt`, `PlayerScreen.kt`, `ExoPlayerComposable.kt` |
| DLNA/UPnP | SSDP über Multicast, aktive IPv4-/Hotspot-Schnittstellen, Broadcast, lokalen /24-Unicast-Scan, Nachbartabelle und manuelle IP; Xbox-Priorisierung, AVTransport, Start an aktueller Position, Play/Pause/Stop/Seek, Trennen und lokal fortsetzen; geschützte Streams über lokalen Header-/Range-/HLS-Relay | `XboxCast.kt`, `LocalCastRelay.kt`, `PlayerScreen.kt`, `PlaybackService.kt` |
| Chromecast | Offizielles Google Cast Framework plus AndroidX MediaRouter 1.8.1, lazy Initialisierung nach Nutzeraktion, MediaRoute-Schaltfläche, Empfänger-IP-Ermittlung, lokaler Stream-Relay, Default Media Receiver, Remote-Laden, Play/Pause/Seek/Stop, Positionsabgleich und lokales Fortsetzen | `AniWorldCastOptionsProvider.kt`, `ChromecastController.kt`, `LocalCastRelay.kt`, `PlayerScreen.kt`, `AndroidManifest.xml`, `app/build.gradle.kts` |
| FCast | Offenes FCast-TCP-Protokoll, Standard-mDNS-Erkennung über Android NSD, MulticastLock, paralleler /24-TCP- und Nachbartabellen-Fallback für WLAN/Handy-Hotspot, manuelle IP, lokaler Stream-Relay, Play/Pause/Resume/Stop/Seek und Zustandsupdates | `FCast.kt`, `LocalCastRelay.kt`, `PlayerScreen.kt` |
| Miracast | Öffnet mit abgesicherten Fallbacks die native Android-/Hersteller-Cast-Systemauswahl; keine vorgetäuschte proprietäre Sender-Implementierung | `ExternalPlayback.kt`, `PlayerScreen.kt` |
| Cast-Stream-Relay | Tokenisierte, LAN-beschränkte HTTP-Weiterleitung auf dem Handy; übernimmt Upstream-Header, Range/If-Range, HLS-Playlist-/Segment-/Key-/Untertitel-Umschreibung und grundlegende DASH-URL-Umschreibung; wählt die zum Empfänger passende WLAN-/Hotspot-Schnittstellenadresse | `LocalCastRelay.kt`, `ChromecastController.kt`, `FCast.kt`, `XboxCast.kt` |
| Player-Auswahl | Sprache und Hoster im Player wechseln, optional externer Player, bevorzugter Hoster automatisch starten | `PlayerScreen.kt`, `ExternalPlayback.kt`, `AppViewModel.kt`, `AppStore.kt` |
| Einstellungen | Neu strukturierte Material-3-Bereiche, Metadatenaktionen Aktualisieren/Löschen/Exportieren/Importieren, Startseitenkonfiguration, Diagnose, Akzentpalette, dynamische Farben, Start-Tab, Live-/Offline-Startseite, Host-/Sprachpriorität, Adblocker, Playeroptionen, Credits | `UiScreens.kt`, `AppStore.kt`, `MainActivity.kt` |
| Persistenz | Room für strukturierte Metadaten/Favoriten/Verlauf/Fortschritt; JSON-Datei für den Offline-Startseitenfeed; DataStore für Einstellungen, Diagnosezustand und Startseitenreihenfolge | `AppDatabase.kt`, `AppStore.kt` |
| Build/Qualität | Paket `io.github.lootdev78.aniworld`, Debug-Suffix, JDK 17, statischer Release-Gate, CI-Build, Lint, APK-Prüfsumme | `app/build.gradle.kts`, `tools/verify_project.py`, `.github/workflows/android-debug.yml` |

## Bewusste Grenzen

- Der lokale Relay übernimmt typische Hoster-Header, Byte-Ranges sowie HLS-/grundlegende DASH-Verweise. DRM, Empfänger-Codecs, proprietäre Receiver-Funktionen oder serverseitige Gerätebindungen kann er nicht umgehen.
- Android-/Hersteller-Hotspots können AP- oder Client-Isolation erzwingen. Die App kombiniert mDNS/SSDP, schnittstellengebundene Suche, Broadcast, Unicast-/Subnetzscan, Nachbartabelle und manuelle IP; eine vom Betriebssystem oder Router blockierte Verbindung kann eine App jedoch nicht erzwingen.
- Chromecast-Geräte werden über das offizielle Google-Cast-/MediaRouter-System angezeigt. Die App kann Empfänger, die Google Play Services oder der System-Router nicht bereitstellt, nicht künstlich hinzufügen.
- Miracast wird über die Android-/Hersteller-Systemoberfläche gestartet; Verfügbarkeit und unterstützte Empfänger hängen vom Gerät und dessen Firmware ab.
- AirPlay und AirServer sind absichtlich nicht implementiert.
- Web-Challenges werden nicht automatisiert umgangen.
- Der native WebView-Filter ist keine 1:1-Ausführung der Browser-Erweiterung uBlock Origin.
- Ein veröffentlichbares Release-APK benötigt einen privaten Signing-Key außerhalb dieses Projekts.
