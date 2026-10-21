package com.weshape.backend.service;

import com.weshape.backend.dto.UpdatePreferencesRequest;
import com.weshape.backend.dto.UpdateProfileRequest;
import com.weshape.backend.exception.NotFoundException;
import com.weshape.backend.exception.ValidationException;
import com.weshape.backend.i18n.SupportedLanguages;
import com.weshape.backend.model.AuthProvider;
import com.weshape.backend.model.User;
import com.weshape.backend.repository.UserRepository;
import com.weshape.backend.security.VerifiedToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final UserCodeGenerator userCodeGenerator;

    public UserService(UserRepository userRepository, UserCodeGenerator userCodeGenerator) {
        this.userRepository = userRepository;
        this.userCodeGenerator = userCodeGenerator;
    }

    /**
     * Looks up the local user for a verified token, creating the row (and its
     * personal code) on first sign-in.
     *
     * A person who registered with email and later signs in with Google under
     * the same address gets a new Firebase uid. Their existing account is
     * adopted when the provider has verified the address, and refused when it
     * has not: adopting on an unverified email would let anyone claim someone
     * else's account by typing their address.
     */
    @Transactional
    public User provision(VerifiedToken token) {
        User user = userRepository.findByFirebaseUid(token.uid()).orElse(null);

        if (user == null) {
            user = adoptExistingAccount(token);
        }

        if (user == null) {
            user = new User();
            user.setFirebaseUid(token.uid());
            user.setUserCode(userCodeGenerator.next());
            user.setAuthProvider(AuthProvider.fromFirebaseSignInProvider(token.signInProvider()));
            applyDisplayName(user, token.displayName());
            log.info("New account {}", user.getUserCode());
        }

        if (token.email() != null) {
            user.setEmail(token.email().trim().toLowerCase(Locale.ROOT));
        }
        if (user.getEmail() == null) {
            // The schema requires an email; Firebase email and Google sign-ins always carry one.
            user.setEmail(token.uid() + "@users.weshape.energy");
        }

        return userRepository.save(user);
    }

    private User adoptExistingAccount(VerifiedToken token) {
        if (token.email() == null) {
            return null;
        }
        User existing = userRepository.findByEmailIgnoreCase(token.email()).orElse(null);
        if (existing == null) {
            return null;
        }
        if (!token.emailVerified()) {
            throw new ValidationException("email",
                    "An account already exists for %s. Sign in the way you did originally."
                            .formatted(token.email()));
        }
        log.info("Linking account {} to a new sign-in ({})", existing.getUserCode(), token.signInProvider());
        existing.setFirebaseUid(token.uid());
        existing.setAuthProvider(AuthProvider.fromFirebaseSignInProvider(token.signInProvider()));
        return existing;
    }

    /** Google gives "Ada Lovelace"; the first word is the first name, the rest the last name. */
    private static void applyDisplayName(User user, String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return;
        }
        String[] parts = displayName.trim().split("\\s+", 2);
        user.setFirstName(truncate(parts[0]));
        if (parts.length > 1) {
            user.setLastName(truncate(parts[1]));
        }
    }

    private static String truncate(String value) {
        return value.length() > 80 ? value.substring(0, 80) : value;
    }

    @Transactional(readOnly = true)
    public Optional<User> findByFirebaseUid(String firebaseUid) {
        return userRepository.findByFirebaseUid(firebaseUid);
    }

    @Transactional(readOnly = true)
    public User requireById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    /** Completes the profile: names, country, and a language that defaults from the country. */
    @Transactional
    public User updateProfile(UUID id, UpdateProfileRequest request) {
        User user = requireById(id);

        String country = request.country().trim().toUpperCase(Locale.ROOT);
        if (!SupportedLanguages.isCountry(country)) {
            throw new ValidationException("country", "Unknown country code: " + request.country());
        }

        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setCountry(country);

        String language = request.preferredLanguage();
        if (language == null || language.isBlank()) {
            user.setPreferredLanguage(SupportedLanguages.forCountry(country));
        } else {
            user.setPreferredLanguage(requireLanguage(language));
        }

        if (request.themeMode() != null) {
            user.setThemeMode(request.themeMode());
        }
        return userRepository.save(user);
    }

    /** Language and theme, saved whenever the person flips them in the app. */
    @Transactional
    public User updatePreferences(UUID id, UpdatePreferencesRequest request) {
        User user = requireById(id);
        if (request.preferredLanguage() != null) {
            user.setPreferredLanguage(requireLanguage(request.preferredLanguage()));
        }
        if (request.themeMode() != null) {
            user.setThemeMode(request.themeMode());
        }
        return userRepository.save(user);
    }

    private static String requireLanguage(String code) {
        String normalized = code.trim().toLowerCase(Locale.ROOT);
        if (!SupportedLanguages.isSupported(normalized)) {
            throw new ValidationException("preferredLanguage",
                    "Unsupported language: %s. Supported: %s".formatted(code, SupportedLanguages.CODES));
        }
        return normalized;
    }
}
