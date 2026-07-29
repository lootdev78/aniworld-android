# AniWorld Android 1.7.8 – Wiedergabe-Hotfix

## Fehlerbild

Nach dem Update auf 1.7.7 konnte die App unmittelbar beim Öffnen eines aufgelösten Hoster-Streams abstürzen. Der neue Google-Cast-Pfad wurde beim Aufbau jedes `PlayerScreen` sofort initialisiert und erstellte direkt einen nativen `MediaRouteButton`. Damit hing normale lokale Wiedergabe unnötig vom Cast-/Play-Services-Setup des jeweiligen Geräts ab.

## Korrektur

- `ChromecastController` initialisiert `CastContext` nicht mehr im Konstruktor.
- Google Cast wird ausschließlich nach einem bewussten Klick auf das Chromecast-Symbol initialisiert.
- Der native `MediaRouteButton` wird erst innerhalb des explizit geöffneten Chromecast-Dialogs erstellt.
- Fehler bei CastContext, Play Services, MediaRouter oder Button-Aufbau werden abgefangen und als normale Player-Fehlermeldung behandelt.
- Die zuvor bei jeder Positionsänderung erneut gestartete `LaunchedEffect(playback.id, position)`-Vorbereitung wurde entfernt.
- DLNA/UPnP, FCast, Miracast, Hotspot-Erkennung, Startseitenanpassung, Diagnose und die vier parallelen Metadatenjobs bleiben erhalten.

## Prüfung

- Statisches Release-Gate: bestanden.
- XML- und String-Ressourcenprüfung: bestanden.
- Hotfix-Regeln prüfen, dass Cast nicht mehr beim normalen Player-Aufbau initialisiert wird.
- Ein vollständiger Gradle-Build war in der isolierten Umgebung nicht möglich, weil `services.gradle.org` nicht aufgelöst werden konnte.

## Version

- `versionCode`: `65`
- `versionName`: `1.7.8-playback-hotfix`
