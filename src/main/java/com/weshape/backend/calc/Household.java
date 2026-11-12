package com.weshape.backend.calc;

/**
 * Hot water and cooking for a household. Expert input v2, section 2.
 */
public final class Household {

    /** Hot water per person (about 40 L/day at 60 °C), kWh/yr [S2][S7]; careful 440–550, heavy up to 1,600. */
    public static final Range HOT_WATER_KWH_PER_PERSON = new Range(440, 800, 1600);
    /** Storage tank losses, kWh/yr: about 400 well insulated, up to 1,200 for an old tank [S7]. */
    public static final double TANK_LOSS_GOOD = 400;
    public static final double TANK_LOSS_OLD = 1200;
    /** Warmer cold-water inlet (15 °C instead of 10 °C): about 10% less [Derived: 45 ÷ 50]. */
    public static final double WARM_INLET_FACTOR = 0.9;

    /** Where the household is, for cooking defaults. */
    public enum Region { EUROPE, USA, AFRICA_LATAM }

    public enum CookingFuel { ELECTRIC, GAS }

    private Household() {
    }

    /** Hot water for the household, kWh/yr, tank losses included. */
    public static Range hotWaterKwh(int people, boolean oldTank, boolean warmInlet) {
        if (people <= 0) throw new IllegalArgumentException("A household has at least one person");
        Range use = HOT_WATER_KWH_PER_PERSON.times(people);
        if (warmInlet) use = use.times(WARM_INLET_FACTOR);
        return use.plus(Range.exactly(oldTank ? TANK_LOSS_OLD : TANK_LOSS_GOOD));
    }

    /**
     * Cooking per person, kWh/yr [Derived in the expert input]: Europe 150 electric or 200 gas (range
     * 100–350); USA about 400; Africa and parts of Latin America 300–350 of final energy (LPG or
     * electric) [S34]. Wood or charcoal is a fuel the user declares, not a default.
     */
    public static Range cookingKwhPerPerson(Region region, CookingFuel fuel) {
        return switch (region) {
            case EUROPE -> fuel == CookingFuel.ELECTRIC ? new Range(100, 150, 350) : new Range(100, 200, 350);
            case USA -> Range.exactly(400);
            case AFRICA_LATAM -> new Range(300, 325, 350);
        };
    }
}
