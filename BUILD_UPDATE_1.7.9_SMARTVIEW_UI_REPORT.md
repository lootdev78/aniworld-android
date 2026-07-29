# Build-Update 1.7.9 – Smart View, Remote-Relay und UI-Fixes

## Umfang

Dieses Paket führt die Anforderungen aus den Cast-/Fernsteuerungsnachrichten und den nachgereichten UI-Hinweisen zusammen. Bestehende Wiedergabe- und Cast-Backends wurden nicht entfernt oder ersetzt. Neue Funktionen liegen überwiegend in zusätzlichen Komponenten; vorhandene Dateien enthalten nur die notwendigen Integrations-Hooks, Einstellungen und UI-Anbindungen.

## Lokale Receiver-Webseite und Fernsteuerung

Neu bzw. vollständig verdrahtet:

- lokale Receiver-Webseite auf einstellbarem Port
- zufällige PIN und lokale Sitzungsadresse
- WebSocket als primärer Echtzeitkanal
- REST-Befehle plus Server-Sent Events als Fallback
- HTTP-Status-Polling als letzter Browser-Fallback
- Play/Pause, Stop, Seek, Vor/Zurück, Mute und Lautstärke
- Web Media Session für Browser-/Hardware-Medientasten
- zentrale, revisionsbasierte `RemotePlaybackBridge`, damit veraltete Steuerbefehle keinen neueren Zustand überschreiben
- dynamisches Starten und Stoppen des Servers über die App-Einstellungen
- tokenisierter Stream-Relay mit Hoster-Headern, Range, HLS- und DASH-Umschreibung

Wichtige Dateien:

- `LocalPlaybackWebServer.kt`
- `RemotePlaybackBridge.kt`
- `LocalCastRelay.kt`

## Native Cast-Ziele

### Samsung Smart View SDK

Mit `SamsungSmartViewCast.kt` ist ein optionales natives Samsung-Backend ergänzt:

- Samsung-Service-Suche im lokalen Netz
- Erkennung verlorener und im Standby gespeicherter Fernseher
- Prüfung von `isDMPSupported`
- Start über Samsungs Default Media Player mit `createVideoPlayer` und `playContent`
- Play, Pause, Stop, Seek, Lautstärke, Mute und Statusereignisse
- Nutzung des lokalen Relays für Streams mit Referer, Cookies oder anderen Hoster-Headern
- Fehler- und Verbindungszustand im bestehenden Cast-Dialog
- klare Anzeige in den Einstellungen, ob das optionale SDK verfügbar ist

Die Samsung-Binärdatei selbst ist aus Lizenzgründen nicht enthalten. `app/build.gradle.kts` lädt automatisch offizielle JAR-/AAR-Dateien aus `app/libs`. Details stehen in `app/libs/README_SMARTVIEW_SDK.md`. Fehlt die Datei, bleiben alle anderen Cast-Wege aktiv und die App zeigt nur einen Hinweis.

### Xbox One und Xbox Series S/X

`XboxCast.kt` verwendet keinen Browser-Receiver, sondern natives UPnP/DLNA:

- separate Geräteprofile für Xbox One und Xbox Series S/X
- AVTransport für URI, Play, Pause, Stop, Seek und Status
- RenderingControl für Lautstärke und Mute, falls vom Gerät angeboten
- Xbox-Priorisierung bei der Geräteerkennung
- lokaler Relay für geschützte Streams
- zeitlich angepasste Übergangssequenzen je Geräteprofil
- Wiederholungs-/Recovery-Pfad für `Transition not available`, UPnP 701 und vergleichbare Zustände

### Samsung Tizen über DLNA

Der vorhandene DLNA-Pfad hat zusätzlich ein Samsung-Tizen-Profil mit Stop/SetURI/Play-Verzögerungen und Transition-Recovery. Damit bleibt ein nativer Fallback vorhanden, wenn Smart View nicht verfügbar ist oder der TV kein DMP unterstützt.

### Weitere Backends

Chromecast, FCast, generisches DLNA und die Android-Miracast-Systemauswahl bleiben erhalten. Der Player trennt die Ziele sauber und beendet beim Wechsel das vorherige Backend.

## Metadaten, Cover und Fortsetzung

- `MetadataInventory.kt` gleicht erwartete Katalogeinträge mit vorhandenen Metadaten ab.
- Fehlende und unvollständige Einträge werden getrennt ausgewiesen.
- `CatalogMetadataWorker.kt` speichert einen Katalog-Fingerprint und abgeschlossene Slugs als Checkpoint.
- Abgebrochene Aktualisierungen setzen beim nächsten Lauf fort.
- Laufende eindeutige Arbeit wird nicht mehr unnötig durch einen Neustart ersetzt.
- Coverdateien werden als Teil des Metadatenbestands gezählt, exportiert, angezeigt und gemeinsam zurückgesetzt.
- Einstellungen zeigen Metadaten- und Covercache-Status.

## Player, Beschreibungen und Navigation

- lange Anime-, Staffel- und Episodentexte sind auf- und zuklappbar
- Staffelbeschreibung hat einen eigenen Bereich
- fehlt eine echte Staffelbeschreibung, wird die Serienbeschreibung sichtbar als Fallback angezeigt
- begonnene, nicht abgeschlossene Staffeln/Folgen werden separat hervorgehoben und bleiben dynamisch einstellbar
- Cast kann global in den Einstellungen aktiviert oder deaktiviert werden
- Chromecast-Aktion erscheint nur bei verfügbarer Cast-Unterstützung
- obere Playeraktionen sind zusätzlich in der Playernavigation zusammengeführt
- Cast-, Sprache- und Hoster-Auswahl wurden übersichtlicher strukturiert
- Einstellungs-Floating-Button bewegt sich direkt über `graphicsLayer`, speichert erst am Drag-Ende und löst nach einer Bewegung keinen versehentlichen Klick aus

## UI-Fixes aus dem Screenshot

### Suche und Wischgeste

Suchtext oder sichtbare Vorschläge erzwingen den Kopfbereich nicht mehr dauerhaft. Beim Scrollen kann der Bereich weiterhin ausgeblendet werden; die Wiederherstellungsaktion bleibt erreichbar. Auswahlmodi in Favoriten und Verlauf bleiben geschützt sichtbar.

### Diagnoseaktionen

Die unteren Schaltflächen passen sich an die verfügbare Breite an:

- sehr schmal: untereinander
- normale Telefonbreite: Kopieren und Teilen nebeneinander, Leeren darunter in voller Breite
- breite Ansicht: alle drei nebeneinander
- mindestens 52 dp Höhe sowie einzeilige, gekürzte Beschriftungen

Damit bricht „Kopieren“ nicht mehr wie im Screenshot in mehrere unleserliche Zeilen um.

### News-WebView

Die isolierte News-WebView erlaubt Scrollen, Langdruck, Textauswahl und die systemeigene Kopieren-/Einfügen-Auswahl. Seiten erhalten zusätzlich CSS für auswählbaren Text.

## Wiedergabestatus und Benachrichtigung

`PlaybackService.kt` nutzt die Android-MediaSession als gemeinsame lokale Steuerbasis, aktualisiert den Wiedergabestatus aus realen Playerereignissen und enthält einen Stale-Session-Workaround. Wird die Wiedergabe tatsächlich beendet bzw. die Sitzung geschlossen, werden Player, MediaSession und Statusleisten-Benachrichtigung bereinigt.

## Warnungsbereinigung

Statisch geprüft:

- Chromecast-Seek verwendet `MediaSeekOptions` statt der veralteten Java-Seek-Methode
- `OpenInNew` verwendet die AutoMirrored-Variante
- der veraltete `setAlwaysVisible`-Aufruf ist entfernt
- die früher gemeldeten dauerhaft wahren Bedingungen im Relay sind umstrukturiert

## Prüfung ohne Gradle-Build

Auf ausdrücklichen Wunsch wurde **kein Gradle-Build** gestartet.

Ausgeführt wurden ausschließlich nicht-Gradle-basierte Prüfungen:

- XML- und Stringressourcenprüfung für Basis, Deutsch und Englisch
- Paket-, Manifest- und Pflichtdateiprüfung
- Regressionserkennung für bekannte Warnungs-/Compile-Muster
- statischer Feature-Gate mit 37 Funktionsgruppen
- isolierter Kotlin-Typcheck des neuen Smart-View-Adapters mit lokalen API-Stubs
- Prüfung auf Build-, IDE-, Cache- und Schlüsseldateien

Ergebnis: `STATIC RELEASE GATE: PASSED`.

Nicht möglich ohne Vendor-Datei und echte Hardware:

- Laufzeittest des Samsung Smart View SDK
- Wiedergabetest auf konkreten Samsung-TV-Modellen
- Xbox-One-/Series-S/X-Gerätetest
- vollständiger Android-Debug-/Release-Build

Vor einer Veröffentlichung sind daher ein echter Gradle-Build und Gerätetests erforderlich.
