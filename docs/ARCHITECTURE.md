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

Offline downloads must support two independent user controls:

- **Maximum M00V13 download space**: hard cap on bytes M00V13 may consume for offline media on the selected storage volume.
- **Minimum free-space reserve**: amount of free space that must remain available on that volume after any download, expansion, remux, subtitle fetch, or artwork write.

Both controls are enforced at the same time and the stricter one wins. Before accepting or starting a download, calculate:

```text
allowed_by_quota   = max_download_bytes - current_m00v13_download_bytes
allowed_by_reserve = current_free_bytes - minimum_free_reserve_bytes
allowed_to_write   = max(0, min(allowed_by_quota, allowed_by_reserve))
```

A download may start only when its estimated required space plus a configurable safety margin is less than or equal to `allowed_to_write`.

Required behavior:

- check space again immediately before writing and periodically during long downloads
- account for temporary/partial files when calculating M00V13 usage
- never allow automatic series/collection downloads to consume the protected reserve
- pause queued automatic downloads before deleting anything when space becomes constrained
- optional cleanup policy may remove watched/expired auto-downloads, oldest eligible downloads, or user-approved cache files
- manually pinned downloads are never auto-deleted unless the user explicitly enables that policy
- show projected post-download free space before a manual download starts
- expose storage controls per storage target, so internal storage and attached USB/storage can have different limits

Suggested UI:

```text
Downloads & Storage

Maximum space M00V13 can use     [ 3.0 GB ]
Always leave this much free      [ 1.5 GB ]
Download location                [ Internal storage > ]

Currently used by M00V13          1.2 GB
Device free space                 3.4 GB
Available for new downloads       1.8 GB
```

For constrained devices such as an 8 GB streaming stick, the reserve should be enabled by default so Android, app updates, databases, thumbnails, and temporary files cannot be starved by automated downloads.

## Repository integration

The current source repositories are kept as upstream references. M00V13 should contain the integrated source tree and attribution/license notices required by the upstream licenses, rather than depending on the three repositories at runtime.
