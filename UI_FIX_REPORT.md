# AniWorldAndroid 1.6.1 – UI-Fix

## Behobene Darstellungsfehler

- Die App setzt jetzt global eine dunkle Hintergrund- und Inhaltsfarbe über eine Material-`Surface`.
- Standardtexte außerhalb von Karten oder Dialogen erben nicht mehr versehentlich Schwarz auf schwarzem Hintergrund.
- Auch bei aktivierten dynamischen Farben bleiben Hintergrund, Oberflächen und zugehörige Textfarben kontrastreich und konsistent.
- `Scaffold` verwendet explizit die dunkle Hintergrund- und Inhaltsfarbe.
- Vollbildansichten berücksichtigen die Statusleiste, sodass Überschriften und Schaltflächen nicht mehr unter den System-Icons liegen.

## Doppelte Filmbeschreibung

- In der Film-/Episoden-Informationsansicht wird die Episoden- beziehungsweise Filmbeschreibung nur noch einmal angezeigt.
- Wenn eine konkrete Filmfolge geöffnet ist, wird die allgemeine Anime-Beschreibung nicht zusätzlich direkt darunter wiederholt.
- Metadaten wie Jahr, Altersfreigabe, Genres, Regie und Produzenten bleiben erhalten.

## Schließen-Schaltflächen

Folgende Vollbildansichten besitzen jetzt rechts oben eine X-Schaltfläche:

- Anime-Sammlungen wie Top 50 und Meistgesehen
- Episoden-Sammlungen
- Hoster-/Film-/Episodenansicht
- Anime- und Episodeninformationen
- Einstellungen

Die Android-Zurück-Geste beziehungsweise Zurück-Taste funktioniert weiterhin.

## Player

- Die bisherige reine Fortschrittsanzeige wurde durch eine bedienbare Zeitleiste ersetzt.
- Die Zeitleiste kann vorwärts und rückwärts gezogen werden.
- Doppeltippen auf die linke Hälfte springt 10 Sekunden zurück.
- Doppeltippen auf die rechte Hälfte springt 10 Sekunden vor.
- Eine kurze `−10 s`- beziehungsweise `+10 s`-Rückmeldung erscheint in der Mitte.
- Die Player-Steuerung blendet sich während der Wiedergabe weiterhin automatisch aus.
- Der Gestenhinweis wurde um Zeitleiste und Doppeltipp ergänzt.

## Katalog

- Das Ein- und Ausblenden von Suche, zuletzt verwendeten Suchen, Genres und Ansichtsumschaltung basiert jetzt auf echten Scrollgesten über `NestedScrollConnection`.
- Beim Hochwischen der Liste verschwindet der gesamte Steuerungsbereich und die Anime-Liste nutzt die frei werdende Höhe.
- Beim Herunterwischen erscheint der Bereich wieder.
- Die frühere Positionsberechnung wurde entfernt, weil die Größenänderung des Headers die Listenposition veränderte und den Header dadurch sofort wieder einblenden konnte.
- Ein kleiner Suchbutton bleibt als nicht platzverbrauchende Einblendung verfügbar, wenn der Bereich verborgen ist.

## Version

- `versionCode`: 51
- `versionName`: `1.6.1-ui-fix`

## Prüfungen

Erfolgreich durchgeführt:

- XML-Parsing aller Ressourcen
- Prüfung aller verwendeten `R.string`-Referenzen
- Kotlin-Parserprüfung der geänderten Dateien
- Archiv- und Strukturprüfung

Ein vollständiger Gradle-Build konnte in der Arbeitsumgebung nicht ausgeführt werden, weil `services.gradle.org` per DNS nicht erreichbar war. Der CI-Build sollte mit folgendem Befehl geprüft werden:

```bash
./gradlew --no-daemon --stacktrace :app:assembleDebug
```
