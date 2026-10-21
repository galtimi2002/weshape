package com.weshape.backend.service;

import com.weshape.backend.repository.UserRepository;
import org.springframework.stereotype.Component;

/**
 * Makes the personal code every account gets, shaped like {@code WSE-K7M2-P9QR}.
 *
 * Eight characters from a 31-letter alphabet give about 850 billion codes, so
 * a clash is rare; when one happens the generator simply draws again. The
 * database's unique constraint is the final guard.
 */
@Component
public class UserCodeGenerator {

    static final String PREFIX = "WSE";
    private static final int MAX_ATTEMPTS = 10;

    private final UserRepository userRepository;

    public UserCodeGenerator(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public String next() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String code = format(RandomCodes.of(8));
            if (!userRepository.existsByUserCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Could not find a free user code");
    }

    static String format(String eight) {
        return PREFIX + "-" + eight.substring(0, 4) + "-" + eight.substring(4, 8);
    }
}
