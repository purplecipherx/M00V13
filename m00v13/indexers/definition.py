from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Mapping, Sequence, Tuple


MOVIE_TV_CATEGORY_PREFIXES = ("2000", "5000")
PUBLIC_TYPES = {"public", "semi-private"}
ENGLISH_CODES = {"en", "en-us", "en-gb", "en-ca", "en-au"}


@dataclass(frozen=True)
class Compatibility:
    compatible: bool
    reasons: Tuple[str, ...] = ()


@dataclass(frozen=True)
class IndexerDefinition:
    id: str
    name: str
    type: str
    language: str
    links: Tuple[str, ...]
    caps: Mapping[str, Any]
    search: Mapping[str, Any]
    login: Mapping[str, Any] | None = None

    @classmethod
    def from_mapping(cls, raw: Mapping[str, Any]) -> "IndexerDefinition":
        return cls(
            id=str(raw.get("id") or "").strip(),
            name=str(raw.get("name") or "").strip(),
            type=str(raw.get("type") or "").strip().lower(),
            language=str(raw.get("language") or "").strip().lower(),
            links=tuple(str(item) for item in (raw.get("links") or ())),
            caps=raw.get("caps") or {},
            search=raw.get("search") or {},
            login=raw.get("login"),
        )

    def compatibility(self) -> Compatibility:
        reasons = []
        if not self.id or not self.name:
            reasons.append("missing id/name")
        if self.type not in PUBLIC_TYPES:
            reasons.append(f"not a public indexer type: {self.type or 'unknown'}")
        if self.language not in ENGLISH_CODES and not self.language.startswith("en-"):
            reasons.append(f"not English: {self.language or 'unknown'}")
        if not self.links:
            reasons.append("no base links")
        if self._requires_account_login():
            reasons.append("requires account/authentication")
        if not self._has_movie_or_tv_capability():
            reasons.append("no movie/TV capability")
        if not self.search:
            reasons.append("no search definition")
        return Compatibility(not reasons, tuple(reasons))

    def _requires_account_login(self) -> bool:
        if not self.login:
            return False
        # Public definitions sometimes contain harmless test/cookie metadata. Treat
        # explicit credential/captcha/login-submit mechanics as account-only.
        login = self.login
        return bool(
            login.get("captcha")
            or login.get("inputs")
            or login.get("selectorinputs")
            or login.get("submitpath")
            or str(login.get("method") or "").upper() == "POST"
        )

    def _has_movie_or_tv_capability(self) -> bool:
        categories = self.caps.get("categories") or {}
        mappings: Sequence[Mapping[str, Any]] = self.caps.get("categorymappings") or ()
        values = [str(key) for key in categories.keys()]
        values.extend(str(item.get("cat") or "") for item in mappings)
        return any(value.startswith(MOVIE_TV_CATEGORY_PREFIXES) for value in values)
