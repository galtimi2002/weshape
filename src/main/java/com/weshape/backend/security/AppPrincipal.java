package com.weshape.backend.security;

import java.util.UUID;

/** The authenticated caller, resolved to a local WeShape user row. */
public record AppPrincipal(UUID userId, String firebaseUid, String email) {
}
