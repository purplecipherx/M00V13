from __future__ import annotations

import email.utils
import random
import threading
import time
from dataclasses import dataclass
from typing import Dict, Mapping, Optional
from urllib.parse import urlparse

import requests


class TransportError(RuntimeError):
    pass


class ChallengeDetected(TransportError):
    """A site returned an anti-bot/interstitial response that M00V13 will not bypass."""


@dataclass(frozen=True)
class TransportPolicy:
    connect_timeout: float = 5.0
    read_timeout: float = 10.0
    retries: int = 2
    min_host_interval: float = 0.35
    backoff_base: float = 0.45
    max_retry_after: float = 15.0
    user_agent: str = "M00V13/0.1 (Kodi; compatible scraper client)"


class HttpTransport:
    RETRYABLE = {429, 500, 502, 503, 504}
    CHALLENGE_MARKERS = (
        "cf-chl-",
        "challenge-platform",
        "cf-browser-verification",
        "attention required! | cloudflare",
        "just a moment...",
    )

    def __init__(self, policy: TransportPolicy = TransportPolicy()) -> None:
        self.policy = policy
        self.session = requests.Session()
        self.session.headers.update({"User-Agent": policy.user_agent, "Accept-Language": "en-US,en;q=0.8"})
        self._host_lock = threading.Lock()
        self._last_request: Dict[str, float] = {}

    def get(
        self,
        url: str,
        *,
        params: Optional[Mapping[str, object]] = None,
        headers: Optional[Mapping[str, str]] = None,
        cookies: Optional[Mapping[str, str]] = None,
    ) -> requests.Response:
        return self.request("GET", url, params=params, headers=headers, cookies=cookies)

    def request(self, method: str, url: str, **kwargs) -> requests.Response:
        host = (urlparse(url).hostname or "").lower()
        if not host:
            raise TransportError("request URL has no hostname")

        timeout = kwargs.pop("timeout", (self.policy.connect_timeout, self.policy.read_timeout))
        last_error: Optional[Exception] = None
        for attempt in range(self.policy.retries + 1):
            self._throttle(host)
            try:
                response = self.session.request(method, url, timeout=timeout, **kwargs)
            except requests.RequestException as exc:
                last_error = exc
                if attempt >= self.policy.retries:
                    raise TransportError(str(exc)) from exc
                self._sleep_backoff(attempt)
                continue

            if self._is_challenge(response):
                raise ChallengeDetected(f"anti-bot challenge detected for {host}")

            if response.status_code not in self.RETRYABLE:
                return response
            if attempt >= self.policy.retries:
                return response

            retry_after = self._retry_after_seconds(response)
            if retry_after is None:
                self._sleep_backoff(attempt)
            else:
                time.sleep(min(retry_after, self.policy.max_retry_after))

        if last_error:
            raise TransportError(str(last_error)) from last_error
        raise TransportError("request failed without a response")

    def _throttle(self, host: str) -> None:
        with self._host_lock:
            now = time.monotonic()
            previous = self._last_request.get(host, 0.0)
            delay = self.policy.min_host_interval - (now - previous)
            if delay > 0:
                time.sleep(delay)
            self._last_request[host] = time.monotonic()

    def _sleep_backoff(self, attempt: int) -> None:
        base = self.policy.backoff_base * (2 ** attempt)
        time.sleep(base + random.uniform(0.0, base * 0.25))

    @classmethod
    def _is_challenge(cls, response: requests.Response) -> bool:
        headers = {key.lower(): value for key, value in response.headers.items()}
        if "cf-ray" not in headers and response.status_code not in (403, 429, 503):
            return False
        sample = (response.text or "")[:250000].lower()
        return any(marker in sample for marker in cls.CHALLENGE_MARKERS)

    @staticmethod
    def _retry_after_seconds(response: requests.Response) -> Optional[float]:
        value = response.headers.get("Retry-After")
        if not value:
            return None
        try:
            return max(0.0, float(value))
        except ValueError:
            pass
        try:
            when = email.utils.parsedate_to_datetime(value)
            now = time.time()
            return max(0.0, when.timestamp() - now)
        except (TypeError, ValueError, OverflowError):
            return None
