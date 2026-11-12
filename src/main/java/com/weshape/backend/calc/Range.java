package com.weshape.backend.calc;

/**
 * A value that is known only within limits: the low end, the value used, and the high end.
 * Results are shown as ranges because the inputs (and the expert's figures) are ranges.
 */
public record Range(double low, double typical, double high) {

    public Range {
        if (!(low <= typical && typical <= high)) {
            throw new IllegalArgumentException("Range must be low <= typical <= high: " + low + ", " + typical + ", " + high);
        }
    }

    /** A range with the middle as the value used. */
    public static Range of(double low, double high) {
        return new Range(low, (low + high) / 2, high);
    }

    /** A single known value. */
    public static Range exactly(double value) {
        return new Range(value, value, value);
    }

    public Range times(double factor) {
        return factor >= 0
                ? new Range(low * factor, typical * factor, high * factor)
                : new Range(high * factor, typical * factor, low * factor);
    }

    /** Both ends multiplied: the product of two uncertain values. */
    public Range times(Range other) {
        double a = low * other.low, b = low * other.high, c = high * other.low, d = high * other.high;
        return new Range(Math.min(Math.min(a, b), Math.min(c, d)), typical * other.typical,
                Math.max(Math.max(a, b), Math.max(c, d)));
    }

    public Range plus(Range other) {
        return new Range(low + other.low, typical + other.typical, high + other.high);
    }

    public boolean contains(double value) {
        return value >= low && value <= high;
    }
}
