package com.weshape.backend.geo;

import java.util.List;

/**
 * Geothermal values at one place.
 *
 * @param groundTemperature annual mean ground temperature near the surface (°C)
 * @param heatFlow          geothermal heat flow (mW/m²), null where no measurement is near
 * @param depths            estimated temperature at 1, 2, 3 and 5 km; empty without heat flow
 * @param conductivity      rock thermal conductivity used for the estimate (W/m·K)
 */
public record GeoPoint(double lat, double lon, double groundTemperature, Double heatFlow, List<Depth> depths,
                       double conductivity, String source) {

    public record Depth(int depthKm, double temperature) {}
}
