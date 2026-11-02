package com.weshape.backend.wind;

import com.weshape.backend.wind.WindData.ByteCache;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One map's data in the bucket under a prefix: one-byte rasters (prefix + name + ".tif") read
 * cell by cell, and stored tiles (prefix + "tiles/" + layer + ".tiles" / ".index"). Opens each
 * file once; a file that cannot be opened is tried again after five minutes; when a read fails
 * (the build workflow replaced the files) everything is dropped and opened again.
 */
public final class MapStore {

    private static final Logger log = LoggerFactory.getLogger(MapStore.class);
    public static final int NODATA = 255;
    private static final long RETRY_AFTER_MS = 5 * 60 * 1000;

    private final RangeSource source;
    private final String prefix;
    private final String label;
    private final Map<String, CogFile> files = new ConcurrentHashMap<>();
    private final Map<String, TileIndex> indexes = new ConcurrentHashMap<>();
    private final Map<String, Long> failedAt = new ConcurrentHashMap<>();
    private final ByteCache blocks = new ByteCache(16L * 1024 * 1024);
    private final ByteCache tiles = new ByteCache(24L * 1024 * 1024);

    /** [label] names the map in the log, e.g. "Biomass map". */
    public MapStore(RangeSource source, String prefix, String label) {
        this.source = source;
        this.prefix = prefix;
        this.label = label;
    }

    public boolean configured() {
        return source != null;
    }

    /** The raster's byte at the place, or {@link #NODATA}; throws {@link Unavailable} when it cannot be read. */
    public int code(String name, double lat, double lon) {
        CogFile f = file(name);
        if (f == null) {
            throw new Unavailable();
        }
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
            } catch (IOException | RuntimeException e) {
                log.warn("{}: block {} unreadable ({}), reopening the files", label, key, e.toString());
                forget();
                throw new Unavailable();
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

    /** A stored tile, or null where the layer has nothing (the sea); throws {@link Unavailable} without tiles. */
    public byte[] tile(String layer, int z, int x, int y) {
        TileIndex idx = index(layer);
        if (idx == null) {
            throw new Unavailable();
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
                log.warn("{}: tile {} unreadable: {}", label, key, e.getMessage());
                throw new Unavailable();
            }
        }
        tiles.put(key, png);
        return png.length == 0 ? null : png;
    }

    public boolean hasFile(String name) {
        return file(name) != null;
    }

    public boolean hasTiles(String layer) {
        return index(layer) != null;
    }

    public TileIndex index(String layer) {
        TileIndex idx = indexes.get(layer);
        if (idx != null || source == null) {
            return idx;
        }
        synchronized (indexes) {
            idx = indexes.get(layer);
            if (idx != null || recentlyFailed("index:" + layer)) {
                return idx;
            }
            try {
                idx = TileIndex.parse(source.readAll(prefix + "tiles/" + layer + ".index"));
                indexes.put(layer, idx);
                failedAt.remove("index:" + layer);
                log.info("{}: {} stored tiles for {}", label, idx.size(), layer);
            } catch (FileNotFoundException e) {
                failedAt.put("index:" + layer, System.currentTimeMillis());
                log.info("{}: no tiles yet for {}", label, layer);
            } catch (IOException e) {
                failedAt.put("index:" + layer, System.currentTimeMillis());
                log.warn("{}: tile index for {} unreadable: {}", label, layer, e.getMessage());
            }
            return idx;
        }
    }

    public CogFile file(String name) {
        CogFile f = files.get(name);
        if (f != null || source == null) {
            return f;
        }
        synchronized (files) {
            f = files.get(name);
            if (f != null || recentlyFailed("file:" + name)) {
                return f;
            }
            try {
                f = new CogFile(source, prefix + name + ".tif");
                files.put(name, f);
                failedAt.remove("file:" + name);
                log.info("{}: opened {} ({} levels)", label, name, f.levels().size());
            } catch (IOException e) {
                failedAt.put("file:" + name, System.currentTimeMillis());
                log.info("{}: cannot open {}: {}", label, name, e.getMessage());
            }
            return f;
        }
    }

    /** Drops every opened file, index and cached block (after the data was rebuilt). */
    public void forget() {
        synchronized (files) {
            files.clear();
        }
        synchronized (indexes) {
            indexes.clear();
        }
        failedAt.clear();
        blocks.clear();
        tiles.clear();
    }

    private boolean recentlyFailed(String key) {
        Long failed = failedAt.get(key);
        return failed != null && System.currentTimeMillis() - failed < RETRY_AFTER_MS;
    }

    /** The data cannot be read right now (not built yet, not configured, or rebuilt while open). */
    public static class Unavailable extends RuntimeException {
        public Unavailable() {
            super("Map data is not available");
        }
    }
}
