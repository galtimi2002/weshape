package com.weshape.backend.solar;

/**
 * Solar resource at one place.
 *
 * @param gti           irradiation on an optimally tilted surface, kWh/m² per year
 * @param pvout         PV output, kWh/kWp per year
 * @param opta          optimum tilt angle, degrees
 * @param gtiMonthly    monthly GTI, kWh/m², January first; sums to {@code gti}
 * @param pvoutMonthly  monthly PV output, kWh/kWp, January first; sums to {@code pvout}
 * @param source        where the numbers come from, shown to the user
 * @param example       true for invented placeholder values
 */
public record SolarPoint(
        double lat,
        double lon,
        double gti,
        double pvout,
        double opta,
        double[] gtiMonthly,
        double[] pvoutMonthly,
        String source,
        boolean example) {
}
