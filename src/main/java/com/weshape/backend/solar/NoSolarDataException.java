package com.weshape.backend.solar;

import com.weshape.backend.exception.NotFoundException;

/** There is nothing to report for this place, typically because it is over the sea. */
public class NoSolarDataException extends NotFoundException {

    public NoSolarDataException(String message) {
        super(message);
    }
}
