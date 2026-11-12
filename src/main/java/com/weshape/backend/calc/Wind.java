package com.weshape.backend.calc;

/**
 * Small wind turbine. Expert input v2, section 9 "Wind" and section 5.
 *
 * <p>Practical from about 4 m/s annual average; 4.5–5.5 m/s at hub height is the minimum for a
 * cost-effective system [S17]. App rule: "possible" from 4.5 m/s and "good" from 5.5 m/s at hub
 * height, after the terrain factor. Small turbines average a 17% capacity factor (2–36%) [S17].
 */
public final class Wind {

    public static final double POSSIBLE_MS = 4.5;
    public static final double GOOD_MS = 5.5;
    /** Average capacity factor of small turbines [S17]; range 2–36%. */
    public static final Range CAPACITY_FACTOR = new Range(0.02, 0.17, 0.36);
    /**
     * Wind-shear exponent for open land. S17 gives 0.14–0.33; 0.14 matches the expert's "+6% from
     * 20 to 30 m in the open" [Derived: 1.5^0.14 = 1.059].
     */
    public static final double SHEAR_OPEN = 0.14;
    /** Open land needed, m² (about 1 acre) [S17]; the expert notes it depends on turbine, tower, rules and noise. */
    public static final double MIN_LAND_M2 = 4000.0;
    /** Setback from the plot boundary, × tower height [S17]. */
    public static final double SETBACK_PER_TOWER_HEIGHT = 1.5;
    /** Blade tips at least this far above anything within 150 m, m [S17]. */
    public static final double CLEARANCE_M = 10.0;
    public static final double CLEARANCE_RADIUS_M = 150.0;

    /**
     * What surrounds the turbine. Factors are against open farmland, at 20 m, for the same wind at
     * 100 m [Derived in the expert input from roughness lengths in S17].
     */
    public enum Terrain {
        OPEN(1.00, 1.00),
        FEW_TREES(0.96, 0.88),
        MANY_TREES(0.89, 0.71),
        FOREST(0.72, 0.37),
        SUBURB(0.65, 0.28),
        /** "Under 20%" of the speed, "about 0%" of the power. */
        CITY(0.20, 0.0);

        public final double speedFactor;
        public final double powerFactor;

        Terrain(double speedFactor, double powerFactor) {
            this.speedFactor = speedFactor;
            this.powerFactor = powerFactor;
        }
    }

    public enum Verdict { TOO_LOW, POSSIBLE, GOOD }

    private Wind() {
    }

    /** Wind speed at another height: v(h) = v_ref · (h / h_ref)^α. */
    public static double atHeight(double speedMs, double fromHeightM, double toHeightM, double shear) {
        if (speedMs < 0 || fromHeightM <= 0 || toHeightM <= 0) {
            throw new IllegalArgumentException("Speed must be >= 0 and heights > 0");
        }
        return speedMs * Math.pow(toHeightM / fromHeightM, shear);
    }

    /** Speed at the hub, corrected for the terrain around the turbine. */
    public static double hubSpeed(double mapSpeedMs, double mapHeightM, double hubHeightM, Terrain terrain) {
        return atHeight(mapSpeedMs, mapHeightM, hubHeightM, SHEAR_OPEN) * terrain.speedFactor;
    }

    public static Verdict verdict(double hubSpeedMs) {
        if (hubSpeedMs >= GOOD_MS) return Verdict.GOOD;
        if (hubSpeedMs >= POSSIBLE_MS) return Verdict.POSSIBLE;
        return Verdict.TOO_LOW;
    }

    /** Energy in a year from a turbine of the rated power, kWh. */
    public static Range annualKwh(double ratedKw) {
        return CAPACITY_FACTOR.times(ratedKw * 8760.0);
    }

    /** Whether the open land is large enough and the turbine can stand back far enough from the boundary. */
    public static boolean fits(double openLandM2, double towerHeightM, double distanceToBoundaryM) {
        return openLandM2 >= MIN_LAND_M2 && distanceToBoundaryM >= SETBACK_PER_TOWER_HEIGHT * towerHeightM;
    }
}
