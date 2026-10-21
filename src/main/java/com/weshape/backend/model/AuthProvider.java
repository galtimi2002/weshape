package com.weshape.backend.model;

public enum AuthProvider {
    GOOGLE,
    EMAIL,
    UNKNOWN;

    public static AuthProvider fromFirebaseSignInProvider(String provider) {
        if (provider == null) {
            return UNKNOWN;
        }
        return switch (provider) {
            case "google.com" -> GOOGLE;
            case "password" -> EMAIL;
            default -> UNKNOWN;
        };
    }
}
