# Umsetzungsbericht – 1.5.0-complete-integration

## Umgesetzt

### Metadaten und Katalog

- Eindeutiger WorkManager-Hintergrundauftrag für die Katalog-Metadatenaktualisierung.
- Fortschrittsbenachrichtigung mit Abbrechen-Aktion und `dataSync`-Foreground-Service-Typ.
- Kontextbezogene Benachrichtigungsberechtigung ab Android 13.
- Einstellungsaktion „Metadaten aktualisieren“.
- Webseiten-Cache ausschließlich für Katalogseiten; Startseite, Details, Staffeln und Episoden werden live geladen.
- Detail-Metadaten werden nur gespeichert, wenn das betreffende Anime im Katalog vorhanden ist.
- Coverloser Katalog, 15 Einträge pro Seite und anklickbare Seitenliste.
- Kein großer Metadaten-Fortschrittsblock auf der Startseite.

### Coverkorrektur

- Bildkandidaten werden nach Elementkontext, URL, Seitenrolle und Abmessungen bewertet.
- Logo-, Branding-, Header-, Icon-, Tracking-, Placeholder- und unplausible Bilder werden verworfen.
- Coil-Schlüssel enthalten Anime-ID/Slug und Cover-URL.
- Rücksetzen löscht gespeicherte Cover, Katalog-Metadaten, Seiten- und Bildcache.

### Benachrichtigungen und Player

- Media3-`MediaSessionService` mit Android-Medienbenachrichtigung.
- Metadaten für Anime, Folge und Artwork.
- Pause/Wiedergabe sowie Stop, vorherige und nächste Folge.
- Optionale Auto-Next-Einstellung mit achtsekündigem Countdown und Abbrechen-Schaltfläche.
- Reale Position und Dauer werden beim Folgenende gespeichert.
- Seitliche Gestenzonen, Mindestbewegung, Begrenzung und stabilisierte Helligkeits-/Lautstärkeregelung.

### Verschiebbarer Einstellungsbutton

- Drag-and-drop innerhalb des durch Scaffold und Systemleisten verfügbaren Bereichs.
- Normalisierte Position in DataStore.
- Wiederherstellung nach Rotation und Neustart.
- Rücksetzfunktion in den Einstellungen.
- Auch auf der Detailansicht verfügbar.

### WebView und Media-Detector

- Konfigurierbarer nativer Filter für Werbung, Tracking, Popups und Weiterleitungen.
- Ein-/Ausschalter und einzeln aktivierbare Filterkategorien.
- Temporäre Ausnahme für die aktuelle Hoster-Domain.
- Einklappbare Bereiche „Gefundene Medien“ und Session/Filter; Zustand wird gespeichert.
- Requests werden während des Ladens nur gesammelt und erst nach `onPageFinished` ausgewertet.
- AniWorld-Verifizierung und Hoster-WebView sind getrennt; Cloudflare-Logik wird nur für AniWorld verwendet.
- Popupfenster, verdächtige Navigationen und bekannte Werbe-/Tracking-Endpunkte werden blockiert, Media-URLs geschützt.

### Startseite

- Anklickbare Überschriften und nahezu vollflächige Gesamtansichten für alle Sammlungen.
- Beliebt, 50 neueste Episoden, neue Animes, derzeit beliebt, Community und Top 50/Meistgesehen.
- Deduplizierung nach Anime-Slug beziehungsweise Episoden-URL.
- Live-Metadaten werden ohne Übernahme falscher Katalogcover zusammengeführt.

### Anime-, Staffel- und Episodenansicht

- Kompakter Netflix-artiger Header mit zugeschnittenem Hintergrund, Textverlauf und Cover.
- Bessere Anordnung von Titel, Jahr, Altersfreigabe, Genres und Beschreibung.
- Direkte Wiedergabe/Fortsetzen-Aktion sowie Favoriten- und Info-Aktionen.
- Staffel-/Filmtrennung, Staffelchips und Hervorhebung der aktuellen Auswahl.
- Kompakte Episodenzeilen und Filter.
- Scrollpositionen für Detail- und Episodenansicht bleiben beim Playerwechsel erhalten.
- Fehlerhafte oder fehlende Bilder zeigen ein neutrales Film-Symbol statt eines AniWorld-Logos.

### Startdialog

- Keine automatische Berechtigungs-Einführung beim Start.
- Berechtigungen werden kontextbezogen oder bewusst über die Einstellungen verwaltet.

## Technische Grenze

Android WebView kann die Browser-Erweiterung uBlock Origin nicht unverändert als 1:1-Erweiterung ausführen. Implementiert ist deshalb ein nativer, konservativer WebView-Filter mit verwaltbaren Kategorien und Domain-Ausnahme. Das Projekt behauptet ausdrücklich keine vollständige uBlock-Origin-Kompatibilität.

## Prüfung

- XML-Dateien und alle drei Stringressourcen-Sätze wurden geparst und auf Vollständigkeit geprüft.
- Alle `R.string`-Referenzen besitzen deutsche und englische Einträge.
- `git diff --check` ist fehlerfrei.
- Kotlin-Quellen wurden mit dem lokalen Kotlin-Parser auf Syntaxfehler geprüft.
- Ein vollständiger Android-Gradle-Build war in der Arbeitsumgebung nicht möglich, weil Android SDK 36 und heruntergeladene Gradle-/Maven-Abhängigkeiten nicht verfügbar waren und der Netzwerkzugriff des Build-Prozesses blockiert ist. Der endgültige Build sollte daher lokal in Android Studio beziehungsweise mit `./gradlew assembleDebug` geprüft werden.
