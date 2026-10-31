package com.weshape.backend.solar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Real solar values: PVGIS for a single place, the PVGIS grid ({@link SolarGrid}) for the map.
 *
 * <p>If PVGIS cannot be reached the grid answers instead (yearly values, typical months); if
 * there is no grid either, {@link ExampleSolarResource} does, and says so.
 */
@Component
@Primary
public class RealSolarResource implements SolarResource {

    private static final Logger log = LoggerFactory.getLogger(RealSolarResource.class);
    private static final int CACHE_SIZE = 5000;

    private final ExampleSolarResource example;
    private final SolarGrid grid;
    private final PvgisClient pvgis;

    /** Recent answers, so moving between layers or clicking the same spot does not ask again. */
    private final Map<String, SolarPoint> cache = new LinkedHashMap<>(256, .75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, SolarPoint> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    public RealSolarResource(ExampleSolarResource example,
                             SolarGrid grid,
                             @Value("${weshape.solar.pvgis.enabled:true}") boolean pvgisEnabled,
                             @Value("${weshape.solar.pvgis.url:" + PvgisClient.DEFAULT_URL + "}") String pvgisUrl,
                             @Value("${weshape.solar.pvgis.timeout-seconds:20}") long timeoutSeconds) {
        this.example = example;
        this.grid = grid;
        this.pvgis = pvgisEnabled ? new PvgisClient(pvgisUrl, Duration.ofSeconds(timeoutSeconds)) : null;
    }

    @Override
    public SolarPoint at(double lat, double lon) {
        String key = String.format(Locale.ROOT, "%.3f,%.3f", lat, lon);
        synchronized (cache) {
            SolarPoint hit = cache.get(key);
            if (hit != null) return hit;
        }
        if (pvgis != null) {
            try {
                SolarPoint point = pvgis.fetch(lat, lon);
                synchronized (cache) {
                    cache.put(key, point);
                }
                return point;
            } catch (IOException e) {
                log.warn("PVGIS unavailable for {}: {}", key, e.getMessage());
            }
        }
        return fromGrid(lat, lon);
    }

    private SolarPoint fromGrid(double lat, double lon) {
        if (!grid.isLoaded()) return example.at(lat, lon);
        double gti = grid.value(SolarLayer.GTI, lat, lon);
        double pvout = grid.value(SolarLayer.PVOUT, lat, lon);
        double opta = grid.value(SolarLayer.OPTA, lat, lon);
        if (Double.isNaN(pvout)) throw new NoSolarDataException("No solar data at this location");
        double[] shares = ExampleSolarResource.monthShares(lat);
        double[] gtiMonthly = new double[12];
        double[] pvMonthly = new double[12];
        for (int m = 0; m < 12; m++) {
            gtiMonthly[m] = gti * shares[m];
            pvMonthly[m] = pvout * shares[m];
        }
        return new SolarPoint(lat, lon, gti, pvout, opta, gtiMonthly, pvMonthly, "PVGIS (map grid)", false);
    }

    @Override
    public double value(SolarLayer layer, double lat, double lon) {
        if (grid.isLoaded()) {
            double v = grid.value(layer, lat, lon);
            if (!Double.isNaN(v)) return v;
        }
        return example.value(layer, lat, lon);
    }

    @Override
    public String source() {
        return grid.isLoaded() ? "PVGIS, European Commission JRC" : example.source();
    }

    @Override
    public boolean isExample() {
        return !grid.isLoaded();
    }
}
