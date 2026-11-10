package com.weshape.backend.building;

import com.weshape.backend.wind.RangeSource;
import com.weshape.backend.wind.RangeSources;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Building outlines around a place, worldwide, from Overture Maps (OpenStreetMap, Microsoft, Google
 * and others, merged). The files stay in Overture's public bucket; an index in R2 (built every week
 * by the "Build building index" workflow) says which of them to open. Answers are kept in memory
 * only: the backend has a read-only key to R2.
 */
@Component
public class BuildingData {

    private static final Logger log = LoggerFactory.getLogger(BuildingData.class);

    public static final String ATTRIBUTION = "Buildings: <a href=\"https://overturemaps.org\" target=\"_blank\" "
            + "rel=\"noopener\">Overture Maps Foundation</a>, © <a href=\"https://www.openstreetmap.org/copyright\" "
            + "target=\"_blank\" rel=\"noopener\">OpenStreetMap</a> contributors (ODbL) and others";
    /** Half the side of the square read around a place, m: the building plus its neighbours. */
    static final double HALF_M = 120;
    /** A place outside every building still picks the nearest one within this distance, m. */
    static final double PICK_M = 20;
    /** Buildings within this distance count as neighbours (shade, or another pick), m. */
    static final double NEARBY_M = 50;
    /** At most this many neighbours in an answer. */
    static final int MAX_NEARBY = 60;
    private static final long RETRY_AFTER_MS = 5 * 60 * 1000;
    private static final long REFRESH_AFTER_MS = 6 * 60 * 60 * 1000;
    private static final int CACHE_ENTRIES = 300;

    private final RangeSource source;
    private final String indexKey;
    private final BuildingReader reader;
    private volatile BuildingIndex index;
    private volatile long loadedAt;
    private volatile long checkedAt;
    private final Map<String, BuildingsAt> cache = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, BuildingsAt> eldest) {
            return size() > CACHE_ENTRIES;
        }
    };

    @Autowired
    public BuildingData(
            @Value("${weshape.wind.r2.account-id:}") String accountId,
            @Value("${weshape.wind.r2.bucket:}") String bucket,
            @Value("${weshape.wind.r2.access-key-id:}") String accessKey,
            @Value("${weshape.wind.r2.secret-access-key:}") String secretKey,
            @Value("${weshape.wind.local-dir:}") String localDir,
            @Value("${weshape.buildings.index-key:buildings/index.json}") String indexKey,
            BuildingReader reader) {
        this(RangeSources.configured(accountId, bucket, accessKey, secretKey, localDir), indexKey, reader);
    }

    public BuildingData(RangeSource source, String indexKey, BuildingReader reader) {
        this.source = source;
        this.indexKey = indexKey;
        this.reader = reader;
    }

    /** True when the building index can be read. */
    public boolean available() {
        return index() != null;
    }

    /** The building at the place (or nearest to it) and the ones around it. */
    public BuildingsAt at(double lat, double lon) {
        BuildingIndex idx = index();
        if (idx == null) {
            throw new BuildingsUnavailableException("Building data is not available");
        }
        String key = String.format(Locale.ROOT, "%s|%.5f|%.5f", idx.release(), lat, lon);
        synchronized (cache) {
            BuildingsAt hit = cache.get(key);
            if (hit != null) {
                return hit;
            }
        }
        double halfLat = Math.toDegrees(HALF_M / 6_371_008.8);
        double halfLon = halfLat / Math.max(0.05, Math.cos(Math.toRadians(lat)));
        double w = lon - halfLon, s = lat - halfLat, e = lon + halfLon, n = lat + halfLat;
        List<BuildingReader.Raw> raw;
        long t = System.currentTimeMillis();
        try {
            raw = reader.read(idx.filesFor(w, s, e, n), w, s, e, n);
        } catch (IOException ex) {
            log.warn("Buildings around {}, {} unreadable: {}", lat, lon, ex.getMessage());
            throw new BuildingsUnavailableException("Building data could not be read just now");
        }
        log.debug("Buildings around {}, {}: {} in {} ms", lat, lon, raw.size(), System.currentTimeMillis() - t);
        BuildingsAt answer = answer(raw, lat, lon, idx.release());
        synchronized (cache) {
            cache.put(key, answer);
        }
        return answer;
    }

    /** Picks the building and its neighbours among the ones read. */
    static BuildingsAt answer(List<BuildingReader.Raw> raw, double lat, double lon, String release) {
        List<BuildingReader.Raw> rows = new ArrayList<>();
        List<Footprint> shapes = new ArrayList<>();
        for (BuildingReader.Raw r : raw) {
            Footprint f;
            try {
                f = new Footprint(Wkb.polygons(r.wkb()), lat, lon);
            } catch (RuntimeException ex) {
                continue; // Not a polygon, or broken: skip it.
            }
            if (!f.isEmpty()) {
                rows.add(r);
                shapes.add(f);
            }
        }
        int chosen = -1;
        double chosenDistance = Double.MAX_VALUE;
        for (int i = 0; i < shapes.size(); i++) {
            double d = shapes.get(i).distanceTo(0, 0);
            // Inside wins; between two buildings the place is in, the smaller one (a house in a courtyard).
            boolean better = d < chosenDistance
                    || (d == 0 && chosenDistance == 0 && shapes.get(i).area() < shapes.get(chosen).area());
            if (d <= PICK_M && better) {
                chosen = i;
                chosenDistance = d;
            }
        }
        Footprint main = chosen >= 0 ? shapes.get(chosen) : null;
        double[] from = main != null ? main.centroid() : new double[] {0, 0};
        List<double[]> near = new ArrayList<>(); // {index, distance}
        for (int i = 0; i < shapes.size(); i++) {
            if (i == chosen) {
                continue;
            }
            double d = main != null ? main.distanceTo(shapes.get(i)) : shapes.get(i).distanceTo(0, 0);
            if (d <= NEARBY_M) {
                near.add(new double[] {i, d});
            }
        }
        near.sort(Comparator.comparingDouble(a -> a[1]));
        List<BuildingsAt.Building> nearby = new ArrayList<>();
        for (double[] a : near.subList(0, Math.min(MAX_NEARBY, near.size()))) {
            int i = (int) a[0];
            nearby.add(building(rows.get(i), shapes.get(i), a[1], from, false));
        }
        BuildingsAt.Building building = main == null ? null
                : building(rows.get(chosen), main, chosenDistance, new double[] {0, 0}, true);
        return new BuildingsAt(building, List.copyOf(nearby), release, ATTRIBUTION);
    }

    private static BuildingsAt.Building building(BuildingReader.Raw r, Footprint f, double distance,
                                                 double[] from, boolean detail) {
        List<double[]> outline = new ArrayList<>();
        for (double[] p : f.outline()) {
            outline.add(new double[] {round(p[0], 7), round(p[1], 7)});
        }
        double[] c = f.centroid();
        double dx = c[0] - from[0], dy = c[1] - from[1];
        Double bearing = Math.hypot(dx, dy) < 0.5 ? null : round(Footprint.bearing(dx, dy), 0);
        List<Footprint.Wall> walls = null;
        Double axis = null;
        if (detail) {
            walls = new ArrayList<>();
            for (Footprint.Wall wall : f.walls()) {
                walls.add(new Footprint.Wall(round(wall.lengthM(), 1), round(wall.facingDeg(), 0) % 360));
            }
            axis = round(f.mainAxis(), 0) % 180;
        }
        return new BuildingsAt.Building(r.id(), outline, round(f.area(), 1), round(f.perimeter(), 1),
                round(distance, 1), bearing, r.heightM() == null ? null : round(r.heightM(), 1), r.floors(),
                r.roofShape(), r.roofDirectionDeg() == null ? null : round(r.roofDirectionDeg(), 0), r.kind(),
                walls, axis);
    }

    private static double round(double v, int digits) {
        double f = Math.pow(10, digits);
        return Math.round(v * f) / f;
    }

    private BuildingIndex index() {
        long now = System.currentTimeMillis();
        BuildingIndex idx = index;
        boolean stale = idx == null ? now - checkedAt > RETRY_AFTER_MS : now - loadedAt > REFRESH_AFTER_MS;
        if (source == null || !stale) {
            return idx;
        }
        synchronized (this) {
            if (index != idx) {
                return index; // Another thread just loaded it.
            }
            checkedAt = now;
            try {
                BuildingIndex fresh = BuildingIndex.parse(source.readAll(indexKey));
                if (idx == null || !fresh.release().equals(idx.release())) {
                    log.info("Buildings: index of Overture {} ({} files) from {}", fresh.release(),
                            fresh.files().size(), source.describe());
                }
                index = fresh;
                loadedAt = now;
            } catch (FileNotFoundException e) {
                log.info("Buildings: no index at {} yet (run the \"Build building index\" workflow)", indexKey);
                // Keep an older index, if any, and look again later.
                loadedAt = now;
            } catch (IOException | RuntimeException e) {
                log.warn("Buildings: index unreadable from {}: {}", source.describe(), e.getMessage());
                loadedAt = now - REFRESH_AFTER_MS + RETRY_AFTER_MS;
            }
            return index;
        }
    }

    /** No building index (yet), or the files could not be read. */
    public static class BuildingsUnavailableException extends RuntimeException {
        public BuildingsUnavailableException(String message) {
            super(message);
        }
    }
}
