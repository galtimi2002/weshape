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
        HYBRID_MAIN
    }

    /** The battery size each advice comes with, kWh. */
    public static Range size(Advice advice, boolean airConditioning) {
        return switch (advice) {
            case NONE -> Range.exactly(0);
            case OPTIONAL -> Range.of(5, 10);
            case BACKUP -> Range.of(10, 13);
            case HYBRID_MAIN -> airConditioning ? Range.of(15, 25) : Range.of(10, 15);
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
}
