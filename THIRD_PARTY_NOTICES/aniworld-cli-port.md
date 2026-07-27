# aniworld-cli Android port notes

This Android project ports the public workflow and parser contracts documented in dxmoc/aniworld-cli:

- AJAX search endpoint `/ajax/search`
- series root filtering `/anime/stream/<slug>`
- navigation block `div#stream` for seasons, films and episodes
- `.changeLanguageBox` language key discovery
- `li[data-link-target][data-lang-key]` hoster parsing
- language-first / hoster-second fallback ordering
- HLS liveness probing before playback in AndroidX Media3/ExoPlayer
- watchlist/resume state

No code is vendored from the Python project; the Android implementation is written in Kotlin and keeps the same high-level architecture.
