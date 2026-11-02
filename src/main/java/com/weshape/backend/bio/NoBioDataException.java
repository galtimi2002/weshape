package com.weshape.backend.bio;

import com.weshape.backend.exception.NotFoundException;

/** No biomass data here (the sea). */
public class NoBioDataException extends NotFoundException {

    public NoBioDataException(double lat, double lon) {
        super("No biomass data at " + lat + ", " + lon);
    }
}
