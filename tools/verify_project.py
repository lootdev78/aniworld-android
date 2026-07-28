#!/usr/bin/env python3
"""Static release gate for AniWorldAndroid.

This checker intentionally uses only Python's standard library so it can run
before Gradle dependency resolution in local and CI builds.
"""
from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app"
SRC = APP / "src" / "main"
KOTLIN = SRC / "java" / "io" / "github" / "lootdev78" / "aniworld"
RES = SRC / "res"
EXPECTED_PACKAGE = "io.github.lootdev78.aniworld"
EXPECTED_VERSION_CODE = 58
EXPECTED_VERSION_NAME = "1.7.1-production-build-fix"

errors: list[str] = []
notes: list[str] = []


def fail(message: str) -> None:
    errors.append(message)


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def read(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8")
    except Exception as exc:  # pragma: no cover - diagnostic path
        fail(f"Kann {path.relative_to(ROOT)} nicht lesen: {exc}")
        return ""


required_files = [
    ROOT / "settings.gradle.kts",
    ROOT / "build.gradle.kts",
    ROOT / "gradle" / "libs.versions.toml",
    ROOT / "gradle" / "wrapper" / "gradle-wrapper.jar",
    ROOT / "gradle" / "wrapper" / "gradle-wrapper.properties",
    ROOT / "gradlew",
    APP / "build.gradle.kts",
    SRC / "AndroidManifest.xml",
    KOTLIN / "AniWorldApplication.kt",
    KOTLIN / "AniWorldRepository.kt",
    KOTLIN / "AppDatabase.kt",
    KOTLIN / "AppStore.kt",
    KOTLIN / "AppViewModel.kt",
    KOTLIN / "CatalogMetadataWorker.kt",
    KOTLIN / "ChallengeScreen.kt",
    KOTLIN / "MediaDetection.kt",
    KOTLIN / "PlaybackService.kt",
    KOTLIN / "PlayerScreen.kt",
    KOTLIN / "UiScreens.kt",
    KOTLIN / "WebAdBlocker.kt",
    ROOT / ".github" / "workflows" / "android-debug.yml",
]
for path in required_files:
    require(path.is_file(), f"Pflichtdatei fehlt: {path.relative_to(ROOT)}")

build_file = read(APP / "build.gradle.kts")
require(f'namespace = "{EXPECTED_PACKAGE}"' in build_file, "Falscher Android-Namespace")
require(f'applicationId = "{EXPECTED_PACKAGE}"' in build_file, "Falsche Application-ID")
require('applicationIdSuffix = ".debug"' in build_file, "Debug-Build benötigt eigenes .debug-Paket")
require(f"versionCode = {EXPECTED_VERSION_CODE}" in build_file, "Unerwarteter versionCode")
require(f'versionName = "{EXPECTED_VERSION_NAME}"' in build_file, "Unerwarteter versionName")
require("compileSdk = 36" in build_file and "targetSdk = 36" in build_file, "SDK-36-Konfiguration fehlt")
require("JavaVersion.VERSION_17" in build_file, "Java-17-Konfiguration fehlt")

manifest_path = SRC / "AndroidManifest.xml"
manifest_text = read(manifest_path)
require('android:allowBackup="false"' in manifest_text, "Android-Backup muss deaktiviert bleiben")
require('android:usesCleartextTraffic="false"' in manifest_text, "Cleartext-Traffic muss deaktiviert bleiben")
require('android:name=".PlaybackService"' in manifest_text, "PlaybackService fehlt im Manifest")
require('android:name=".AniWorldApplication"' in manifest_text, "Application-Klasse fehlt im Manifest")

# XML well-formedness.
xml_files = sorted({*RES.rglob("*.xml"), manifest_path})
for path in xml_files:
    try:
        ET.parse(path)
    except ET.ParseError as exc:
        fail(f"Ungültiges XML in {path.relative_to(ROOT)}: {exc}")
notes.append(f"XML-Dateien: {len(xml_files)}")

# Android string resource integrity and locale parity.
def strings_in(path: Path) -> set[str]:
    if not path.is_file():
        fail(f"Stringdatei fehlt: {path.relative_to(ROOT)}")
        return set()
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError as exc:
        fail(f"Ungültige Stringdatei {path.relative_to(ROOT)}: {exc}")
        return set()
    names: set[str] = set()
    for child in root:
        name = child.attrib.get("name")
        if name:
            if name in names:
                fail(f"Doppelter Stringschlüssel {name} in {path.relative_to(ROOT)}")
            names.add(name)
    return names

base_strings = strings_in(RES / "values" / "strings.xml")
for locale in ("values-de", "values-en"):
    localized = strings_in(RES / locale / "strings.xml")
    missing = sorted(base_strings - localized)
    extra = sorted(localized - base_strings)
    if missing:
        fail(f"{locale} fehlen {len(missing)} Strings: {', '.join(missing[:12])}")
    if extra:
        fail(f"{locale} enthält unbekannte Strings: {', '.join(extra[:12])}")
notes.append(f"Stringschlüssel: {len(base_strings)}")

kotlin_files = sorted(KOTLIN.glob("*.kt"))
require(len(kotlin_files) >= 20, "Unerwartet wenige Kotlin-Quelldateien")
all_kotlin = "\n".join(read(path) for path in kotlin_files)
used_strings = set(re.findall(r"R\.string\.([A-Za-z0-9_]+)", all_kotlin))
missing_strings = sorted(used_strings - base_strings)
if missing_strings:
    fail(f"Fehlende R.string-Ressourcen: {', '.join(missing_strings)}")
notes.append(f"Kotlin-Dateien: {len(kotlin_files)}; verwendete Strings: {len(used_strings)}")

# Package/path integrity.
for path in kotlin_files:
    text = read(path)
    package_match = re.search(r"(?m)^package\s+([\w.]+)\s*$", text)
    require(package_match is not None, f"Kein package-Block in {path.name}")
    if package_match:
        require(package_match.group(1) == EXPECTED_PACKAGE, f"Falsches package in {path.name}")
require("de.dxmoc.aniworld" not in all_kotlin + build_file + manifest_text, "Alte Paket-ID ist noch vorhanden")

# Regression patterns that previously broke compileDebugKotlin or emitted deprecations.
for pattern, description in {
    r"private\s+inline\s+fun\s+<T>\s+parseObjectMap": "Nicht-lokaler Mapper erneut inline",
    r"\.databaseEnabled\s*=": "Veraltetes WebView databaseEnabled",
    r"Icons\.Filled\.ArrowBack": "Veraltetes ArrowBack-Icon",
    r"Icons\.Filled\.List\b": "Veraltetes List-Icon",
    r"Icons\.Filled\.VolumeUp": "Veraltetes VolumeUp-Icon",
    r"TODO\s*\(": "Nicht implementiertes TODO",
    r"TODO\b": "TODO-Markierung",
    r"FIXME\b": "FIXME-Markierung",
    r"onClick\s*=\s*\{\s*\}": "Leerer onClick-Handler",
    r"librarySeries\.forEach\(store::ensureOfflineMetadata\)": "Suspend-Metadatenfunktion in nicht-suspendierender forEach-Referenz",
    r"item\.episode\.localizedDisplayTitle\(\)\.contains": "Composable Texthelfer innerhalb von remember",
}.items():
    if re.search(pattern, all_kotlin):
        fail(f"Regression gefunden: {description}")

# Required end-to-end feature markers. These are intentionally tied to concrete
# source files so accidental file loss is caught during consolidation.
feature_markers: dict[str, tuple[Path, tuple[str, ...]]] = {
    "Adblocker (standardmäßig aus)": (KOTLIN / "WebAdBlocker.kt", ("object WebAdBlocker", "shouldBlockNavigation", "emptyResponse")),
    "Adblocker-Einstellung": (KOTLIN / "AppStore.kt", ("WEB_ADBLOCK_ENABLED", "?: false")),
    "Direkter Media-Detector": (KOTLIN / "MediaDetection.kt", ("object DirectMediaDetector", "application/x-mpegURL", "application/dash+xml", "SmoothStreaming")),
    "Hoster-WebView und Session": (KOTLIN / "ChallengeScreen.kt", ("CookieManager", "shouldInterceptRequest", "mediaDetectionEnabled")),
    "Challenge-Fallback": (KOTLIN / "ChallengeSession.kt", ("ChallengeRequiredException", "throwIfRequired")),
    "MediaSession-Player": (KOTLIN / "PlaybackService.kt", ("MediaSessionService", "ACTION_PREVIOUS", "ACTION_NEXT", "ACTION_SEEK")),
    "Player-Zeitleiste und ±10 s": (KOTLIN / "PlayerScreen.kt", ("Slider(", "10_000L", "play_from_beginning")),
    "Externer Player": (KOTLIN / "ExternalPlayback.kt", ("ACTION_VIEW", "FLAG_GRANT_READ_URI_PERMISSION")),
    "Katalog-Worker": (KOTLIN / "CatalogMetadataWorker.kt", ("CoroutineWorker", "setForeground", "UNIQUE_WORK")),
    "Offline-Metadaten": (KOTLIN / "AppDatabase.kt", ("SeriesMetadataEntity", "coverUrl", "description", "genres")),
    "Katalog-/Coverparser": (KOTLIN / "AniWorldRepository.kt", ("catalog", "cover", "parseSeries")),
    "Anime-News": (KOTLIN / "AniWorldRepository.kt", ("HomeNews", "parseHomeNews")),
    "Favoriten und Verlauf": (KOTLIN / "UiScreens.kt", ("FavoritesScreen", "HistoryScreen", "LibraryViewMode")),
    "Mehrfachauswahl": (KOTLIN / "UiScreens.kt", ("selectedSlugs", "delete_selected")),
    "Start-Tab und Akzentfarbe": (KOTLIN / "AppStore.kt", ("STARTUP_TAB", "ACCENT_COLOR")),
    "Auto-Next und bevorzugter Hoster": (KOTLIN / "AppStore.kt", ("AUTO_NEXT_ENABLED", "AUTO_PLAY_PREFERRED_HOSTER")),
}
for feature, (path, markers) in feature_markers.items():
    text = read(path)
    for marker in markers:
        if marker not in text:
            fail(f"Featuremarker fehlt ({feature}): {path.name} -> {marker}")
notes.append(f"Featuregruppen geprüft: {len(feature_markers)}")

# Manifest component classes must exist in source.
for cls in re.findall(r'android:name="\.([A-Za-z0-9_]+)"', manifest_text):
    require((KOTLIN / f"{cls}.kt").is_file(), f"Manifestklasse ohne Quelldatei: {cls}")

# Avoid shipping generated, local or secret material.
for forbidden in ("build", ".gradle", ".idea", ".git", "local.properties"):
    matches = [p for p in ROOT.rglob(forbidden) if p != ROOT / ".github"]
    if matches:
        fail(f"Nicht auslieferbarer Build-/IDE-Pfad enthalten: {matches[0].relative_to(ROOT)}")
for suffix in ("*.jks", "*.keystore", "*.p12"):
    if next(ROOT.rglob(suffix), None):
        fail(f"Privater Signaturschlüssel im Projekt gefunden: {suffix}")

if errors:
    print("STATIC RELEASE GATE: FAILED")
    for item in errors:
        print(f"  - {item}")
    sys.exit(1)

print("STATIC RELEASE GATE: PASSED")
for item in notes:
    print(f"  - {item}")
