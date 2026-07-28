# AniWorldAndroid

Benutzerfreundlicher Android-Port des `aniworld-cli`-Workflows mit Kotlin, Jetpack Compose, Material 3, Room und AndroidX Media3/ExoPlayer.

## Version

`1.4.0-direct-media-detector`


## App-Icon und Sprachen

- Launcher-Icon für alle klassischen Android-Dichten
- adaptives Icon und Round Icon ab Android 8
- monochromes Themed Icon ab Android 13
- Icon-Gestaltung auf Basis des aktuellen AniWorld-Wordmarks
- sämtliche sichtbaren App-Texte als Android-Stringressourcen
- Englisch als Standardsprache in `values/` und `values-en/`
- Deutsch in `values-de/`
- Android-Sprachauswahl über `locales_config.xml` für Deutsch und Englisch

## Oberfläche

- Streaming-Startseite im Netflix-/Prime-Stil
- Hero-Bereich und horizontale Inhaltsreihen
- „Beliebt bei AniWorld“
- „Die 50 neuesten Episoden“
- „Neue Animes“
- „Derzeit beliebt“
- „Das sehen andere AniWorld Nutzer“
- persönliche „Weiterschauen“-Reihe
- reduzierte Navigation ohne obere App-Leiste; schwebende Bottom Bar auf Smartphones und Navigation Rail auf größeren Displays
- Material-3-Dark-Mode, optional dynamische Android-Farben
- Pull-to-refresh, Skeleton-Ladezustände, leere Zustände und verständliche Fehlermeldungen
- Cover, Anime-/Film-/Folgentitel, Genres, Veröffentlichungsjahr, Altersangabe, Beschreibungen und Fortschrittsanzeigen
- Katalogsuche mit gespeicherten Suchbegriffen, A–Z- und Genre-Filter
- lokale Katalogseiten mit genau 15 Einträgen pro Seite

## Bibliothek und Verlauf

- Favoriten per Herz
- Favoritenliste mit alphabetischer, zeitlicher oder eigener Sortierung
- lokaler Wiedergabe- und Animeverlauf
- angefangen/gesehen-Markierungen für Folgen und Filme
- Staffelstatus und Fortschrittsanzeige
- Filter für alle, begonnene, ungesehene und gesehene Inhalte
- zuletzt verwendete Suchbegriffe
- gespeicherte Katalogsuchbegriffe mit verzögerter Speicherung
- Wiederherstellung der zuletzt geöffneten Serie und Staffel nach einem Prozess-Neustart

Die Nutzerdaten werden ausschließlich lokal in einer Room-Datenbank gespeichert. Android-Backup, Geräteübertragung, Cloud-Backup sowie Export/Import sind nicht enthalten; `android:allowBackup` ist deaktiviert.

## Offline und Performance

- Startseite und vollständiger alphabetischer Katalog werden beim ersten Start im Hintergrund vorgeladen
- lokaler Room-Cache für Startseite, Katalog und Anime-Metadaten
- Coil-Disk-Cache und Hintergrund-Prefetch für bereits ermittelte Anime-Cover
- zeitlich begrenzte Cache-Einträge
- veraltete Cache-Kopie als Offline-Fallback bei Netzwerkfehlern
- begrenzte parallele Katalog- und Metadatenanfragen
- automatische Wiederholungsversuche mit kurzem Backoff
- Metadaten werden in Favoriten, Verlauf und Weiterschauen nachgeführt

## AniWorld-Workflow

- Suche über `/ajax/search`
- Serien-, Staffel-, Film- und Episodennavigation
- Startseiten- und Katalogparser
- Sprach- und Hosterprioritäten
- gruppierte Hoster-Auswahl nach Sprache, direkter bevorzugter Hoster und interner Medien-Detector
- Hoster-Fallback und optionale Stream-Erreichbarkeitsprüfung
- Resolver-Diagnose
- manuell bedienbare WebView für eine eventuell erforderliche CAPTCHA-/Cloudflare-Verifizierung
- gemeinsamer Cookie-Speicher für WebView und OkHttp
- Fortsetzen der unterbrochenen Aktion nach erfolgreicher manueller Verifizierung
- generische Erkennung direkt angeforderter HLS-, DASH-, SmoothStreaming- und progressiver Medien-URLs in der manuellen Hoster-WebView
- Übergabe von Referer, User-Agent und vorhandenen Session-Cookies an Media3

Die App löst Web-Challenges nicht automatisch und umgeht keine Schutzprüfung. Der Nutzer führt eine eventuell angezeigte Prüfung selbst durch. Der Medien-Detector verarbeitet ausschließlich bereits sichtbare HTTP(S)-Anfragen mit einem eindeutig Media3-kompatiblen Format. Er entschlüsselt keine Skripte, rekonstruiert keine Tokens und umgeht keine Zugriffskontrollen.

## Player

Die Wiedergabe läuft direkt in der App über Media3/ExoPlayer. Der Player ist bewusst reduziert:

- Tippen: Pause oder Weiter
- vertikale Geste links: Bildschirmhelligkeit
- vertikale Geste rechts: Medienlautstärke
- HLS-, DASH-, SmoothStreaming- und progressive Media3-Wiedergabe
- Schließen und lokales Speichern des Fortschritts
- vorherige/nächste Folge innerhalb der geöffneten Staffel
- Rückkehr aus dem Player in die zuletzt geöffnete Staffel- oder Filmliste

Es gibt keine Qualitäts-, Spur-, Geschwindigkeits-, Chromecast-, Picture-in-Picture- oder Auto-Weiter-Menüs.

## Navigation

Die App verwendet Navigation Compose für Start, Katalog, Favoriten, Verlauf und Details. Die separate Suchseite wurde entfernt; Suche und Filter befinden sich direkt im Katalog. Ein lokaler Deep Link kann eine Serie öffnen:

```text
aniworldapp://anime/<slug>
```

## Projektstruktur

```text
AniWorldAndroid/
├── app/
│   ├── schemas/          # Room-Schemata beim lokalen Build
│   └── src/main/         # Compose-UI, Datenbank, Parser, Resolver und Player
├── gradle/               # Version Catalog und Wrapper
├── THIRD_PARTY_NOTICES/
├── build.gradle.kts
├── settings.gradle.kts
├── gradlew
└── gradlew.bat
```

Es sind kein mpv-Modul, keine CI-Konfiguration, keine Testmodule und keine Dateien mit `.sh`-Endung enthalten.

## Build

Voraussetzungen:

- Android Studio
- JDK 17
- Android SDK 36
- Internetzugriff beim ersten Gradle-Sync

Linux/macOS:

```text
./gradlew assembleDebug
```

Windows:

```text
gradlew.bat assembleDebug
```

Release-Build:

```text
./gradlew assembleRelease
```

Für eine eigene Veröffentlichung muss lokal ein persönlicher Signing-Key in Android Studio konfiguriert werden. Private Signaturschlüssel sind absichtlich nicht im Projekt enthalten.

## Build-Konfiguration

- Android Gradle Plugin `8.13.2`
- Gradle `8.14.3`
- Kotlin `2.2.21`
- Room `2.8.4`
- Navigation Compose `2.9.8`
- Media3 `1.10.1`
- `compileSdk` / `targetSdk` 36
- `minSdk` 26

## Rechtliches

Dieses Projekt hostet keine Medien. Verwende es nur für Inhalte und Quellen, für die du berechtigt bist, und beachte die Nutzungsbedingungen der beteiligten Dienste sowie die Lizenz des zugrunde liegenden Projekts.

## Build-Hinweis für RV2IDE / alte Gradle-Caches

Diese Version verwendet Kotlin **2.2.21**. Alle Kotlin-Stdlib-Varianten werden ebenfalls auf `2.2.21` festgesetzt, damit Gradle keine inkompatible `2.4.x`-Metadatenversion auswählt.

Nach dem Ersetzen einer älteren Projektversion einmal **Clean Project** ausführen. Falls RV2IDE weiterhin eine ältere oder neuere Kotlin-Stdlib aus dem Cache benutzt, den Projektordner `.gradle` löschen und anschließend neu synchronisieren. Danach `:app:assembleDebug` erneut starten.

