# Build-Update 1.7.9 – finaler UI- und Player-Nachtrag

## Enthaltene Basis

Dieses Vollprojekt baut auf dem bereits ergänzten Smart-View-, Xbox-, DLNA-, FCast-, Chromecast-, Hotspot-Web-Relay-, Fernsteuerungs-, Metadaten- und UI-Paket auf. Vorhandene Backends wurden nicht entfernt. Originalstände wichtiger Quelldateien bleiben zusätzlich unter `docs/original-source/` erhalten.

## Suchleisten und Wischgeste

Die kollabierbaren Kopfbereiche in Katalog, Favoriten und Verlauf werden beim Tippen nicht mehr nach jedem Zeichen zwangsweise geöffnet oder an den Listenanfang gesetzt. Dadurch kann der Nutzer den Such-/Vorschlagsbereich auch mit gefüllter Suchleiste und sichtbaren Vorschlägen per Aufwärtswisch ausblenden. Sortierung, Ansicht oder Statusfilter dürfen den Bereich weiterhin bewusst zurücksetzen.

## Staffelbeschreibung

Der Staffelbereich bleibt in der Episodenansicht sichtbar und verwendet den vorhandenen auf-/zuklappbaren Beschreibungstext. Die Repository-Schicht gibt jetzt nur eine tatsächlich gefundene Staffelbeschreibung als solche zurück. Fehlt sie, kennzeichnet die UI den Rückfall auf die Anime-Beschreibung korrekt; fehlt auch diese, wird ein eindeutiger Platzhalter angezeigt.

## Diagnose-Schaltflächen

Die Aktionen `Kopieren`, `Teilen` und `Leeren` sind responsiv:

- unter 330 dp: alle Aktionen untereinander
- Telefonbreite bis 600 dp: Kopieren und Teilen nebeneinander, Leeren in voller Breite darunter
- ab 600 dp: alle drei Aktionen in einer Reihe

Die Beschriftungen bleiben einzeilig und erhalten mindestens 54 dp Höhe.

## Alphabetischer Schnellindex

Der frühere sehr kleine `# A–Z`-Text wurde durch eine deutlichere A–Z-Schaltfläche mit großem aktivem Buchstaben ersetzt. Der Auswahldialog zeigt fünf große 52-dp-Felder pro Reihe mit 22-sp-Buchstaben und deaktiviert nicht vorhandene Anfangsbuchstaben.

## Player: Videolink und Streamformat

In der unteren Videonavigation sind zwei neue Aktionen vorhanden:

- `Videolink kopieren` kopiert die aktuell aufgelöste Video-/Stream-URL direkt in die Zwischenablage.
- `Stream-Info` öffnet Format, MIME-Typ, Host, adaptive/progressive Übertragungsart und den auswählbaren Link.

Zusätzlich zeigt eine dauerhaft sichtbare Infozeile in der Navigation beispielsweise `HLS · application/x-mpegURL · host.example` an. Die Formaterkennung verwendet vorhandene MIME-Daten und fällt ohne Netzwerkprobe auf die URL-Endung zurück.

## Prüfung ohne Gradle-Build

Auf ausdrücklichen Wunsch wurde kein Gradle-Task und kein Android-Build gestartet.

Ausgeführt:

- Projekt-, Manifest-, XML- und Stringressourcenprüfung über `tools/verify_project.py`
- statische Feature-Gates für 41 Funktionsgruppen
- Warnungsmusterprüfung für die gemeldeten Deprecated-/Always-true-Stellen
- isolierter Kotlin-Typcheck von `StreamPresentation.kt` mit lokalen Stubs
- Prüfung auf unerwünschte Build-, IDE-, Cache- und Schlüsseldateien vor dem Verpacken

Ergebnis: `STATIC RELEASE GATE: PASSED`.

Ein echter Android-/Gradle-Build sowie Tests auf Samsung-TV, Xbox One, Xbox Series S/X und Chromecast bleiben vor Veröffentlichung erforderlich.
