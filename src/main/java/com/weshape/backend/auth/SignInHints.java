package com.weshape.backend.auth;

/**
 * Whether an email address belongs to an account that signs in with Google only (no password). Used
 * after a failed email sign-in, to tell the person to use "Continue with Google" instead of "incorrect
 * email or password" (docs/decisions/2026-11-18-sign-in-hint.md).
 */
public interface SignInHints {

    boolean googleOnly(String email);
}
