package com.weshape.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A WeShape account. The password is not here: Firebase Authentication stores
 * it (hashed) and the backend only ever sees verified ID tokens.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
public class User {

    @Id
    private UUID id;

    /** Personal code shown to the user, e.g. WSE-K7M2-P9QR. Set once, never changed. */
    @Column(name = "user_code", nullable = false, unique = true, length = 16, updatable = false)
    private String userCode;

    @Column(name = "firebase_uid", nullable = false, unique = true, length = 128)
    private String firebaseUid;

    @Column(nullable = false, unique = true, length = 320)
    private String email;

    @Column(name = "first_name", length = 80)
    private String firstName;

    @Column(name = "last_name", length = 80)
    private String lastName;

    /** ISO 3166-1 alpha-2, upper case. */
    @Column(length = 2)
    private String country;

    /** ISO 639-1, lower case. */
    @Column(name = "preferred_language", nullable = false, length = 8)
    private String preferredLanguage = "en";

    @Enumerated(EnumType.STRING)
    @Column(name = "theme_mode", nullable = false, length = 8)
    private ThemeMode themeMode = ThemeMode.SYSTEM;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false, length = 16)
    private AuthProvider authProvider = AuthProvider.UNKNOWN;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Registration collects all three; a Google sign-in arrives without a
     * country, so the app asks for the rest once before letting the user in.
     */
    public boolean isProfileComplete() {
        return notBlank(firstName) && notBlank(lastName) && notBlank(country);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
