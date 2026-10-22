# AniSkip Android core port report — 1.8.0

## Source and licensing

The uploaded `ani-skip-master.zip` was inspected. Its upstream `ani-skip` script (version 1.2.2) resolves MyAnimeList IDs, optionally resolves AllAnime IDs to MAL IDs, calls `https://api.aniskip.com/v1/skip-times/{malId}/{episode}?types=op&types=ed`, and applies the end-early offset to opening/ending ranges. Its mpv Lua integration skips each segment once unless toggled. The Android port implements those behaviors natively with Kotlin, OkHttp, and the existing Media3 player. No shell, `curl`, `fzf`, temporary chapters file, or mpv dependency is introduced.

The upstream GPL-3.0 license and a notice are included under `THIRD_PARTY_NOTICES/ani-skip/`. The project already declares GPL-3.0-or-later.

## Integration

- `AniSkipAndroidCore` is now the core implementation; `AniSkipRepository` is a compatibility facade so the existing app ViewModel, PlayerScreen, favorites, history and settings keep one shared source of truth.
- MyAnimeList prefix search is the default source; AllAnime search resolves each result back to a MAL ID. Jikan-compatible metadata (Tenrai first, Jikan endpoint best-effort) only enriches titles and images; it does not replace the MAL ID chosen by the resolver.
- AniSkip v1 OP/ED is queried using the upstream endpoint and repeated `types` parameters. AniSkip v2 supplements the same database with mixed OP/ED and recap segments and provides IDs needed for contribution/voting actions. Duplicate timestamp ranges are merged.
- Per-series/per-season MAL mappings continue to persist in DataStore. Auto lookup refuses ambiguous matches and does not silently reuse a season-1 mapping for season 2. Ending timestamps do not mark an episode watched early; the actual player-ended event remains responsible for completion and auto-next.
- The source selector in the player mapping dialog lets the user search MyAnimeList or AllAnime.
- Opening and ending auto-skip defaults now match upstream ani-skip for fresh installs. Existing saved user choices are respected.
- Skip offset follows upstream semantics: `target = endTime - offset`, with offset constrained to 0–5 seconds. The same shared calculation is used for foreground and minimized/background playback.
- Manual skip, recap extensions, Opening/Ending community submission, persistent preferences, progress memory, auto-next, compact player, notification return, favorites and history remain wired through existing app state.

## Validation

- `python3 tools/verify_project.py`: PASS (XML/resources, feature references, AniSkip v1/v2 core markers, one-time skip/offset semantics, and season-description removal; 46 feature groups).
- XML parsing and locale-key parity: checked after adding source labels.
- Gradle compile: NOT VERIFIED. The wrapper attempted to download Gradle 8.14.3, but this environment cannot resolve `services.gradle.org` (`UnknownHostException`).
- Runtime API tests and Android device playback tests: NOT RUN. The v1/v2 APIs, MyAnimeList search, AllAnime search, submission, and actual seeking still need testing from a networked Android Studio/device environment.

## Recommended smoke tests

1. Build `:app:assembleDebug` in Android Studio with network access.
2. Search the same title using MyAnimeList and AllAnime; select the correct result and confirm the mapping persists for that series/season.
3. Load a mapped episode with known AniSkip OP/ED timestamps; verify markers and automatic seek.
4. Set the end-early offset to 3 seconds; confirm playback seeks to `endTime - 3`, never `endTime + 3`.
5. Rewind into a segment and confirm default one-time skip behavior lets the user watch it again.
6. Confirm v2 mixed/recap segments still appear when available.
7. Submit a community timestamp only after verifying the episode length and exact start/end.
8. Minimize the player, verify background skipping and the compact timeline, then return via media notification. Swipe the app task away and verify playback is not automatically restored.
