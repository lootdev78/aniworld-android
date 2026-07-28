# AniWorld Android 1.7.5 – Hotspot-Cast und Bibliotheks-UI

## Xbox-/DLNA-Erkennung im Android-Hotspot

Die Geräteerkennung verwendet nun mehrere lokale Strategien statt ausschließlich einer einzelnen SSDP-Multicast-Anfrage:

- normale SSDP-Multicast-Suche
- an jede aktive lokale IPv4-Schnittstelle gebundene Suche
- Suche an die jeweilige Subnetz-Broadcast-Adresse
- begrenzter Unicast-Scan des lokalen `/24`-Netzes bei WLAN-/Hotspot-Schnittstellen
- direkte Prüfung von Einträgen aus der lokalen ARP-/Nachbartabelle, sofern Android diese freigibt
- zusätzliche SSDP-Ziele `upnp:rootdevice` und `ssdp:all`
- manuelle Eingabe der Xbox-IP im Player als letzter Fallback

Die App lädt weiterhin nur Gerätebeschreibungen, die einen `AVTransport`-Dienst anbieten. Xbox- und Microsoft-Renderer werden in der Auswahlliste priorisiert.

## Katalog, Favoriten und Verlauf

- Die Such-/Filterbereiche reagieren auf die tatsächliche Scrollrichtung mit einer kleinen Hysterese gegen Flattern.
- Beim Hochscrollen werden die Bereiche mit `shrinkVertically`, Translation und Fade weich ausgeblendet.
- Beim Herunterscrollen werden sie weich wieder eingeblendet.
- Die Listen erhalten dadurch sofort die freigewordene Höhe.
- Ein schwebendes Suchsymbol kann den ausgeblendeten Bereich direkt wieder öffnen.
- Favoriten und Verlauf zeigen bei alphabetischer Sortierung die vollständige `#`-/A–Z-Leiste.
- Buchstaben ohne Treffer bleiben sichtbar, sind aber deaktiviert.
- Bei „Zuletzt geändert“ und „Eigene Reihenfolge“ bleiben die vorhandenen zeitlichen beziehungsweise positionsbezogenen Schnellziele erhalten.

## Version

- `versionCode`: 62
- `versionName`: `1.7.5-hotspot-library-ui`
- Debug-Paket: `io.github.lootdev78.aniworld.debug`

## Prüfungen

- Statisches Release-Gate bestanden.
- 21 Kotlin-Dateien mit Kotlin-PSI geprüft: 0 Syntaxfehler.
- 12 XML-/Manifestdateien gültig.
- 336 Stringressourcen in Standard, Deutsch und Englisch vollständig.
- ZIP-Integrität wird nach dem Paketieren geprüft.
- Ein vollständiger Gradle-Build konnte wegen der blockierten DNS-Auflösung von `services.gradle.org` nicht ausgeführt werden.

## Technische Grenze

Kein Android-Workaround kann eine Erkennung garantieren, wenn der Hotspot Client-Isolation erzwingt, die Xbox keine DLNA-Renderer-App geöffnet hat oder das Betriebssystem lokale UDP-Antworten vollständig blockiert. Die manuelle IP-Suche umgeht fehlendes Multicast, benötigt aber weiterhin einen aktiven UPnP/DLNA-Renderer auf der Xbox.
