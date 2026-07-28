# AniWorldAndroid 1.7.3 – PiP, Player-Zeitleiste, Anime-News und Metadatenjob

## Version

- `versionCode`: 60
- `versionName`: `1.7.3-pip-news-metadata`
- Namespace/Application-ID: `io.github.lootdev78.aniworld`
- Debug-Paket durch `applicationIdSuffix`: `io.github.lootdev78.aniworld.debug`

## Player

- Picture-in-Picture-Unterstützung in `MainActivity` aktiviert.
- Bild-in-Bild-Schaltfläche in der oberen Player-Steuerung ergänzt.
- Die Player-Zeitleiste wird bei eingeblendeten Steuerelementen immer dargestellt.
- Aktuelle Position steht links, bekannte Videolänge rechts.
- Der Slider zeigt den aktuellen Positionspunkt und kann nach Bekanntwerden der Laufzeit frei verschoben werden.
- Bereits offline gespeicherte Laufzeiten werden direkt beim Öffnen des Players übernommen.
- Zehn Sekunden zurück/vor als Icon-Aktionen und weiterhin per Doppeltipp links/rechts.
- „Von Anfang abspielen“ ist eine reine Replay-Icon-Aktion.
- Die Player-Oberfläche blendet sich während der Wiedergabe weiterhin automatisch aus.

## Katalog-Metadaten

- Der WorkManager-Auftrag wird als Foreground-Worker mit laufender Android-Systembenachrichtigung ausgeführt.
- Die Benachrichtigung zeigt Vorbereitung beziehungsweise `aktuell/gesamt`, einen Fortschrittsbalken und eine Abbrechen-Aktion.
- Der Auftrag läuft bei geschlossener Ansicht weiter.
- Nach Erfolg oder endgültigem Fehler erscheint eine abschließende Benachrichtigung.

## Anime News

- Es wird ausschließlich der originale Abschnitt mit der Überschrift `Anime News` auf der AniWorld-Startseite ausgewertet.
- Nur die im Abschnitt verlinkten Anime2You-Artikel werden übernommen; Anime-, Support- oder andere Startseitenkarten werden nicht mehr als News fehlklassifiziert.
- Bilder werden dem jeweiligen Ziellink zugeordnet und anhand von `img`, `picture/source`, Lazy-Load-Attributen und CSS-Hintergründen ausgewählt.
- Fehlt im AniWorld-Kartenelement ein verwendbares Bild, wird ausschließlich das Open-Graph-/Twitter-Bild des verlinkten Artikels nachgeladen.
- Externe Bilder werden nur über ihre URL angezeigt und nicht in das Projektarchiv eingebettet.

## Prüfungen

- Statisches Projekt-Gate: bestanden
- Kotlin-PSI-Syntaxprüfung: 20 Dateien, 0 Syntaxfehler
- XML-Dateien: 12 gültig
- Stringschlüssel: 315
- verwendete Stringreferenzen: 275
- Featuregruppen: 16 geprüft
- Kein vollständiger Gradle-/Android-Build in dieser Arbeitsumgebung ausgeführt
