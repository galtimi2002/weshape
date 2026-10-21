package com.weshape.backend.dto;

import com.weshape.backend.model.AuthProvider;
import com.weshape.backend.model.ThemeMode;
import com.weshape.backend.model.User;

import java.time.Instant;
import java.util.UUID;

public record UserDto(
        UUID id,
        String userCode,
        String email,
        String firstName,
        String lastName,
        String country,
        String preferredLanguage,
        ThemeMode themeMode,
        AuthProvider authProvider,
        /** False until first name, last name and country are set; the app routes on this. */
        boolean profileComplete,
        Instant createdAt
) {
    public static UserDto from(User user) {
        return new UserDto(
                user.getId(),
                user.getUserCode(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getCountry(),
                user.getPreferredLanguage(),
                user.getThemeMode(),
                user.getAuthProvider(),
                user.isProfileComplete(),
                user.getCreatedAt()
        );
    }
}
