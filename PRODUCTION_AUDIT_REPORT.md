# Production-Audit – AniWorldAndroid 1.7.0

## Ziel

Konsolidierter Quellstand mit dem vollständigen Funktionsumfang der bisherigen 1.5.x- und 1.6.x-Stände. Der ausgelieferte Build-Typ bleibt absichtlich `debug`; Paketname, Architektur, lokale Persistenz und UI entsprechen dem vorgesehenen Produktionsumfang.

## Behobener Buildfehler

In `AppStore.kt` waren die generischen Hilfsfunktionen `parseArray` und `parseObjectMap` als `inline` deklariert. `parseObjectMap` übergab den Funktionsparameter anschließend in `JSONObject.keys().forEach`. Diese Java-Iteration ist kein Kotlin-Inline-Aufruf; der Compiler verlangt dort `crossinline` oder eine nicht-inline Funktion. Beide Parser sind jetzt reguläre private Funktionen. Dadurch entfallen nicht-lokale Return-Probleme, ohne das Datenformat oder die Migration zu verändern.

## Konsolidierungsprüfung

Der letzte 1.6.6-Stand wurde gegen die verfügbaren vorherigen Projektarchive abgeglichen. Die aktuellen Module enthalten weiterhin den Media-Detector, den WebView-Adblocker, den Challenge-/Session-Ablauf, WorkManager, MediaSession, externen Player, Katalogmetadaten, Favoriten, Verlauf und sämtliche später ergänzten UI-/Player-Einstellungen. Absichtlich entfernte Altstrukturen wie sichtbare Katalogpagination, große Sammlungs-Bottom-Sheets und der automatische Start-Permissiondialog wurden nicht wieder eingeführt.

## Automatische lokale Prüfungen

`tools/verify_project.py` kontrolliert ohne externe Python-Abhängigkeiten:

- notwendige Gradle-, Wrapper-, Manifest- und Quelldateien
- Namespace, Application-ID, Debug-Suffix und Versionswerte
- XML-Wohlgeformtheit
- vollständige deutsche und englische Stringressourcen
- alle verwendeten `R.string`-Referenzen
- Paketpfade und verbliebene alte Paketnamen
- bekannte Deprecated-/Compiler-Regressionsmuster
- konkrete Featuremarker für 16 Funktionsgruppen
- Manifestklassen und verbotene Build-/IDE-/Schlüsseldateien

Ergebnis des finalen Quellstands: **bestanden**.

Zusätzlich wurde jede Kotlin-Datei mit dem Kotlin-PSI-Parser eingelesen. Ergebnis: **20 Dateien, 0 Syntaxfehler**.

## CI-Release-Gate

`.github/workflows/android-debug.yml` führt bei Push, Pull Request und manueller Ausführung folgende Schritte aus:

1. JDK 17 und Android SDK 36 bereitstellen
2. statischen Production-Gate ausführen
3. sauberen `:app:assembleDebug`-Build ausführen
4. `:app:lintDebug` ausführen
5. Debug-APK umbenennen, SHA-256-Prüfsumme erzeugen und beides als Artefakt hochladen
6. Build-/Lintberichte bei Fehlern separat hochladen

## Funktionsstatus

Die vollständige Zuordnung steht in `FEATURE_MATRIX.md`. Besonders geprüft wurden:

- Adblocker weiterhin vorhanden und bei Neuinstallation standardmäßig aus
- Stream-URL-Erkennung für HLS, DASH, SmoothStreaming und progressive Quellen vorhanden
- CAPTCHA-/Turnstile-Ablauf nur als sichtbare manuelle WebView-Prüfung
- MediaSession-Player, Timeline, ±10 Sekunden, Start-von-vorn, Auto-Next und Auto-Hide vorhanden
- Katalog-Offline-Metadaten und Systemfortschritt über WorkManager vorhanden
- Coverparser, Cache-Schlüssel und Resetlogik vorhanden
- Favoriten/Verlauf mit Sortierung, Ansichten, Schnellnavigation und Mehrfachlöschung vorhanden
- externe Wiedergabe, Start-Tab, Akzentfarbe, Sprach-/Hosterpriorität und Credits vorhanden

## Was ohne Geräte-/Netzwerktest nicht seriös garantiert werden kann

Parser und Hoster hängen von externen Webseiten ab. DOM-Struktur, Hoster-Weiterleitungen, DNS, Cloudflare/Turnstile und Medien-URLs können sich unabhängig von der App ändern. Deshalb ist nach einem erfolgreichen CI-Build ein kurzer Test auf mindestens einem Android-13+-Gerät und einem älteren unterstützten Gerät sinnvoll. `SMOKE_TEST_CHECKLIST.md` enthält die dafür vorgesehenen Schritte.
