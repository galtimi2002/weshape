package com.weshape.backend.exception;

import java.util.Map;

/**
 * Rule violations that validation annotations cannot express — anything needing
 * more than one field, or a database lookup.
 */
public class ValidationException extends RuntimeException {

    private final transient Map<String, String> fieldErrors;

    public ValidationException(String field, String message) {
        super(message);
        this.fieldErrors = Map.of(field, message);
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
