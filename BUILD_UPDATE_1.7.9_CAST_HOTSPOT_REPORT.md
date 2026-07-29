# Build-Update 1.7.9 – Cast und Handy-Hotspot

## Ziel

Die lokale Cast-Unterstützung wurde so erweitert, dass kompatible Empfänger sowohl in einem normalen WLAN als auch als Clients des vom Android-Handy bereitgestellten Hotspots möglichst zuverlässig gefunden und mit Hoster-Streams versorgt werden. AirPlay und AirServer sind ausdrücklich nicht enthalten.

## Eingebaute Protokolle und Bibliotheken

### Google Cast / Chromecast

- offizielles Google Play Services Cast Framework
- explizite AndroidX-MediaRouter-Abhängigkeit 1.8.1
- Initialisierung ausschließlich nach einer bewussten Cast-Aktion; der normale Hoster-Player bleibt unabhängig
- Übernahme der IP-Adresse des ausgewählten Cast-Empfängers
- Laden über den lokalen Stream-Relay, wenn der Stream am Empfänger zusätzliche Hoster-Header benötigen kann
- Remote Play, Pause, Seek, Stop, Positionsabgleich und lokales Fortsetzen

### DLNA / UPnP AVTransport

- SSDP-Multicast auf aktiven Schnittstellen
- schnittstellengebundene SSDP-Suche
- Subnetz-Broadcast
- lokaler /24-Unicast-Fallback
- Auswertung bekannter Nachbarn
- manuelle IP-Suche
- Xbox-Priorisierung und generische MediaRenderer-Unterstützung
- AVTransport Play, Pause, Stop und Seek
- lokale Relay-URL für geschützte Streams

### FCast

- offenes FCast-TCP-Protokoll
- Standarderkennung über Android NSD/mDNS (`_fcast._tcp.`)
- MulticastLock während der mDNS-Suche
- paralleler TCP-Probe-Fallback im aktiven privaten /24-Netz
- Nachbartabellen-Fallback und manuelle IP
- Play, Pause, Resume, Stop, Seek und Zustandsrückmeldungen
- lokale Relay-URL für Streams mit Upstream-Headern

### Miracast / Wi-Fi Display

Miracast wird über die native Android-/Hersteller-Systemauswahl gestartet. Dazu werden `ACTION_CAST_SETTINGS`, der ältere Wi-Fi-Display-Einstieg und die allgemeinen Drahtloseinstellungen nacheinander abgesichert geprüft. Eine private oder nicht öffentliche Miracast-Sender-API wird nicht vorgetäuscht.

## Lokaler Cast-Stream-Relay

Der neue `LocalCastRelay` läuft nur während einer Cast-Nutzung auf dem Handy und stellt dem Empfänger eine tokenisierte lokale HTTP-Adresse bereit.

Er unterstützt:

- Referer, Cookie, User-Agent und weitere sichere Upstream-Request-Header
- Weiterleitung von Range, If-Range, ETag-/Änderungsprüfungen und Accept
- HTTP-206/Content-Range für seekbare Streams
- Umschreiben von HLS-Varianten, Segmenten, Schlüsseln, Maps und Untertitel-URIs
- grundlegendes Umschreiben von DASH-BaseURLs und Medien-/Initialisierungsattributen
- Auswahl der lokalen Adresse auf derselben Route bzw. im selben Subnetz wie der Empfänger
- typische Android-Hotspot-Gateway-Schnittstellen sowie private IPv4-, Link-Local-, CGNAT- und IPv6-ULA-Clients
- Zugriff nur aus lokalen Netzen und nur mit zufälligem Sitzungstoken

Damit muss der Fernseher oder Cast-Empfänger Hoster-Cookies und Referer nicht selbst senden; das Handy lädt den Stream mit den erforderlichen Headern und reicht ihn lokal weiter.

## Android-16-/Berechtigungsbehandlung

`NEARBY_WIFI_DEVICES`, WLAN-Zustand und Multicast-Berechtigung sind im Manifest vorhanden. Vor DLNA-/FCast-Suche und vor der Chromecast-Auswahl wird ab Android 13 die Berechtigung „Geräte in der Nähe“ geprüft. Das deckt auch die Android-16-Vorbereitung für lokale Netzwerk-Sockets ab.

## Bewusste technische Grenzen

Eine Android-App kann nicht garantieren, ausnahmslos jedes als „castfähig“ beworbene Gerät zu finden oder jedes Format abzuspielen:

- Das Ziel muss Google Cast, DLNA/UPnP AVTransport, FCast oder die Miracast-Systemfunktion tatsächlich unterstützen.
- Manche Hersteller aktivieren im Hotspot AP-/Client-Isolation. Die App umgeht fehlendes Multicast mit Broadcast, Unicast, Subnetzscan, Nachbartabelle und manueller IP, kann aber eine vom Kernel, Hotspot-Firmware oder Empfänger blockierte Verbindung nicht erzwingen.
- Miracast besitzt für normale Drittanbieter-Apps keine universelle öffentliche Sendersteuerung; deshalb wird die Systemoberfläche verwendet.
- DRM, proprietäre Authentifizierung, nicht unterstützte Video-/Audio-Codecs oder receiverseitige Formatgrenzen bleiben außerhalb der App-Kontrolle.
- AirPlay und AirServer sind nicht implementiert.

## Regressionsschutz

- Google Cast, Relay und Discovery werden nicht beim Öffnen eines Hosters gestartet.
- Die lokale Wiedergabe verwendet weiterhin den Wiedergabepfad aus dem 1.7.8-Hotfix.
- Relay und Controller werden beim Verlassen des Players geschlossen.

## Prüfung

- XML- und Stringressourcenprüfung
- statischer Release-Gate mit 31 Funktionsgruppen
- isolierter Kotlin-Typcheck der neuen Relay-, FCast- und Chromecast-Komponenten mit Android-/Cast-API-Stubs
- HLS-/DASH-Rewrite-Funktionstests
- Archivprüfung nach erneutem Entpacken

Ein vollständiger Gradle-Build konnte in der isolierten Umgebung nicht ausgeführt werden, weil die benötigte Gradle-Distribution nicht aus dem Internet geladen werden konnte. Deshalb ist vor Veröffentlichung zusätzlich ein realer Debug-/Release-Build mit angeschlossenem Android-Gerät sowie je einem Chromecast-, DLNA-/Xbox- und FCast-Empfänger erforderlich.
