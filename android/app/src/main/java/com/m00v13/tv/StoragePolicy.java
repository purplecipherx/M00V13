package com.m00v13.tv;

import android.os.StatFs;
import java.io.File;

public final class StoragePolicy {
    public static final long MIB = 1024L * 1024L;
    public static final long SYSTEM_RESERVE_BYTES = 1536L * MIB;
    public static final long CRITICAL_BYTES = 768L * MIB;
    public static final long EMERGENCY_TARGET_BYTES = 1280L * MIB;
    public static final long DOWNLOAD_HEADROOM_BYTES = 256L * MIB;

    private StoragePolicy() {}

    public static long availableBytes(File path) {
        return new StatFs(path.getAbsolutePath()).getAvailableBytes();
    }

    public static long allowedWriteBytes(File path, long currentM00v13Bytes, long maxM00v13Bytes, long userReserveBytes) {
        long reserve = Math.max(SYSTEM_RESERVE_BYTES, userReserveBytes);
        long free = availableBytes(path);
        long byReserve = Math.max(0L, free - reserve);
        long byQuota = maxM00v13Bytes <= 0 ? Long.MAX_VALUE : Math.max(0L, maxM00v13Bytes - currentM00v13Bytes);
        return Math.min(byReserve, byQuota);
    }

    public static boolean canStartDownload(File path, long estimatedBytes, long currentM00v13Bytes, long maxM00v13Bytes, long userReserveBytes) {
        long needed = Math.max(0L, estimatedBytes) + DOWNLOAD_HEADROOM_BYTES;
        return needed <= allowedWriteBytes(path, currentM00v13Bytes, maxM00v13Bytes, userReserveBytes);
    }

    public static boolean isCritical(File path) {
        return availableBytes(path) < CRITICAL_BYTES;
    }

    public static long bytesNeededForEmergencyTarget(File path) {
        return Math.max(0L, EMERGENCY_TARGET_BYTES - availableBytes(path));
    }
}
