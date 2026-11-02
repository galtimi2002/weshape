package com.weshape.backend.bio;

import com.weshape.backend.wind.MapStore;
import com.weshape.backend.wind.RangeSource;
import com.weshape.backend.wind.RangeSources;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The biomass map, built by the "Build biomass map data" workflow (tool/bio_build.py) into the
 * same R2 bucket as the other map data, under bio/: four one-byte rasters on a 5 arc-minute grid
 * (density, crop, forest, energy) and stored tiles for three layers (density, resource, energy).
 */
@Component
public class BioData {

    public static final String SOURCE = "MapSPAM 2020 (IFPRI) · ESA CCI Biomass";
    public static final int MAX_ZOOM = 7;
    public static final double RADIUS_KM = 25;
    /** Heat a family home uses in a year (MWh), for the "homes heated" figure. */
    public static final double HOME_MWH = 15;
    public static final List<String> LAYERS = List.of("density", "resource", "energy");

    private final MapStore store;

    @Autowired
    public BioData(
            @Value("${weshape.wind.r2.account-id:}") String accountId,
            @Value("${weshape.wind.r2.bucket:}") String bucket,
            @Value("${weshape.wind.r2.access-key-id:}") String accessKey,
            @Value("${weshape.wind.r2.secret-access-key:}") String secretKey,
            @Value("${weshape.wind.local-dir:}") String localDir,
            @Value("${weshape.bio.prefix:bio/}") String prefix) {
        this(RangeSources.configured(accountId, bucket, accessKey, secretKey, localDir), prefix);
    }

    public BioData(RangeSource source, String prefix) {
        this.store = new MapStore(source, prefix, "Biomass map");
        if (source != null) {
            Thread.ofVirtual().name("bio-warm-up").start(() -> {
                for (String f : List.of("density", "crop", "forest", "energy")) {
                    store.file(f);
                }
                store.index("density");
            });
        }
    }

    public boolean available() {
        return store.hasFile("density") && store.hasTiles("density");
    }

    /** value = a x (exp(code / s) - 1), as tool/bio_build.py writes it. */
    static double decode(String name, int code) {
        double a;
        double s;
        switch (name) {
            case "density" -> { a = 0.01; s = 30; }
            case "energy" -> { a = 0.4; s = 24; }
            default -> { a = 0.1; s = 24; }
        }
        return a * Math.expm1(code / s);
    }

    public BioPoint point(double lat, double lon) {
        try {
            int d = store.code("density", lat, lon);
            if (d == MapStore.NODATA) {
                throw new NoBioDataException(lat, lon);
            }
            double crop = value("crop", lat, lon);
            double forest = value("forest", lat, lon);
            double energy = value("energy", lat, lon);
            long homes = Math.round(energy * 1000 / HOME_MWH / 100) * 100;
            return new BioPoint(lat, lon, round(decode("density", d), 2), round(crop, 1), round(forest, 1),
                    round(crop + forest, 1), round(energy, 0), homes, RADIUS_KM, SOURCE);
        } catch (MapStore.Unavailable e) {
            throw new BioUnavailableException();
        }
    }

    private double value(String name, double lat, double lon) {
        int c = store.code(name, lat, lon);
        return c == MapStore.NODATA ? 0 : decode(name, c);
    }

    private static double round(double v, int digits) {
        double f = Math.pow(10, digits);
        return Math.round(v * f) / f;
    }

    /** A stored PNG tile, or null where there is nothing (the sea). */
    public byte[] tile(String layer, int z, int x, int y) {
        if (!LAYERS.contains(layer)) {
            throw new IllegalArgumentException("layer " + layer);
        }
        if (z > MAX_ZOOM) {
            return null;
        }
        try {
            return store.tile(layer, z, x, y);
        } catch (MapStore.Unavailable e) {
            throw new BioUnavailableException();
        }
    }

    void forget() {
        store.forget();
    }

    /** The biomass data cannot be read right now. */
    public static class BioUnavailableException extends RuntimeException {
        public BioUnavailableException() {
            super("Biomass data is not available");
        }
    }
}
