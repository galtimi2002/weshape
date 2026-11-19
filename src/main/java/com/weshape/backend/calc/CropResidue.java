package com.weshape.backend.calc;

import java.util.Map;

/**
 * What stays on the person's own fields after the harvest (maize stalks and cobs, straw, sunflower
 * stalks, prunings) and the heat it holds. The app asks the crop and the area in hectares or acres
 * (decided with Timi, docs/decisions/2026-11-19-question-changes.md).
 *
 * <p>Residue, tonnes of dry matter a hectare [Reference, crop residue ratios]: maize 5–8, wheat or barley
 * 3–5, sunflower 3–5, rapeseed 3–5, vineyard or orchard prunings 1.5–3. Not all of it should be
 * taken: on fields 30–50% may be removed, the rest keeps the soil fertile; prunings are cut away anyway
 * (60–90%) [Reference; Judgement, WeShape]. Dry residue holds 4.0–4.5 kWh a kg (14.5–16 MJ/kg)
 * [Reference].
 */
public final class CropResidue {

    /** The app's answer "which crop". */
    public enum Crop {
        MAIZE(new Range(5, 6.5, 8), FIELD),
        WHEAT_BARLEY(new Range(3, 4, 5), FIELD),
        SUNFLOWER(new Range(3, 4, 5), FIELD),
        RAPESEED(new Range(3, 4, 5), FIELD),
        VINEYARD_ORCHARD(new Range(1.5, 2, 3), PRUNINGS);

        /** Residue, tonnes of dry matter a hectare a year. */
        public final Range tonnesPerHa;
        /** The share that can be taken away. */
        public final Range removable;

        Crop(Range tonnesPerHa, Range removable) {
            this.tonnesPerHa = tonnesPerHa;
            this.removable = removable;
        }
    }

    /** The unit the person chose for the area. */
    public enum AreaUnit { HECTARE, ACRE }

    static final Range FIELD = new Range(0.3, 0.4, 0.5);
    static final Range PRUNINGS = new Range(0.6, 0.75, 0.9);

    /** Hectares in an acre. */
    public static final double HA_PER_ACRE = 0.40468564224;
    /** Energy in a kg of dry residue, kWh [Reference]. */
    public static final Range KWH_PER_KG = new Range(4.0, 4.2, 4.5);

    private CropResidue() {
    }

    public static double hectares(double area, AreaUnit unit) {
        if (!(area >= 0)) throw new IllegalArgumentException("The area cannot be negative");
        return unit == AreaUnit.HECTARE ? area : area * HA_PER_ACRE;
    }

    /** Residue that can be taken away each year, tonnes of dry matter. */
    public static Range tonnes(Crop crop, double area, AreaUnit unit) {
        return crop.tonnesPerHa.times(crop.removable).times(hectares(area, unit));
    }

    /** All the crops together, tonnes of dry matter a year. */
    public static Range tonnes(Map<Crop, Double> hectaresByCrop) {
        Range total = Range.exactly(0);
        for (Map.Entry<Crop, Double> e : hectaresByCrop.entrySet()) {
            total = total.plus(tonnes(e.getKey(), e.getValue(), AreaUnit.HECTARE));
        }
        return total;
    }

    /** Heat held in that residue each year, kWh. */
    public static Range kwhPerYear(Range tonnes) {
        return tonnes.times(1000).times(KWH_PER_KG);
    }
}
