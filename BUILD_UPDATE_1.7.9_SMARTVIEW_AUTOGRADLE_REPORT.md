# AniWorld Android 1.7.9 – Samsung Smart View Auto-Gradle Update

## Ziel

Das optionale proprietäre Samsung Smart View SDK kann jetzt über Gradle installiert und in `app/libs` eingebunden werden, ohne die vorhandene Kotlin-Cast-Implementierung zu ersetzen.

## Änderungen

- neue Datei `gradle/samsung-smartview-sdk.gradle`
- ein kleiner Apply-Hook in `app/build.gradle.kts`
- aktualisierte Dokumentation in `app/libs/README_SMARTVIEW_SDK.md`
- lokale SDK-Binärdateien und Installer-Metadaten in `.gitignore`

## Verhalten

- Der Installer validiert `Service`, `Search` und `VideoPlayer` in JAR/AAR.
- ZIP-Pakete werden Zip-Slip-sicher entpackt.
- Direkte Downloads verwenden HTTPS und standardmäßig nur Samsung-Domains.
- Ein optionaler SHA-256-Wert kann erzwungen werden.
- Vor `preBuild` wird eine fehlende SDK-Datei automatisch aus einer konfigurierten URL, Datei oder dem Downloads-Ordner installiert.
- Ohne SDK oder ohne lokale Lizenzbestätigung bleibt der optionale Backend-Pfad deaktiviert und der normale Build wird bei indirekter Ausführung nicht blockiert.
- Eine ausdrücklich angeforderte Installation schlägt mit einer klaren Anleitung fehl, falls Zustimmung oder Quelle fehlt.

## Lizenzannahme

Die Samsung-Bedingungen werden nicht stillschweigend oder im Repository für andere Personen akzeptiert. Der Benutzer bestätigt einmalig bewusst mit `-PsamsungSmartViewLicenseAccepted=I_ACCEPT`; Gradle speichert diese Entscheidung lokal unter `~/.gradle/aniworld/samsung-smartview-license.properties`.

## Prüfung

Wie angefordert wurde kein Gradle-Build und kein Gradle-Task ausgeführt. Geprüft wurden statisch:

- Vorhandensein des Apply-Hooks
- Klammer- und Stringstruktur des neuen Groovy-Skripts
- unveränderte Kotlin-Quelldateien gegenüber dem Eingangs-ZIP
- ZIP-Struktur und Prüfsumme des Ausgabearchivs
