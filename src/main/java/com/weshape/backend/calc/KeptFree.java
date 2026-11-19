package com.weshape.backend.calc;

import java.util.List;

/**
 * A part of the roof or yard the person wants kept free (decided with Timi,
 * docs/decisions/2026-11-19-question-changes.md):
 * <ul>
 *   <li>something is there (a chimney, a window, something else): only that thing and a margin around it
 *       are left out; the rest of the part can still be used, and the app says so;</li>
 *   <li>not wanted there, hard to reach for upkeep, or the rules do not allow it: nothing goes on that
 *       part.</li>
 * </ul>
 */
public final class KeptFree {

    /** Why the part is kept free. */
    public enum Reason { SOMETHING_THERE, NOT_WANTED, HARD_TO_REACH, RULES }

    /** What is there. */
    public enum Thing { CHIMNEY, WINDOW, OTHER }

    /**
     * A thing on the part. For a chimney no size is asked: at most about 0.8 × 0.8 m with 0.5 m kept clear
     * around it for shade and the sweep, about 3.2 m², 2 m² used [Judgement, WeShape]. For a window or
     * anything else the person gives the size; 0.3 m is kept clear on each side for fixing and access
     * [Judgement, WeShape].
     */
    public record Obstacle(Thing thing, double widthM, double heightM) {
        public static Obstacle chimney() {
            return new Obstacle(Thing.CHIMNEY, 0, 0);
        }

        public Obstacle {
            if (thing != Thing.CHIMNEY && (!(widthM > 0) || !(heightM > 0))) {
                throw new IllegalArgumentException("A window or other thing needs its width and height");
            }
        }

        /** Area left out for it, m². */
        public Range area() {
            if (thing == Thing.CHIMNEY) return CHIMNEY_M2;
            return Range.exactly((widthM + 2 * MARGIN_M) * (heightM + 2 * MARGIN_M));
        }
    }

    /** Area a chimney takes with its clearance, m². */
    public static final Range CHIMNEY_M2 = new Range(1, 2, 3.2);
    /** Clearance around a window or other thing, m. */
    public static final double MARGIN_M = 0.3;

    private KeptFree() {
    }

    /** Whether anything can still go on the part. */
    public static boolean partlyUsable(Reason reason) {
        return reason == Reason.SOMETHING_THERE;
    }

    /** Area of the part still usable, m²: all of it minus the things on it, or nothing. */
    public static Range usableM2(double partM2, Reason reason, List<Obstacle> things) {
        if (!(partM2 >= 0)) throw new IllegalArgumentException("The area cannot be negative");
        if (!partlyUsable(reason)) return Range.exactly(0);
        Range taken = Range.exactly(0);
        for (Obstacle o : things) taken = taken.plus(o.area());
        // More taken means less left: the low end of what is left goes with the high end of what is taken.
        return new Range(Math.max(0, partM2 - taken.high()), Math.max(0, partM2 - taken.typical()),
                Math.max(0, partM2 - taken.low()));
    }
}
