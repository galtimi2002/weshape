package com.weshape.backend.wind;

import com.weshape.backend.exception.NotFoundException;

/** The wind atlas has no value here (open sea beyond the coastal zone, or outside its area). */
public class NoWindDataException extends NotFoundException {

    public NoWindDataException(double lat, double lon) {
        super("No wind data at " + lat + ", " + lon);
    }
}
