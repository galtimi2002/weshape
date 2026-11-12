package com.weshape.backend.calc;

/**
 * How much of the solar electricity the home uses itself (the rest goes to the grid and is usually
 * worth much less). Our own research (docs/research/self-consumption.md).
 *
 * <p>Anchor: a system that makes about as much in a year as the home uses (ratio 1).
 * <ul>
 *   <li>Without a battery: 28% [P1], just under 30% [P2], 20–33% for homes with a heat pump [P3].</li>
 *   <li>A battery of 0.5–1 kWh per kWp adds 13–24 percentage points [P4]; +19 with 0.6 kWh/kWp [P1];
 *       about +30 with 0.8 kWh/kWp [P2].</li>
 * </ul>
 * Other sizes [Judgement, WeShape]: a larger system does not raise the kWh used at home, so the share
 * falls as 1 ÷ ratio; a smaller system keeps the share of ratio 1. Both are on the safe side.
 */
public final class SelfConsumption {

    /** Share used at home without a battery, at ratio 1 [P1][P2][P3]. */
    public static final Range WITHOUT_BATTERY = new Range(0.20, 0.30, 0.41);
    /** Percentage points a battery of at least {@link #FULL_GAIN_KWH_PER_KWP} adds [P1][P2][P4]. */
    public static final Range BATTERY_GAIN = new Range(0.13, 0.20, 0.30);
    /** Battery size from which the full gain counts, kWh per kWp [P4]. */
    public static final double FULL_GAIN_KWH_PER_KWP = 0.5;

    private SelfConsumption() {
    }

    /**
     * Share of the solar electricity used at home.
     *
     * @param ratio       solar kWh a year ÷ the home's kWh a year
     * @param kwhPerKwp   usable battery kWh per kWp of panels; 0 without a battery
     */
    public static Range share(double ratio, double kwhPerKwp) {
        if (ratio <= 0) throw new IllegalArgumentException("Ratio must be positive");
        if (kwhPerKwp < 0) throw new IllegalArgumentException("Battery cannot be negative");
        double batteryPart = Math.min(1, kwhPerKwp / FULL_GAIN_KWH_PER_KWP);
        Range atOne = WITHOUT_BATTERY.plus(BATTERY_GAIN.times(batteryPart));
        // At most 0.41 + 0.30 = 71%, so never above 100%.
        return ratio > 1 ? atOne.times(1 / ratio) : atOne;
    }

    /** kWh of solar electricity used at home in a year. */
    public static Range usedKwh(double solarKwh, double homeKwh, double batteryKwh, double kwp) {
        if (solarKwh < 0 || homeKwh <= 0 || kwp <= 0) throw new IllegalArgumentException("Bad input");
        return share(solarKwh / homeKwh, batteryKwh / kwp).times(solarKwh);
    }

    /** kWh sent to the grid in a year: solar minus what the home used. */
    public static Range exportedKwh(double solarKwh, double homeKwh, double batteryKwh, double kwp) {
        Range used = usedKwh(solarKwh, homeKwh, batteryKwh, kwp);
        return new Range(solarKwh - used.high(), solarKwh - used.typical(), solarKwh - used.low());
    }
}
