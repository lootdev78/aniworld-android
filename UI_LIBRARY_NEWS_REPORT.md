# AniWorld Android 1.6.5 – UI, Bibliothek und News

## Version

- `versionCode`: 55
- `versionName`: `1.6.5-ui-library-news`
- Application-ID/Namespace: `io.github.lootdev78.aniworld`

## UI- und Farbkorrekturen

- Der App-Scaffold verwendet nun explizit `background` und `onBackground` des aktiven Material-3-Farbschemas.
- Detail-, Hoster-, Anime-Info-, Sammlungs- und Einstellungsansichten liegen jeweils in einer eigenen `Surface` mit festgelegter Hintergrund- und Inhaltsfarbe.
- Diese Vollbildansichten berücksichtigen Status- und Navigationsleiste über `systemBarsPadding()`.
- Schaltertitel und Untertitel verwenden explizit `onSurface` beziehungsweise `onSurfaceVariant`.
- Das dynamische Dark-Theme übernimmt weiterhin die Akzentfarben des Systems, verwendet für Flächen und Texte aber kontrastfeste App-Farben.

## Player

- Die vorhandene verschiebbare Zeitleiste bleibt erhalten.
- Direkte Sprünge um zehn Sekunden zurück oder vor wurden ergänzt.
- Neue Aktion „Von Anfang abspielen“ setzt Playerposition, UI-Zeitleiste und gespeicherten Fortschritt auf den Anfang.
- Doppeltipp links/rechts für −/+10 Sekunden bleibt aktiv.
- Sprach-, Hoster- und externer Playerwechsel bleiben im Player verfügbar.
- Die Player-Hilfe beschreibt die Doppeltipp-Gesten jetzt vollständig.

## Favoriten und Verlauf

- Sortierung nach eigener Reihenfolge, letzter Änderung und Alphabet bleibt direkt oberhalb der Liste verfügbar.
- Rechte Schnellnavigation:
  - Alphabetisch: vorhandene Anfangsbuchstaben
  - Zuletzt geändert: Neu, 7 Tage, 30 Tage, älter
  - Eigene Reihenfolge: Anfang, Viertel, Mitte, Dreiviertel, Ende
- Lange Berührung aktiviert die Mehrfachauswahl.
- Alle sichtbaren Einträge können gemeinsam ausgewählt werden.
- Mehrfaches Löschen erfordert eine zusätzliche Bestätigung.
- Karten bleiben vollständig anklickbar und öffnen die kanonische Anime-Hauptansicht.

## Offline-Metadaten

- Favoriten-, Verlauf-, Fortschritts- und Watchlist-Aktionen sichern weiterhin strukturierte Serienmetadaten in Room.
- Beim ersten Laden der gespeicherten Einstellungen wird ein einmaliger Backfill ausgeführt:
  - bestehende Favoriten prüfen
  - bestehende Verlaufseinträge prüfen
  - fehlende `series_metadata`-Datensätze ergänzen
- Jahr und Altersfreigabe werden beim Speichern nicht mehr verworfen.
- Es werden keine vollständigen HTML-Seiten als Katalog-Metadaten gespeichert.

## Anime-News

- Oberhalb des bisherigen Startseiten-Hero-Bereichs befindet sich ein horizontaler „Anime-News“-Bereich.
- Titel, Ziel-URL, Vorschautext und Bild werden live aus dem Bereich vor „Beliebt bei AniWorld“ auf der AniWorld-Startseite ermittelt.
- Branding-, Logo-, Platzhalter- und Trackingbilder werden herausgefiltert.
- Bilder werden über den bestehenden Coil-Cache vorgeladen.
- Einträge öffnen die jeweilige AniWorld-Seite in der vorhandenen Webansicht.

## Neue Einstellungen

- „Bevorzugten Hoster automatisch starten“:
  - lädt die Episode und startet den bestbewerteten Treffer sofort
  - deaktiviert: normale Sprach-/Hosterauswahl bleibt sichtbar
- „Startbereich“:
  - Startseite
  - Favoriten
  - Verlauf
- Die gewählte Startansicht wird dauerhaft in DataStore gespeichert.

## Statische Prüfung

Erfolgreich geprüft:

- 20 Kotlin-Dateien auf ausgeglichene Klammern und geschlossene Strings/Kommentare
- 12 XML-/Manifestdateien auf gültiges XML
- 261 verwendete `R.string`-Referenzen
- je 308 Stringressourcen in Standard, Deutsch und Englisch
- Application-ID, Namespace, Versionscode und Versionsname
- keine Referenz auf die frühere Paket-ID `de.dxmoc.aniworld`
- keine erneut eingeführten gemeldeten Deprecated-Muster
- ZIP-Struktur ohne Build-, Gradle- oder Git-Artefakte

## Build-Hinweis

Der vollständige Android-Build konnte in der Arbeitsumgebung nicht gestartet werden, weil der Gradle-Wrapper die Distribution unter `services.gradle.org` wegen einer DNS-Sperre nicht herunterladen konnte. Der verbindliche CI-Lauf bleibt:

```bash
./gradlew --no-daemon --stacktrace :app:assembleDebug
```
