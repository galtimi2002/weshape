package com.weshape.backend.solar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;

/**
 * Real values for the map, sampled from PVGIS on a grid by {@link SolarGridBuilder} and shipped
 * with the backend as {@code solar/pvgis-grid.csv.gz}.
 *
 * <p>The samples are put on a 0.5° grid (Europe is sampled at that spacing, the rest of the
 * world every 2°), gaps are filled from their neighbours, and values in between are interpolated.
 * Without the file the grid is empty and {@link #isLoaded()} is false.
 */
@Component
public class SolarGrid {

    private static final Logger log = LoggerFactory.getLogger(SolarGrid.class);

    public static final String RESOURCE = "solar/pvgis-grid.csv.gz";

    static final double STEP = 0.5;
    static final double LAT0 = -60;
    static final double LAT1 = 75;
    static final double LON0 = -180;
    static final double LON1 = 180;
    static final int ROWS = (int) Math.round((LAT1 - LAT0) / STEP) + 1;
    static final int COLS = (int) Math.round((LON1 - LON0) / STEP) + 1;

    private final float[][] values = new float[3][];
    private boolean loaded;

    public SolarGrid() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                log.info("No {} yet: the map shows example values. Run SolarGridBuilder to make it.", RESOURCE);
                return;
            }
            load(new GZIPInputStream(in));
        } catch (IOException e) {
            log.warn("Could not read {}: the map shows example values", RESOURCE, e);
        }
    }

    /** For tests: a grid from an uncompressed CSV. */
    SolarGrid(InputStream csv) throws IOException {
        load(csv);
    }

    public boolean isLoaded() {
        return loaded;
    }

    /** The interpolated yearly value at a place, or NaN outside the grid. */
    public double value(SolarLayer layer, double lat, double lon) {
        if (!loaded || lat < LAT0 || lat > LAT1) return Double.NaN;
        float[] v = values[layer.ordinal()];
        double r = (lat - LAT0) / STEP;
        double c = (lon - LON0) / STEP;
        int r0 = Math.min(ROWS - 2, (int) Math.floor(r));
        int c0 = Math.min(COLS - 2, Math.max(0, (int) Math.floor(c)));
        double fr = r - r0, fc = c - c0;
        double a = v[r0 * COLS + c0], b = v[r0 * COLS + c0 + 1];
        double d = v[(r0 + 1) * COLS + c0], e = v[(r0 + 1) * COLS + c0 + 1];
        return (a * (1 - fc) + b * fc) * (1 - fr) + (d * (1 - fc) + e * fc) * fr;
    }

    private void load(InputStream raw) throws IOException {
        for (int l = 0; l < 3; l++) {
            values[l] = new float[ROWS * COLS];
            Arrays.fill(values[l], Float.NaN);
        }
        int count = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(raw, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#") || line.startsWith("lat")) continue;
                String[] f = line.split(",", -1);
                if (f.length < 5 || f[2].isEmpty()) continue; // a sea point: no data
                int r = (int) Math.round((Double.parseDouble(f[0]) - LAT0) / STEP);
                int c = (int) Math.round((Double.parseDouble(f[1]) - LON0) / STEP);
                if (r < 0 || r >= ROWS || c < 0 || c >= COLS) continue;
                values[SolarLayer.GTI.ordinal()][r * COLS + c] = Float.parseFloat(f[2]);
                values[SolarLayer.PVOUT.ordinal()][r * COLS + c] = Float.parseFloat(f[3]);
                values[SolarLayer.OPTA.ordinal()][r * COLS + c] = Float.parseFloat(f[4]);
                count++;
            }
        }
        if (count == 0) return;
        for (float[] layer : values) fillGaps(layer);
        loaded = true;
        log.info("Solar grid loaded: {} PVGIS samples", count);
    }

    /**
     * Fills empty cells from their filled neighbours, a ring at a time, until everything has a
     * value. Between the 2° samples this interpolates; over the sea it only extends the coast,
     * and the map's water covers it.
     */
    static void fillGaps(float[] v) {
        float[] next = v.clone();
        boolean changed = true;
        for (int pass = 0; pass < 400 && changed; pass++) {
            changed = false;
            for (int r = 0; r < ROWS; r++) {
                for (int c = 0; c < COLS; c++) {
                    int i = r * COLS + c;
                    if (!Float.isNaN(v[i])) continue;
                    double sum = 0;
                    int n = 0;
                    for (int dr = -1; dr <= 1; dr++) {
                        int rr = r + dr;
                        if (rr < 0 || rr >= ROWS) continue;
                        for (int dc = -1; dc <= 1; dc++) {
                            int cc = (c + dc + COLS) % COLS; // the map wraps round east to west
                            float x = v[rr * COLS + cc];
                            if (!Float.isNaN(x)) {
                                sum += x;
                                n++;
                            }
                        }
                    }
                    if (n > 0) {
                        next[i] = (float) (sum / n);
                        changed = true;
                    }
                }
            }
            System.arraycopy(next, 0, v, 0, v.length);
        }
    }
}
