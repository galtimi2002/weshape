package com.weshape.backend.calc;

/**
 * Heat pumps: how much heat (or cooling) they give per kWh of electricity over a season.
 * Our own research, November 2026 (docs/research/heat-pumps.md); the expert input gives no values.
 *
 * <p>Heating: the measured seasonal performance factors of heat pumps in existing German homes
 * (Fraunhofer ISE field trial, 61 air and 16 ground-source systems) [R1], adjusted to the climate
 * with the ratios of the EU reference climates in lab tests (EN 14825 / HP Keymark) [R2][R3].
 * Hot zones need no heating; there the question is cooling (split air conditioners) [R4][R5] and
 * hot water (heat pump water heaters) [R6].
 */
public final class HeatPump {

    /** Air-source, measured in existing homes in Germany (cool-temperate): average 3.4, 2.6–4.9 [R1]. */
    public static final Range AIR_FIELD = new Range(2.6, 3.4, 4.9);
    /** Ground-source (brine/water), same trial: average 4.3, 3.6–5.4 [R1]. */
    public static final Range GROUND_FIELD = new Range(3.6, 4.3, 5.4);

    /** Lab SCOP of an 8 kW air/water heat pump, HP Keymark [R3]: {35 °C, 55 °C} for each EU climate. */
    static final double[] LAB_COLDER = {4.44, 3.12};
    static final double[] LAB_AVERAGE = {5.15, 3.63};
    static final double[] LAB_WARMER = {6.51, 4.47};

    /** Colder climate (Helsinki, −22 °C design) against average (Strasbourg, −10 °C): about 0.86 [Derived]. */
    public static final double COLDER_RATIO = ratio(LAB_COLDER);
    /** Warmer climate (Athens, +2 °C design) against average: about 1.25 [Derived]. */
    public static final double WARMER_RATIO = ratio(LAB_WARMER);

    /** Split air conditioners, seasonal efficiency (ISEER, kWh/kWh), Indian labels [R4][R5]. */
    public enum Cooling {
        /** 1-star, older units: 3.1–3.3 [R4]. */
        OLD(new Range(3.1, 3.2, 3.3)),
        /** 3-star inverter: 3.7–4.38 [R5]. */
        STANDARD(new Range(3.7, 4.0, 4.38)),
        /** 5-star inverter: 5–5.85 [R5]. */
        EFFICIENT(new Range(5.0, 5.4, 5.85));

        public final Range seer;

        Cooling(Range seer) {
            this.seer = seer;
        }
    }

    /** Heat pump water heaters measured in a hot-humid climate (Florida): COP 2.2 and 2.5 [R6]. */
    public static final Range WATER_HEATER_COP = new Range(2.2, 2.35, 2.5);
    /** A standard electric storage water heater, standby losses included, in the same tests: 0.82 [R6]. */
    public static final double ELECTRIC_TANK_COP = 0.82;

    public enum Kind { AIR, GROUND }

    private HeatPump() {
    }

    private static double ratio(double[] lab) {
        return (lab[0] / LAB_AVERAGE[0] + lab[1] / LAB_AVERAGE[1]) / 2;
    }

    /**
     * Heat per kWh of electricity over a heating season. Ground-source is the same everywhere (the
     * ground keeps its temperature through the year) [Judgement, WeShape]. Hot zones have no heating:
     * an IllegalArgumentException.
     */
    public static Range seasonalFactor(Kind kind, HeatDemand.Zone zone) {
        if (zone == HeatDemand.Zone.HOT_HUMID || zone == HeatDemand.Zone.HOT_ARID) {
            throw new IllegalArgumentException("No heating season in a hot zone");
        }
        if (kind == Kind.GROUND) return GROUND_FIELD;
        return switch (zone) {
            case COLD -> AIR_FIELD.times(COLDER_RATIO);
            case MILD -> AIR_FIELD.times(WARMER_RATIO);
            default -> AIR_FIELD;
        };
    }

    /** Electricity for a heat demand, kWh: heat ÷ factor (the best factor gives the least electricity). */
    public static Range electricityKwh(Range heatKwh, Range factor) {
        return divide(heatKwh, factor);
    }

    /** Electricity of an air conditioner for a cooling load, kWh. */
    public static Range coolingElectricityKwh(Range coolingLoadKwh, Cooling unit) {
        return divide(coolingLoadKwh, unit.seer);
    }

    /** The share of electricity a heat pump water heater saves against an electric tank: 1 − 0.82 ÷ COP. */
    public static Range waterHeaterSaving() {
        return new Range(1 - ELECTRIC_TANK_COP / WATER_HEATER_COP.low(), 1 - ELECTRIC_TANK_COP / WATER_HEATER_COP.typical(),
                1 - ELECTRIC_TANK_COP / WATER_HEATER_COP.high());
    }

    private static Range divide(Range amount, Range by) {
        if (by.low() <= 0) throw new IllegalArgumentException("Efficiency must be positive");
        return new Range(amount.low() / by.high(), amount.typical() / by.typical(), amount.high() / by.low());
    }
}
