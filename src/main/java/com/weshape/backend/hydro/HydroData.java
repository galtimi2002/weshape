package com.weshape.backend.hydro;

import com.weshape.backend.wind.RangeSource;
import com.weshape.backend.wind.RangeSources;
import com.weshape.backend.wind.TileIndex;
import com.weshape.backend.wind.WindData.ByteCache;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The river map: vector tiles of every river reach (RiverATLAS, built by the
 * "Build hydro map data" workflow), kept in the same R2 bucket as the wind data
 * under hydro/tiles/. The map draws the tiles itself; a point query reads the
 * most detailed tile around the place and picks the nearest reach.
 */
@Component
public class HydroData {

    private static final Logger log = LoggerFactory.getLogger(HydroData.class);

    public static final String SOURCE = "RiverATLAS v1.0 (HydroSHEDS / HydroATLAS)";
    /** The deepest zoom the tiles are built for; the map enlarges them beyond it. */
    public static final int MAX_ZOOM = 10;
    /** The tiles' layer name. */
    public static final String LAYER = "rivers";
    /** Share of the mean flow left in the river for nature; the rest is "available". Same as the builder. */
    public static final double ECOLOGICAL_SHARE = 0.10;
    /** Turbine and generator efficiency used for the potential. Same as the builder. */
    public static final double EFFICIENCY = 0.80;
    /** How far from a click a river still counts as "here". */
    static final double SEARCH_KM = 2.0;
    private static final long RETRY_AFTER_MS = 5 * 60 * 1000;
    private static final double EARTH_CIRCUMFERENCE_M = 40_075_016.686;

    private final RangeSource source;
    private final String prefix;
    private final ByteCache cache = new ByteCache(24L * 1024 * 1024);
    private volatile TileIndex index;
    private volatile long checkedAt;

    @Autowired
    public HydroData(
            @Value("${weshape.wind.r2.account-id:}") String accountId,
            @Value("${weshape.wind.r2.bucket:}") String bucket,
            @Value("${weshape.wind.r2.access-key-id:}") String accessKey,
            @Value("${weshape.wind.r2.secret-access-key:}") String secretKey,
            @Value("${weshape.wind.local-dir:}") String localDir,
            @Value("${weshape.hydro.prefix:hydro/tiles/}") String prefix) {
        this(RangeSources.configured(accountId, bucket, accessKey, secretKey, localDir), prefix);
    }

    public HydroData(RangeSource source, String prefix) {
        this.source = source;
        this.prefix = prefix;
        if (source != null) {
            Thread.ofVirtual().name("hydro-warm-up").start(this::index);
        }
    }

    /** True when the river tiles can be read. */
    public boolean available() {
        return index() != null;
    }

    /**
     * One vector tile, gzip-compressed as stored, or an empty array when there are no
     * rivers in it (sea, desert) or the zoom is beyond the stored ones.
     */
    public byte[] tile(int z, int x, int y) {
        TileIndex idx = require();
        if (z > MAX_ZOOM) {
            return new byte[0];
        }
        String key = z + "/" + x + "/" + y;
        byte[] cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        long[] at = idx.find(z, x, y);
        byte[] data;
        if (at == null) {
            data = new byte[0];
        } else {
            try {
                data = source.read(prefix + LAYER + ".tiles", at[0], (int) at[1]);
            } catch (IOException e) {
                log.warn("Hydro map: tile {} unreadable: {}", key, e.getMessage());
                throw new HydroUnavailableException();
            }
        }
        cache.put(key, data);
        return data;
    }

    /** The nearest river reach within {@value #SEARCH_KM} km of the place. */
    public HydroPoint point(double lat, double lon) {
        require();
        if (Math.abs(lat) > 85) {
            throw new NoHydroDataException(lat, lon);
        }
        int z = MAX_ZOOM;
        int n = 1 << z;
        double gx = (lon + 180) / 360 * n;
        double latRad = Math.toRadians(lat);
        double gy = (1 - Math.log(Math.tan(latRad) + 1 / Math.cos(latRad)) / Math.PI) / 2 * n;
        double tileMeters = EARTH_CIRCUMFERENCE_M * Math.cos(latRad) / n;
        double r = SEARCH_KM * 1000 / tileMeters;

        double best = Double.MAX_VALUE;
        MvtReader.Line bestLine = null;
        for (int ty = (int) Math.floor(gy - r); ty <= (int) Math.floor(gy + r); ty++) {
            if (ty < 0 || ty >= n) {
                continue;
            }
            for (int tx = (int) Math.floor(gx - r); tx <= (int) Math.floor(gx + r); tx++) {
                int wx = Math.floorMod(tx, n);
                MvtReader.Layer layer = decode(z, wx, ty);
                if (layer == null) {
                    continue;
                }
                // The place in this tile's own units.
                double px = (gx - tx) * layer.extent();
                double py = (gy - ty) * layer.extent();
                for (MvtReader.Line line : layer.lines()) {
                    double d = distance(line, px, py) / layer.extent();
                    if (d < best) {
                        best = d;
                        bestLine = line;
                    }
                }
            }
        }
        if (bestLine == null || best > r) {
            throw new NoHydroDataException(lat, lon);
        }
        var v = bestLine.values();
        double km = best * tileMeters / 1000;
        return new HydroPoint(lat, lon, round(km, 2), value(v, "p"), value(v, "q"), value(v, "qa"),
                value(v, "qn"), value(v, "qx"), value(v, "h"), value(v, "l"), ECOLOGICAL_SHARE, EFFICIENCY, SOURCE);
    }

    private MvtReader.Layer decode(int z, int x, int y) {
        byte[] data = tile(z, x, y);
        if (data.length == 0) {
            return null;
        }
        try {
            return MvtReader.layer(gunzip(data), LAYER);
        } catch (IOException e) {
            log.warn("Hydro map: tile {}/{}/{} unreadable: {}", z, x, y, e.getMessage());
            return null;
        }
    }

    /** Shortest distance from (px, py) to the line, in tile units. */
    static double distance(MvtReader.Line line, double px, double py) {
        double best = Double.MAX_VALUE;
        for (int[] part : line.parts()) {
            for (int i = 0; i + 3 < part.length; i += 2) {
                best = Math.min(best, segment(px, py, part[i], part[i + 1], part[i + 2], part[i + 3]));
            }
        }
        return best;
    }

    private static double segment(double px, double py, double ax, double ay, double bx, double by) {
        double dx = bx - ax;
        double dy = by - ay;
        double len2 = dx * dx + dy * dy;
        double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / len2));
        double cx = ax + t * dx - px;
        double cy = ay + t * dy - py;
        return Math.sqrt(cx * cx + cy * cy);
    }

    private static double value(java.util.Map<String, Double> values, String key) {
        Double v = values.get(key);
        return v == null || v.isNaN() ? 0 : v;
    }

    private static double round(double v, int digits) {
        double f = Math.pow(10, digits);
        return Math.round(v * f) / f;
    }

    static byte[] gunzip(byte[] data) throws IOException {
        if (data.length < 2 || (data[0] & 0xff) != 0x1f || (data[1] & 0xff) != 0x8b) {
            return data; // stored without compression
        }
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(data))) {
            return in.readAllBytes();
        }
    }

    private TileIndex require() {
        TileIndex idx = index();
        if (idx == null) {
            throw new HydroUnavailableException();
        }
        return idx;
    }

    private TileIndex index() {
        TileIndex idx = index;
        if (idx != null || source == null) {
            return idx;
        }
        synchronized (this) {
            if (index != null) {
                return index;
            }
            if (checkedAt != 0 && System.currentTimeMillis() - checkedAt < RETRY_AFTER_MS) {
                return null;
            }
            checkedAt = System.currentTimeMillis();
            try {
                index = TileIndex.parse(source.readAll(prefix + LAYER + ".index"));
                log.info("Hydro map: {} river tiles from {}", index.size(), source.describe());
            } catch (FileNotFoundException e) {
                log.info("Hydro map: no river tiles yet ({}{}.index)", prefix, LAYER);
            } catch (IOException e) {
                log.warn("Hydro map: tile index unreadable: {}", e.getMessage());
            }
            return index;
        }
    }

    /** The river tiles cannot be read right now (not built yet, not configured, or the store is unreachable). */
    public static class HydroUnavailableException extends RuntimeException {
        public HydroUnavailableException() {
            super("Hydro data is not available");
        }
    }
}
