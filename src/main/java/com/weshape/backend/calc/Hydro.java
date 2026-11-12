package com.weshape.backend.calc;

import java.util.ArrayList;
import java.util.List;

/**
 * Small (micro) hydro on a stream at the place. Expert input v2, section 9 "River".
 *
 * <p>Power ≈ 5 W per metre of head per litre per second of flow [Derived from S21: US DOE,
 * head (ft) × flow (gpm) ÷ 10]. The app's rule [Judgement]: mention hydro only with a stream within
 * 500 m, at least 3 m of drop in a pipe of up to about 300 m, at least 10 L/s all year, and at
 * least about 300 W continuous.
 */
public final class Hydro {

    /** W per metre of head per L/s of flow [Derived, S21]. */
    public static final double WATTS_PER_METRE_PER_LPS = 5.0;
    /** Below this head micro-hydro is generally uneconomical, m [S21]. */
    public static final double MIN_HEAD_M = 3.0;
    /** Flow needed all year, including the dry season, L/s [Judgement]. S21's absolute floor is 0.6 L/s. */
    public static final double MIN_FLOW_LPS = 10.0;
    /** Smallest continuous power worth showing, W [Judgement]. */
    public static final double MIN_POWER_W = 300.0;
    /** Stream at most this far, m [Judgement]. */
    public static final double MAX_STREAM_DISTANCE_M = 500.0;
    /** Pipe (penstock) at most this long, m [Judgement]. */
    public static final double MAX_PIPE_M = 300.0;
    public static final double HOURS_PER_YEAR = 8760.0;

    /** Why hydro is not shown. */
    public enum Reason { STREAM_TOO_FAR, PIPE_TOO_LONG, HEAD_TOO_LOW, FLOW_TOO_LOW, POWER_TOO_LOW }

    /** The verdict for a site: its power, its yearly energy if it ran all year, and what rules it out. */
    public record Site(double powerW, double annualKwh, List<Reason> reasons) {
        public boolean shown() {
            return reasons.isEmpty();
        }
    }

    private Hydro() {
    }

    /** Continuous power, W. */
    public static double powerW(double headM, double flowLps) {
        if (headM < 0 || flowLps < 0) {
            throw new IllegalArgumentException("Head and flow cannot be negative");
        }
        return WATTS_PER_METRE_PER_LPS * headM * flowLps;
    }

    /** Energy in a year of running all the time, kWh. */
    public static double annualKwh(double powerW) {
        return powerW * HOURS_PER_YEAR / 1000.0;
    }

    /**
     * @param headM          vertical drop that a pipe can capture, m
     * @param flowLps        flow in the dry season, L/s
     * @param streamDistanceM distance from the home to the stream, m
     * @param pipeLengthM    length of the pipe needed for that drop, m
     */
    public static Site assess(double headM, double flowLps, double streamDistanceM, double pipeLengthM) {
        List<Reason> reasons = new ArrayList<>();
        if (streamDistanceM > MAX_STREAM_DISTANCE_M) reasons.add(Reason.STREAM_TOO_FAR);
        if (pipeLengthM > MAX_PIPE_M) reasons.add(Reason.PIPE_TOO_LONG);
        if (headM < MIN_HEAD_M) reasons.add(Reason.HEAD_TOO_LOW);
        if (flowLps < MIN_FLOW_LPS) reasons.add(Reason.FLOW_TOO_LOW);
        double power = powerW(headM, flowLps);
        if (power < MIN_POWER_W) reasons.add(Reason.POWER_TOO_LOW);
        return new Site(power, annualKwh(power), List.copyOf(reasons));
    }
}
