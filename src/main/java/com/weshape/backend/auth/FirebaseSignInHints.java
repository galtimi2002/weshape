package com.weshape.backend.auth;

import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserInfo;
import com.google.firebase.auth.UserRecord;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Looks the account up with the Firebase Admin SDK and reads its sign-in providers. */
public class FirebaseSignInHints implements SignInHints {

    private static final Logger log = LoggerFactory.getLogger(FirebaseSignInHints.class);

    private final FirebaseAuth auth;

    public FirebaseSignInHints(FirebaseAuth auth) {
        this.auth = auth;
    }

    @Override
    public boolean googleOnly(String email) {
        try {
            UserRecord user = auth.getUserByEmail(email);
            Set<String> providers = new HashSet<>();
            for (UserInfo info : user.getProviderData()) providers.add(info.getProviderId());
            return providers.contains("google.com") && !providers.contains("password");
        } catch (FirebaseAuthException e) {
            if (e.getAuthErrorCode() != AuthErrorCode.USER_NOT_FOUND) {
                log.warn("Sign-in hint lookup failed: {}", e.getAuthErrorCode());
            }
            return false;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
