package com.weshape.backend.wind;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.io.FileNotFoundException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The Global Wind Atlas layers (250 m, whole world), read from the R2 bucket on demand:
 * values for a point, and coloured map tiles. Without bucket settings the wind map is
 * simply reported as unavailable.
 */
@Component
public class WindData {

    private static final Logger log = LoggerFactory.getLogger(WindData.class);

    public static final String SOURCE = "Global Wind Atlas 4.0 (DTU, World Bank Group)";
    static final int TILE = 256;
    private static final long RETRY_AFTER_MS = 5 * 60 * 1000;
    /** Zoom levels the "Build wind map tiles" workflow draws in advance. */
    public static final int STORED_MAX_ZOOM = 8;
    private static final List<String> FILES = List.of("ws100", "ws10", "ws50", "ws200", "pd100");
    private static final List<String> LAYER_IDS =
            List.of("speed", "power", "height-10", "height-50", "height-100", "height-150", "height-200");

    private final RangeSource source;
    private final String prefix;
    private final String tilesPrefix;
    private final ExecutorService readers = Executors.newVirtualThreadPerTaskExecutor();
    private final Thread warmUp;
    /** Per layer: when its stored tiles were last looked for, and their index once found. */
    private final Map<String, Long> storedCheckedAt = new ConcurrentHashMap<>();
    private final Map<String, TileIndex> stored = new ConcurrentHashMap<>();
    private final Map<String, CogFile> files = new ConcurrentHashMap<>();
    private final Map<String, Long> failedAt = new ConcurrentHashMap<>();
    private final ByteCache tiles = new ByteCache(48L * 1024 * 1024);
    private final ByteCache images = new ByteCache(32L * 1024 * 1024);
    private final byte[] emptyPng;

    @Autowired
    public WindData(
            @Value("${weshape.wind.r2.account-id:}") String accountId,
            @Value("${weshape.wind.r2.bucket:}") String bucket,
            @Value("${weshape.wind.r2.access-key-id:}") String accessKey,
            @Value("${weshape.wind.r2.secret-access-key:}") String secretKey,
            @Value("${weshape.wind.local-dir:}") String localDir,
            @Value("${weshape.wind.prefix:wind/gwa/}") String prefix,
            @Value("${weshape.wind.tiles-prefix:wind/tiles/}") String tilesPrefix) {
        this(RangeSources.configured(accountId, bucket, accessKey, secretKey, localDir), prefix, tilesPrefix);
    }

    public WindData(RangeSource source, String prefix) {
        this(source, prefix, "wind/tiles/");
    }

    public WindData(RangeSource source, String prefix, String tilesPrefix) {
        this.source = source;
        this.prefix = prefix;
        this.tilesPrefix = tilesPrefix;
        this.emptyPng = png(new BufferedImage(TILE, TILE, BufferedImage.TYPE_INT_ARGB));
        log.info(source == null ? "Wind map: no data source configured" : "Wind map: data from " + source.describe());
        if (source != null) {
            // Read the file headers now, so the first click on the map does not wait for them.
            warmUp = Thread.ofVirtual().name("wind-warm-up").start(() -> FILES.forEach(this::file));
        } else {
            warmUp = null;
        }
    }

    /** Waits until the file headers are read (tests: on Windows an open file cannot be deleted). */
    void awaitWarmUp() throws InterruptedException {
        if (warmUp != null) {
            warmUp.join();
        }
    }

    /** True when the files can be read (checked by opening the 100 m speed layer). */
    public boolean available() {
        return file("ws100") != null;
    }

    // ---- point values ----

    public WindPoint point(double lat, double lon) {
        CogFile ref = require("ws100");
        CogFile.Level level = ref.levels().get(0);
        int col = (int) Math.floor((normalizeLon(lon) - level.originX()) / level.pixelX());
        int row = (int) Math.floor((level.originY() - lat) / level.pixelY());
        if (row < 0 || row >= level.height() || col < 0 || col >= level.width()) {
            throw new NoWindDataException(lat, lon);
        }
        // Fetch the cell's block from every file at once instead of one after another.
        List<Future<?>> reads = new ArrayList<>();
        for (String name : FILES) {
            if (file(name) != null) {
                reads.add(readers.submit(() -> code(name, 0, col, row)));
            }
        }
        for (Future<?> read : reads) {
            try {
                read.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new WindUnavailableException();
            } catch (java.util.concurrent.ExecutionException e) {
                if (e.getCause() instanceof RuntimeException r) {
                    throw r;
                }
                throw new WindUnavailableException();
            }
        }
        int c100 = code("ws100", 0, col, row);
        if (c100 == WindEncoding.NODATA) {
            throw new NoWindDataException(lat, lon);
        }
        Map<Integer, Double> stored = new HashMap<>();
        stored.put(100, WindEncoding.speed(c100));
        for (int h : new int[] {10, 50, 200}) {
            if (file("ws" + h) == null) {
                continue;
            }
            int c = code("ws" + h, 0, col, row);
            if (c != WindEncoding.NODATA) {
                stored.put(h, WindEncoding.speed(c));
            }
        }
        List<WindPoint.Speed> speeds = new ArrayList<>();
        for (int h : WindLayer.HEIGHTS) {
            Double v = h == 150 && stored.containsKey(200)
                    ? WindEncoding.between(stored.get(100), 100, stored.get(200), 200, 150)
                    : stored.get(h);
            if (v != null) {
                speeds.add(new WindPoint.Speed(h, Math.round(v * 10) / 10.0));
            }
        }
        // Power density is optional: the point still has its speeds while that file is missing.
        double pd = Double.NaN;
        if (file("pd100") != null) {
            int cpd = code("pd100", 0, col, row);
            pd = cpd == WindEncoding.NODATA ? Double.NaN : Math.round(WindEncoding.powerDensity(cpd));
        }
        return new WindPoint(lat, lon, List.copyOf(speeds), stored.get(100), pd, SOURCE);
    }

    // ---- map tiles ----

    /** A 256 px PNG map tile (Web Mercator z/x/y) of a layer: "speed", "power" or "height-10" ... "height-200". */
    public byte[] tile(String layerId, int z, int x, int y) {
        if (!LAYER_IDS.contains(layerId)) {
            throw new IllegalArgumentException("layer " + layerId);
        }
        String cacheKey = layerId + "/" + z + "/" + x + "/" + y;
        byte[] cached = images.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        byte[] png = storedTile(layerId, z, x, y);
        if (png == null) {
            png = render(layerId, z, x, y);
        }
        images.put(cacheKey, png);
        return png;
    }

    /**
     * A tile drawn in advance by the "Build wind map tiles" workflow, or null when that
     * layer has no stored tiles (then it is drawn here). Inside a stored layer a tile
     * that is not in the index has no data (open sea), so it is empty.
     */
    private byte[] storedTile(String layerId, int z, int x, int y) {
        if (source == null || z > STORED_MAX_ZOOM) {
            return null;
        }
        TileIndex index = storedIndex(layerId);
        if (index == null) {
            return null;
        }
        long[] at = index.find(z, x, y);
        if (at == null) {
            return emptyPng;
        }
        try {
            return source.read(tilesPrefix + layerId + ".tiles", at[0], (int) at[1]);
        } catch (IOException e) {
            log.warn("Wind map: stored tile {}/{}/{}/{} unreadable: {}", layerId, z, x, y, e.getMessage());
            return null;
        }
    }

    private TileIndex storedIndex(String layerId) {
        TileIndex index = stored.get(layerId);
        if (index != null) {
            return index;
        }
        Long checked = storedCheckedAt.get(layerId);
        if (checked != null && System.currentTimeMillis() - checked < RETRY_AFTER_MS) {
            return null;
        }
        storedCheckedAt.put(layerId, System.currentTimeMillis());
        try {
            index = TileIndex.parse(source.readAll(tilesPrefix + layerId + ".index"));
        } catch (FileNotFoundException e) {
            return null;
        } catch (IOException e) {
            log.warn("Wind map: tile index for {} unreadable: {}", layerId, e.getMessage());
            return null;
        }
        stored.put(layerId, index);
        log.info("Wind map: using {} stored tiles for {}", index.size(), layerId);
        return index;
    }

    private byte[] render(String layerId, int z, int x, int y) {
        WindLayer layer;
        int height;
        if (layerId.equals("speed")) {
            layer = WindLayer.SPEED;
            height = 100;
        } else if (layerId.equals("power")) {
            layer = WindLayer.POWER;
            height = 0;
        } else if (layerId.startsWith("height-")) {
            layer = WindLayer.HEIGHT;
            height = Integer.parseInt(layerId.substring(7));
            if (!WindLayer.HEIGHTS.contains(height)) {
                throw new IllegalArgumentException("height " + height);
            }
        } else {
            throw new IllegalArgumentException("layer " + layerId);
        }
        CogFile ref = require("ws100");
        int levelIndex = levelFor(ref, z);
        CogFile.Level level = ref.levels().get(levelIndex);

        int[] lut = new int[256];
        for (int c = 0; c < 255; c++) {
            lut[c] = layer.argb(layer == WindLayer.POWER ? WindEncoding.powerDensity(c) : WindEncoding.speed(c));
        }
        String[] keys = layer == WindLayer.POWER ? new String[] {"pd100"}
                : height == 150 ? new String[] {"ws100", "ws200"}
                : new String[] {"ws" + height};
        for (String k : keys) {
            require(k);
        }

        double n = 1 << z;
        int[] pixels = new int[TILE * TILE];
        boolean any = false;
        int[] cols = new int[TILE];
        for (int px = 0; px < TILE; px++) {
            double lon = (x + (px + 0.5) / TILE) / n * 360 - 180;
            cols[px] = (int) Math.floor((lon - level.originX()) / level.pixelX());
        }
        for (int py = 0; py < TILE; py++) {
            double my = Math.PI * (1 - 2 * (y + (py + 0.5) / TILE) / n);
            double lat = Math.toDegrees(Math.atan(Math.sinh(my)));
            int row = (int) Math.floor((level.originY() - lat) / level.pixelY());
            if (row < 0 || row >= level.height()) {
                continue;
            }
            for (int px = 0; px < TILE; px++) {
                int col = cols[px];
                if (col < 0 || col >= level.width()) {
                    continue;
                }
                int c = code(keys[0], levelIndex, col, row);
                if (c == WindEncoding.NODATA) {
                    continue;
                }
                if (keys.length == 2) {
                    int c2 = code(keys[1], levelIndex, col, row);
                    if (c2 == WindEncoding.NODATA) {
                        continue;
                    }
                    double v = WindEncoding.between(WindEncoding.speed(c), 100, WindEncoding.speed(c2), 200, 150);
                    c = (int) Math.min(254, Math.round(v * 10));
                }
                pixels[py * TILE + px] = lut[c];
                any = true;
            }
        }
        if (!any) {
            return emptyPng;
        }
        BufferedImage img = new BufferedImage(TILE, TILE, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, TILE, TILE, pixels, 0, TILE);
        return png(img);
    }

    /** The coarsest level that still has at least one source cell per map pixel. */
    static int levelFor(CogFile file, int z) {
        double degreesPerPixel = 360.0 / (TILE * (double) (1L << z));
        List<CogFile.Level> levels = file.levels();
        int best = 0;
        for (int i = 0; i < levels.size(); i++) {
            if (levels.get(i).pixelX() <= degreesPerPixel * 1.01) {
                best = i;
            }
        }
        return best;
    }

    // ---- reading ----

    private int code(String name, int levelIndex, int col, int row) {
        CogFile f = require(name);
        CogFile.Level l = f.levels().get(levelIndex);
        int tx = col / l.tileWidth();
        int ty = row / l.tileHeight();
        String key = name + ":" + CogFile.tileId(levelIndex, tx, ty);
        byte[] data = tiles.get(key);
        if (data == null) {
            try {
                data = f.readTile(levelIndex, tx, ty);
            } catch (IOException e) {
                throw new UncheckedIOException("wind tile " + key, e);
            }
            if (data == null) {
                data = new byte[0];
            }
            tiles.put(key, data);
        }
        if (data.length == 0) {
            return WindEncoding.NODATA;
        }
        int v = data[(row % l.tileHeight()) * l.tileWidth() + col % l.tileWidth()] & 0xff;
        return v == f.nodata() ? WindEncoding.NODATA : v;
    }

    private CogFile require(String name) {
        CogFile f = file(name);
        if (f == null) {
            throw new WindUnavailableException();
        }
        return f;
    }

    private CogFile file(String name) {
        if (source == null) {
            return null;
        }
        CogFile f = files.get(name);
        if (f != null) {
            return f;
        }
        Long failed = failedAt.get(name);
        if (failed != null && System.currentTimeMillis() - failed < RETRY_AFTER_MS) {
            return null;
        }
        synchronized (files) {
            f = files.get(name);
            if (f == null) {
                try {
                    f = new CogFile(source, prefix + name + ".tif");
                    files.put(name, f);
                    failedAt.remove(name);
                    log.info("Wind map: opened {} ({} levels)", name, f.levels().size());
                } catch (IOException e) {
                    failedAt.put(name, System.currentTimeMillis());
                    log.warn("Wind map: cannot open {}: {}", name, e.getMessage());
                    return null;
                }
            }
        }
        return f;
    }

    private static double normalizeLon(double lon) {
        double l = ((lon + 180) % 360 + 360) % 360 - 180;
        return l;
    }

    private static byte[] png(BufferedImage img) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The wind files cannot be read right now (not configured, or the store is unreachable). */
    public static class WindUnavailableException extends RuntimeException {
        public WindUnavailableException() {
            super("Wind data is not available");
        }
    }

    /** A small least-recently-used cache limited by total size. */
    public static final class ByteCache {
        private final long budget;
        private long used;
        private final LinkedHashMap<String, byte[]> map = new LinkedHashMap<>(256, 0.75f, true);

        public ByteCache(long budget) {
            this.budget = budget;
        }

        public synchronized byte[] get(String key) {
            return map.get(key);
        }

        public synchronized void clear() {
            map.clear();
            used = 0;
        }

        public synchronized void put(String key, byte[] value) {
            byte[] old = map.put(key, value);
            used += value.length + 64 - (old == null ? 0 : old.length + 64);
            var it = map.entrySet().iterator();
            while (used > budget && it.hasNext()) {
                var e = it.next();
                if (e.getKey().equals(key)) {
                    continue;
                }
                used -= e.getValue().length + 64;
                it.remove();
            }
        }
    }
}
