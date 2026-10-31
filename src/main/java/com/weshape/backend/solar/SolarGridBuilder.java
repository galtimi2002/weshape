package com.weshape.backend.solar;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;

/**
 * Builds {@code src/main/resources/solar/pvgis-grid.csv.gz}: PVGIS values on a grid, the data
 * behind the map layers. Run it once (and again only to refresh the data), then commit the file.
 *
 * <p>In IntelliJ: right-click this file, Run 'SolarGridBuilder.main()'. It needs the internet,
 * takes roughly 20 to 40 minutes, and can be stopped and started again: points already asked
 * are kept in {@code target/pvgis-grid.partial.csv} and skipped.
 *
 * <p>Europe is sampled every 0.5°, the rest of the world every 2° (about 19,600 points; most
 * of the 2° ones are sea and come back quickly). PVGIS allows 30 requests a second per address;
 * this stays well under that.
 */
public final class SolarGridBuilder {

    private static final double[] EUROPE = {-12, 34, 42, 71.5}; // west, south, east, north
    private static final double EUROPE_STEP = 0.5;
    private static final double WORLD_STEP = 2;
    private static final double WORLD_SOUTH = -56;
    private static final double WORLD_NORTH = 74;
    private static final int THREADS = 8;
    private static final long MIN_GAP_NANOS = TimeUnit.MILLISECONDS.toNanos(55); // ~18 requests a second

    private SolarGridBuilder() {
    }

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args.length > 0 ? args[0] : "src/main/resources/" + SolarGrid.RESOURCE);
        Path partial = Path.of("target/pvgis-grid.partial.csv");
        Files.createDirectories(partial.toAbsolutePath().getParent());
        Files.createDirectories(output.toAbsolutePath().getParent());

        List<double[]> points = points();
        Set<String> done = new HashSet<>();
        if (Files.exists(partial)) {
            for (String line : Files.readAllLines(partial, StandardCharsets.UTF_8)) {
                String[] f = line.split(",", -1);
                if (f.length >= 2) done.add(f[0] + "," + f[1]);
            }
        }
        List<double[]> todo = new ArrayList<>();
        for (double[] p : points) if (!done.contains(key(p[0], p[1]))) todo.add(p);
        System.out.printf("%d points, %d already done, %d to ask PVGIS%n", points.size(), points.size() - todo.size(), todo.size());

        String url = System.getenv().getOrDefault("PVGIS_URL", PvgisClient.DEFAULT_URL);
        PvgisClient pvgis = new PvgisClient(url, Duration.ofSeconds(40));
        String header = SolarDataCheck.header(url, pvgis.dataVersion(SolarDataCheck.REFERENCE_LAT, SolarDataCheck.REFERENCE_LON));
        System.out.println("Using " + header);
        AtomicInteger finished = new AtomicInteger();
        AtomicInteger land = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        long start = System.nanoTime();
        Object pace = new Object();
        long[] nextSlot = {System.nanoTime()};

        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(partial, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND))) {
            ExecutorService pool = Executors.newFixedThreadPool(THREADS);
            for (double[] p : todo) {
                pool.submit(() -> {
                    String line = null;
                    for (int attempt = 1; attempt <= 4 && line == null; attempt++) {
                        synchronized (pace) {
                            long now = System.nanoTime();
                            long wait = nextSlot[0] - now;
                            nextSlot[0] = Math.max(now, nextSlot[0]) + MIN_GAP_NANOS;
                            if (wait > 0) sleep(TimeUnit.NANOSECONDS.toMillis(wait));
                        }
                        try {
                            SolarPoint s = pvgis.fetch(p[0], p[1]);
                            line = String.format(Locale.ROOT, "%s,%.1f,%.1f,%.0f", key(p[0], p[1]), s.gti(), s.pvout(), s.opta());
                            land.incrementAndGet();
                        } catch (NoSolarDataException sea) {
                            line = key(p[0], p[1]) + ",,,";
                        } catch (IOException e) {
                            sleep(3000L * attempt);
                        }
                    }
                    if (line == null) {
                        failed.incrementAndGet(); // left out; a later run asks again
                    } else {
                        synchronized (out) {
                            out.println(line);
                            out.flush();
                        }
                    }
                    int n = finished.incrementAndGet();
                    if (n % 200 == 0 || n == todo.size()) {
                        double secs = (System.nanoTime() - start) / 1e9;
                        double left = secs / n * (todo.size() - n);
                        System.out.printf("%d / %d (%d on land, %d failed), about %.0f min left%n",
                                n, todo.size(), land.get(), failed.get(), left / 60);
                    }
                });
            }
            pool.shutdown();
            pool.awaitTermination(1, TimeUnit.DAYS);
        }

        if (failed.get() > 0) {
            System.out.printf("%d points failed (PVGIS busy or unreachable). Run again to fill them in.%n", failed.get());
        }
        writeOutput(partial, output, header);
        System.out.println("Wrote " + output.toAbsolutePath() + ". Restart the backend to use it.");
    }

    static List<double[]> points() {
        List<double[]> points = new ArrayList<>();
        for (double lat = EUROPE[1]; lat <= EUROPE[3] + 1e-9; lat += EUROPE_STEP) {
            for (double lon = EUROPE[0]; lon <= EUROPE[2] + 1e-9; lon += EUROPE_STEP) {
                points.add(new double[]{lat, lon});
            }
        }
        for (double lat = WORLD_SOUTH; lat <= WORLD_NORTH + 1e-9; lat += WORLD_STEP) {
            for (double lon = -180; lon < 180 - 1e-9; lon += WORLD_STEP) {
                boolean inEurope = lat >= EUROPE[1] && lat <= EUROPE[3] && lon >= EUROPE[0] && lon <= EUROPE[2];
                if (!inEurope) points.add(new double[]{lat, lon});
            }
        }
        return points;
    }

    private static String key(double lat, double lon) {
        return String.format(Locale.ROOT, "%.1f,%.1f", lat, lon);
    }

    private static void writeOutput(Path partial, Path output, String header) throws IOException {
        List<String> lines = new ArrayList<>(new java.util.LinkedHashSet<>(Files.readAllLines(partial, StandardCharsets.UTF_8)));
        lines.sort(null);
        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(
                new GZIPOutputStream(Files.newOutputStream(output)), StandardCharsets.UTF_8))) {
            w.write(header + "\n");
            w.write("# European Commission JRC, 1 kWp, 14% losses, optimum tilt facing the equator\n");
            w.write("lat,lon,gti_kwh_m2,pvout_kwh_kwp,opta_deg\n");
            for (String line : lines) {
                w.write(line);
                w.write('\n');
            }
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
