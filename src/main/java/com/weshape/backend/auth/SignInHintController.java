package com.weshape.backend.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * After a failed email sign-in: does this address sign in with Google? Open without a token (the
 * person is not signed in). Only "Google only" is ever revealed, and each client may ask a few times
 * an hour (docs/decisions/2026-11-18-sign-in-hint.md).
 */
@RestController
@RequestMapping("/api/v1/auth")
public class SignInHintController {

    /** Lookups per client per hour. */
    static final int MAX_PER_HOUR = 10;

    private final SignInHints hints;
    private final HintLimiter limiter = new HintLimiter(MAX_PER_HOUR, 60 * 60 * 1000L, Clock.systemUTC());

    public SignInHintController(SignInHints hints) {
        this.hints = hints;
    }

    public record HintRequest(String email) {}

    @Operation(summary = "Whether an email address signs in with Google only (after a failed sign-in)")
    @SecurityRequirements
    @PostMapping("/sign-in-hint")
    public Map<String, Boolean> hint(@RequestBody(required = false) HintRequest body, HttpServletRequest request) {
        String email = body == null || body.email() == null ? "" : body.email().strip();
        if (email.isEmpty() || email.length() > 254 || !email.contains("@")) return Map.of("google", false);
        if (!limiter.allow(client(request))) return Map.of("google", false);
        return Map.of("google", hints.googleOnly(email));
    }

    /** The caller's address; behind Render's proxy the first one in X-Forwarded-For. */
    private static String client(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].strip();
        return request.getRemoteAddr();
    }
}
