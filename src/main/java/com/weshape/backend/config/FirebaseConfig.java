package com.weshape.backend.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.weshape.backend.security.DevTokenVerifier;
import com.weshape.backend.security.FirebaseTokenVerifier;
import com.weshape.backend.security.TokenVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Configuration
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    @Value("${weshape.auth.mode:firebase}")
    private String authMode;

    @Value("${weshape.auth.firebase.credentials-json:}")
    private String credentialsJson;

    @Value("${weshape.auth.firebase.credentials-base64:}")
    private String credentialsBase64;

    @Value("${weshape.auth.firebase.credentials-path:}")
    private String credentialsPath;

    @Value("${weshape.auth.firebase.project-id:}")
    private String projectId;

    @Bean
    public TokenVerifier tokenVerifier() throws IOException {
        if ("dev".equalsIgnoreCase(authMode)) {
            return new DevTokenVerifier();
        }
        FirebaseApp app = firebaseApp();
        return new FirebaseTokenVerifier(FirebaseAuth.getInstance(app));
    }

    private FirebaseApp firebaseApp() throws IOException {
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }

        GoogleCredentials credentials = loadCredentials();
        FirebaseOptions.Builder builder = FirebaseOptions.builder().setCredentials(credentials);
        if (!projectId.isBlank()) {
            builder.setProjectId(projectId);
        }
        log.info("Initialising Firebase Admin SDK");
        return FirebaseApp.initializeApp(builder.build());
    }

    private GoogleCredentials loadCredentials() throws IOException {
        if (!credentialsJson.isBlank()) {
            try (InputStream in = new ByteArrayInputStream(credentialsJson.getBytes(StandardCharsets.UTF_8))) {
                return GoogleCredentials.fromStream(in);
            }
        }
        if (!credentialsBase64.isBlank()) {
            byte[] decoded = Base64.getDecoder().decode(credentialsBase64.trim());
            try (InputStream in = new ByteArrayInputStream(decoded)) {
                return GoogleCredentials.fromStream(in);
            }
        }
        if (!credentialsPath.isBlank()) {
            try (InputStream in = new FileInputStream(credentialsPath)) {
                return GoogleCredentials.fromStream(in);
            }
        }
        try {
            return GoogleCredentials.getApplicationDefault();
        } catch (IOException e) {
            throw new IllegalStateException("""
                    No Firebase credentials found.

                    Set one of:
                      FIREBASE_CREDENTIALS_JSON    - the service-account JSON, inline
                      FIREBASE_CREDENTIALS_BASE64  - the same JSON, base64 encoded
                      FIREBASE_CREDENTIALS_PATH    - path to a mounted service-account file

                    Or, while you are still wiring things up, start the app with
                    WESHAPE_AUTH_MODE=dev to use the development token verifier.
                    """, e);
        }
    }
}
