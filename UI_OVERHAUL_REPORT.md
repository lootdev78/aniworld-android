# AniWorldAndroid 1.6.0 – UI-Overhaul und Fehlerkorrekturen

## Umgesetzte Anforderungen

### Katalog

- Die sichtbare Seitennavigation wurde vollständig entfernt.
- Der Katalog ist eine durchgehend scrollbare Liste beziehungsweise ein Raster.
- Am rechten Rand befindet sich eine A–Z-/`#`-Schnellnavigation.
- Suche, Genre-Filter, Trefferzahl und Ansichtsumschaltung blenden sich beim Weiterblättern nach unten aus und erscheinen bei einer Gegenbewegung wieder.
- Kompakte Liste, Detail-Liste und Poster-Raster stehen zur Verfügung; die gewählte Ansicht wird gespeichert.
- Katalogkarten zeigen die von AniWorld ermittelten Cover/Poster. Logo-, Header-, Icon-, Tracking-, Placeholder- und unplausible Bildkandidaten werden verworfen.
- Sichtbare Einträge ohne ausreichende Metadaten werden begrenzt parallel nachgeladen.
- Der vollständige Katalog wird erst beim Öffnen des Katalog-Tabs geladen. Dadurch erzeugt der App-Start nicht mehr sofort die im Fehlerprotokoll sichtbare Kaskade aus sämtlichen Buchstaben- und Seitenaufrufen.

### Film-, Staffel- und Episodeninformationen

- „Beschreibung anzeigen“ ist nicht mehr nur ein wirkungsloser Text. Der Parser berücksichtigt sichtbare Texte, Collapse-Ziele, `aria-controls`, `data-target`, Hash-Ziele und typische Beschreibungscontainer.
- Film-/Episodenbeschreibung, Veröffentlichungsdatum, Sprache und Hoster werden auf einer eigenen vollflächigen Seite angezeigt.
- Staffelbeschreibungen werden live von der jeweiligen AniWorld-Staffelseite gelesen und oberhalb der Episoden angezeigt.
- Anime-, Staffel-, Film- und Episodeninformationen werden nicht mehr in inhaltlichen Dialogen oder Bottom Sheets verwaltet.

### Startseiten-Sammlungen

- Top 50, Meistgesehen und die weiteren Sammlungen öffnen eigene vollflächige Listenansichten.
- Die vorherige Bottom-Sheet-Höhen-/Overscroll-Problematik entfällt.
- Sammlungen unterstützen Suche und bei Anime-Sammlungen kompakte, detaillierte und Rasteransicht.

### Verlauf

- Eigener Tab „Verlauf“.
- Darstellung wie bei Favoriten: kompakt, detailliert oder als Poster-Raster.
- Suche und Sortierung.
- Filter für alle, begonnen, vollständig gesehen und favorisiert.
- Einträge lassen sich öffnen, aus dem Verlauf entfernen und bei benutzerdefinierter Sortierung verschieben.

### Detailansicht und Startseite

- Kompakter Netflix-artiger Detail-Header mit Bildverlauf, Cover, Titel, Metadaten und direkter Wiedergabe-/Fortsetzen-Aktion.
- Staffelchips, Staffelstatus, Beschreibung und kompakte Episodenzeilen.
- Persönliche „Weiterschauen“-Reihe auf der Startseite.
- Favoriten und Katalog besitzen dieselben drei Darstellungsarten.
- Einstellungen werden als eigener vollflächiger Bildschirm dargestellt.
- Smartphones verwenden eine untere Navigation; größere Fenster eine Navigation Rail.

### Player

- Die Player-Oberfläche blendet sich während laufender Wiedergabe nach drei Sekunden aus.
- Pause oder Interaktion blendet sie wieder ein.
- Vorherige/nächste Folge, Fortschritt, Auto-Next und System-Mediensteuerung bleiben erhalten.

## CAPTCHA-/Turnstile-Ablauf

Eine CAPTCHA-/Turnstile-Prüfung wird nicht automatisch umgangen. Der stabile Ablauf lautet:

1. Ein Hoster mit Browserprüfung wird zunächst übersprungen.
2. Der Resolver probiert die übrigen Hoster gemäß Sprach- und Hosterpriorität.
3. Nur wenn kein direkter Alternativstream funktioniert, öffnet die App die konkrete Hoster-Seite in einer WebView.
4. Die Prüfung wird dort sichtbar vom Nutzer abgeschlossen.
5. Die Schaltfläche „Prüfung abgeschlossen – Hoster erneut versuchen“ übernimmt die gemeinsame Cookie-/WebView-Session und startet denselben Resolver erneut.
6. Zusätzlich können bereits im sichtbaren WebView-Netzwerkverkehr erkannte HLS-, DASH-, SmoothStreaming- oder progressive Medienquellen direkt an Media3 übergeben werden.

Die bisherige ungefangene `ChallengeRequiredException` wird beim normalen automatischen Resolver-Fallback nicht mehr als allgemeiner App-Fehler behandelt. Auch eine Challenge während der optionalen Stream-Erreichbarkeitsprüfung wird abgefangen und führt zum nächsten Hoster.

## Netzwerkfehler

- `UnknownHostException` bedeutet, dass Android den Host zu diesem Zeitpunkt per DNS nicht auflösen konnte; eine UI- oder Parseränderung kann eine tatsächlich fehlende Verbindung nicht erzwingen.
- Die App zeigt dafür eine verständliche Offline-Meldung und verwendet vorhandene Katalog-Fallbackdaten.
- Kataloganfragen werden nicht mehr beim App-Start ausgelöst.
- Parallelität, Wiederholungen und DNS-Backoff bleiben begrenzt, damit aus einem kurzen Ausfall keine Anfragekaskade entsteht.

## Auf Wunsch ausdrücklich nicht neu erweitert

- kein zusätzlicher Datenschutz-/WebView-Einstellungsblock aus der Vorschlagsliste
- keine automatisierten Accessibility-Tests
- keine zusätzliche TalkBack-Reihenfolge für Hero oder Player
- keine separate Tastatur-, D-Pad- oder Switch-Access-Überarbeitung
- kein eigenes Projekt zur Unterstützung besonders großer Systemschrift

Die bereits für den Hoster-/Challenge-Ablauf notwendige WebView bleibt technisch erhalten.

## Nicht als Teil dieses Builds umgesetzt

Die folgenden größeren, eigenständigen Ausbaustufen aus der früheren Ideensammlung bleiben separat: benutzerdefinierte Listen, Backup/Import, Benachrichtigungen über neue Folgen, Startseitenbereiche per Drag-and-drop, Mini-Player/Picture-in-Picture sowie Qualitäts-, Audio-, Untertitel- und Geschwindigkeitsmenüs. Der vorliegende Stand konzentriert sich auf die konkret gemeldeten UI- und Funktionsfehler und die unmittelbar dazugehörigen Hauptvorschläge.

## Prüfung

- 19 Kotlin-Dateien mit Kotlin-PSI geparst: keine Syntaxfehler
- 11 XML-Ressourcendateien geparst: gültig
- 224 verwendete Stringreferenzen geprüft: keine fehlenden Ressourcen
- `values`, `values-de` und `values-en`: jeweils 252 Strings
- Version Catalog (`libs.versions.toml`) erfolgreich geparst
- Wrapper-Skript mit `bash -n` geprüft

Ein vollständiger Android-Gradle-Build konnte in der Arbeitsumgebung nicht abgeschlossen werden, weil `services.gradle.org` per DNS nicht erreichbar war. Deshalb ist ein anschließender CI-Lauf mit `./gradlew --no-daemon --stacktrace :app:assembleDebug` weiterhin erforderlich.
