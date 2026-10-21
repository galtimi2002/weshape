package com.weshape.backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class PingController {

    @Value("${weshape.auth.mode:firebase}")
    private String authMode;

    @Operation(summary = "Public liveness check, no authentication required")
    @SecurityRequirements
    @GetMapping("/ping")
    public Map<String, Object> ping() {
        return Map.of(
                "service", "weshape-backend",
                "status", "ok",
                "authMode", authMode,
                "time", Instant.now().toString()
        );
    }
}
