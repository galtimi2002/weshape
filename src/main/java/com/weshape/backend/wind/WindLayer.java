package com.weshape.backend.wind;

import java.util.List;

/**
 * The wind map layers and how their colours are drawn. Scales are fixed, so the same
 * colour means the same wind everywhere and on every visit.
 */
public enum WindLayer {

    /** Mean wind speed at 100 m. */
    SPEED("speed", "m/s", 3, 9, false, 100, List.of("#E3EEF8", "#7FB2E0", "#2F6DB0", "#163E78")),
    /** Mean wind power density at 100 m. */
    POWER("power", "W/m²", 50, 800, true, 100, List.of("#EEE8F6", "#B39BDA", "#7552B5", "#46297F")),
    /** Mean wind speed at a chosen height (10, 50, 100, 150 or 200 m). */
    HEIGHT("height", "m/s", 2, 10, false, 0, List.of("#E0F2F5", "#7CC5D6", "#2B8BA8", "#145A73"));

    /** Heights with a speed layer; 150 m is worked out from 100 and 200 m. */
    public static final List<Integer> HEIGHTS = List.of(10, 50, 100, 150, 200);

    public final String id;
    public final String unit;
    public final double min;
    public final double max;
    public final boolean logarithmic;
    public final int height;
    public final List<String> colors;

    WindLayer(String id, String unit, double min, double max, boolean logarithmic, int height, List<String> colors) {
        this.id = id;
        this.unit = unit;
        this.min = min;
        this.max = max;
        this.logarithmic = logarithmic;
        this.height = height;
        this.colors = colors;
    }

    /** Position of a value on the colour scale, 0 to 1. */
    public double scale(double value) {
        double t = logarithmic
                ? (Math.log(Math.max(value, 1e-6)) - Math.log(min)) / (Math.log(max) - Math.log(min))
                : (value - min) / (max - min);
        return Math.max(0, Math.min(1, t));
    }

    /** Opaque ARGB colour for a value. */
    public int argb(double value) {
        double t = scale(value) * (colors.size() - 1);
        int i = Math.min(colors.size() - 2, (int) Math.floor(t));
        double u = t - i;
        int a = Integer.parseInt(colors.get(i).substring(1), 16);
        int b = Integer.parseInt(colors.get(i + 1).substring(1), 16);
        int r = mix(a >> 16 & 0xff, b >> 16 & 0xff, u);
        int g = mix(a >> 8 & 0xff, b >> 8 & 0xff, u);
        int bl = mix(a & 0xff, b & 0xff, u);
        return 0xff000000 | r << 16 | g << 8 | bl;
    }

    private static int mix(int a, int b, double u) {
        return (int) Math.round(a + (b - a) * u);
    }
}
