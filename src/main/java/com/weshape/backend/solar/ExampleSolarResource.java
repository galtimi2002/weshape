package com.weshape.backend.solar;

import org.springframework.stereotype.Component;

/**
 * Plausible stand-in values for the whole world until the real data is connected.
 *
 * <p>Built to look and behave like the real thing: most sun in the subtropical deserts (Sahara,
 * Arabia, Atacama, Australia, the American south-west), less under the cloudy tropics and towards
 * the poles, less in the Alps, the Pyrenees and the Carpathians, steeper optimum tilt away from
 * the equator, summer-heavy months at high latitudes. The numbers are invented and must not be
 * presented as measurements.
 */
@Component
public class ExampleSolarResource implements SolarResource {

    /** PV output by distance from the equator (degrees of latitude), kWh/kWp per year. */
    private static final double[][] PV_BY_LAT = {
            {0, 1430}, {10, 1560}, {20, 1700}, {28, 1760}, {34, 1700}, {38, 1640}, {42, 1460},
            {45, 1330}, {47, 1240}, {50, 1060}, {53, 960}, {56, 900}, {60, 840}, {66, 760}, {75, 650}
    };

    /** Optimum tilt by distance from the equator, degrees. */
    private static final double[][] TILT_BY_LAT = {
            {0, 6}, {10, 12}, {20, 20}, {30, 27}, {36, 32}, {42, 34.5}, {46, 36}, {50, 38}, {56, 40}, {66, 43}, {75, 46}
    };

    /** Sunnier or cloudier regions: lon, lat, radius in degrees, change in kWh/kWp. */
    private static final double[][] REGIONS = {
            {8, 23, 9, 260},      // Sahara
            {45, 22, 7, 260},     // Arabia
            {-69, -23, 4, 380},   // Atacama
            {132, -25, 10, 200},  // Australian interior
            {-112, 33, 6, 260},   // American south-west
            {21, -24, 6, 170},    // Kalahari
            {87, 32, 6, 220},     // Tibet
            {-62, -4, 10, -170},  // Amazon
            {22, 0, 8, -170},     // Congo basin
            {113, -1, 9, -130},   // Indonesia
            {105, 27, 6, -230},   // Sichuan and the cloudy south of China
            {-5, 56, 6, -60},     // Scotland and the Irish Sea
    };

    /** Mountain ridges (lon, lat) that cut the yield a little and steepen the tilt. */
    private static final double[][][] RIDGES = {
            {{5.5, 44}, {7, 45.8}, {9.5, 46.4}, {12, 47}, {14.5, 47.2}},
            {{22.3, 45}, {23.6, 45.4}, {25, 45.5}, {25.9, 45.6}, {26.4, 46.1}, {25.9, 46.9}, {25.2, 47.6}, {24.3, 47.9}},
            {{-1.5, 42.8}, {1, 42.6}, {2.8, 42.4}}
    };

    /** Share of the year's energy per month at mid latitudes, January first. */
    private static final double[] MONTH_SHARE = {
            .030, .047, .077, .100, .117, .122, .130, .118, .088, .063, .034, .026
    };

    @Override
    public SolarPoint at(double lat, double lon) {
        double gti = value(SolarLayer.GTI, lat, lon);
        double pvout = value(SolarLayer.PVOUT, lat, lon);
        double opta = value(SolarLayer.OPTA, lat, lon);
        double[] shares = monthShares(lat);
        double[] gtiMonthly = new double[12];
        double[] pvMonthly = new double[12];
        for (int m = 0; m < 12; m++) {
            gtiMonthly[m] = gti * shares[m];
            pvMonthly[m] = pvout * shares[m];
        }
        return new SolarPoint(lat, lon, gti, pvout, opta, gtiMonthly, pvMonthly, source(), true);
    }

    @Override
    public double value(SolarLayer layer, double lat, double lon) {
        double n = Math.sin(lon * 1.7 + lat * .9) * Math.cos(lat * 1.3 - lon * .6) + .5 * Math.sin(lon * 4.1 - lat * 3.3);
        double ridge = ridge(lon, lat);
        double absLat = Math.abs(lat);
        boolean europe = lon > -15 && lon < 45 && lat > 35;
        double pv = interpolate(PV_BY_LAT, absLat) + (europe ? clamp((lon - 12) * 3.2, -40, 60) : 0)
                + regions(lon, lat) + 28 * n - 95 * ridge;
        return switch (layer) {
            case PVOUT -> pv;
            case GTI -> pv * clamp(1.19 + .002 * (46 - absLat), 1.12, 1.3) + 15 * n;
            case OPTA -> interpolate(TILT_BY_LAT, absLat) + .6 * n + 1.2 * ridge;
        };
    }

    @Override
    public String source() {
        return "Example values";
    }

    @Override
    public boolean isExample() {
        return true;
    }

    /** Month shares that lean towards summer the further north the place is; they sum to 1. */
    public static double[] monthShares(double lat) {
        double k = clamp((Math.abs(lat) - 36) / 20, 0, 1);
        double[] shares = new double[12];
        double sum = 0;
        // South of the equator the seasons are the other way round.
        int shift = lat < 0 ? 6 : 0;
        double flatten = 1 - clamp((36 - Math.abs(lat)) / 36, 0, 1) * .7; // little season near the equator
        for (int m = 0; m < 12; m++) {
            double tweak = Math.abs(m - 5.5) < 3 ? .18 : -.25;
            double share = MONTH_SHARE[(m + shift) % 12];
            share = 1.0 / 12 + (share - 1.0 / 12) * flatten;
            shares[m] = share * (1 + k * tweak);
            sum += shares[m];
        }
        for (int m = 0; m < 12; m++) {
            shares[m] /= sum;
        }
        return shares;
    }

    private static double regions(double lon, double lat) {
        double sum = 0;
        for (double[] r : REGIONS) {
            double dx = (lon - r[0]) * Math.cos(Math.toRadians(r[1])), dy = lat - r[1];
            sum += r[3] * Math.exp(-(dx * dx + dy * dy) / (r[2] * r[2]));
        }
        return sum;
    }

    private static double ridge(double lon, double lat) {
        double best = 0;
        for (double[][] line : RIDGES) {
            for (int i = 1; i < line.length; i++) {
                double x1 = line[i - 1][0], y1 = line[i - 1][1], dx = line[i][0] - x1, dy = line[i][1] - y1;
                double u = clamp(((lon - x1) * dx + (lat - y1) * dy) / (dx * dx + dy * dy), 0, 1);
                double ex = lon - (x1 + u * dx), ey = lat - (y1 + u * dy);
                best = Math.max(best, Math.exp(-(ex * ex + ey * ey) / .18));
            }
        }
        return best;
    }

    private static double interpolate(double[][] table, double x) {
        if (x <= table[0][0]) return table[0][1];
        for (int i = 1; i < table.length; i++) {
            if (x <= table[i][0]) {
                double a = table[i - 1][0], va = table[i - 1][1], b = table[i][0], vb = table[i][1];
                return va + (vb - va) * (x - a) / (b - a);
            }
        }
        return table[table.length - 1][1];
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
