package com.weshape.backend.calc;

/**
 * Home battery for power cuts. Expert input v2, section 4.
 *
 * <p>Thresholds [Judgement]: under about 60 min of cuts a year no backup battery; 60–300 min an
 * optional 5–10 kWh add-on; over 300 min, monthly cuts or a critical load a 10–13 kWh backup
 * battery; over about 20 hours a month a hybrid solar-and-battery system as the main
 * recommendation (10–15 kWh, 15–25 kWh with air conditioning) [S33]. A normal grid-tied solar
 * system stops in a cut, so panels alone give no backup [S12].
 */
public final class Battery {

    /** Usable depth of discharge [S12]. */
    public static final double DEPTH_OF_DISCHARGE = 0.9;
    /** Round-trip efficiency [S12]. */
    public static final double EFFICIENCY = 0.9;
    /** Essential loads (fridge, lights, router, chargers, circulation pump), kWh per day [S12]. */
    public static final Range ESSENTIAL_KWH_PER_DAY = Range.of(5, 10);

    public enum Advice {
        /** Not for backup; mention only if it pays through self-consumption. */
        NONE,
        /** Optional backup add-on: 5–10 kWh with a 3–5 kW inverter. */
        OPTIONAL,
        /** A 10–13 kWh battery with backup (islanding) capability. */
        BACKUP,
        /** Solar plus battery as the main recommendation. */
        HYBRID_MAIN,
        /** No grid, or the person wants to be fully independent of it: solar plus battery on its own. */
        OFF_GRID
    }

    /** The app's answer to "how often does the power go out". */
    public enum Cuts { NEVER, HARDLY, MONTHLY, DAILY }

    /** The app's answer to "how long does a cut usually last". */
    public enum CutLength { UNDER_HOUR, HOURS, OVER_DAY }

    /** Days without grid that a long cut or an off-grid home must bridge: 24–72 h [expert table, long events; S12]. */
    public static final Range LONG_CUT_DAYS = new Range(1, 2, 3);

    /** The battery size each advice comes with, kWh. */
    public static Range size(Advice advice, boolean airConditioning) {
        return switch (advice) {
            case NONE -> Range.exactly(0);
            case OPTIONAL -> Range.of(5, 10);
            case BACKUP -> Range.of(10, 13);
            case HYBRID_MAIN -> airConditioning ? Range.of(15, 25) : Range.of(10, 15);
            case OFF_GRID -> throw new IllegalArgumentException("Off-grid is sized from the daily use: kwh(...)");
        };
    }

    private Battery() {
    }

    /**
     * @param outageMinutesPerYear unplanned outage per year
     * @param cutsMonthly          cuts at least once a month
     * @param criticalLoad         medical equipment, a well pump, remote work
     */
    public static Advice advise(double outageMinutesPerYear, boolean cutsMonthly, boolean criticalLoad) {
        if (outageMinutesPerYear > 20 * 60 * 12) return Advice.HYBRID_MAIN;
        if (outageMinutesPerYear > 300 || cutsMonthly || criticalLoad) return Advice.BACKUP;
        if (outageMinutesPerYear >= 60) return Advice.OPTIONAL;
        return Advice.NONE;
    }

    /** Battery for a number of days: critical kWh/day × days ÷ (depth of discharge × efficiency) [S12; Derived]. */
    public static double sizeKwh(double criticalKwhPerDay, double daysOfAutonomy) {
        if (criticalKwhPerDay < 0 || daysOfAutonomy < 0) {
            throw new IllegalArgumentException("Load and days cannot be negative");
        }
        return criticalKwhPerDay * daysOfAutonomy / (DEPTH_OF_DISCHARGE * EFFICIENCY);
    }

    /**
     * Advice from the app's answers (decided with Timi, docs/decisions/2026-11-14-power-cuts-back.md),
     * row by row from the expert's table (section 4):
     * <ul>
     *   <li>no grid → off-grid; wants full independence → off-grid where {@link #offGridPossible} allows it;</li>
     *   <li>never → none;</li>
     *   <li>once or twice a year: short → none (under 60 min/yr); hours → optional (60–300 min/yr);
     *       over a day → backup (a long event);</li>
     *   <li>a few times a month: short → optional ("1–3 short cuts a month"); hours → backup ("cuts at
     *       least monthly"); over a day → hybrid (over 20 hours a month);</li>
     *   <li>most days: short → backup; hours or longer → hybrid ("frequent or daily cuts").</li>
     * </ul>
     */
    public static Advice plan(boolean hasGrid, boolean wantsIndependence, Cuts cuts, CutLength length) {
        if (!hasGrid) return Advice.OFF_GRID;
        if (wantsIndependence && offGridPossible(cuts, length)) return Advice.OFF_GRID;
        if (cuts == null || cuts == Cuts.NEVER) return Advice.NONE;
        if (length == null) throw new IllegalArgumentException("How long the cuts last is needed");
        return switch (cuts) {
            case HARDLY -> switch (length) {
                case UNDER_HOUR -> Advice.NONE;
                case HOURS -> Advice.OPTIONAL;
                case OVER_DAY -> Advice.BACKUP;
            };
            case MONTHLY -> switch (length) {
                case UNDER_HOUR -> Advice.OPTIONAL;
                case HOURS -> Advice.BACKUP;
                case OVER_DAY -> Advice.HYBRID_MAIN;
            };
            case DAILY -> length == CutLength.UNDER_HOUR ? Advice.BACKUP : Advice.HYBRID_MAIN;
            case NEVER -> Advice.NONE;
        };
    }

    /**
     * Whether full independence of the grid can be offered (decided with Timi,
     * docs/decisions/2026-11-19-question-changes.md): yes, unless the power goes out most days for hours or
     * longer; "most days, under an hour" is the worst case still allowed. The app does not ask "fully
     * independent?" when this is false.
     */
    public static boolean offGridPossible(Cuts cuts, CutLength length) {
        return !(cuts == Cuts.DAILY && (length == CutLength.HOURS || length == CutLength.OVER_DAY));
    }

    /**
     * Battery size, kWh, for the advice.
     * <ul>
     *   <li>Off-grid: the home's daily use × 1–3 days ÷ (0.9 × 0.9) [S12; Judgement, WeShape: the long-event
     *       days used for off-grid too].</li>
     *   <li>Cuts over a day: essential loads (5–10 kWh/day, at most the home's daily use) × 1–3 days ÷
     *       (0.9 × 0.9), at least the advice's own size [S12].</li>
     *   <li>Otherwise the advice's own size [S33].</li>
     * </ul>
     *
     * @param dailyKwh the home's use per day; needed off-grid, optional otherwise (null when unknown)
     */
    public static Range kwh(Advice advice, CutLength length, Range dailyKwh, boolean airConditioning) {
        double loss = 1 / (DEPTH_OF_DISCHARGE * EFFICIENCY);
        if (advice == Advice.OFF_GRID) {
            if (dailyKwh == null) throw new IllegalArgumentException("Off-grid needs the daily use");
            return dailyKwh.times(LONG_CUT_DAYS).times(loss);
        }
        Range own = size(advice, airConditioning);
        if (advice == Advice.NONE || length != CutLength.OVER_DAY) return own;
        Range essential = dailyKwh == null ? ESSENTIAL_KWH_PER_DAY : new Range(
                Math.min(ESSENTIAL_KWH_PER_DAY.low(), dailyKwh.low()),
                Math.min(ESSENTIAL_KWH_PER_DAY.typical(), dailyKwh.typical()),
                Math.min(ESSENTIAL_KWH_PER_DAY.high(), dailyKwh.high()));
        Range need = essential.times(LONG_CUT_DAYS).times(loss);
        return new Range(Math.max(own.low(), need.low()), Math.max(own.typical(), need.typical()),
                Math.max(own.high(), need.high()));
    }
}
