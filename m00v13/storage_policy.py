from __future__ import annotations

from dataclasses import dataclass
from typing import Iterable, List, Optional

MIB = 1024 * 1024

# Internal-storage safety floors for constrained Android/Google TV devices.
# These are system-owned and cannot be disabled by ordinary download settings.
SYSTEM_RESERVE_BYTES = 1536 * MIB
EMERGENCY_TARGET_BYTES = 1280 * MIB
CRITICAL_FREE_BYTES = 768 * MIB
DOWNLOAD_HEADROOM_BYTES = 256 * MIB


@dataclass(frozen=True)
class DownloadRecord:
    id: str
    size_bytes: int
    watched: bool
    pinned: bool = False
    last_accessed_epoch: float = 0.0
    completed_epoch: float = 0.0


@dataclass(frozen=True)
class StorageDecision:
    can_write: bool
    writable_bytes: int
    required_free_bytes: int
    reason: str = ""


def required_reserve_bytes(user_reserve_bytes: int = 0) -> int:
    """The built-in Android safety floor always applies; user policy may be stricter."""
    return max(SYSTEM_RESERVE_BYTES, max(0, user_reserve_bytes))


def writable_bytes(
    *,
    free_bytes: int,
    m00v13_used_bytes: int,
    max_m00v13_bytes: Optional[int] = None,
    user_reserve_bytes: int = 0,
) -> int:
    reserve = required_reserve_bytes(user_reserve_bytes)
    by_reserve = max(0, free_bytes - reserve)
    if max_m00v13_bytes is None:
        return by_reserve
    by_quota = max(0, max_m00v13_bytes - m00v13_used_bytes)
    return min(by_reserve, by_quota)


def can_start_download(
    estimated_bytes: int,
    *,
    free_bytes: int,
    m00v13_used_bytes: int,
    max_m00v13_bytes: Optional[int] = None,
    user_reserve_bytes: int = 0,
) -> StorageDecision:
    required = max(0, estimated_bytes) + DOWNLOAD_HEADROOM_BYTES
    available = writable_bytes(
        free_bytes=free_bytes,
        m00v13_used_bytes=m00v13_used_bytes,
        max_m00v13_bytes=max_m00v13_bytes,
        user_reserve_bytes=user_reserve_bytes,
    )
    return StorageDecision(
        can_write=required <= available,
        writable_bytes=available,
        required_free_bytes=required_reserve_bytes(user_reserve_bytes),
        reason="" if required <= available else "storage safety reserve or M00V13 quota would be crossed",
    )


def cleanup_order(records: Iterable[DownloadRecord]) -> List[DownloadRecord]:
    """Return auto-deletion candidates in safest deletion order.

    1) watched, unpinned downloads, oldest access first
    2) unwatched, unpinned downloads, oldest access first
    Pinned downloads are never returned.
    """
    eligible = [record for record in records if not record.pinned]
    return sorted(
        eligible,
        key=lambda record: (
            0 if record.watched else 1,
            record.last_accessed_epoch or record.completed_epoch,
            record.completed_epoch,
        ),
    )


def bytes_to_reclaim(free_bytes: int) -> int:
    """Reclaim toward a healthy operating target once free space becomes critical."""
    if free_bytes >= CRITICAL_FREE_BYTES:
        return 0
    return max(0, EMERGENCY_TARGET_BYTES - free_bytes)
