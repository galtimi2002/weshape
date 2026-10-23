# weshape-backend

REST API for **WeShape Energy**. Spring Boot 3.4, Java 21, PostgreSQL (Neon),
Flyway, Firebase Authentication. Same stack and conventions as Inventica.

## What it stores

One table, `users`:

| Column | Notes |
| --- | --- |
| `user_code` | Personal code like `WSE-K7M2-P9QR`, generated at sign-up, unique, never changes |
| `email` | From the verified Firebase token |
| `first_name`, `last_name` | Set at registration (or the "one more step" screen after Google sign-in) |
| `country` | ISO 3166-1 alpha-2 (`RO`, `HU`, ...) |
| `preferred_language` | One of 25: en hu ro de fr es it pt nl pl hr fi sv da el ru tr zh ja ko hi bn id vi ar |
| `theme_mode` | `SYSTEM`, `LIGHT` or `DARK` |

**Passwords are not in this database.** Firebase Authentication stores them,
salted and hashed; the backend only ever receives signed ID tokens.

## Endpoints

| Method | Path | Auth | |
| --- | --- | --- | --- |
| GET | `/api/v1/ping` | public | liveness |
| GET | `/api/v1/languages?country=HU` | public | supported languages, the country's starting language |
| GET | `/api/v1/users/me` | token | profile; the first call after sign-up creates it |
| PUT | `/api/v1/users/me` | token | complete the profile: `firstName`, `lastName`, `country`, optional `preferredLanguage`, `themeMode` |
| PUT | `/api/v1/users/me/preferences` | token | save `preferredLanguage` and/or `themeMode` |

Leaving `preferredLanguage` out of the profile call sets it from the country
(HU → hu, AT → de, BR → pt, anything unmapped → en).

## Run locally

```bash
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
# fill in the Neon connection string, keep auth mode "dev" for now
./mvnw spring-boot:run
```

With no profile given, the `local` profile is used, so the green Run button
in IntelliJ works the same way.

In dev mode any `Authorization: Bearer dev:<uid>:<email>` token is accepted:

```powershell
curl.exe http://localhost:8080/api/v1/users/me -H "Authorization: Bearer dev:gabor:gabor@weshape.energy"
```

Swagger UI: http://localhost:8080/docs

## Tests

```bash
./mvnw verify
```

Integration tests run on in-memory H2 with dev tokens: account creation and
the unique code, language from country, preferences persisting, validation,
and account linking by verified email.

## Deploy (Render)

`render.yaml` describes the service. Environment variables to fill in:

- `DATABASE_URL`: Neon connection string (`postgres://...` is fine)
- `FIREBASE_PROJECT_ID`
- `FIREBASE_CREDENTIALS_BASE64`: the service-account JSON, base64 encoded:
  `[Convert]::ToBase64String([IO.File]::ReadAllBytes("serviceAccount.json"))`
- `CORS_ALLOWED_ORIGINS`: the web app's address, e.g. `https://weshape-app.onrender.com`
