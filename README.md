<div align="center">

# 🌸 AniWorld Android

### Anime · Filme · Folgen — deine Bibliothek und Wiedergabe in einer Android-App

[![Android build](https://github.com/lootdev78/aniworld-android/actions/workflows/android-debug.yml/badge.svg?branch=main)](https://github.com/lootdev78/aniworld-android/actions/workflows/android-debug.yml)
[![License](https://img.shields.io/badge/license-GPL--3.0--or--later-e84b63)](LICENSE)
[![Android 8+](https://img.shields.io/badge/Android-8.0%2B-3ddc84)](#requirements-anforderungen)

**A community-built, native Android client inspired by the AniWorld browsing experience.**

**Ein unabhängiger, nativer Android-Client im Stil des AniWorld-Browsererlebnisses.**

</div>

> **Unofficial / Inoffiziell:** This project is not affiliated with or endorsed by AniWorld. /
> Dieses Projekt ist nicht mit AniWorld verbunden und wird nicht von AniWorld unterstützt.

---

## English

AniWorld Android is a Kotlin app built with Jetpack Compose and AndroidX Media3. It brings anime discovery, a locally stored library, episode playback, and optional casting into a mobile interface with a dark, red-accented visual style.

### Features

- **Discover:** live home feed, anime news, searchable catalog, genres, A–Z navigation, seasons, movies, and episodes.
- **Personal library:** favorites and viewing progress stored locally; grid, compact, and detailed views; sorting, search, and resume position.
- **Offline and backups:** import/export catalog metadata, import a library backup, and separately back up or restore app settings. Restore behavior is shown before applying a backup.
- **Appearance:** dark/light theme support, Android dynamic colors, preset accent colors, a custom color picker, and draggable home-section ordering.
- **Playback:** native Media3 player, persistent progress, language/hoster selection, next-episode countdown, picture-in-picture, and controls for brightness and volume.
- **AniSkip:** OP/ED and other community skip ranges, automatic skipping, and manual MAL mapping. Automatic mapping is allowed only when an AniWorld title equals a title returned by MyAnimeList or AllAnime, ignoring letter case only; punctuation, accents, and wording are not normalized. Choose a result manually when there is no unique exact match.
- **Casting:** Google Cast, Samsung Smart View, DLNA/UPnP (including Xbox), FCast, and the Android system entry point for Miracast.
- **Anime shortcuts:** pin a shortcut to an anime from its detail page or from the favorite list's long-press actions.
- **News:** articles open in an in-app WebView with normal page scrolling, touch gestures, and zoom.

The app does not automatically bypass site challenges. If a site asks for verification, the user must complete it.

### Requirements & Anforderungen

- Android 8.0 (API 26) or later.
- App version: `1.8.1` (`versionCode` 68).
- Build environment: JDK 17 and Android SDK 36.
- Package: `io.github.lootdev78.aniworld`.

### Build, runner status & releases

The **Android Production-Debug Candidate** workflow uses a GitHub-hosted Ubuntu runner, installs JDK 17 and Android SDK 36, runs unit tests, then assembles a clean debug APK. It is configured for pushes and pull requests targeting `main`, plus manual `workflow_dispatch`; feature-branch pushes alone do not trigger it. Successful runs upload the APK and SHA-256 file as an Actions artifact for 14 days.

[Open workflow and runner status](https://github.com/lootdev78/aniworld-android/actions/workflows/android-debug.yml) · [Open Releases](https://github.com/lootdev78/aniworld-android/releases)

There is currently no published GitHub Release. An Actions artifact is a build result, not a signed store release. The repository has no checked-in changelog; its current workflow does not generate changelog files or release notes.

To build locally:

```shell
./gradlew :app:assembleDebug
```

On Windows, run `.\gradlew.bat :app:assembleDebug`.

### Privacy and attribution

Favorites, playback progress, and preferences are stored on-device. Online features contact the respective sites and APIs. See [`LICENSE`](LICENSE) for the application license, project references, direct dependency notices, trademark note, and the unresolved terms for the bundled Samsung Smart View AAR.

## Deutsch

AniWorld Android ist eine Kotlin-App mit Jetpack Compose und AndroidX Media3. Anime-Entdeckung, eine lokal gespeicherte Bibliothek, Folgenwiedergabe und optionales Casting sind in einer mobilen Oberfläche mit dunklem, rot akzentuiertem Erscheinungsbild vereint.

### Funktionen

- **Entdecken:** Live-Startseite, Anime-News, durchsuchbarer Katalog, Genres, A–Z-Navigation, Staffeln, Filme und Folgen.
- **Persönliche Bibliothek:** lokal gespeicherte Favoriten und Wiedergabefortschritte; Raster-, Kompakt- und Detailansicht; Sortierung, Suche und Fortsetzen der Wiedergabe.
- **Offline und Sicherungen:** Katalog-Metadaten importieren/exportieren, Bibliothekssicherung importieren sowie App-Einstellungen separat sichern und wiederherstellen. Vor dem Wiederherstellen wird die Auswirkung angezeigt.
- **Darstellung:** helle/dunkle Themes, dynamische Android-Farben, Akzentfarbpaletten, eigener Farbwähler und per Drag-and-drop sortierbare Startseitenbereiche.
- **Wiedergabe:** nativer Media3-Player, gespeicherter Fortschritt, Sprach-/Hosterwahl, Countdown zur nächsten Folge, Bild-in-Bild sowie Helligkeits- und Lautstärkesteuerung.
- **AniSkip:** Community-Zeitbereiche für OP/ED und weitere Segmente, automatisches Überspringen und manuelle MAL-Zuordnung. Automatisch wird nur zugeordnet, wenn ein AniWorld-Titel mit einem von MyAnimeList oder AllAnime gelieferten Titel übereinstimmt; Groß-/Kleinschreibung wird ignoriert, Satzzeichen, Akzente und Wortlaut bleiben unverändert. Ohne eindeutigen exakten Treffer ist eine manuelle Auswahl möglich.
- **Casting:** Google Cast, Samsung Smart View, DLNA/UPnP (einschließlich Xbox), FCast und der Android-Systemeinstieg für Miracast.
- **Anime-Verknüpfungen:** Launcher-Shortcut zur Serie über die Detailseite oder das Langdruckmenü der Favoritenliste hinzufügen.
- **News:** Artikel öffnen in einer integrierten WebView mit normalem Scrollen, Touch-Gesten und Zoom.

Die App umgeht Website-Prüfungen nicht automatisch. Eine gegebenenfalls angezeigte Prüfung muss der Nutzer selbst abschließen.

### Anforderungen

- Android 8.0 (API 26) oder neuer.
- App-Version: `1.8.1` (`versionCode` 68).
- Build-Umgebung: JDK 17 und Android SDK 36.
- Paket: `io.github.lootdev78.aniworld`.

### Build, Runner-Status und Releases

Der Workflow **Android Production-Debug Candidate** nutzt einen von GitHub gehosteten Ubuntu-Runner, installiert JDK 17 und Android SDK 36, führt Unit-Tests aus und erstellt anschließend ein sauberes Debug-APK. Auslöser sind Pushes und Pull Requests nach `main` sowie `workflow_dispatch`; Pushes auf Feature-Branches allein starten den Workflow nicht. Erfolgreiche Läufe stellen APK und SHA-256-Datei 14 Tage als Actions-Artefakt bereit.

[Workflow- und Runner-Status öffnen](https://github.com/lootdev78/aniworld-android/actions/workflows/android-debug.yml) · [Releases öffnen](https://github.com/lootdev78/aniworld-android/releases)

Aktuell ist kein GitHub-Release veröffentlicht. Ein Actions-Artefakt ist ein Build-Ergebnis und kein signiertes Store-Release. Im Repository liegt keine Changelog-Datei; der derzeitige Workflow erzeugt weder Changelog-Dateien noch Release Notes.

Lokal bauen:

```shell
./gradlew :app:assembleDebug
```

Unter Windows: `.\gradlew.bat :app:assembleDebug`.

### Datenschutz und Hinweise

Favoriten, Wiedergabefortschritt und Einstellungen werden lokal auf dem Gerät gespeichert. Online-Funktionen greifen auf die jeweiligen Websites und APIs zu. [`LICENSE`](LICENSE) enthält die Projektlizenz, Referenzen auf Originalprojekte, Hinweise zu direkten Abhängigkeiten, den Markenhinweis und den noch ungeklärten Lizenzstatus der gebündelten Samsung-Smart-View-AAR.
