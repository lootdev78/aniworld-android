# AniWorld Android 1.7.9 – Player Compile Fix

## Ausgangsfehler

Der GitHub-Actions-Build vom 30. Juli 2026 scheiterte in `PlayerScreen.kt` mit:

- `Unresolved reference 'horizontalScroll'`
- Zugriff auf die interne `RowColumnParentData?.weight`-Property
- Aufruf von `stringResource(...)` aus einer nicht als Composable markierten lokalen Funktion

## Korrekturen

1. `horizontalScroll` wird aus `androidx.compose.foundation.horizontalScroll` importiert.
2. Der explizite Import `androidx.compose.foundation.layout.weight` wurde entfernt. Die öffentliche `RowScope`-/`ColumnScope`-Erweiterung wird an den jeweiligen Aufrufstellen über den Scope aufgelöst.
3. Die vier Cast-Protokolltexte werden im Composable-Kontext vorab mit `stringResource(...)` gelesen. Die lokale `protocolLabel(...)`-Funktion verarbeitet danach nur noch normale Strings.
4. Das statische Release-Gate erkennt künftig beide fehlerhaften Compose-Imports als Regression.

## Prüfung

- Statisches Release-Gate: bestanden
- XML- und String-Ressourcen: bestanden
- Falsche Compose-Imports: nicht mehr vorhanden
- ZIP-Integritätsprüfung: vorgesehen nach Paketierung

Ein lokaler vollständiger Gradle-Build war in der isolierten Umgebung nicht möglich, weil `services.gradle.org` nicht per DNS erreichbar war. Die gemeldeten Kotlin-Compilerfehler wurden direkt anhand des CI-Logs und des betroffenen Quellcodes korrigiert.
