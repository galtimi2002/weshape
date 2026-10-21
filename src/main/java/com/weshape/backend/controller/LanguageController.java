package com.weshape.backend.controller;

import com.weshape.backend.i18n.SupportedLanguages;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/languages")
public class LanguageController {

    @Operation(summary = "Supported languages, and optionally the starting language for a country")
    @SecurityRequirements
    @GetMapping
    public Map<String, Object> languages(@RequestParam(required = false) String country) {
        List<String> codes = SupportedLanguages.CODES;
        return country == null
                ? Map.of("languages", codes, "default", SupportedLanguages.DEFAULT)
                : Map.of("languages", codes, "default", SupportedLanguages.DEFAULT,
                        "forCountry", SupportedLanguages.forCountry(country));
    }
}
