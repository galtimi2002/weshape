package com.weshape.backend.hydro;

import com.weshape.backend.exception.NotFoundException;

/** No river reach in the data near this place. */
public class NoHydroDataException extends NotFoundException {

    public NoHydroDataException(double lat, double lon) {
        super("No river near " + lat + ", " + lon);
    }
}
