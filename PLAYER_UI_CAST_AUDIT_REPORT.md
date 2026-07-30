# Player- und Cast-Audit – 1.7.9

## Ergebnis

Der aktuelle Player wurde strukturell bereinigt und die Cast-Oberfläche konsolidiert. Der statische Release-Gate-Test ist bestanden.

## Player-UI

- doppelte und deaktivierte Alt-UIs aus `PlayerScreen.kt` entfernt
- Staffel- und Episodennummer als kompakte Badges in der Titelzeile
- AniWorld-Webseitenpalette unabhängig vom frei wählbaren App-Akzent
- weich ein- und ausfahrende obere und untere Steuerung
- reine Icon-Aktionsleiste mit gedrückt-gehaltenem Hinweis, Haptik und Accessibility-Aktion
- dunkle, player-spezifische Dialogfarben für Sprache, Hoster, Streaminfo, Cast und manuelle Verbindung
- Streaminfo bleibt bei langen URLs scrollbar
- Fehler werden als schließbare Meldungsfläche im Steuerungsbereich dargestellt

## Tap- und Lifecycle-Verhalten

- bei ausgeblendeter Steuerung blendet der erste Tipp ausschließlich die Controls ein
- ein weiterer Tipp pausiert oder startet die Wiedergabe
- der frühere Doppeltipp-Seek wurde entfernt, damit er nicht mit der gewünschten Zwei-Tipp-Bedienung kollidiert
- Pause hält die Controls sichtbar; laufende Wiedergabe blendet sie nach einer kurzen Inaktivität aus
- Schließen, Navigation zu einer anderen Folge, Hoster-/Sprachwechsel und externer Player räumen aktive Cast-Verbindungen auf
- `DisposableEffect` stoppt lokale Wiedergabe und schließt alle Controller auch bei unerwartetem Entfernen des Player-Composables

## Gemeinsame Cast-Ansicht

- Google Cast, Samsung Smart View, DLNA/UPnP, Xbox und FCast in einer einzigen Geräteliste
- Geräte werden über normalisierte Namen protokollübergreifend gruppiert
- bei mehreren Verbindungsmethoden erscheint eine Protokollauswahl direkt unter dem Gerät
- verbundener Empfänger, aktives Protokoll und unterstützte Lautstärke werden oben angezeigt
- Refresh, manuelle IP-Suche und Miracast-Systemauswahl befinden sich im selben Dialog
- Google-Cast-Discovery besitzt jetzt ein kontrolliertes Suchfenster statt eines sofort beendeten Ladezustands
- Android- und Empfängerlautstärke werden bei Google Cast, Smart View und DLNA bestmöglich bidirektional synchronisiert

## Entfernte Altlasten

- `LocalPlaybackWebServer.kt` entfernt
- `RemotePlaybackBridge.kt` entfernt
- Pair-Code-/lokale Webplayer-Einstellungen, DataStore-Felder und Service-Brücken entfernt
- statischer Release-Gate-Test entsprechend aktualisiert und gegen eine erneute Einführung abgesichert

## Prüfungen

- Android-XML und String-Parität: bestanden
- statischer Feature-/Regressionstest: bestanden
- Klammer- und Parser-Syntaxprüfung der geänderten Kotlin-Dateien: bestanden
- lokale Smart-View-AAR-Struktur: bestanden
- vollständiger Gradle-Build: in dieser isolierten Umgebung nicht ausführbar, da `services.gradle.org` nicht erreichbar ist
