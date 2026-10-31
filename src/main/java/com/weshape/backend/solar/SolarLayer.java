package com.weshape.backend.solar;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * The three solar map layers, named as in the Global Solar Atlas GIS data.
 *
 * <p>{@code min} and {@code max} are where the colour scale starts and ends; values outside
 * still exist, they just take the end colour.
 */
public enum SolarLayer {

    /** Global irradiation on an optimally tilted surface, kWh/m² per year. */
    GTI("gti", "kWh/m²", 1100, 2500, new String[]{"#F6EBC6", "#EDBF5E", "#D9892A", "#B5651D"}),

    /** Specific photovoltaic power output, kWh/kWp per year. */
    PVOUT("pvout", "kWh/kWp", 900, 2000, new String[]{"#E3F2E6", "#7CC49A", "#2E9E66", "#1C6B44"}),

    /** Optimum tilt angle of PV modules, degrees. */
    OPTA("opta", "°", 5, 45, new String[]{"#EAF0F8", "#9DB4DE", "#3E64A8", "#1E3A6E"});

    private final String id;
    private final String unit;
    private final double min;
    private final double max;
    private final String[] colors;

    SolarLayer(String id, String unit, double min, double max, String[] colors) {
        this.id = id;
        this.unit = unit;
        this.min = min;
        this.max = max;
        this.colors = colors;
    }

    public String id() {
        return id;
    }

    public String unit() {
        return unit;
    }

    public double min() {
        return min;
    }

    public double max() {
        return max;
    }

    public String[] colors() {
        return colors.clone();
    }

    public static Optional<SolarLayer> fromId(String id) {
        String wanted = id == null ? "" : id.toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(l -> l.id.equals(wanted)).findFirst();
    }
}
