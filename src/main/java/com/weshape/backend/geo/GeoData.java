package com.weshape.backend.geo;

import com.weshape.backend.wind.CogFile;
import com.weshape.backend.wind.RangeSource;
import com.weshape.backend.wind.RangeSources;
import com.weshape.backend.wind.TileIndex;
import com.weshape.backend.wind.WindData.ByteCache;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The geothermal map, built by the "Build geothermal map data" workflow (tool/geo_build.py)
 * into the same R2 bucket as the wind data, under geo/: two one-byte rasters for a point
 * (ground temperature, heat flow) and stored map tiles for six layers.
 */
@Component
public class GeoData {

    private static final Logger log = LoggerFactory.getLogger(GeoData.class);

    public static final String SOURCE = "SoilTemp (Lembrechts et al. 2022) · Global Heat Flow Database 2024 (IHFC)";
    public static final int MAX_ZOOM = 7;
    public static final int NODATA = 255;
    /** Rock thermal conductivity for the temperature at depth (W/m·K), as in the builder. */
    public static final double CONDUCTIVITY = 2.5;
    public static final List<Integer> DEPTHS = List.of(1, 2, 3, 5);
    public static final List<String> LAYERS =
            List.of("ground", "heatflow", "depth-1", "depth-2", "depth-3", "depth-5");
    private static final long RETRY_AFTER_MS = 5 * 60 * 1000;

    private final RangeSource source;
    private final String prefix;
    private final Map<String, CogFile> files = new ConcurrentHashMap<>();
    private final Map<String, TileIndex> indexes = new ConcurrentHashMap<>();
    private final Map<String, Long> checkedAt = new ConcurrentHashMap<>();
    private final ByteCache blocks = new ByteCache(16L * 1024 * 1024);
    private final ByteCache tiles = new ByteCache(24L * 1024 * 1024);

    @Autowired
    public GeoData(
            @Value("${weshape.wind.r2.account-id:}") String accountId,
            @Value("${weshape.wind.r2.bucket:}") String bucket,
            @Value("${weshape.wind.r2.access-key-id:}") String accessKey,
            @Value("${weshape.wind.r2.secret-access-key:}") String secretKey,
            @Value("${weshape.wind.local-dir:}") String localDir,
            @Value("${weshape.geo.prefix:geo/}") String prefix) {
        this(RangeSources.configured(accountId, bucket, accessKey, secretKey, localDir), prefix);
    }

    public GeoData(RangeSource source, String prefix) {
        this.source = source;
        this.prefix = prefix;
        if (source != null) {
            Thread.ofVirtual().name("geo-warm-up").start(() -> {
                file("ground");
                file("heatflow");
                index("ground");
            });
        }
    }

    /** True when the ground temperature raster and its tiles can be read. */
    public boolean available() {
        return file("ground") != null && index("ground") != null;
    }

    // ---- point ----

    public GeoPoint point(double lat, double lon) {
        CogFile ground = file("ground");
        if (ground == null) {
            throw new GeoUnavailableException();
        }
        int g = code(ground, lat, lon);
        if (g == NODATA) {
            throw new NoGeoDataException(lat, lon);
        }
        double t0 = groundTemperature(g);
        CogFile heat = file("heatflow");
        int q = heat == null ? NODATA : code(heat, lat, lon);
        List<GeoPoint.Depth> depths = new ArrayList<>();
        Double heatFlow = null;
        if (q != NODATA) {
            heatFlow = (double) q;
            for (int d : DEPTHS) {
                depths.add(new GeoPoint.Depth(d, Math.round(temperatureAt(t0, q, d) * 10) / 10.0));
            }
        }
        return new GeoPoint(lat, lon, t0, heatFlow, List.copyOf(depths), CONDUCTIVITY, SOURCE);
    }

    /** Ground temperature in °C from its stored byte: (°C + 30) x 4. */
    static double groundTemperature(int code) {
        return code / 4.0 - 30;
    }

    /** Conductive estimate: ground temperature + heat flow / conductivity x depth. */
    static double temperatureAt(double groundC, double heatFlowMw, int depthKm) {
        return groundC + heatFlowMw / CONDUCTIVITY * depthKm;
    }

    private int code(CogFile f, double lat, double lon) {
        CogFile.Level l = f.levels().get(0);
        int col = (int) Math.floor((lon - l.originX()) / l.pixelX());
        int row = (int) Math.floor((l.originY() - lat) / l.pixelY());
        if (row < 0 || row >= l.height() || col < 0 || col >= l.width()) {
            return NODATA;
        }
        int tx = col / l.tileWidth();
        int ty = row / l.tileHeight();
        String key = f.key() + ":" + CogFile.tileId(0, tx, ty);
        byte[] data = blocks.get(key);
        if (data == null) {
            try {
                data = f.readTile(0, tx, ty);
            } catch (IOException e) {
                log.warn("Geothermal map: block {} unreadable: {}", key, e.getMessage());
                throw new GeoUnavailableException();
            }
            if (data == null) {
                data = new byte[0];
            }
            blocks.put(key, data);
        }
        if (data.length == 0) {
            return NODATA;
        }
        int v = data[(row % l.tileHeight()) * l.tileWidth() + col % l.tileWidth()] & 0xff;
        return v == f.nodata() ? NODATA : v;
    }

    // ---- tiles ----

    /** A stored PNG map tile, or null where the layer has no data (the sea). */
    public byte[] tile(String layer, int z, int x, int y) {
        if (!LAYERS.contains(layer)) {
            throw new IllegalArgumentException("layer " + layer);
        }
        TileIndex idx = index(layer);
        if (idx == null) {
            throw new GeoUnavailableException();
        }
        if (z > MAX_ZOOM) {
            return null;
        }
        String key = layer + "/" + z + "/" + x + "/" + y;
        byte[] cached = tiles.get(key);
        if (cached != null) {
            return cached.length == 0 ? null : cached;
        }
        long[] at = idx.find(z, x, y);
        byte[] png = new byte[0];
        if (at != null) {
            try {
                png = source.read(prefix + "tiles/" + layer + ".tiles", at[0], (int) at[1]);
            } catch (IOException e) {
                log.warn("Geothermal map: tile {} unreadable: {}", key, e.getMessage());
                throw new GeoUnavailableException();
            }
        }
        tiles.put(key, png);
        return png.length == 0 ? null : png;
    }

    private TileIndex index(String layer) {
        TileIndex idx = indexes.get(layer);
        if (idx != null || source == null) {
            return idx;
        }
        Long checked = checkedAt.get("index:" + layer);
        if (checked != null && System.currentTimeMillis() - checked < RETRY_AFTER_MS) {
            return null;
        }
        checkedAt.put("index:" + layer, System.currentTimeMillis());
        try {
            idx = TileIndex.parse(source.readAll(prefix + "tiles/" + layer + ".index"));
            indexes.put(layer, idx);
            log.info("Geothermal map: {} stored tiles for {}", idx.size(), layer);
        } catch (FileNotFoundException e) {
            log.info("Geothermal map: no tiles yet for {}", layer);
        } catch (IOException e) {
            log.warn("Geothermal map: tile index for {} unreadable: {}", layer, e.getMessage());
        }
        return idx;
    }

    private CogFile file(String name) {
        CogFile f = files.get(name);
        if (f != null || source == null) {
            return f;
        }
        Long checked = checkedAt.get("file:" + name);
        if (checked != null && System.currentTimeMillis() - checked < RETRY_AFTER_MS) {
            return null;
        }
        synchronized (files) {
            f = files.get(name);
            if (f != null) {
                return f;
            }
            checkedAt.put("file:" + name, System.currentTimeMillis());
            try {
                f = new CogFile(source, prefix + name + ".tif");
                files.put(name, f);
                log.info("Geothermal map: opened {} ({} levels)", name, f.levels().size());
            } catch (IOException e) {
                log.info("Geothermal map: cannot open {}: {}", name, e.getMessage());
            }
            return f;
        }
    }

    /** The geothermal data cannot be read right now (not built yet, or the store is unreachable). */
    public static class GeoUnavailableException extends RuntimeException {
        public GeoUnavailableException() {
            super("Geothermal data is not available");
        }
    }
}
