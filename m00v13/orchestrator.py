from __future__ import annotations

from dataclasses import dataclass
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


class SearchOrchestrator:
    """Sequential-tier search coordinator.

    Providers inside a tier can later be executed by the Kodi host's thread pool;
    this core keeps the policy deterministic and host-independent.
    """

    def __init__(
        self,
        tier1: Sequence[Provider] = (),
        tier2: Sequence[Provider] = (),
        tier3: Sequence[Provider] = (),
        policy: SearchPolicy = SearchPolicy(),
        cached_probe: Optional[Callable[[List[NormalizedSource]], List[NormalizedSource]]] = None,
    ) -> None:
        self.tiers = (tuple(tier1), tuple(tier2), tuple(tier3))
        self.policy = policy
        self.cached_probe = cached_probe

    def search(self, query: dict) -> List[NormalizedSource]:
        collected: List[NormalizedSource] = []
        for providers in self.tiers:
            collected.extend(self._run_tier(providers, query))
            collected = self._dedupe(collected)
            collected = self._apply_cache_probe(collected)
            if self._satisfied(collected):
                break
        return self._rank(collected)

    @staticmethod
    def _run_tier(providers: Sequence[Provider], query: dict) -> List[NormalizedSource]:
        out: List[NormalizedSource] = []
        for provider in providers:
            try:
                out.extend(source for source in provider.search(query) if source.usable)
            except Exception:
                # Provider failures must not abort the overall scrape.
                continue
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
