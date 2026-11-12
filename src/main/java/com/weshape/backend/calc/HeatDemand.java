package com.weshape.backend.calc;

/**
 * Space heating of a home, kWh of heat a year. Expert input v2, section 1.
 *
 * <p>Base: measured heating demand of single-family houses in a cool-temperate climate by building
 * class, kWh per m² of heated floor a year [S2]. Then the windows (section 1c), the climate zone and,
 * for a renovation, the realism factor. What is marked [Judgement, WeShape] is our own mapping of the
 * app's answers onto the expert's classes and must be confirmed by an expert before it is scored.
 */
public final class HeatDemand {

    /** Built before 1977, no renovation: over 145, typically 150–250 [S2]. */
    public static final Range BEFORE_1977 = new Range(150, 200, 250);
    /** Built 1977–2002, first insulation rules: 90–145 [S2]. */
    public static final Range FROM_1977 = new Range(90, 117.5, 145);
    /** Built after 2002: under 90; new builds 30–75 [S2]. */
    public static final Range AFTER_2002 = new Range(30, 60, 90);
    /** The worst types (old adobe, uninsulated) in Central and Eastern Europe, about 400 [S5]. */
    public static final double VERY_POOR = 400;

    /**
     * Share of a poorly insulated home's heat loss through the windows: 25–40% [S6]; the middle is used.
     */
    public static final double WINDOW_SHARE = 0.325;
    /** A standard renovation package cuts about 54%, an advanced one about 75% [S4; Derived]. */
    public static final double STANDARD_PACKAGE_CUT = 0.54;
    public static final double ADVANCED_PACKAGE_CUT = 0.75;
    /** Measured savings are about 0.75 of calculated ones [S3; Derived: 25 ÷ 33]. */
    public static final double REALISM = 0.75;
    /** Mild zone against cool-temperate, same building period [S1; Derived: 31.8 ÷ 76.8]. */
    public static final double MILD_ZONE_FACTOR = 31.8 / 76.8;

    /** The app's answer "when was it built". */
    public enum Era { BEFORE_1960, FROM_1960, FROM_1990, FROM_2010, UNKNOWN }

    /** Window types, with heat loss against single glazing [S6; Derived: U ÷ 5.0]. */
    public enum Windows {
        SINGLE(1.00), DOUBLE_OLD(0.55), DOUBLE_NEW(0.25), TRIPLE(0.15),
        /** A mix, or not known: older double glazing is assumed [Judgement, WeShape]. */
        MIX(0.55), UNKNOWN(0.55);

        public final double lossVsSingle;

        Windows(double lossVsSingle) {
            this.lossVsSingle = lossVsSingle;
        }
    }

    /** Climate zones of the expert input. Zone boundaries come from degree-days [Judgement]. */
    public enum Zone { COLD, COOL_TEMPERATE, MILD, HOT_HUMID, HOT_ARID }

    private HeatDemand() {
    }

    /**
     * kWh per m² a year in a cool-temperate climate, by the app's era answer [Judgement, WeShape:
     * 1960–1989 spans the "before 1977" and "1977–2002" classes, 1990–2009 spans "1977–2002" and
     * "after 2002"].
     */
    public static Range byEra(Era era) {
        return switch (era) {
            case BEFORE_1960 -> BEFORE_1977;
            case FROM_1960 -> new Range(FROM_1977.low(), 160, BEFORE_1977.high());
            case FROM_1990 -> new Range(AFTER_2002.typical(), FROM_1977.typical(), FROM_1977.high());
            case FROM_2010 -> AFTER_2002;
            case UNKNOWN -> new Range(AFTER_2002.low(), BEFORE_1977.low(), BEFORE_1977.high());
        };
    }

    /**
     * How the windows change the heating against older double glazing (which the class values are
     * taken to include [Judgement, WeShape]): with the window share s of a single-glazed home,
     * (1 − s + s·loss) ÷ (1 − s + s·loss_old-double). Single → modern double comes out at about −24%,
     * inside the expert's "roughly 20–30%" [Derived].
     */
    public static double windowsFactor(Windows windows) {
        double rest = 1 - WINDOW_SHARE;
        return (rest + WINDOW_SHARE * windows.lossVsSingle) / (rest + WINDOW_SHARE * Windows.DOUBLE_OLD.lossVsSingle);
    }

    /** Against the cool-temperate value. Cold zones: often similar (better insulated) [Judgement]. */
    public static double zoneFactor(Zone zone) {
        return switch (zone) {
            case COLD, COOL_TEMPERATE -> 1.0;
            case MILD -> MILD_ZONE_FACTOR;
            case HOT_HUMID, HOT_ARID -> 0.0;
        };
    }

    /** A saving as it will really be: the calculated cut × 0.75 [S3]. */
    public static double realCut(double calculatedCut) {
        return calculatedCut * REALISM;
    }

    /** Heating a year, kWh: area × kWh/m² by era × windows × zone. */
    public static Range annualKwh(double heatedAreaM2, Era era, Windows windows, Zone zone) {
        if (heatedAreaM2 <= 0) throw new IllegalArgumentException("Heated area must be positive");
        return byEra(era).times(heatedAreaM2 * windowsFactor(windows) * zoneFactor(zone));
    }
}
