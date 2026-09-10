# Profiles, offline downloads, watch-state, and recommendations

## Goal

M00V13 should work well for users with unreliable or slow internet and for households with multiple viewers. Playback state, download automation, language preferences, and recommendations are scoped per profile.

## Profiles

Each profile owns:

- display name and avatar
- preferred audio language
- preferred subtitle language
- subtitle fallback policy
- maximum/default playback quality
- maximum/default download quality
- watch history and progress
- watched/unwatched state
- favorites and watchlist
- recommendation signals
- download preferences
- optional content restrictions/PIN

No profile should inherit another profile's watch history or recommendations unless explicitly configured.

## Watch-state behavior

Track per-title and per-episode:

- started_at
- last_played_at
- playback_position
- duration
- completion_percent
- watched flag
- completed_at
- source/release used

Default watched rule should be configurable, with a sensible default near 90% completion. Credits-aware completion may later replace a simple percentage when metadata is available.

When a title is watched:

- mark the item watched for the current profile
- update Continue Watching
- update recommendation signals
- advance series next-episode state
- trigger configured automatic-download rules

## Offline downloads

Downloads are first-class media objects, not anonymous files.

Store metadata alongside each download:

- profile owner(s)
- movie/show/season/episode IDs
- title/release
- source provider
- infohash or stable source identity when applicable
- video quality/codec/HDR
- detected audio languages/codecs/channels
- detected subtitle languages
- file size
- download progress/state
- verification result
- local path
- expiry/cleanup policy

The player should prefer a verified local copy over network playback when the requested item is already downloaded.

## Manual download UX

Every movie, episode, season, collection/trilogy, and series should expose a simple `Download` action.

Default behavior:

1. use the profile's download policy
2. find a suitable source
3. verify required preferred-language audio or subtitle fallback is available when metadata permits
4. calculate free-space impact
5. queue download
6. verify completed file before marking it Offline Ready

Advanced source selection remains optional.

## Automatic download rules

Users can enable automation at several scopes:

### Series

Modes:

- download next episode only
- keep next N unwatched episodes ready
- download remaining season
- download all unwatched episodes
- download new episodes automatically when discovered

For limited storage, `keep next N` is the preferred default. After an episode is watched, M00V13 can delete it according to policy and queue the next episode.

### Collections / trilogies

Modes:

- download entire collection
- download next unwatched movie
- keep next N unwatched movies ready

Collection membership should come from metadata providers rather than title-string guessing whenever possible.

### Watchlist

Optional rule: automatically maintain offline copies of selected watchlist items or the next N items.

## Storage manager

Because target TV sticks may have very limited internal storage, offline mode requires strict storage controls.

Support:

- internal storage and adopted/external storage where Android permits it
- minimum free-space reserve
- per-profile and global download quotas
- preferred maximum item size
- delete-after-watched policy
- delete oldest watched downloads first
- never-delete/pinned downloads
- partial-download cleanup
- download queue pause when storage falls below reserve

Before starting an automatic batch, estimate required capacity and reduce the queue rather than exhausting device storage.

## Poor-internet behavior

Profiles can choose download constraints:

- Wi-Fi only
- download only during user-defined hours
- pause while actively streaming
- maximum concurrent downloads
- bandwidth limit
- retry/backoff for interrupted downloads
- resume partial downloads
- prefer smaller encodes when bandwidth/storage are constrained

A `Low bandwidth` preset should bias toward efficient HEVC/AV1 encodes supported by the device instead of merely lowering resolution.

## Language-aware downloads

Use the same language policy as playback.

Default:

1. prefer a release with audio in the profile's preferred language
2. if preferred-language audio is unavailable, require or strongly prefer subtitles in that language
3. preserve subtitle-off default when preferred audio exists
4. store actual media-track metadata after the file is available locally

The source chooser should display known/detected audio language before download, with unknown values clearly marked rather than guessed.

## Recommendations

Recommendations are generated per profile from explicit and implicit signals.

### Explicit signals

- favorite
- like/dislike
- watchlist additions
- hidden/not interested
- `More like this`

### Implicit signals

- completed titles
- abandoned titles
- rewatched titles
- genres
- actors/directors
- franchises/collections
- release era
- runtime preferences
- language
- series completion behavior

### Recommendation surfaces

Home screen should support at least:

- `Recommended for You` — overall profile ranking
- `Because You Watched <title>` — similarity seeded from one title
- `More Like <title>` — explicit similarity request
- `Continue Watching`
- `Next Up`
- `From Series/Collections You Like`
- `Recently Added That Fits You`

Do not mix household profiles by default.

## Recommendation engine phases

### Phase 1: metadata similarity

Use metadata available from movie/TV metadata providers:

- genres
- keywords
- cast
- director/creator
- collection/franchise
- language
- year
- popularity/rating signals

This should run locally with lightweight scoring and requires no ML model.

### Phase 2: profile weighting

Learn simple per-profile preference weights from watch history and explicit feedback. Keep the model interpretable and cheap enough for low-memory TV hardware.

### Phase 3: optional collaborative/embedding layer

If later desired, add server-side or cloud-assisted embeddings/collaborative filtering. This must remain optional so M00V13 can retain a lightweight local mode.

## Home screen behavior

On launch, after profile selection (or automatic entry into the default profile), prioritize:

1. Continue Watching
2. Next Up
3. Offline Ready
4. Recommended for You
5. Because You Watched ...
6. Movies
7. TV Shows
8. Search

If the device is offline, hide network-dependent rows or mark them unavailable and elevate Offline Ready.

## Background orchestration

A single low-priority scheduler should coordinate:

- next-episode pre-scraping
- automatic download queue
- metadata refresh
- recommendation refresh
- cleanup of watched/expired downloads

Background jobs must respect playback: active playback takes priority over scraping, downloads, metadata refreshes, and recommendation computation.

## Privacy and portability

Profile/watch data should be stored locally by default and exportable/importable. Trakt or other sync services may be connected per profile, but are not required for basic watch history, recommendations, or downloads.
