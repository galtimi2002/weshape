package com.weshape.backend.security;

/**
 * Provider-agnostic view of a verified identity token.
 *
 * {@code emailVerified} matters for account linking: adopting an existing local
 * account by email is only safe when the provider has proven the person controls
 * that address. Google sign-in always has; a self-declared email may not.
 */
public record VerifiedToken(
        String uid,
        String email,
        boolean emailVerified,
        String phoneNumber,
        String displayName,
        String signInProvider
) {
}
