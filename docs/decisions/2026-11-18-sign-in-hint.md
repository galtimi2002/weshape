# "Use Google" instead of "incorrect password" for Google accounts

Date: 2026-11-18. Agreed with Timi.

## The problem

Someone who always signed in with Google has no WeShape password: Google never gives us their
Google password. Typing it on the email form fails, and Firebase only says "invalid credential"
(it does not reveal which addresses have accounts). The app showed "Incorrect email or password",
which is confusing when the email is right.

## Decision

After a failed email sign-in, the app asks the backend whether the address belongs to an account
that signs in with Google only (`POST /api/v1/auth/sign-in-hint`, `{"email": ...}` →
`{"google": true|false}`). The backend looks it up with the Firebase Admin SDK.

- Google only (Google, no password): "This email address signs in with Google. Use
  “Continue with Google”."
- Anything else (no account, a password account, the lookup failing): "Incorrect email or password."

## What it reveals, and the limit

The answer tells whether an address is a Google-only account here. So that this cannot be used to
test long lists of addresses, each client (IP address) gets 10 lookups an hour; after that the
answer is always `false`. Nothing else about the account is returned. The endpoint needs no token,
because the person is not signed in yet.

## Code

Backend: `auth/SignInHints`, `FirebaseSignInHints`, `HintLimiter`, `SignInHintController`,
`SignInHintTest`. Frontend: `ApiClient.signInHint`, the login screen.
