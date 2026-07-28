# AniWorldAndroid 1.6.4 – Metadata & Package Fix

## Stand

- Version: `1.6.4-metadata-package-fix`
- `versionCode`: `54`
- Namespace und Application-ID: `io.github.lootdev78.aniworld`
- Credits: `lootdev78`
- Repository: `https://github.com/lootdev78/aniworld-android`

> Hinweis zur Paketänderung: Android behandelt `io.github.lootdev78.aniworld` als eine andere App als das bisherige Paket `de.dxmoc.aniworld`. Bereits im privaten Speicher der alten Paket-ID vorhandene Favoriten, Verlauf, Einstellungen und Katalogdaten werden deshalb nicht automatisch in die neue Installation übernommen.

## Behobene Deprecation-Warnungen

- `WebSettings.databaseEnabled` entfernt. WebSQL wird nicht mehr aktiviert.
- Veraltete `WebViewClient`-Callbacks mit `String`-URL entfernt; es bleiben die aktuellen `WebResourceRequest`-Varianten.
- `ArrowBack`, `List` und `VolumeUp` auf die Auto-Mirrored-Material-Icons umgestellt.
- Den unmöglichen `null`-Zweig in der Player-Gestensteuerung entfernt.
- Der Projektquelltext enthält keine der vom CI-Lauf gemeldeten veralteten Aufrufe mehr.

## Sprachen und Länderkennzeichnung

Unterstützte Sprachvarianten wurden erweitert und in der Oberfläche mit Länderflaggen gekennzeichnet:

- 🇩🇪 Deutsch Dub
- 🇩🇪 Deutsch Sub
- 🇬🇧 Englisch Dub
- 🇬🇧 Englisch Sub
- 🇯🇵 Japanisch Dub
- 🇯🇵 Japanisch Sub
- 🇯🇵 Japanisch Original
- 🌐 Unbekannt/sonstige Sprache

Bereits gespeicherte Sprachprioritäten werden beim Einlesen um neu hinzugekommene Varianten ergänzt, ohne die bisherige erste Wahl zu überschreiben.

## Credits und Projektlinks

In den Einstellungen wurde der Bereich **Über & Credits** ergänzt. Er enthält:

- Entwickler-/GitHub-Verweis auf `lootdev78`
- direkten Link zum Repository `lootdev78/aniworld-android`

## Live-Startseite und Offline-Katalog

- Startseiten-Sammlungen und sichtbare Startseiten-Titel werden live von AniWorld angereichert.
- Startseiten-, Detail-, Staffel- und Episodenseiten werden nicht als vollständige HTML-Seiten offline gespeichert.
- Die bisherige HTML-Seiten-Cache-Nutzung wurde aus dem Repository entfernt; eventuell vorhandene alte `page_cache`-Einträge werden beim App-Start gelöscht.
- Für den Katalog werden ausschließlich strukturierte Daten gespeichert:
  - Titel
  - Slug/URL
  - Beschreibung
  - Cover-/Poster-URL
  - Genres
- Die vorhandene `page_cache`-Tabelle bleibt nur aus Datenbank-/Migrationskompatibilität bestehen und wird nicht mehr befüllt.

## Katalog-Metadatenaktualisierung

- Die Einstellungen enthalten die Schaltfläche **Metadaten aktualisieren**.
- Die Aktualisierung läuft als eindeutiger WorkManager-Hintergrundauftrag weiter, auch wenn die Ansicht geschlossen wird.
- Fortschritt und Abbrechen werden über eine Android-Systembenachrichtigung angeboten.
- Einzelne fehlerhafte Katalogseiten brechen nicht mehr sofort den gesamten Import ab; sie werden protokolliert und übersprungen.
- Ein Import ohne einen einzigen gefundenen Titel wird als Fehler beendet und im Katalog angezeigt.
- Nach erfolgreichem Auftrag wird der offline gespeicherte Katalog automatisch neu eingelesen.

Vor dem ersten erfolgreichen Import:

- zeigt der Katalog Skeleton-/Preview-Karten,
- sind Suche, Genres, letzte Suchbegriffe und Ansichtsumschalter deaktiviert,
- werden keine leeren, anklickbaren Animeobjekte dargestellt.

## Favoriten und Verlauf

- Jede Auswahl erhält eine neue `selectionVersion`.
- Dadurch navigiert die App auch dann erneut korrekt in die Detailansicht, wenn derselbe Anime bereits ausgewählt war.
- Das gilt für Favoriten, Verlauf, Startseitenfolgen und normale Serienauswahl.

## Gesehen-Zähler und Wiedergabefortschritt

- „0 gesehen“ wird nicht mehr eingeblendet.
- Gesehen-Zähler erscheinen erst ab einem Wert von `1`.
- Der Wiedergabefortschritt wird nicht mehr als Prozenttext angezeigt.
- Stattdessen wird er als `HH:MM:SS – HH:MM:SS` dargestellt, zum Beispiel `00:12:34 – 00:24:10`.
- Position und Laufzeit bleiben in Room pro Episode beziehungsweise Film offline gespeichert.
- Fortschrittsbalken bleiben als visuelle Ergänzung erhalten.

## Staffelbeschreibungen

Der Parser wurde enger auf echte Staffel-/Filmbeschreibungscontainer, referenzierte Collapse-Ziele und nahe Beschreibungselemente ausgerichtet. Episodentabellen, Hostertexte, Navigationstexte und eine lediglich erneut ausgegebene allgemeine Serienbeschreibung werden verworfen. Falls AniWorld keine eigene Staffelbeschreibung liefert, zeigt die App keine künstlich duplizierte Serienbeschreibung an.

## Validierung

Durchgeführt wurden:

- XML-Parsing aller 11 Ressourcen-/Manifestdateien
- vollständiger Schlüsselabgleich der 285 Stringressourcen in Standard, Deutsch und Englisch
- Platzhalterabgleich der lokalisierten Strings
- Prüfung aller 244 verwendeten `R.string`-Referenzen
- Prüfung aller 20 Kotlin-Dateien auf unausgeglichene Klammern sowie nicht geschlossene Strings/Kommentare
- Suche nach alter Paket-ID und den konkret gemeldeten Deprecated-Aufrufen
- Manifest-, Namespace-, ProGuard- und Komponentenprüfung
- ZIP-Integritätsprüfung

## Build-Hinweis

Ein vollständiger Gradle-/Android-Kotlin-Build konnte in der bereitgestellten Umgebung nicht bis zur Projektkompilierung ausgeführt werden, weil die Gradle-Distribution unter `services.gradle.org` dort nicht per DNS aufgelöst werden konnte (`UnknownHostException`). Die Quell- und Ressourcenprüfungen waren erfolgreich; der verbindliche CI-Lauf bleibt:

```bash
./gradlew --no-daemon --stacktrace :app:assembleDebug
```
