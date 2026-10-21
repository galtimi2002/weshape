package com.weshape.backend.security;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;

import java.util.Map;

public class FirebaseTokenVerifier implements TokenVerifier {

    private final FirebaseAuth firebaseAuth;

    public FirebaseTokenVerifier(FirebaseAuth firebaseAuth) {
        this.firebaseAuth = firebaseAuth;
    }

    @Override
    public VerifiedToken verify(String rawToken) throws InvalidTokenException {
        try {
            FirebaseToken token = firebaseAuth.verifyIdToken(rawToken, true);
            return new VerifiedToken(
                    token.getUid(),
                    token.getEmail(),
                    token.isEmailVerified(),
                    phoneNumberOf(token),
                    token.getName(),
                    signInProviderOf(token)
            );
        } catch (FirebaseAuthException e) {
            throw new InvalidTokenException("Firebase rejected the ID token: " + e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw new InvalidTokenException("Malformed ID token", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static String signInProviderOf(FirebaseToken token) {
        Object firebase = token.getClaims().get("firebase");
        if (firebase instanceof Map<?, ?> map) {
            Object provider = ((Map<String, Object>) map).get("sign_in_provider");
            return provider != null ? provider.toString() : null;
        }
        return null;
    }

    private static String phoneNumberOf(FirebaseToken token) {
        Object phone = token.getClaims().get("phone_number");
        return phone != null ? phone.toString() : null;
    }
}
