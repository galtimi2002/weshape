package com.weshape.backend.config;

import com.weshape.backend.security.AuthTokenFilter;
import com.weshape.backend.security.RestAuthenticationEntryPoint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final AuthTokenFilter authTokenFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;

    @Value("${weshape.cors.allowed-origins:http://localhost:*}")
    private String allowedOrigins;

    @Value("${weshape.docs.public:true}")
    private boolean docsPublic;

    public SecurityConfig(AuthTokenFilter authTokenFilter,
                          RestAuthenticationEntryPoint authenticationEntryPoint) {
        this.authTokenFilter = authTokenFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    /** Swagger UI and the OpenAPI document. */
    private static final String[] DOCS_PATHS = {
            "/docs", "/docs/**", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                            .requestMatchers(
                                    "/api/v1/ping",
                                    "/api/v1/languages",
                                    // Spring forwards failures here; keeping it open lets the
                                    // real status (400, 403, 404) reach the client instead of a 401.
                                    "/error",
                                    "/actuator/health",
                                    "/actuator/health/**",
                                    "/actuator/info"
                            ).permitAll();

                    // The docs describe every endpoint and payload shape. Open
                    // locally so they are usable without ceremony; closed in
                    // production so the API's surface is not published to
                    // strangers. Set DOCS_PUBLIC=false on the deployed service.
                    if (docsPublic) {
                        auth.requestMatchers(DOCS_PATHS).permitAll();
                    } else {
                        auth.requestMatchers(DOCS_PATHS).authenticated();
                    }

                    auth.anyRequest().authenticated();
                })
                .exceptionHandling(ex -> ex.authenticationEntryPoint(authenticationEntryPoint))
                .addFilterBefore(authTokenFilter, UsernamePasswordAuthenticationFilter.class)
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable());

        return http.build();
    }

    /**
     * {@link AuthTokenFilter} is a {@code @Component}, which means Boot would also
     * register it as a plain servlet filter running ahead of the security chain —
     * so a bad token would be rejected even on public endpoints. This turns that
     * automatic registration off; the filter runs only where the chain places it.
     */
    @Bean
    public FilterRegistrationBean<AuthTokenFilter> disableAutoRegistration(AuthTokenFilter filter) {
        FilterRegistrationBean<AuthTokenFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        CorsConfiguration config = new CorsConfiguration();
        // setAllowedOriginPatterns supports wildcards such as http://localhost:*
        config.setAllowedOriginPatterns(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
