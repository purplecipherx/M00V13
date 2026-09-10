from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Mapping, Optional, Tuple


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
    audio_languages: Tuple[str, ...] = ()
    subtitle_languages: Tuple[str, ...] = ()
    audio_codec: Optional[str] = None
    audio_channels: Optional[str] = None
    hdr_format: Optional[str] = None
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

    @property
    def display_audio_language(self) -> str:
        if not self.audio_languages:
            return "Unknown"
        return ", ".join(lang.upper() for lang in self.audio_languages)
