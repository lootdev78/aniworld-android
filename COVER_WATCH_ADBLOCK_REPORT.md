# AniWorld Android 1.6.6 – Cover, Gesehen-Status und Adblocker

## Version

- `versionCode`: `56`
- `versionName`: `1.6.6-cover-watch-adblock`
- Paket: `io.github.lootdev78.aniworld`

## Katalogcover

Die Coverauswahl wurde von einer allgemeinen Container-Suche auf eine animebezogene Zuordnung umgestellt:

- Für jeden Katalogtitel wird der kleinste DOM-Container gesucht, der genau eine AniWorld-Serien-URL enthält.
- Bilder, die zu einem anderen `/anime/stream/<slug>`-Link gehören, werden verworfen.
- Exakt verlinkte Bilder, Cover-/Poster-Container, `itemprop=image`, Titel-/Alt-Übereinstimmung und Coverpfade erhalten getrennte Gewichtungen.
- `data-src`, `data-lazy-src`, `data-original`, `data-srcset`, `srcset`, `src` und `<picture><source>` werden berücksichtigt.
- Logo-, Header-, Branding-, Avatar-, Placeholder-, Tracking- und unplausibel dimensionierte Bilder werden verworfen.
- Auf Detailseiten werden strukturierte Coverbereiche und Social-Metadaten kontrolliert ausgewertet.

Für bestehende Installationen wird die alte Coverzuordnung einmalig invalidiert. Gespeicherte Coverfelder und Coil-Caches werden geleert, danach wird automatisch der WorkManager-Metadatenjob gestartet. Titel, Beschreibungen, Genres, Jahr und Altersfreigabe bleiben erhalten.

## Gesehen und ungesehen

- Episoden und Filme können in der Hoster-/Wiedergabeansicht manuell als gesehen markiert werden.
- Die Aktion ist zusätzlich direkt in der „Weiterschauen“-Karte verfügbar.
- Eine vorhandene Wiedergabeposition wird beim manuellen Markieren nicht mehr durch die Folgenlänge ersetzt.
- Beim Entfernen der Markierung bleibt vorhandener Fortschritt erhalten; nur rein manuelle Einträge ohne Fortschritt werden vollständig entfernt.
- Staffel `0` beziehungsweise Filme verwenden dieselbe Statuslogik wie normale Episoden.

## Adblocker

- Der WebView-Werbeblocker ist wieder als Schalter in den Einstellungen vorhanden.
- Bei aktivem Schalter können Werbung, Tracking, Popups und Weiterleitungen einzeln gewählt werden.
- Neue Installationen starten mit deaktiviertem Werbeblocker.
- Bereits gespeicherte Nutzereinstellungen werden weiterhin respektiert.

## Geänderte Dateien

- `AniWorldRepository.kt`
- `AniWorldApplication.kt`
- `AppDatabase.kt`
- `AppStore.kt`
- `AppViewModel.kt`
- `MainActivity.kt`
- `Models.kt`
- `UiScreens.kt`
- `strings.xml`, `values-de/strings.xml`, `values-en/strings.xml`
- `app/build.gradle.kts`
- `README.md`

## Prüfungen

- Alle Ressourcen- und Manifest-XML-Dateien wurden erfolgreich geparst.
- Alle drei Sprachdateien besitzen denselben Bestand von 310 Stringschlüsseln.
- Alle 270 verwendeten `R.string`-Referenzen sind vorhanden.
- Die Klammer- und Stringstruktur aller Kotlin-Dateien wurde geprüft.
- Ein Kotlin-Parserlauf zeigte keine Syntaxfehler; nicht auflösbare Android-/Compose-Symbole sind ohne Android-Klassenpfad erwartbar.
- Der vollständige Gradle-Build konnte in der Ausführungsumgebung nicht gestartet werden, weil die Gradle-Distribution wegen fehlender DNS-Auflösung nicht heruntergeladen werden konnte.
