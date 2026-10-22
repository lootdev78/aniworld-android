# ani-skip Android core port

This native Kotlin/Android implementation is based on the workflow and behavior of `synacktraa/ani-skip` version 1.2.2.

Upstream: https://github.com/synacktraa/ani-skip
License: GNU General Public License v3.0; full text is in `LICENSE-GPL-3.0.txt`.

Ported concepts include MyAnimeList title resolution, optional AllAnime ID resolution, AniSkip opening/ending timestamp lookup, one-time automatic skipping, and end-early offset behavior. The Android version uses OkHttp and AndroidX Media3 instead of shell commands, `curl`, `fzf`, temporary chapter files, and mpv Lua hooks.

The native implementation is adapted for the existing app architecture; it is not a byte-for-byte copy of the shell script.
