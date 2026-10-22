# Player behavior fixes

## Root causes found

- Next/previous episode navigation only looked inside the currently loaded season's episode list. At season boundaries (or when that list was stale), next/previous and automatic next could silently do nothing.
- The season-detail UI rendered a season-description card and series-description fallback.
- The player did not have a persistent in-app minimized state. Leaving the player via its close action stopped playback, so there was no compact timeline surface across the main tabs.
- Playback seek state was not copied into the in-memory playback model, leaving a minimized timeline stale even while the player was progressing.

## Changes

- Resolve adjacent episodes from the current season and, when needed, fetch the neighboring season's episode page.
- Enable previous/next availability across season boundaries.
- Remove the season/movie description card from the season episode screen.
- Add a minimize control that keeps service playback alive and shows a compact episode title + current position/duration + progress line across Home, Catalog, Search/Home content, Favorites, History, and detail screens. Settings remains separate.
- Make media-notification taps request the full player view, and use clear-top/single-top activity flags to reuse the activity where possible.
- Initialize the media item with the saved start position and keep the in-memory playback progress/duration current.

## Validation

- XML resource files parsed successfully.
- Kotlin compilation could not be completed in this environment: the Gradle wrapper attempted to download Gradle 8.14.3, but network DNS access to `services.gradle.org` was unavailable. No successful build or device playback test is claimed.
