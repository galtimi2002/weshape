package com.weshape.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weshape.backend.exception.ValidationException;
import com.weshape.backend.model.User;
import com.weshape.backend.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Verifies the bearer token and, if it checks out, puts the caller in the
 * security context.
 *
 * It deliberately does <em>not</em> reject bad tokens itself. This filter sits
 * inside the security chain, so it runs for public endpoints too — failing here
 * would make a junk Authorization header 401 a public URL like /api/v1/ping.
 * Instead an unusable token simply leaves the request unauthenticated, and the
 * authorization rules decide: public endpoints carry on, protected ones fall
 * through to {@link RestAuthenticationEntryPoint}.
 */
@Component
public class AuthTokenFilter extends OncePerRequestFilter {

    /** Set when a token was supplied but could not be verified, so the entry point can say so. */
    public static final String INVALID_TOKEN_ATTRIBUTE = "weshape.auth.invalidToken";

    private static final Logger log = LoggerFactory.getLogger(AuthTokenFilter.class);
    private static final String BEARER = "Bearer ";

    private final TokenVerifier tokenVerifier;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    public AuthTokenFilter(TokenVerifier tokenVerifier, UserService userService, ObjectMapper objectMapper) {
        this.tokenVerifier = tokenVerifier;
        this.userService = userService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER)) {
            filterChain.doFilter(request, response);
            return;
        }

        String rawToken = header.substring(BEARER.length()).trim();
        try {
            VerifiedToken verified = tokenVerifier.verify(rawToken);

            User user;
            try {
                user = provision(verified);
            } catch (RuntimeException e) {
                // This filter runs outside the dispatcher, so @RestControllerAdvice
                // never sees these — without this, a provisioning failure reaches the
                // client as a bare container error page instead of our JSON.
                writeProvisioningFailure(request, response, e);
                return;
            }

            AppPrincipal principal = new AppPrincipal(user.getId(), user.getFirebaseUid(), user.getEmail());

            // ROLE_USER means "signed in". Roles for features to come (admin,
            // installer, ...) get added here once they exist.
            List<SimpleGrantedAuthority> authorities = new ArrayList<>();
            authorities.add(new SimpleGrantedAuthority("ROLE_USER"));

            var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (InvalidTokenException e) {
            log.debug("Unusable token on {} {}: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
            SecurityContextHolder.clearContext();
            request.setAttribute(INVALID_TOKEN_ATTRIBUTE, Boolean.TRUE);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Right after sign-up the app may fire two requests at once, and both try to
     * create the same account. The loser hits the unique constraint; by then the
     * winner's row exists, so it is simply read back.
     */
    private User provision(VerifiedToken verified) {
        try {
            return userService.provision(verified);
        } catch (DataIntegrityViolationException e) {
            return userService.findByFirebaseUid(verified.uid()).orElseThrow(() -> e);
        }
    }

    private void writeProvisioningFailure(HttpServletRequest request,
                                          HttpServletResponse response,
                                          RuntimeException e) throws IOException {
        SecurityContextHolder.clearContext();

        boolean identityClash = e instanceof ValidationException
                || e instanceof DataIntegrityViolationException;

        HttpStatus status = identityClash ? HttpStatus.CONFLICT : HttpStatus.INTERNAL_SERVER_ERROR;
        String message = identityClash
                ? e.getMessage()
                : "Could not set up your account";

        if (identityClash) {
            log.warn("Could not provision {}: {}", request.getRequestURI(), e.getMessage());
        } else {
            log.error("Provisioning failed for {}", request.getRequestURI(), e);
        }

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), Map.of(
                "timestamp", Instant.now().toString(),
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", message == null ? "Could not set up your account" : message,
                "path", request.getRequestURI()
        ));
    }
}
