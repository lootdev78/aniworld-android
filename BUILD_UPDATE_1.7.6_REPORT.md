# AniWorld Android 1.7.6 – Offline-Metadaten und Bibliotheks-UI

## Umgesetzt

### Favoriten und Verlauf

- Such-, Sortier- und Filterbereiche werden nur gerendert, wenn die jeweilige Bibliothek mindestens einen Eintrag besitzt.
- Ein leerer Favoriten- oder Verlaufsbereich zeigt dadurch ausschließlich den leeren Zustand und keine nutzlosen Bedienelemente.
- Bei alphabetischer Sortierung erscheint rechts eine überarbeitete, vollhohe `#`-/A–Z-Schnellleiste.
- Vorhandene Buchstaben sind aktiv, nicht vorhandene Buchstaben bleiben zur Orientierung sichtbar, aber deaktiviert.
- Der aktive Buchstabe wird mit einer Material-3-Fläche deutlich markiert.

### Metadaten-Datei über Android DocumentProvider

- Neuer Export über `ActivityResultContracts.CreateDocument("application/json")`.
- Der Nutzer wählt den Speicherort direkt im Android-Dokumentanbieter.
- Exportiert werden die strukturierten Offline-Katalogmetadaten und – sofern vorhanden – der gespeicherte Startseitenfeed.
- Neuer Import über `ActivityResultContracts.OpenDocument()`.
- Nach einem gültigen Import werden die Katalogdaten in Room zusammengeführt, die Erstinitialisierung als abgeschlossen markiert und der Katalog neu geladen.
- Ein Import ersetzt keine Favoriten, Verlaufseinträge oder Player-Einstellungen.

### Live-/Offline-Startseite

- Neue DataStore-Einstellung `home_offline_mode`.
- Im Live-Modus wird die AniWorld-Startseite wie bisher gecrawlt; ein erfolgreicher Feed wird zusätzlich offline gespeichert.
- Im Offline-Modus wird ausschließlich der zuletzt gespeicherte Feed geladen.
- Die Einstellungsaktion „Startseiten-Metadaten jetzt aktualisieren“ erzwingt einen Live-Crawl und aktualisiert die Offline-Datei.
- Pull-to-refresh im Offline-Modus liest nur die lokale Datei erneut und löst keinen versteckten Live-Crawl aus.

### Benachrichtigungsberechtigung

- Ab Android 13 wird `POST_NOTIFICATIONS` beim ersten Start einmalig über den Android-Systemdialog angefragt.
- Ein eigener DataStore-Schlüssel verhindert wiederholte automatische Abfragen, unabhängig von der früheren Berechtigungs-Einführung.
- Auf älteren Android-Versionen wird die Abfrage als erledigt gespeichert, da keine Laufzeitberechtigung erforderlich ist.

## Version

- `versionCode`: 63
- `versionName`: `1.7.6-offline-metadata`
- Debug-Paket: `io.github.lootdev78.aniworld.debug`

## Prüfungen

- statisches Release-Gate: bestanden
- 21 Kotlin-Dateien mit Kotlin-PSI geprüft: 0 Syntaxfehler
- 12 XML-/Manifestdateien gültig
- 352 Stringschlüssel in Standard, Englisch und Deutsch vollständig
- 21 zentrale Featuregruppen geprüft
- ZIP-Integrität wird nach dem Paketieren geprüft

## Nicht ausgeführt

Ein vollständiger Gradle-/Android-Build wurde in dieser Umgebung nicht ausgeführt, weil die Gradle-Distribution wegen fehlender DNS-Auflösung von `services.gradle.org` nicht geladen werden konnte. Der verbindliche Build-Test bleibt:

```bash
./gradlew --no-daemon --stacktrace --warning-mode all clean :app:assembleDebug
```
