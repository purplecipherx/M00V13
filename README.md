# M00V13

M00V13 is an all-in-one Kodi video add-on project combining ideas and components from POV/Kodifitzwell, Viper Scrapers/Old Salt, and Prowlarr/Cardigann-style indexer definitions.

## Design goals

- Preserve a POV-style Kodi browsing/playback experience.
- Use a Viper-style scraper/provider interface.
- Add a definition-driven public-indexer engine so indexers can be updated as data rather than rewritten as Python modules.
- Search in sequential tiers and stop early when enough acceptable sources are found.
- Target English-language public indexers only for the definition-driven tier.
- Respect site access controls: normal HTTP/API/feed access, caching, backoff, rate limits, and optional user-provided session state. M00V13 does not implement CAPTCHA solving or Cloudflare/anti-bot bypass.

## Search waterfall

1. Tier 1: preferred native Viper providers.
2. If the result policy is not satisfied, Tier 2: broader native Viper providers.
3. If still unsatisfied, Tier 3: compatible public English Cardigann-style definitions.
4. Normalize, deduplicate, rank, and return candidates to the existing POV/debrid/playback layer.

See `docs/ARCHITECTURE.md` for the integration contract.
