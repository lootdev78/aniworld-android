# Build-Fix-Bericht – 1.5.1-build-fix

Ausgangspunkt war der GitHub-Actions-Build vom 28. Juli 2026 mit Fehlern in
`AniWorldRepository.kt` und `MainActivity.kt`.

## Behobene Compilerfehler

1. **Top-50-Parser:** `parseSeriesSection` benötigt `Document` und Überschrift.
   Die ungültige Funktionsreferenz `.map(::parseSeriesSection)` wurde durch
   `.map { heading -> parseSeriesSection(doc, heading) }` ersetzt.
2. **Kaskadenfehler bei Typinferenz:** Durch die Korrektur des Mappings sind
   `firstOrNull`, `orEmpty` und `distinctBy` wieder eindeutig typisiert.
3. **Jsoup-Bildprüfung:** Die nicht vorhandene Methode `Element.matches(...)`
   wurde durch eine `closest(...)`-Prüfung auf Cover-/Poster-Container ersetzt.
4. **Compose-Layout:** Der nicht auflösbare Import `matchParentSize` wurde entfernt
   und am Overlay durch `Modifier.fillMaxSize()` ersetzt.

## Unkritische Buildmeldung

Die Meldung, dass `libandroidx.graphics.path.so` und
`libdatastore_shared_counter.so` nicht gestripped werden konnten, ist eine
Packaging-Warnung und war nicht die Ursache des Buildabbruchs.

## Lokale Prüfung

- Fehlerausdrücke aus dem CI-Protokoll sind nicht mehr vorhanden.
- XML-Ressourcen lassen sich parsen.
- Alle verwendeten `R.string`-Schlüssel sind definiert.
- ZIP-Integrität wurde geprüft.

Ein vollständiger Gradle-Build kann in dieser isolierten Arbeitsumgebung nicht
laufen, weil `services.gradle.org` per DNS nicht erreichbar ist. Der nächste
CI-Lauf sollte daher `:app:compileDebugKotlin` erneut ausführen und mögliche
nachgelagerte Fehler sichtbar machen.
