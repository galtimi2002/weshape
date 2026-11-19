package com.weshape.backend.calc;

/**
 * Whether the roof can carry panels or a solar water heater, from what the building is made of, when it
 * was built and whether the roof was renovated (decided with Timi,
 * docs/decisions/2026-11-19-question-changes.md; [Judgement, WeShape], for the expert to confirm).
 * Panels add about 15–25 kg a m² to the roof.
 */
public final class RoofSafety {

    /** The app's answer "what is the building made of". */
    public enum Material { BRICK, CONCRETE, WOOD, STONE_ADOBE, UNKNOWN }

    /** The app's answer "when was it built". */
    public enum Era { BEFORE_1960, FROM_1960, FROM_1990, FROM_2010, UNKNOWN }

    public enum Verdict {
        /** Roof options can be offered. */
        SAFE,
        /** Roof options with a note: have the roof structure checked first. */
        CHECK,
        /** No roof options; ground or yard ones only. */
        UNSAFE
    }

    private RoofSafety() {
    }

    /**
     * <ul>
     *   <li>a renovated roof: safe, whatever the building;</li>
     *   <li>a wooden building from before 1960 (1960 itself included in the app's answer): unsafe;</li>
     *   <li>a wooden building of unknown or later date (up to 1990): to be checked;</li>
     *   <li>stone or adobe, or an unknown material, from before 1960 or of unknown date: to be checked;</li>
     *   <li>otherwise (brick or concrete, and anything from 1990 on): safe.</li>
     * </ul>
     */
    public static Verdict roof(Material material, Era era, boolean roofRenovated) {
        if (roofRenovated) return Verdict.SAFE;
        Material m = material == null ? Material.UNKNOWN : material;
        Era e = era == null ? Era.UNKNOWN : era;
        boolean old = e == Era.BEFORE_1960;
        boolean oldOrUnknown = old || e == Era.UNKNOWN;
        if (m == Material.WOOD) {
            if (old) return Verdict.UNSAFE;
            if (e == Era.FROM_1960 || e == Era.UNKNOWN) return Verdict.CHECK;
            return Verdict.SAFE;
        }
        if ((m == Material.STONE_ADOBE || m == Material.UNKNOWN) && oldOrUnknown) return Verdict.CHECK;
        return Verdict.SAFE;
    }
}
