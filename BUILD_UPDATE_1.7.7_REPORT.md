# AniWorld Android 1.7.7 – Einstellungen, Startseite, Cast und Metadatenjobs

## Umgesetzte Anforderungen

### Einstellungen und Metadatenverwaltung

- Die Einstellungsseite ist in klar beschriftete Material-3-Karten mit Untertiteln und zusammengehörigen Optionen gegliedert.
- Die Metadatenaktionen stehen in der gewünschten Reihenfolge:
  1. **Aktualisieren**
  2. **Vorhandene löschen**
  3. **Exportieren**
  4. **Importieren**
- Beim Öffnen der Einstellungen wird der lokale Metadatenbestand asynchron geprüft.
- **Vorhandene löschen** und **Exportieren** sind erst nach abgeschlossener Prüfung und nur bei vorhandenem Bestand anklickbar.
- Das Löschen ist durch eine Bestätigung geschützt und aktualisiert anschließend Bestandsanzeige und Katalogzustand.
- Import und Export verwenden weiterhin den Android-Dokumentanbieter und verändern weder Favoriten noch Verlauf.

### Diagnose und globales App-Logging

- Ein persistenter Diagnose-Schalter steuert das gesamte interne `AppLogger`-Logging.
- Bei deaktivierter Diagnose werden keine neuen App-Logeinträge aufgenommen; vorhandene In-App-Einträge werden entfernt.
- Der frühere Diagnose-Popup-Dialog wurde durch eine eigene Vollbildansicht ersetzt.
- Die Ansicht bietet Ein/Aus, Leeren, Kopieren und Teilen sowie einen klaren Leerzustand.

### Startseite

- Favoriten werden als eigene horizontale Startseitenreihe angezeigt, analog zu persönlichen Bereichen wie „Weiterschauen“.
- Alle vorhandenen Bereiche einschließlich News, Hero, Favoriten, Weiterschauen, „Beliebt bei AniWorld“, neue Episoden, neue Animes, aktuell beliebt, Community und Top 50 können einzeln ein- oder ausgeblendet werden.
- Die Reihenfolge kann in den Einstellungen nach oben oder unten verschoben werden.
- Sichtbarkeit und Reihenfolge werden dauerhaft in DataStore gespeichert und beim nächsten Start wiederhergestellt.

### News

- Die bestehende News-Karten-/Startseitendarstellung wurde nicht neu gestaltet.
- Beim Anklicken eines News-Artikels wird nicht mehr die Challenge-/Cloudflare-/Extractor-WebView verwendet.
- Artikel laufen in einem isolierten normalen WebView ohne Challenge-Cookies, Cloudflare-Sitzungsprüfung, Hoster-Wiederholung, Adblock-Sessionpanel oder Medien-Extractor.

### Player und Cast

- **DLNA/UPnP einschließlich Xbox:** vorhandene AVTransport-Steuerung bleibt erhalten und wurde für lokale Discovery weiter gehärtet.
- **Chromecast:** offizielles Google Cast Framework mit MediaRoute-Schaltfläche, Default Media Receiver, Laden an aktueller Position, Play/Pause/Seek/Stop und Rückkehr zur lokalen Wiedergabe.
- **FCast:** offenes TCP-Cast-Protokoll auf dem Standardport 46899 mit Play/Pause/Resume/Stop/Seek, Stream-Headern, Titelmetadaten und Remote-Zustandsupdates.
- **Miracast:** eigener Menüpunkt öffnet die native Android-Cast-Systemauswahl.
- **AirPlay/AirServer:** absichtlich nicht hinzugefügt.

### Geräte am Handy-Hotspot

- DLNA/UPnP sendet Discovery nicht nur per normalem SSDP-Multicast, sondern zusätzlich über aktive IPv4-Schnittstellen, Subnetz-Broadcast, direkte lokale /24-Unicast-Ziele und bekannte Nachbarn.
- FCast scannt private aktive /24-Netze per TCP und ergänzt Kandidaten aus der lokalen ARP-/Nachbartabelle.
- Die manuellen IP-Aktionen prüfen sowohl DLNA/UPnP als auch FCast.
- Damit hängt die Erkennung von Clients am vom Handy bereitgestellten Hotspot nicht ausschließlich von Multicast-Weiterleitung ab.

### Katalog-Metadaten

- Anime-Detailmetadaten werden in Batches mit exakt **maximal vier gleichzeitig laufenden Jobs** verarbeitet.
- Der nächste Batch beginnt erst nach Abschluss des vorherigen Vierer-Batches.
- Vorhandene Retry-, Backoff-, Fortschritts- und Abbruchlogik bleibt erhalten.

## Wesentliche neue oder geänderte Dateien

- `app/src/main/java/io/github/lootdev78/aniworld/DiagnosticScreen.kt`
- `app/src/main/java/io/github/lootdev78/aniworld/IsolatedWebPageScreen.kt`
- `app/src/main/java/io/github/lootdev78/aniworld/AniWorldCastOptionsProvider.kt`
- `app/src/main/java/io/github/lootdev78/aniworld/ChromecastController.kt`
- `app/src/main/java/io/github/lootdev78/aniworld/FCast.kt`
- `app/src/main/java/io/github/lootdev78/aniworld/XboxCast.kt`
- `app/src/main/java/io/github/lootdev78/aniworld/PlayerScreen.kt`
- `app/src/main/java/io/github/lootdev78/aniworld/UiScreens.kt`
- `app/src/main/java/io/github/lootdev78/aniworld/AppStore.kt`
- `app/src/main/java/io/github/lootdev78/aniworld/AppViewModel.kt`
- `app/src/main/java/io/github/lootdev78/aniworld/AppLogger.kt`
- `app/src/main/java/io/github/lootdev78/aniworld/Models.kt`
- `app/src/main/java/io/github/lootdev78/aniworld/AniWorldRepository.kt`
- `app/src/main/AndroidManifest.xml`
- `app/build.gradle.kts`
- `gradle/libs.versions.toml`

## Version

- `versionCode`: 64
- `versionName`: `1.7.7-settings-home-cast`
- Debug-Paket: `io.github.lootdev78.aniworld.debug`

## Durchgeführte Prüfungen

- statisches Release-Gate: **bestanden**
- 12 XML-/Manifestdateien geprüft
- 414 Stringschlüssel in Standard, Englisch und Deutsch vollständig
- 26 Kotlin-Dateien und 355 verwendete Stringressourcen statisch geprüft
- 30 zentrale Featuregruppen geprüft
- neue FCast- und Chromecast-Controller zusätzlich mit isolierten Kotlin-/API-Stubs typgeprüft
- Projektbaum auf versehentliche Backups, Build-Ausgaben, APK/AAB-Dateien, lokale Eigenschaften und private Signing-Dateien geprüft

## Nicht vollständig ausführbar in dieser Umgebung

Ein vollständiger Android-/Gradle-Build konnte im bereitgestellten Offline-Container nicht abgeschlossen werden. Der Gradle-Wrapper versuchte Gradle 8.14.3 von `services.gradle.org` zu laden; die Umgebung besitzt keine funktionierende externe DNS-/Netzwerkverbindung und keinen passenden Gradle-Cache.

Verbindlicher lokaler Build-Test:

```bash
./gradlew --no-daemon --stacktrace --warning-mode all clean :app:assembleDebug
```

Empfohlene Gerätetests:

1. Einstellungen öffnen und Metadaten-Schaltzustände mit leerem sowie gefülltem Bestand prüfen.
2. Diagnose ausschalten, App bedienen und sicherstellen, dass keine neuen Einträge erscheinen.
3. Startseitenbereiche neu ordnen/ausblenden, App neu starten und Persistenz prüfen.
4. News öffnen und sicherstellen, dass keine Challenge-/Medien-Extractor-Panels erscheinen.
5. Chromecast, DLNA/Xbox und FCast im normalen WLAN testen.
6. Android-Handy als Hotspot verwenden, Empfänger damit verbinden und DLNA/FCast-Discovery sowie manuelle IP prüfen.
7. Miracast-Menüpunkt auf einem Gerät mit und ohne Herstellerunterstützung testen.
8. Metadatenaktualisierung starten und Fortschritt/Abbruch bei mehreren Anime-Einträgen testen.
