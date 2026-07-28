# Build-Fix 1.7.1

## Behobene CI-Compilerfehler

### `AppViewModel.kt:120`

Die suspendierende Funktion `ensureOfflineMetadata(series)` wurde als gebundene Funktionsreferenz an `forEach` übergeben. Die normale `Iterable.forEach`-Lambda ist nicht suspendierend. Der Backfill verwendet nun eine reguläre `for`-Schleife innerhalb der bereits laufenden Coroutine.

### `UiScreens.kt:1856`

`Episode.localizedDisplayTitle()` ist eine `@Composable`-Hilfsfunktion, wurde aber innerhalb eines `remember`-Berechnungsblocks aufgerufen. Der Bildschirm verwendet nun die nicht-komponierbare Context-Variante `localizedDisplayTitle(context)`.

### `UiScreens.kt:1878`

Auch `localizedLabel()` und `localizedDisplayTitle()` wurden in der nicht-komponierbaren `suggestionSubtitle`-Callbackfunktion aufgerufen. Beide Aufrufe verwenden jetzt die Context-Varianten.

## Prüfungen

- Kotlin-PSI-Syntaxprüfung für alle 20 Kotlin-Dateien
- XML- und Stringressourcenprüfung
- Paket-, Manifest- und Featuremarkerprüfung
- Regressionstest für die drei CI-Fehlermuster
- ZIP-Integritätsprüfung

Der vollständige Gradle-Build wurde nicht ausgeführt, weil die Gradle-Distribution in dieser Umgebung nicht heruntergeladen werden konnte. Die vom CI gemeldeten drei Compilerstellen wurden direkt korrigiert.
