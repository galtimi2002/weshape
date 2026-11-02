package com.weshape.backend.geo;

import com.weshape.backend.exception.NotFoundException;

/** No geothermal data here (sea, ice sheet, outside the maps). */
public class NoGeoDataException extends NotFoundException {

    public NoGeoDataException(double lat, double lon) {
        super("No geothermal data at " + lat + ", " + lon);
    }
}
