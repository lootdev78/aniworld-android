# Samsung Smart View SDK: automatische Gradle-Installation

Der vorhandene Samsung-Cast-Code bleibt optional und wird weiterhin per Reflection geladen. Die App funktioniert deshalb auch ohne das proprietäre Samsung-Paket. Neu ist ein Gradle-Installer in `gradle/samsung-smartview-sdk.gradle`, der eine offizielle JAR/AAR automatisch validiert, nach `app/libs/` kopiert und bei späteren Builds wiederverwendet.

## Warum die Lizenz nicht still automatisch angenommen wird

Samsung behandelt Downloads vom Developer Portal als lizenzgebundene Software. Eine rechtliche Zustimmung muss der Benutzer beziehungsweise Projektverantwortliche selbst abgeben. Das Projekt speichert diese Zustimmung deshalb erst nach einem ausdrücklich gestarteten Gradle-Befehl lokal unter `~/.gradle/aniworld/`. Die Zustimmung wird nicht ins Repository geschrieben und nicht an andere Entwickler weitergegeben.

## Einmalige Einrichtung

### Variante A: offizielles Paket bereits heruntergeladen

```bash
./gradlew acceptSamsungSmartViewLicense installSamsungSmartViewSdk \
  -PsamsungSmartViewLicenseAccepted=I_ACCEPT \
  -PsamsungSmartViewSdkFile="$HOME/Downloads/<offizielles-samsung-paket>.zip"
```

Der Installer akzeptiert auch eine direkte `.jar` oder `.aar`. Er sucht im Paket zwingend nach:

- `com.samsung.multiscreen.Service`
- `com.samsung.multiscreen.Search`
- `com.samsung.multiscreen.VideoPlayer`

### Variante B: direkte offizielle Samsung-Download-URL

```bash
./gradlew acceptSamsungSmartViewLicense installSamsungSmartViewSdk \
  -PsamsungSmartViewLicenseAccepted=I_ACCEPT \
  -PsamsungSmartViewSdkUrl="https://<offizielle-samsung-domain>/<paket>" \
  -PsamsungSmartViewSdkSha256="<optional-aber-empfohlen>"
```

Standardmäßig werden nur HTTPS-URLs auf `samsung.com` oder einer Subdomain davon angenommen. Ein interner geprüfter Spiegel muss bewusst mit `-PsamsungSmartViewAllowNonSamsungHost=true` freigeschaltet werden.

### Variante C: Paket liegt im Downloads-Ordner

Nach der einmaligen Lizenzbestätigung genügt:

```bash
./gradlew installSamsungSmartViewSdk
```

Der Task sucht in `~/Downloads` und `~/Download` nach einem aktuellen Smart-View-ZIP/JAR/AAR, validiert die enthaltenen Klassen und installiert es. Ein anderer Ordner kann über `-PsamsungSmartViewDownloadsDir=/pfad` gesetzt werden.

## Danach automatisch

Sobald die lokale Zustimmung gespeichert ist, hängt `preBuild` automatisch von `installSamsungSmartViewSdk` ab. Bei jedem normalen Build gilt dann:

1. vorhandene gültige JAR/AAR verwenden;
2. sonst konfigurierte direkte URL herunterladen;
3. sonst ein passendes Paket im Downloads-Ordner finden;
4. Inhalt und optional SHA-256 prüfen;
5. als `app/libs/samsung-smartview-sdk-2.5.34.jar` oder `.aar` einfügen.

Ohne Zustimmung oder ohne Paket bleibt der Smart-View-Pfad deaktiviert; Chromecast, FCast, DLNA/Xbox, Miracast und der lokale Relay funktionieren weiter.

## Konfiguration über CI-Umgebungsvariablen

- `SAMSUNG_SMARTVIEW_LICENSE_ACCEPTED=I_ACCEPT` — nur für eine bewusst kontrollierte einmalige Bestätigung
- `SAMSUNG_SMARTVIEW_SDK_URL=https://…`
- `SAMSUNG_SMARTVIEW_SDK_FILE=/pfad/paket.zip`
- `SAMSUNG_SMARTVIEW_SDK_SHA256=<hash>`
- `SAMSUNG_SMARTVIEW_AUTHORIZATION=<HTTP-Authorization-Header>`
- `SAMSUNG_SMARTVIEW_AUTO_INSTALL=false` — automatische Installation ausschalten

Eine CI sollte die Lizenzbestätigung nicht bei jedem Lauf vortäuschen. Besser ist ein geschütztes Runner-Image mit bereits akzeptierter lokaler Markierung oder ein internes Artefakt-Repository, dessen Nutzung durch die Organisation geklärt ist.

## Hilfstasks

```bash
./gradlew showSamsungSmartViewSdkInfo
./gradlew openSamsungSmartViewDownloadPage
./gradlew verifySamsungSmartViewSdk
./gradlew removeManagedSamsungSmartViewSdk
```

Offizielle Seiten:

- Download: `https://developer.samsung.com/smarttv/develop/extension-libraries/smart-view-sdk/download.html`
- Bedingungen: `https://developer.samsung.com/terms`
