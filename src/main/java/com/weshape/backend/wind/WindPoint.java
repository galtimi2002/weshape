package com.weshape.backend.wind;

import java.util.List;

/** The wind at one place: mean speed by height, and the power density at 100 m. */
public record WindPoint(double lat, double lon, List<Speed> speeds, double speed100, double powerDensity100,
                        String source) {

    public record Speed(int height, double speed) {}
}
