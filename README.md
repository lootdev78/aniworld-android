# AniWorldAndroid

Benutzerfreundlicher Android-Port des `aniworld-cli`-Workflows mit Kotlin, Jetpack Compose, Material 3, Room und AndroidX Media3/ExoPlayer.


## 1.7.7 Einstellungen, Startseite und erweitertes Casting

- strukturiert die Einstellungen in klar getrennte Material-3-Bereiche mit überarbeiteten Bezeichnungen und Hilfetexten
- prüft beim Öffnen der Einstellungen den lokalen Metadatenbestand und aktiviert „Vorhandene löschen“ sowie „Exportieren“ nur bei tatsächlich vorhandenen Daten
- ordnet die Metadatenaktionen als Aktualisieren, Vorhandene löschen, Exportieren und Importieren an
- ergänzt einen globalen Diagnose-/Logging-Schalter und eine eigene Vollbild-Diagnoseansicht statt des bisherigen Popups
- zeigt Favoriten als frei konfigurierbaren Startseitenbereich und speichert Sichtbarkeit sowie Reihenfolge aller Startseitenbereiche dauerhaft
- belässt die vorhandenen News-Karten unverändert, öffnet Artikel aber in einem isolierten normalen WebView ohne Challenge-, Cloudflare- oder Medien-Extractor-Logik
- erweitert den Player um offizielles Google Cast für Chromecast, FCast als offenes Cast-Protokoll und den Android-Systemeinstieg für Miracast; AirPlay/AirServer sind nicht enthalten
- erweitert DLNA/UPnP- und FCast-Erkennung um direkte lokale Subnetz-/Nachbar-Fallbacks für Geräte, die als Clients mit dem Handy-Hotspot verbunden sind
- begrenzt parallele Anime-Detailabrufe bei der Katalog-Metadatenaktualisierung auf maximal vier gleichzeitig

## 1.7.6 Offline-Metadaten und Erststart-Berechtigung

- blendet Suche, Sortierung und Filter in Favoriten und Verlauf erst ein, sobald tatsächlich Einträge vorhanden sind
- überarbeitet die rechte `#`-/A–Z-Leiste als deutlich sichtbare, vollhohe Schnellnavigation bei alphabetischer Sortierung
- exportiert Katalog- und optional gespeicherte Startseitenmetadaten über den Android-Dokumentanbieter als JSON-Datei
- importiert eine ausgewählte Metadatendatei über den Android-Dokumentanbieter und schaltet den Katalog danach sofort für die Offline-Nutzung frei
- ergänzt einen umschaltbaren Offline-Modus für die Startseite; im Offline-Modus wird der letzte gespeicherte Feed verwendet und nur über die manuelle Einstellungsaktion neu gecrawlt
- fragt ab Android 13 die Benachrichtigungsberechtigung beim ersten App-Start einmalig über den Android-Systemdialog ab

## 1.7.5 Hotspot-Cast und Bibliotheksleisten

- erweitert Xbox-/DLNA-Erkennung für Android-Hotspots um schnittstellengebundene SSDP-Suche, Subnetz-Broadcast, lokalen /24-Unicast-Fallback und bekannte Hotspot-Nachbarn
- ergänzt im Player eine manuelle Xbox-IP-Suche als Fallback, wenn ein Hotspot Multicast unterdrückt
- lässt Such-, Filter- und Sortierbereiche in Katalog, Favoriten und Verlauf mit der Scrollrichtung weich ein- und ausfahren
- zeigt bei alphabetischer Sortierung in Favoriten und Verlauf dauerhaft die vollständige `#`-/A–Z-Schnellleiste wie im Katalog
- behält zeitliche und benutzerdefinierte Schnellnavigation für die anderen Sortierungen bei

## 1.7.4 Xbox-Casting

- ergänzt im Player eine Cast-Schaltfläche für Xbox One sowie Xbox Series S/X über DLNA/UPnP AVTransport
- sucht kompatible Renderer per SSDP im lokalen Netzwerk und priorisiert erkannte Xbox-Geräte
- sendet die aktuelle Stream-URL inklusive Titel-Metadaten an die Xbox und startet an der aktuellen Wiedergabeposition
- unterstützt Fernsteuerung für Wiedergabe/Pause, Stop, Zeitleisten-Suche sowie Rückkehr auf das Android-Gerät
- lässt die globale HTTPS-only-Richtlinie unverändert; lokale UPnP-HTTP-Endpunkte werden über einen isolierten Socket-Client angesprochen
- fordert die Berechtigung für Geräte in der Nähe erst beim Öffnen der Cast-Auswahl an

## 1.7.3 Picture-in-Picture, Anime-News und Metadaten-Benachrichtigung

- konsolidiert den vollständigen Funktionsumfang der vorherigen Projektstände in einer gemeinsamen Codebasis
- behebt den Kotlin-Compilerfehler im generischen DataStore-Migrationsparser: Callback-Funktionen werden nicht mehr fälschlich als `inline` durch eine Java-Iteration gereicht
- enthält einen statischen Release-Gate-Checker für Paket, Ressourcen, Manifest, Featuremarker und bekannte Regressionen
- ergänzt Bild-in-Bild im internen Player, eine dauerhaft sichtbare und verschiebbare Wiedergabezeitleiste sowie reine Icon-Aktionen für Neustart und ±10 Sekunden
- liest den originalen Startseitenabschnitt „Anime News“ aus und ordnet Bilder artikelgenau zu; fehlende Bilder werden über das Open-Graph-Bild des verlinkten Artikels ergänzt
- zeigt den Katalog-Metadatenimport als laufende Android-Systembenachrichtigung mit Fortschritt, Abbrechen und Abschlussstatus
- enthält eine CI-Pipeline, die den statischen Gate, einen sauberen Debug-Build und Android Lint ausführt und anschließend APK plus SHA-256-Prüfsumme bereitstellt
- behält den Debug-Paket-Suffix `.debug`; es werden keine privaten Signing-Schlüssel ausgeliefert

## Version

`1.7.7-settings-home-cast`

## Paket und Projekt

- Android-Paket/Namespace: `io.github.lootdev78.aniworld`
- Projekt-Credits und Repository sind direkt in den Einstellungen verlinkt
- Sprachvarianten zeigen Länderflaggen für Deutsch, Englisch und Japanisch

## App-Icon und Sprachen

- Launcher-Icon für alle klassischen Android-Dichten
- adaptives Icon und Round Icon ab Android 8
- monochromes Themed Icon ab Android 13
- Icon-Gestaltung auf Basis des aktuellen AniWorld-Wordmarks
- sämtliche sichtbaren App-Texte als Android-Stringressourcen
- Englisch als Standardsprache in `values/` und `values-en/`
- Deutsch in `values-de/`
- Android-Sprachauswahl über `locales_config.xml` für Deutsch und Englisch

## Oberfläche

- Streaming-Startseite im Netflix-/Prime-Stil
- Hero-Bereich und horizontale Inhaltsreihen
- „Beliebt bei AniWorld“
- „Die 50 neuesten Episoden“
- „Neue Animes“
- „Derzeit beliebt“
- „Das sehen andere AniWorld Nutzer“
- eigene Top-50-/Meistgesehen-Sammlung
- Vollansichten für alle Startseiten-Sammlungen über die anklickbaren Abschnittsüberschriften
- persönliche „Weiterschauen“-Reihe
- persönliche Favoriten-Reihe auf der Startseite
- dauerhaft gespeicherte Sichtbarkeit und Reihenfolge aller Startseitenbereiche
- reduzierte Navigation ohne obere App-Leiste; schwebende Bottom Bar auf Smartphones und Navigation Rail auf größeren Displays
- frei verschiebbarer Einstellungsbutton mit begrenzter, rotationsfester DataStore-Position und Rücksetzfunktion
- Material-3-Dark-Mode, optional dynamische Android-Farben sowie eine feste Akzentfarbpalette in den Einstellungen
- Pull-to-refresh, Skeleton-Ladezustände, leere Zustände und verständliche Fehlermeldungen
- Cover, Anime-/Film-/Folgentitel, Genres, Veröffentlichungsjahr, Altersangabe, Beschreibungen und Fortschrittsanzeigen
- alle Sucheingaben mit bis zu drei direkten Treffervorschlägen und einer Löschen-Schaltfläche am rechten Rand
- Katalogsuche mit gespeicherten Suchbegriffen, A–Z- und Genre-Filter
- durchgehend scrollbarer Katalog ohne sichtbare Seitennavigation, mit A–Z-Schnellleiste am rechten Rand

## Bibliothek und Verlauf

- Favoriten per Herz
- Favoritenliste mit alphabetischer, zeitlicher oder eigener Sortierung
- eigene Verlaufseite mit denselben Raster-, Kompakt- und Detailansichten wie Favoriten
- angefangen/gesehen-Markierungen für Folgen und Filme
- Staffelstatus und Wiedergabefortschritt als `HH:MM:SS – HH:MM:SS`; Position und Laufzeit werden offline gespeichert
- Verlaufsfilter für alle, begonnene, vollständig gesehene und favorisierte Inhalte
- zuletzt verwendete Suchbegriffe
- gespeicherte Katalogsuchbegriffe mit verzögerter Speicherung
- Wiederherstellung der zuletzt geöffneten Serie und Staffel nach einem Prozess-Neustart

Die Nutzerdaten werden ausschließlich lokal in Room und DataStore gespeichert. Android-Backup, Geräteübertragung, Cloud-Backup sowie Export/Import sind nicht enthalten; `android:allowBackup` ist deaktiviert.

## Offline und Performance

- Startseite, Anime-Details, Staffeln und Episodenseiten werden live geladen und nicht als Webseiten-Metadaten gecacht
- Room speichert für den alphabetischen Katalog Titel, URL, Beschreibung, Cover-/Poster-URL und Genres offline; Startseitenmetadaten bleiben live
- vollständige Webseiten werden nicht offline gecacht; dauerhaft gespeichert werden nur die für den Katalog benötigten strukturierten Metadaten
- manuell startbarer WorkManager-Hintergrundauftrag für die vollständige Katalog-Metadatenaktualisierung; bis zum abgeschlossenen Erstimport zeigt der Katalog Skeletons und deaktivierte Bedienelemente
- Fortschritt und Abbrechen ausschließlich über eine Android-Systembenachrichtigung; der Auftrag läuft bei geschlossener Ansicht weiter
- Coil-Disk- und Memory-Cache mit anime- und URL-spezifischen Cover-Schlüsseln
- zentrale Filterung von Logo-, Branding-, Header-, Placeholder-, Tracking- und unplausibel dimensionierten Bildern
- Rücksetzfunktion für gespeicherte Coverdaten, Katalogseiten, Metadaten sowie Coil-Bildcache
- begrenzte parallele Katalog- und Metadatenanfragen; Anime-Detailmetadaten werden mit maximal vier gleichzeitigen Jobs verarbeitet
- automatische Wiederholungsversuche mit Backoff und eine kurze DNS-Sperre gegen Retry-Kaskaden
- der vollständige Katalog wird erst beim Öffnen des Katalog-Tabs geladen, damit der App-Start keine tausenden Kataloganfragen auslöst

## AniWorld-Workflow

- Suche über `/ajax/search`
- Serien-, Staffel-, Film- und Episodennavigation
- Startseiten- und Katalogparser
- Sprach- und Hosterprioritäten
- gruppierte Hoster-Auswahl nach Sprache, direkter bevorzugter Hoster und interner Medien-Detector
- automatischer Hoster-Fallback und optionale Stream-Erreichbarkeitsprüfung; Hoster mit Browserprüfung werden zunächst übersprungen
- Resolver-Diagnose
- gezielte Hoster-WebView für eine eventuell erforderliche CAPTCHA-/Turnstile-Prüfung, nur wenn kein direkter Alternativhoster funktioniert
- gemeinsamer Cookie-Speicher für WebView und OkHttp
- Fortsetzen der unterbrochenen Aktion nach erfolgreicher manueller Verifizierung
- sichtbare Aktion „Prüfung abgeschlossen – Hoster erneut versuchen“ übernimmt die WebView-Session und startet denselben Hoster erneut
- Erkennung direkt angeforderter HLS-, DASH-, SmoothStreaming- und progressiver Medien-URLs erst nach vollständig geladener Hoster-Seite
- getrennte AniWorld-Verifizierung und Hoster-WebView, einklappbare und gespeicherte Session-/Medienbereiche
- nativer, konfigurierbarer WebView-Filter für typische Werbung, Tracking, Popups und Weiterleitungen sowie temporäre Domain-Ausnahme
- Übergabe von Referer, User-Agent und vorhandenen Session-Cookies an Media3
- optionaler Start in einer installierten externen Video-App; die Funktion muss zuvor in den Einstellungen aktiviert werden

Die App löst Web-Challenges nicht automatisch und umgeht keine Schutzprüfung. Der Nutzer führt eine eventuell angezeigte Prüfung selbst durch. Der Medien-Detector verarbeitet ausschließlich bereits sichtbare HTTP(S)-Anfragen mit einem eindeutig Media3-kompatiblen Format. Er entschlüsselt keine Skripte, rekonstruiert keine Tokens und umgeht keine Zugriffskontrollen.

## Player

Die Wiedergabe läuft direkt in der App über Media3/ExoPlayer. Der Player ist bewusst reduziert:

- Tippen: Pause oder Weiter
- vertikale Geste links: Bildschirmhelligkeit
- vertikale Geste rechts: Medienlautstärke
- HLS-, DASH-, SmoothStreaming- und progressive Media3-Wiedergabe
- Wiedergabe über einen `MediaSessionService` mit offizieller Android-Medienbenachrichtigung
- Titel/Folge, Pause/Wiedergabe, Stop sowie vorherige/nächste Folge in der Systemoberfläche
- kontextbezogene `POST_NOTIFICATIONS`-Abfrage ab Android 13
- Schließen und lokales Speichern des realen Fortschritts
- vorherige/nächste Folge innerhalb der geöffneten Staffel
- Wechsel von Sprache und Hoster direkt in der eingeblendeten Player-Steuerung
- dauerhaft sichtbare Wiedergabezeitleiste bei eingeblendeten Steuerelementen mit aktuellem Zeitpunkt, Videolänge und verschiebbarem Positionspunkt
- Doppeltipp links/rechts sowie Icon-Aktionen für zehn Sekunden zurück/vor
- Icon zum Starten von Anfang und Picture-in-Picture-Schaltfläche
- DLNA/UPnP-Casting einschließlich Xbox mit Gerätesuche, Wiedergabe/Pause, Remote-Suche und Trennen/Fortsetzen
- offizielles Google Cast für Chromecast über die Android MediaRoute-Auswahl
- FCast-Wiedergabe als offenes Cast-Protokoll sowie Miracast-Einstieg über die Android-Systemauswahl
- lokaler Subnetz- und Nachbartabellen-Fallback für DLNA/FCast-Geräte am Handy-Hotspot
- optionales Auto-Next mit achtsekündigem, abbrechbarem Countdown
- stabilisierte Helligkeits-/Lautstärkegesten mit seitlichen Zonen und Bewegungsschwelle
- automatisch ausblendende Player-Oberfläche während laufender Wiedergabe; Interaktion oder Pause blendet sie wieder ein
- Rückkehr aus dem Player in die zuletzt geöffnete Staffel- oder Filmliste einschließlich Scrollposition

Es gibt weiterhin keine Qualitäts- oder Geschwindigkeitsmenüs. Picture-in-Picture ist über das Player-Icon verfügbar. Unterstützt werden DLNA/UPnP-Renderer einschließlich Xbox, Chromecast über Google Cast, FCast sowie Miracast über die Android-Systemauswahl. Lokale Renderer können sich im selben WLAN oder als Clients am vom Android-Gerät bereitgestellten Hotspot befinden. AirPlay und AirServer sind nicht enthalten.

## Navigation

Die App verwendet Navigation Compose für Start, Katalog, Favoriten, Verlauf und Details. Die separate Suchseite wurde entfernt; Suche und Filter befinden sich direkt im Katalog. Ein lokaler Deep Link kann eine Serie öffnen:

```text
aniworldapp://anime/<slug>
```

## Cover-, Info- und Listenansichten

- AniWorld-Cover werden bevorzugt aus den eigentlichen Cover-/Poster-Elementen gelesen; Logo-, Icon-, Tracking- und Placeholder-Bilder werden verworfen.
- Katalogeinträge zeigen die echten AniWorld-Cover beziehungsweise Poster und können als kompakte Liste, Detail-Liste oder Raster angezeigt werden.
- Favoriten unterstützen dieselben drei Ansichtsarten; die Auswahl wird dauerhaft gespeichert.
- Optionale Zähler werden erst ab einem Wert von 1 angezeigt.
- Langes Drücken auf einen Anime lädt nur aktuelle Anime-Informationen. Langes Drücken auf eine Episode zeigt Anime- und Episodeninformationen.
- Startseitenbereiche zeigen zunächst zehn Einträge; Überschrift und „Mehr anzeigen“ öffnen eine eigene vollflächige, deduplizierte Sammlungsseite ohne Bottom-Sheet-Überlauf.
- Die Detailseite besitzt einen kompakten Netflix-artigen Bildheader, Verlauf für lesbaren Text, direkte Wiedergabe/Fortsetzen-Aktion, Staffelchips und kompakte Episodenzeilen.
- Detailinformationen können Jahr, Altersfreigabe, Genres, Regie, Produzenten, Darsteller, Land, IMDb und Nutzerbewertung enthalten, sofern AniWorld diese Angaben bereitstellt.

## Webfilter-Hinweis

Der integrierte Filter ist eine native, bewusst konservative WebView-Implementierung. Eine unveränderte 1:1-Ausführung der Browser-Erweiterung uBlock Origin ist in Android WebView nicht möglich; das Projekt behauptet daher keine vollständige uBlock-Origin-Kompatibilität. Die vorhandenen Filterkategorien können in den Einstellungen einzeln aktiviert und bei Hoster-Problemen temporär für die aktuelle Domain ausgesetzt werden.

## Projektstruktur

```text
AniWorldAndroid/
├── app/
│   ├── schemas/          # Room-Schemata beim lokalen Build
│   └── src/main/         # Compose-UI, Datenbank, Parser, Resolver und Player
├── gradle/               # Version Catalog und Wrapper
├── .github/workflows/ # CI für Prüfung, Lint und Debug-APK
├── tools/             # statisches Release-Gate
├── FEATURE_MATRIX.md
├── PRODUCTION_AUDIT_REPORT.md
├── THIRD_PARTY_NOTICES/
├── build.gradle.kts
├── settings.gradle.kts
├── gradlew
└── gradlew.bat
```

Es ist kein mpv-Modul enthalten. Die CI-Konfiguration erzeugt ausschließlich ein Debug-APK; private Signing-Schlüssel und Release-Signaturen sind absichtlich nicht enthalten.

## Build

Voraussetzungen:

- Android Studio
- JDK 17
- Android SDK 36
- Internetzugriff beim ersten Gradle-Sync

Statischer Projektcheck:

```text
python3 tools/verify_project.py
```

Linux/macOS:

```text
./gradlew --no-daemon --stacktrace clean :app:assembleDebug
```

Windows:

```text
gradlew.bat --no-daemon --stacktrace clean :app:assembleDebug
```

CI-Debug-Build:

Die Workflow-Datei `.github/workflows/android-debug.yml` führt den statischen Gate, `:app:assembleDebug` und `:app:lintDebug` aus. Das erzeugte Debug-APK und seine SHA-256-Datei werden als GitHub-Actions-Artefakt hochgeladen.

Release-Build:

```text
./gradlew assembleRelease
```

Für eine eigene Veröffentlichung muss lokal ein persönlicher Signing-Key in Android Studio konfiguriert werden. Private Signaturschlüssel sind absichtlich nicht im Projekt enthalten.

## Build-Konfiguration

- Android Gradle Plugin `8.13.2`
- Gradle `8.14.3`
- Kotlin `2.2.21`
- Room `2.8.4`
- Navigation Compose `2.9.8`
- Media3 `1.10.1`
- `compileSdk` / `targetSdk` 36
- `minSdk` 26

## Rechtliches

Dieses Projekt hostet keine Medien. Verwende es nur für Inhalte und Quellen, für die du berechtigt bist, und beachte die Nutzungsbedingungen der beteiligten Dienste sowie die Lizenz des zugrunde liegenden Projekts.

## Build-Hinweis für RV2IDE / alte Gradle-Caches

Diese Version verwendet Kotlin **2.2.21**. Alle Kotlin-Stdlib-Varianten werden ebenfalls auf `2.2.21` festgesetzt, damit Gradle keine inkompatible `2.4.x`-Metadatenversion auswählt.

Nach dem Ersetzen einer älteren Projektversion einmal **Clean Project** ausführen. Falls RV2IDE weiterhin eine ältere oder neuere Kotlin-Stdlib aus dem Cache benutzt, den Projektordner `.gradle` löschen und anschließend neu synchronisieren. Danach `:app:assembleDebug` erneut starten.

