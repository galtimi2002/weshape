package com.weshape.backend.dto;

import com.weshape.backend.model.ThemeMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Sent once at the end of registration (or after a first Google sign-in) to
 * fill in the profile. The language is optional: left out, it follows the country.
 */
public record UpdateProfileRequest(
        @NotBlank @Size(max = 80)
        String firstName,

        @NotBlank @Size(max = 80)
        String lastName,

        @NotBlank @Pattern(regexp = "[A-Za-z]{2}", message = "country must be a two-letter ISO code")
        String country,

        String preferredLanguage,

        ThemeMode themeMode
) {
}
