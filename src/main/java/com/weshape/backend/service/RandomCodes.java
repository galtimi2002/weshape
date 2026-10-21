package com.weshape.backend.service;

import java.security.SecureRandom;

/**
 * Source of human-readable random strings, used for the personal user codes.
 *
 * The alphabet drops the characters that get misread off a screen or over the
 * phone: no I/1/L, no O/0. A user code is read aloud far more often than it
 * is copied, so this matters more than the extra entropy would.
 */
public final class RandomCodes {

    public static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

    private static final SecureRandom RANDOM = new SecureRandom();

    private RandomCodes() {
    }

    public static String of(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
