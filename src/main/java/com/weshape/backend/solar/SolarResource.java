package com.weshape.backend.solar;

/**
 * Where solar values come from.
 *
 * <p>Today {@link ExampleSolarResource}; later the Global Solar Atlas rasters for the map and
 * PVGIS for monthly figures, behind this same interface.
 */
public interface SolarResource {

    /** Values for one place. */
    SolarPoint at(double lat, double lon);

    /** One layer's yearly value, used to paint the map. */
    double value(SolarLayer layer, double lat, double lon);

    /** Short name of the data source, shown to the user as attribution. */
    String source();

    /** True while the numbers are placeholders, so the app can say so. */
    boolean isExample();
}
