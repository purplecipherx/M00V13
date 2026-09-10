package com.m00v13.tv;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

public final class ResumableDownloader {
    public interface Control {
        boolean cancelled();
        void onProgress(long downloadedBytes, long totalBytes);
    }

    public static final class Result {
        public final boolean complete;
        public final long bytes;
        public final String reason;
        Result(boolean complete, long bytes, String reason) {
            this.complete = complete; this.bytes = bytes; this.reason = reason;
        }
    }

    private ResumableDownloader() {}

    public static Result download(String rawUrl, File destination, File storageRoot,
                                  long currentM00v13Bytes, long maxM00v13Bytes,
                                  long userReserveBytes, Control control) throws IOException {
        File parent = destination.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("Cannot create download directory");
        File part = new File(destination.getAbsolutePath() + ".part");
        long existing = part.exists() ? part.length() : 0L;

        HttpURLConnection conn = (HttpURLConnection) new URL(rawUrl).openConnection();
        conn.setConnectTimeout(12000);
        conn.setReadTimeout(20000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("Accept-Encoding", "identity");
        if (existing > 0) conn.setRequestProperty("Range", "bytes=" + existing + "-");
        conn.connect();

        int code = conn.getResponseCode();
        boolean resumed = existing > 0 && code == HttpURLConnection.HTTP_PARTIAL;
        if (code < 200 || code >= 300) {
            conn.disconnect();
            return new Result(false, existing, "HTTP " + code);
        }
        if (existing > 0 && !resumed) {
            existing = 0L;
            if (part.exists() && !part.delete()) {
                conn.disconnect();
                throw new IOException("Server does not support resume and partial file cannot be reset");
            }
        }

        long responseLength = conn.getContentLengthLong();
        long total = responseLength > 0 ? existing + responseLength : -1L;
        if (total > 0 && !StoragePolicy.canStartDownload(storageRoot, Math.max(0L, total - existing),
                currentM00v13Bytes, maxM00v13Bytes, userReserveBytes)) {
            conn.disconnect();
            return new Result(false, existing, "storage reserve");
        }

        long written = existing;
        byte[] buffer = new byte[64 * 1024];
        try (BufferedInputStream in = new BufferedInputStream(conn.getInputStream(), buffer.length);
             FileOutputStream out = new FileOutputStream(part, resumed)) {
            int n;
            int chunks = 0;
            while ((n = in.read(buffer)) >= 0) {
                if (n == 0) continue;
                if (control != null && control.cancelled()) return new Result(false, written, "cancelled");
                if ((chunks++ & 15) == 0) {
                    long free = StoragePolicy.availableBytes(storageRoot);
                    long reserve = Math.max(StoragePolicy.SYSTEM_RESERVE_BYTES, userReserveBytes);
                    if (free <= reserve + StoragePolicy.DOWNLOAD_HEADROOM_BYTES) {
                        return new Result(false, written, "storage reserve");
                    }
                }
                out.write(buffer, 0, n);
                written += n;
                if (control != null) control.onProgress(written, total);
            }
            out.getFD().sync();
        } finally {
            conn.disconnect();
        }

        if (destination.exists() && !destination.delete()) throw new IOException("Cannot replace existing download");
        if (!part.renameTo(destination)) throw new IOException("Cannot finalize download");
        return new Result(true, written, "complete");
    }
}
