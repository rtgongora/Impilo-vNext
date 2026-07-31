# Impilo MFA current-state evidence

**Evidence date:** 2026-07-31  
**Canonical source:** `80565bd178c61d13f0cd2d8cadd2cb0782c611f9`  
**Preview namespace:** `impilo-full-preview`

This report is the mandatory truth gate for the production MFA programme. It distinguishes
tracked configuration, live runtime evidence, and intended policy. It contains no credentials,
factor secrets, recovery codes, user names, email addresses, or other personal data.

## Live runtime

| Area | Verified state | Consequence |
| --- | --- | --- |
| Keycloak runtime | Keycloak 25, `start-dev`, persistent embedded H2 | Not an acceptable production baseline; schema rollback and backup are weak |
| Realm | `impilo`, TLS requirement `none`, brute-force protection disabled | Realm posture is below the production security baseline |
| Users | 42 enabled users | Migration must preserve IDs, attributes, roles, and password credential hashes |
| MFA credentials | 0 OTP, 0 WebAuthn, 0 recovery credentials | No user currently has working MFA |
| Authentication flows | Built-in flows only | Tracked passkey flow is not active |
| Required actions | TOTP and WebAuthn actions enabled but not assigned | Workforce enrollment is not enforced |
| Clients | `experience-ui` direct grants enabled; wildcard web callbacks; no live `impilo-passkey` client | Browser login bypasses interactive MFA and redirect scope is excessive |
| Events/recovery mail | No governed security-event or SMTP configuration in tracked preview realm | Recovery and centralized MFA audit are not operational |
| PostgreSQL | No live `keycloak` database | H2-to-PostgreSQL migration is required before Keycloak upgrade |

## Application and trust plane

- `experience-bff` performs a Resource Owner Password Credentials exchange. That grant cannot
  execute the required interactive enrollment and step-up journeys.
- The web shell persists the access token in `sessionStorage`. The existing Security Settings
  and `/auth/mfa` screens call endpoints that do not exist and must not be represented as live.
- Mobile uses authorization code + PKCE and securely stores issued tokens, but the pending state,
  nonce, and verifier are process-local and cannot survive a callback after process death.
- `tshepo-authz-service` contains its own TOTP secret store and SMS/TOTP challenge engine. The live
  `tshepo_authz.totp_enrolment` row count is **0**, so it can be retired without migrating secrets.
- Tshepo currently treats OIDC `acr` and `X-Assurance-Level` as one numeric LoA. The architecture
  contract requires identity assurance (IAL) and authentication assurance (AAL) to remain distinct.
- Full-preview Helm injects `IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS=true` estate-wide. Preview
  therefore cannot be accepted as proof of JWT or Tshepo enforcement until this is removed and
  every protected service passes the real-token matrix.
- Bootstrap activation trusts a client-supplied `mfaConfigured` boolean. That is not evidence and
  must be replaced with server-derived Keycloak credential and token-assurance checks.

## Configuration drift cause

The realm import is a first-boot seed. Keycloak skips an existing realm, so changes to the tracked
realm JSON do not reconcile the live realm. MFA configuration must be managed by a versioned,
idempotent plan/apply reconciler with an expected-current-hash guard.

## Release blockers

MFA must not be activated until all of the following are proven:

1. H2 export, PostgreSQL import, Keycloak 26.7 upgrade, comparison, backup, and restore rehearsal.
2. Interactive web authorization-code flow with server-side sessions and CSRF protection.
3. Native TOTP, passkey, recovery-code, and step-up journeys through the public preview ingress.
4. Explicit AAL claims and Tshepo policy enforcement without trusting actor-supplied headers.
5. Removal of the preview OAuth test bypass in controlled service cohorts.
6. Security event ingestion into the append-only Tshepo audit chain.
7. Exact redirect URIs, brute-force protection, SMTP recovery delivery, and rollback evidence.

This document records current truth only. It does not assert that any release blocker is complete.
