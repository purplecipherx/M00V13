from __future__ import annotations

from concurrent.futures import FIRST_COMPLETED, ThreadPoolExecutor, wait
from dataclasses import dataclass
from time import monotonic
from typing import Callable, Iterable, List, Optional, Protocol, Sequence

from .source import NormalizedSource


class Provider(Protocol):
    name: str

    def search(self, query: dict) -> Iterable[NormalizedSource]:
        ...


@dataclass(frozen=True)
class SearchPolicy:
    minimum_usable: int = 5
    minimum_cached: int = 0
    tier1_timeout_seconds: float = 4.0
    tier2_timeout_seconds: float = 7.0
    tier3_timeout_seconds: float = 12.0
    max_workers_per_tier: int = 12


class SearchOrchestrator:
    """Run providers concurrently inside sequential fallback tiers."""

    def __init__(
        self,
        tier1: Sequence[Provider] = (),
        tier2: Sequence[Provider] = (),
        tier3: Sequence[Provider] = (),
        policy: SearchPolicy = SearchPolicy(),
        cached_probe: Optional[Callable[[List[NormalizedSource]], List[NormalizedSource]]] = None,
        provider_error: Optional[Callable[[str, Exception], None]] = None,
    ) -> None:
        self.tiers = (tuple(tier1), tuple(tier2), tuple(tier3))
        self.policy = policy
        self.cached_probe = cached_probe
        self.provider_error = provider_error

    def search(self, query: dict) -> List[NormalizedSource]:
        collected: List[NormalizedSource] = []
        timeouts = (
            self.policy.tier1_timeout_seconds,
            self.policy.tier2_timeout_seconds,
            self.policy.tier3_timeout_seconds,
        )
        for providers, timeout in zip(self.tiers, timeouts):
            collected.extend(self._run_tier(providers, query, timeout))
            collected = self._dedupe(collected)
            collected = self._apply_cache_probe(collected)
            if self._satisfied(collected):
                break
        return self._rank(collected)

    def _run_tier(
        self,
        providers: Sequence[Provider],
        query: dict,
        timeout_seconds: float,
    ) -> List[NormalizedSource]:
        if not providers:
            return []

        out: List[NormalizedSource] = []
        workers = max(1, min(self.policy.max_workers_per_tier, len(providers)))
        deadline = monotonic() + max(0.0, timeout_seconds)
        executor = ThreadPoolExecutor(max_workers=workers, thread_name_prefix="m00v13-scrape")
        future_to_provider = {executor.submit(provider.search, query): provider for provider in providers}
        pending = set(future_to_provider)
        try:
            while pending:
                remaining = deadline - monotonic()
                if remaining <= 0:
                    break
                done, pending = wait(pending, timeout=remaining, return_when=FIRST_COMPLETED)
                if not done:
                    break
                for future in done:
                    provider = future_to_provider[future]
                    try:
                        out.extend(source for source in future.result() if source.usable)
                    except Exception as exc:
                        if self.provider_error:
                            self.provider_error(getattr(provider, "name", provider.__class__.__name__), exc)
        finally:
            for future in pending:
                future.cancel()
            # Do not make the tier deadline meaningless by waiting for hung providers.
            executor.shutdown(wait=False, cancel_futures=True)
        return out

    @staticmethod
    def _dedupe(sources: Iterable[NormalizedSource]) -> List[NormalizedSource]:
        unique = {}
        for source in sources:
            unique.setdefault(source.dedupe_key, source)
        return list(unique.values())

    def _apply_cache_probe(self, sources: List[NormalizedSource]) -> List[NormalizedSource]:
        if not self.cached_probe:
            return sources
        return self.cached_probe(sources)

    def _satisfied(self, sources: Sequence[NormalizedSource]) -> bool:
        usable = sum(1 for source in sources if source.usable)
        if usable < self.policy.minimum_usable:
            return False
        if self.policy.minimum_cached:
            cached = sum(1 for source in sources if source.cached is True)
            if cached < self.policy.minimum_cached:
                return False
        return True

    @staticmethod
    def _rank(sources: Iterable[NormalizedSource]) -> List[NormalizedSource]:
        quality_rank = {
            "2160p": 5,
            "4k": 5,
            "1080p": 4,
            "720p": 3,
            "sd": 2,
            "cam": 0,
        }

        def score(source: NormalizedSource):
            quality = quality_rank.get((source.quality or "").lower(), 1)
            cached = 1 if source.cached else 0
            seeders = source.seeders or 0
            return (cached, quality, seeders)

        return sorted(sources, key=score, reverse=True)
