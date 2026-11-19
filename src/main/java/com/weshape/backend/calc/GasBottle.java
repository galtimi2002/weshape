package com.weshape.backend.calc;

/**
 * Bottled gas (LPG: propane, butane or a mix): how many bottles and how big, the size in the unit the
 * person chose (decided with Timi, docs/decisions/2026-11-19-question-changes.md):
 * <ul>
 *   <li>kilograms: the gas in the bottle, as written on it (11 kg);</li>
 *   <li>litres: the bottle's volume, as bottles are labelled in Europe (26 L), filled only to 80–85% so the
 *       liquid has room to expand [Reference, LPG];</li>
 *   <li>US gallons: gallons of liquid gas, as propane is sold and delivered in the US (a 20 lb tank holds
 *       4.7 gal) [Reference, LPG].</li>
 * </ul>
 *
 * <p>Reference values [Reference, LPG]: liquid density propane 0.51 kg/L, butane 0.58 kg/L, the mixes
 * sold in bottles about 0.54 kg/L; lower heating value propane 12.9 kWh/kg (46.4 MJ/kg), butane
 * 12.7 kWh/kg (45.7 MJ/kg). A US gallon is 3.785 L. A Romanian 26 L bottle holds about 11 kg.
 */
public final class GasBottle {

    /** The unit of the bottle size. */
    public enum Unit { LITRE, KG, GALLON }

    /** Kilograms per litre of liquid gas: propane to butane, a mix used [Reference, LPG]. */
    public static final Range KG_PER_LITRE = new Range(0.51, 0.54, 0.58);
    /** Share of the bottle's volume filled with liquid [Reference, LPG]. */
    public static final Range FILL = new Range(0.80, 0.82, 0.85);
    /** kWh per kilogram (lower heating value): butane to propane [Reference, LPG]. */
    public static final Range KWH_PER_KG = new Range(12.7, 12.8, 12.9);
    /** Litres in a US gallon. */
    public static final double LITRES_PER_GALLON = 3.785411784;

    private GasBottle() {
    }

    /** Kilograms of gas in one bottle of this size. */
    public static Range kgPerBottle(double size, Unit unit) {
        if (!(size >= 0)) throw new IllegalArgumentException("The bottle size cannot be negative");
        return switch (unit) {
            case KG -> Range.exactly(size);
            case LITRE -> FILL.times(KG_PER_LITRE).times(size);
            case GALLON -> KG_PER_LITRE.times(size * LITRES_PER_GALLON);
        };
    }

    /** Energy in one bottle, kWh. */
    public static Range kwhPerBottle(double size, Unit unit) {
        return kgPerBottle(size, unit).times(KWH_PER_KG);
    }

    /** Energy in the bottles of a year, kWh. */
    public static Range kwhPerYear(double bottlesPerMonth, double size, Unit unit) {
        if (!(bottlesPerMonth >= 0)) throw new IllegalArgumentException("The number of bottles cannot be negative");
        return kwhPerBottle(size, unit).times(bottlesPerMonth * 12);
    }
}
