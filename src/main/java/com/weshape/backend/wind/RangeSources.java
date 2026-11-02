package com.weshape.backend.wind;

import java.nio.file.Path;

/** Picks where the map data files are read from: the R2 bucket, a local folder, or nowhere. */
public final class RangeSources {

    private RangeSources() {}

    /** The R2 bucket when all its settings are there, else the local folder, else null (no data). */
    public static RangeSource configured(String accountId, String bucket, String accessKey, String secretKey,
                                         String localDir) {
        if (!accountId.isBlank() && !bucket.isBlank() && !accessKey.isBlank() && !secretKey.isBlank()) {
            return new R2RangeSource(accountId.trim(), bucket.trim(), accessKey.trim(), secretKey.trim());
        }
        if (!localDir.isBlank()) {
            return new FileRangeSource(Path.of(localDir));
        }
        return null;
    }
}
