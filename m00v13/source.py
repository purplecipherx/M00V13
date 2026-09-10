from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Mapping, Optional


@dataclass(frozen=True)
class NormalizedSource:
    provider: str
    title: str
    url: str
    infohash: Optional[str] = None
    quality: Optional[str] = None
    size_bytes: Optional[int] = None
    seeders: Optional[int] = None
    leechers: Optional[int] = None
    language: str = "en"
    cached: Optional[bool] = None
    metadata: Mapping[str, Any] = field(default_factory=dict)

    @property
    def dedupe_key(self) -> str:
        if self.infohash:
            return f"hash:{self.infohash.lower()}"
        return f"url:{self.url.strip().lower()}|title:{self.title.strip().lower()}"

    @property
    def usable(self) -> bool:
        return bool(self.title and self.url)
