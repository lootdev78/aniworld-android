# AniWorld Android 1.7.4 – Xbox-Cast-Update

## Umgesetzt

- Cast-Schaltfläche direkt in der Player-Kopfzeile.
- SSDP-Suche nach UPnP/DLNA-MediaRenderern im lokalen Netzwerk.
- Xbox One und Xbox Series S/X werden anhand von Gerätename, Modell und Hersteller priorisiert.
- AVTransport-Steuerung mit `SetAVTransportURI`, `Play`, `Pause`, `Stop`, `Seek`, `GetPositionInfo` und `GetTransportInfo`.
- Start der Xbox-Wiedergabe an der aktuellen Playerposition.
- Player-Zeitleiste und ±10-Sekunden-Aktionen steuern bei aktiver Übertragung die Xbox.
- Tippen auf die Cast-Ansicht schaltet Wiedergabe/Pause auf der Konsole um.
- Trennen übernimmt die Xbox-Position und setzt die Wiedergabe auf Android fort.
- Lokale SSDP-/UPnP-Kommunikation nutzt Multicast-Lock und die Berechtigung für Geräte in der Nähe.
- Die App-weite Einstellung `usesCleartextTraffic=false` bleibt erhalten; lokale HTTP-UPnP-Endpunkte werden isoliert über Raw-Sockets angesprochen.

## Technische Grenze

DLNA übergibt der Xbox eine Medien-URL, aber keine beliebigen App-spezifischen Cookie- oder Referer-Header. Ein Stream kann deshalb im internen Player funktionieren und auf der Xbox dennoch abgelehnt werden. In diesem Fall sollte im Player ein anderer Hoster gewählt werden.

## Prüfungen

- Statisches Release-Gate: bestanden.
- 21 Kotlin-Dateien per Kotlin-PSI: 0 Syntaxfehler.
- `XboxCast.kt` zusätzlich gegen isolierte Kotlin-/API-Stubs kompiliert.
- 12 XML-/Manifestdateien gültig.
- 329 Stringressourcen in Standard, Deutsch und Englisch vollständig.
- 17 zentrale Featuregruppen geprüft.
- Vollständiger Gradle-Build nicht möglich, da `services.gradle.org` in der Arbeitsumgebung nicht per DNS erreichbar war.
