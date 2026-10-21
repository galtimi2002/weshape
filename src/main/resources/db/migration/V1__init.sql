-- WeShape Energy initial schema.
--
-- Passwords are NOT stored here. Firebase Authentication owns the credentials
-- (salted and hashed with scrypt on Google's side); this table holds the
-- profile and preferences that belong to the person behind the Firebase uid.

CREATE TABLE users (
    -- Internal key. UUID rather than a sequence, so ids in tokens and URLs do
    -- not reveal how many accounts exist.
    id                  UUID         PRIMARY KEY,

    -- The person's own, human-readable code, e.g. WSE-K7M2-P9QR. Generated once
    -- at sign-up, never reused and never changed.
    user_code           VARCHAR(16)  NOT NULL UNIQUE,

    firebase_uid        VARCHAR(128) NOT NULL UNIQUE,
    email               VARCHAR(320) NOT NULL UNIQUE,
    first_name          VARCHAR(80),
    last_name           VARCHAR(80),

    -- ISO 3166-1 alpha-2, e.g. RO, HU, DE. Null until the profile is completed.
    country             VARCHAR(2),

    -- ISO 639-1, e.g. en, hu, ro. English until the person chooses otherwise.
    preferred_language  VARCHAR(8)   NOT NULL DEFAULT 'en',
    theme_mode          VARCHAR(8)   NOT NULL DEFAULT 'SYSTEM',

    auth_provider       VARCHAR(16)  NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT users_auth_provider_check
        CHECK (auth_provider IN ('GOOGLE', 'EMAIL', 'UNKNOWN')),
    CONSTRAINT users_theme_mode_check
        CHECK (theme_mode IN ('SYSTEM', 'LIGHT', 'DARK'))
);

-- Email lookups are case-insensitive (account linking by email).
CREATE INDEX idx_users_email_lower ON users (lower(email));
