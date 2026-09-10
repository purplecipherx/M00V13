from __future__ import annotations

from dataclasses import dataclass
from typing import Iterable, Optional, Sequence, Tuple


LANGUAGE_ALIASES = {
    "eng": "en",
    "english": "en",
    "en-us": "en",
    "en-gb": "en",
    "spa": "es",
    "spanish": "es",
    "es-es": "es",
    "fre": "fr",
    "fra": "fr",
    "french": "fr",
    "ger": "de",
    "deu": "de",
    "german": "de",
    "ita": "it",
    "italian": "it",
    "por": "pt",
    "portuguese": "pt",
    "jpn": "ja",
    "japanese": "ja",
    "kor": "ko",
    "korean": "ko",
    "chi": "zh",
    "zho": "zh",
    "chinese": "zh",
}


def normalize_language(value: Optional[str]) -> Optional[str]:
    if not value:
        return None
    normalized = value.strip().lower().replace("_", "-")
    return LANGUAGE_ALIASES.get(normalized, normalized.split("-", 1)[0])


def normalize_languages(values: Iterable[str]) -> Tuple[str, ...]:
    out = []
    for value in values:
        language = normalize_language(value)
        if language and language not in out:
            out.append(language)
    return tuple(out)


@dataclass(frozen=True)
class PlaybackLanguagePolicy:
    preferred_audio_language: str = "en"
    preferred_subtitle_language: Optional[str] = None
    subtitles_when_preferred_audio_missing: bool = True
    subtitles_default_off: bool = True

    @property
    def audio_language(self) -> str:
        return normalize_language(self.preferred_audio_language) or "en"

    @property
    def subtitle_language(self) -> str:
        return normalize_language(self.preferred_subtitle_language) or self.audio_language


@dataclass(frozen=True)
class TrackSelection:
    audio_index: Optional[int]
    subtitle_index: Optional[int]
    subtitles_enabled: bool
    preferred_audio_found: bool
    reason: str


def _track_language(track: object) -> Optional[str]:
    if isinstance(track, dict):
        return normalize_language(track.get("language") or track.get("lang"))
    return normalize_language(getattr(track, "language", None) or getattr(track, "lang", None))


def choose_tracks(
    audio_tracks: Sequence[object],
    subtitle_tracks: Sequence[object],
    policy: PlaybackLanguagePolicy,
) -> TrackSelection:
    preferred_audio = policy.audio_language
    preferred_subtitle = policy.subtitle_language

    audio_index = None
    for index, track in enumerate(audio_tracks):
        if _track_language(track) == preferred_audio:
            audio_index = index
            break

    if audio_index is not None:
        return TrackSelection(
            audio_index=audio_index,
            subtitle_index=None,
            subtitles_enabled=False if policy.subtitles_default_off else bool(subtitle_tracks),
            preferred_audio_found=True,
            reason="preferred_audio_available",
        )

    fallback_audio_index = 0 if audio_tracks else None
    subtitle_index = None
    if policy.subtitles_when_preferred_audio_missing:
        for index, track in enumerate(subtitle_tracks):
            if _track_language(track) == preferred_subtitle:
                subtitle_index = index
                break

    return TrackSelection(
        audio_index=fallback_audio_index,
        subtitle_index=subtitle_index,
        subtitles_enabled=subtitle_index is not None,
        preferred_audio_found=False,
        reason=(
            "preferred_audio_missing_subtitles_enabled"
            if subtitle_index is not None
            else "preferred_audio_missing_no_matching_subtitles"
        ),
    )
