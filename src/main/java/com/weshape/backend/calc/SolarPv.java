package com.weshape.backend.calc;

/**
 * Solar panels. Expert input v2, section 5 (space, orientation, dust and heat).
 *
 * <p>Roof: 4.3–4.8 m² per kWp with 430 W modules [S13]; of a roof plane about 65–80% is usable after
 * edge strips, chimneys, roof windows and shade [Judgement]. The expert's check: a 60 m² plane gives
 * about 42 m² usable, about 9 kWp [Derived].
 */
public final class SolarPv {

    /** Roof area per kWp, m² [S13]; 4.6 is the value the expert uses in the worked example. */
    public static final Range ROOF_M2_PER_KWP = new Range(4.3, 4.6, 4.8);
    /** Ground area per kWp, m²: panel area ÷ ground-cover ratio 0.4–0.5 [Derived]; more at high latitudes. */
    public static final Range GROUND_M2_PER_KWP = new Range(10, 15, 20);
    /** Share of a roof plane that panels can use [Judgement]. */
    public static final Range USABLE_ROOF_SHARE = new Range(0.65, 0.70, 0.80);
    /** Near the equator orientation matters little and a flat roof is close to optimal, degrees [S14]. */
    public static final double EQUATOR_BAND_DEG = 15.0;

    /** Which way the panels face, as the compass sees it (before the hemisphere is taken into account). */
    public enum Facing { SOUTH, SOUTH_EAST_OR_WEST, EAST_OR_WEST, EAST_WEST_FLAT_PAIR, FLAT, NORTH_EAST_OR_WEST, NORTH }

    /** For the extra losses of the climate: dusty places lose more to soiling [S36]. */
    public enum Climate { TEMPERATE, HOT, HOT_DUSTY }

    private SolarPv() {
    }

    /** Usable roof area of a plane, m². */
    public static Range usableRoofM2(double roofPlaneM2) {
        return USABLE_ROOF_SHARE.times(roofPlaneM2);
    }

    /** Panel power that fits on a roof plane, kWp: usable area ÷ area per kWp. */
    public static Range kWpOnRoof(double roofPlaneM2) {
        Range usable = usableRoofM2(roofPlaneM2);
        return new Range(usable.low() / ROOF_M2_PER_KWP.high(), usable.typical() / ROOF_M2_PER_KWP.typical(),
                usable.high() / ROOF_M2_PER_KWP.low());
    }

    /**
     * Output against the best orientation (facing the equator at 30–35°), mid-latitudes [S14].
     * The southern hemisphere swaps north and south; within 15° of the equator orientation is not
     * counted [S14 as quoted in the expert input].
     */
    public static Range orientationFactor(Facing compassFacing, double latitude) {
        if (Math.abs(latitude) < EQUATOR_BAND_DEG) {
            return Range.exactly(1.0);
        }
        Facing facing = latitude < 0 ? swapHemisphere(compassFacing) : compassFacing;
        return switch (facing) {
            case SOUTH -> Range.exactly(1.0);
            case SOUTH_EAST_OR_WEST -> Range.of(0.90, 0.97);
            case EAST_OR_WEST -> Range.of(0.75, 0.85);
            case EAST_WEST_FLAT_PAIR -> Range.of(0.85, 0.90);
            case FLAT -> Range.of(0.85, 0.88);
            case NORTH_EAST_OR_WEST -> Range.of(0.55, 0.65);
            case NORTH -> Range.of(0.40, 0.55);
        };
    }

    /** In the southern hemisphere a north roof is the good one. */
    static Facing swapHemisphere(Facing f) {
        return switch (f) {
            case SOUTH -> Facing.NORTH;
            case NORTH -> Facing.SOUTH;
            case SOUTH_EAST_OR_WEST -> Facing.NORTH_EAST_OR_WEST;
            case NORTH_EAST_OR_WEST -> Facing.SOUTH_EAST_OR_WEST;
            default -> f;
        };
    }

    /**
     * What is left after the extra dust of dry, dusty places. PVGIS (where the specific yield comes
     * from) already models the heat of the panels and counts about 14% general losses, dust included,
     * so heat (8–12% in hot zones, expert input) is not counted again. Dust is 3–5% on average but up
     * to about 7–10% in very dusty or polluted places [S36]: the extra 2–6% is counted there [Derived].
     */
    public static Range climateFactor(Climate climate) {
        return switch (climate) {
            case TEMPERATE, HOT -> Range.exactly(1.0);
            case HOT_DUSTY -> new Range(0.94, 0.96, 0.98);
        };
    }

    /**
     * Energy in a year, kWh: kWp × specific yield (kWh/kWp, from PVGIS for the place, which already
     * includes the usual system losses) × orientation × the extra losses of the climate.
     */
    public static Range annualKwh(Range kWp, double specificYieldKwhPerKwp, Facing facing, double latitude, Climate climate) {
        return kWp.times(specificYieldKwhPerKwp).times(orientationFactor(facing, latitude)).times(climateFactor(climate));
    }
}
