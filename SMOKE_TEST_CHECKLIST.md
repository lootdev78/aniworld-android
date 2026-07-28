# Geräte-Smoke-Test

Nach einem erfolgreichen CI-Build einmal auf einem realen Gerät prüfen:

## Start und Persistenz

- Neuinstallation startet im gewählten Start-Tab.
- Akzentfarbe, dynamische Farben und verschiebbarer Settings-Button bleiben nach Neustart erhalten.
- Keine Erfolgs-/Lade-Toasts; nur echte Fehler werden angezeigt.

## Startseite und Katalog

- Startseite lädt Anime-News und Live-Sammlungen.
- Katalog ist vor dem Erstimport gesperrt und danach bedienbar.
- Drei Suchvorschläge und Löschen-X funktionieren.
- Such-/Filterbereich verschwindet beim Scrollen und erscheint bei Gegenrichtung.
- Cover passen zu Titel und Detailseite; Reset und Metadatenupdate funktionieren.

## Details, Favoriten und Verlauf

- Anime öffnet aus Startseite, Katalog, Favoriten und Verlauf dieselbe Detailansicht.
- Beschreibungen erscheinen je Ansicht nur einmal; Staffel-/Film-/Folgendaten sind plausibel.
- Favoriten und Verlauf unterstützen alle drei Ansichten und Sortierungen.
- Mehrfachauswahl löscht erst nach Bestätigung.
- Begonnene Folge/Film lässt sich als gesehen markieren und zurücksetzen.

## Hoster und WebView

- Bevorzugter Hoster startet optional automatisch.
- Hoster-Fallback überspringt nicht erreichbare/challengepflichtige Quellen.
- Falls nötig öffnet sich die manuelle Hoster-WebView; nach erfolgreicher Prüfung wird die Session übernommen.
- Adblocker ist nach Neuinstallation aus; Filter und Domain-Ausnahme funktionieren nach Aktivierung.
- Erkannte HLS-/DASH-/SmoothStreaming-/progressive URLs erscheinen erst nach Seitenabschluss.

## Player

- Interner Stream startet mit Referer/User-Agent/Cookies.
- Timeline springt an die gewählte Stelle.
- Doppeltipp links/rechts und ±10-s-Schaltflächen funktionieren.
- „Von Anfang“ setzt auf 00:00.
- Player-UI blendet sich beim Abspielen aus und bei Interaktion/Pause wieder ein.
- Hoster-/Sprachwechsel, vorherige/nächste Folge und Auto-Next funktionieren.
- System-Medienbenachrichtigung zeigt Titel/Folge und Steuerung.
- Externer Player wird nur bei aktivierter Einstellung angeboten.
