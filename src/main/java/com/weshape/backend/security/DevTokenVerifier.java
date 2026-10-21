package com.weshape.backend.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Development-only verifier so the stack can be exercised end to end before Firebase
 * is configured. Accepts tokens shaped like:
 *
 * <pre>dev:&lt;uid&gt;:&lt;email&gt;</pre>
 *
 * for example {@code dev:demo-user:demo@weshape.energy}.
 *
 * Never enable this in production ({@code weshape.auth.mode=dev}).
 */
public class DevTokenVerifier implements TokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(DevTokenVerifier.class);

    public DevTokenVerifier() {
        log.warn("=================================================================");
        log.warn(" WeShape is running with weshape.auth.mode=dev.");
        log.warn(" Any 'dev:<uid>:<email>' bearer token is accepted. DO NOT use");
        log.warn(" this mode for a deployment that real users can reach.");
        log.warn("=================================================================");
    }

    @Override
    public VerifiedToken verify(String rawToken) throws InvalidTokenException {
        if (rawToken == null || !rawToken.startsWith("dev:")) {
            throw new InvalidTokenException("Dev tokens must look like dev:<uid>:<email>");
        }
        String[] parts = rawToken.split(":", 3);
        if (parts.length < 2 || parts[1].isBlank()) {
            throw new InvalidTokenException("Dev token is missing a uid");
        }
        String uid = parts[1];
        String email = parts.length == 3 && !parts[2].isBlank() ? parts[2] : uid + "@dev.weshape.energy";
        return new VerifiedToken(uid, email, true, null, uid, "password");
    }
}
