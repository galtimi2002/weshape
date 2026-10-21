package com.weshape.backend.security;

public interface TokenVerifier {

    /**
     * @param rawToken the value of the Authorization header after "Bearer ".
     * @return the verified identity
     * @throws InvalidTokenException when the token is missing, malformed, expired or not trusted
     */
    VerifiedToken verify(String rawToken) throws InvalidTokenException;
}
