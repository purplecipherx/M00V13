# M00V13 architecture

## Core flow

```text
POV-style UI / metadata / debrid / playback
                  |
                  v
          M00V13 SearchOrchestrator
          /          |           \
   Tier 1 native  Tier 2 native  Tier 3 definitions
       Viper          Viper        Cardigann-style
          \          |           /
                  v
             NormalizedSource
                  |
                  v
          dedupe -> rank -> return
```

## Tier semantics

Tiers are sequential; providers within a tier may execute concurrently. A tier does not advance merely because it returned one result. The result gate evaluates usable source count and quality.

Default gate:

- minimum usable sources: 5
- optional minimum cached sources: configurable by host integration
- hard provider timeout: configurable per tier
- duplicate key: infohash when available, otherwise normalized URL + title

The orchestrator accepts a host callback for cached/debrid availability so the scraper package does not own debrid-specific APIs.

## Tier 3 inclusion policy

Definition-driven indexers are eligible when all of the following are true:

- public/no tracker account required for ordinary searching
- English is the primary UI/result language or the tracker has an English search surface
- torrent results are supported
- movie and/or TV categories/capabilities are exposed
- definition can execute with the supported safe transport feature set

Private/account-only trackers are excluded from automatic Tier 3 discovery.

## Transport policy

The HTTP layer is intentionally conservative:

- normal HTTPS requests
- realistic but static application User-Agent
- connection pooling
- bounded concurrency
- per-host rate limiting
- exponential backoff with jitter on 429/5xx
- Retry-After support
- conditional requests where possible
- response caching
- optional user-supplied cookies/session values for sessions the user legitimately established
- detection of challenge/interstitial pages and clean provider failure

It does **not** solve CAPTCHAs, emulate browser fingerprinting to evade bot controls, or attempt to defeat Cloudflare challenges. Where a site exposes RSS, Torznab, JSON, or another supported feed/API, that path should be preferred over HTML scraping.

## Definition engine

The Tier 3 adapter should implement the Cardigann-style subset actually required by public English indexers, expanding capability based on compatibility tests rather than cloning the full Prowlarr application.

Initial feature families:

1. metadata: id/name/language/type/capabilities
2. base URLs and alternate URLs
3. search paths and query parameters
4. category mapping
5. HTML CSS selectors
6. JSON/XML result extraction
7. simple regex/string transforms
8. magnet/torrent-link extraction
9. seeders/leechers/size/date fields
10. IMDb/TMDb/title/season/episode query mapping

Unsupported definition features cause the indexer to be marked incompatible rather than silently producing bad results.

## Source contract

Every provider returns a normalized mapping compatible with `m00v13.source.NormalizedSource`. Native Viper results are adapted into the same model before ranking.

## Download storage policy

Offline downloads must enforce a built-in system safety floor in addition to optional user limits.

### Built-in Android safety reserve

On internal Android/Google TV storage, M00V13 permanently protects **1536 MiB** of free space for Android, app updates, databases, thumbnails, logs, package installation, temporary files, and player scratch space. This system reserve is not disableable in normal settings.

Additional thresholds:

- **1536 MiB**: normal protected reserve; downloads may not intentionally cross it
- **768 MiB**: critical free-space threshold; emergency cleanup is allowed
- **1280 MiB**: emergency cleanup target; once critical, reclaim toward this level
- **256 MiB**: per-download write headroom added to the estimated download size

A user's `Always leave this much free` setting may raise the 1536 MiB floor but never lower it.

### User quota

Users may separately configure **Maximum M00V13 download space**. Both the quota and reserve apply simultaneously and the stricter one wins:

```text
required_reserve   = max(1536 MiB, user_reserve)
allowed_by_quota   = max_download_bytes - current_m00v13_download_bytes
allowed_by_reserve = current_free_bytes - required_reserve
allowed_to_write   = max(0, min(allowed_by_quota, allowed_by_reserve))
```

A download may start only when its estimated size plus 256 MiB of headroom fits within `allowed_to_write`.

### Automatic cleanup order

M00V13 should automatically reclaim storage when necessary rather than allowing Android to become unusable.

1. Pause queued/background automatic downloads.
2. Delete **watched, unpinned downloads**, oldest/least-recently-accessed first.
3. If free space is still below the emergency target and system operation is threatened, delete **oldest unpinned unwatched downloads**, oldest/least-recently-accessed first.
4. Never automatically delete pinned downloads.
5. Stop cleanup once the emergency target is restored or no eligible downloads remain.

Automatic series/collection downloads are disposable cache-like media unless pinned. A watched episode is therefore normally the first storage candidate after its watched state is safely recorded.

Required behavior:

- check free space before a download and periodically while writing
- account for `.part`/temporary downloads in M00V13 usage
- stop/pause a writer before it crosses the safety floor
- trigger emergency cleanup if another process causes storage to fall below the critical threshold
- never delete the file currently being played
- never delete an active partial download until its writer is stopped and state is committed
- preserve watch/progress metadata even when the media file is removed
- after cleanup, allow the series automation scheduler to re-download an episode later if it becomes part of the configured next-unwatched window
- show the reason for automatic deletion in Download History

Suggested UI:

```text
Downloads & Storage

Maximum space M00V13 can use     [ 3.0 GB ]
Extra free-space reserve          [ 0 MB ]
System safety reserve              1.5 GB  (protected)
Download location                [ Internal storage > ]

Currently used by M00V13          1.2 GB
Device free space                 3.4 GB
Available for new downloads       1.8 GB
```

The displayed `Extra free-space reserve` is added conceptually by taking the maximum of the user preference and the built-in 1536 MiB safety floor; setting it to zero does not disable the system floor.

## Repository integration

The current source repositories are kept as upstream references. M00V13 should contain the integrated source tree and attribution/license notices required by the upstream licenses, rather than depending on the three repositories at runtime.
