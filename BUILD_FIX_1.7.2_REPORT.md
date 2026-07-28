# AniWorldAndroid 1.7.2 – Kotlin Build Fix

## Behobener CI-Fehler

Der Fehler in `UiScreens.kt:1767` (`Function invocation context(...) expected`) entstand im Bereich der generischen `remember(page.items, query)`-Berechnung für Suchvorschläge. Die Filter- und Vorschlagslisten werden nun explizit typisiert und ohne diese problematische `remember`-Überladung berechnet.

Zusätzlich wurde in der Episodensammlung der Android-Kontext eindeutig als `appContext` benannt. Die Suchfilter verwenden ausschließlich normale Datenfelder; lokalisierte Texte werden erst für die sichtbare Vorschlagszeile erzeugt.

## Versionsstand

- `versionCode`: 59
- `versionName`: `1.7.2-production-build-fix`
- Debug-Variante: `1.7.2-production-build-fix-debug`

## Beibehaltener Umfang

Alle Funktionen aus 1.7.1 bleiben enthalten. Es wurden ausschließlich die fehlerhaften Collection-Suchberechnungen und die Versions-/Prüfmetadaten geändert.

## Prüfung

- statisches Release-Gate
- XML- und Stringressourcen
- Paket- und Manifeststruktur
- Featuremarker
- ZIP-Integrität

Ein vollständiger Gradle-Build wurde entsprechend der Absprache nicht lokal ausgeführt.
