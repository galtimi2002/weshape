package com.weshape.backend.calc;

import java.util.Map;
import java.util.Set;

/**
 * "Is this monthly electricity use right?" Expert input v2, section 3.
 *
 * <p>Household use differs about 100-fold between countries [S29], so the warning compares with
 * the user's own country: "check" from 2× and "very high" from 3× its average, after adding a heat
 * pump (+400), an electric car (+250) and air conditioning (+200–500 per split unit in heavy use;
 * 350 used) kWh per month [Judgement].
 */
public final class UsageCheck {

    public static final double CHECK_TIMES = 2.0;
    public static final double VERY_HIGH_TIMES = 3.0;
    public static final double HEAT_PUMP_KWH_MONTH = 400;
    public static final double EV_KWH_MONTH = 250;
    public static final double AIR_CONDITIONING_KWH_MONTH = 350;

    /** Average household electricity per month by country, kWh [S9][S29]. */
    static final Map<String, Double> AVERAGE_KWH_MONTH = Map.ofEntries(
            Map.entry("IN", 84.0),
            Map.entry("BR", 145.0),
            Map.entry("MX", 145.0),
            Map.entry("ZA", 218.0),
            Map.entry("ES", 347.0),
            Map.entry("AU", 493.0),
            Map.entry("US", 864.0),
            Map.entry("CA", 942.0));
    /** Central and Eastern Europe, about 150 kWh a month (e.g. Romania) [S9]. */
    static final Set<String> CENTRAL_EASTERN_EUROPE = Set.of("RO", "HU", "PL", "BG", "SK", "CZ", "HR", "SI", "RS", "LT", "LV", "EE", "MD", "UA");
    static final double CEE_KWH_MONTH = 150;
    /** The rest of the EU (and nearby), about 300 kWh a month [S9]. */
    static final Set<String> EUROPE = Set.of("AT", "BE", "CY", "DE", "DK", "FI", "FR", "GR", "IE", "IT", "LU", "MT", "NL", "PT", "SE", "GB", "NO", "CH", "IS");
    static final double EU_KWH_MONTH = 300;

    public enum Level { OK, CHECK, VERY_HIGH, UNKNOWN_COUNTRY }

    private UsageCheck() {
    }

    /** The country's average household use per month, kWh, or null when the app does not know it. */
    public static Double averageKwhPerMonth(String countryCode) {
        if (countryCode == null) return null;
        String c = countryCode.toUpperCase();
        if (AVERAGE_KWH_MONTH.containsKey(c)) return AVERAGE_KWH_MONTH.get(c);
        if (CENTRAL_EASTERN_EUROPE.contains(c)) return CEE_KWH_MONTH;
        if (EUROPE.contains(c)) return EU_KWH_MONTH;
        return null;
    }

    public static Level check(String countryCode, double kwhPerMonth, boolean heatPump, boolean electricCar, int airConditioners) {
        Double average = averageKwhPerMonth(countryCode);
        if (average == null) return Level.UNKNOWN_COUNTRY;
        double extra = (heatPump ? HEAT_PUMP_KWH_MONTH : 0) + (electricCar ? EV_KWH_MONTH : 0)
                + Math.max(0, airConditioners) * AIR_CONDITIONING_KWH_MONTH;
        if (kwhPerMonth >= VERY_HIGH_TIMES * average + extra) return Level.VERY_HIGH;
        if (kwhPerMonth >= CHECK_TIMES * average + extra) return Level.CHECK;
        return Level.OK;
    }
}
