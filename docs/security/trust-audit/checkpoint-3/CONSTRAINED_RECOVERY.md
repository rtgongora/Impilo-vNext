# Checkpoint 3 — Constrained recovery semantics

## Defect corrected

Recovery-code authentication previously mapped to ordinary AAL2 authority. Source now classifies it as `CONSTRAINED_RECOVERY` and denies privileged work until a new approved factor is enrolled and a fresh ordinary authentication completes.

## Required behavior (implemented in source)

| Requirement | Implementation |
|---|---|
| Preserve `amr=recovery` (and Keycloak recovery form marker) | `KeycloakAdapter` keeps AMR; BFF stores AMR on session |
| Classify as `CONSTRAINED_RECOVERY` | v1 `AuthenticationAssurance.recoveryStateFromAmr` + BFF `SessionData.recovery` |
| Do not expose ordinary workforce AAL2 | Legacy adapter demotes AAL ≤ 1 when constrained; BFF never grants ordinary authority for recovery sessions |
| Max recovery-session lifetime 15 minutes | `WebAuthSessionProperties.recoverySessionTtlSeconds=900`; absolute `recoveryExpiresAt` enforced on lookup/refresh |
| Permit only factor re-enrollment / inspection / session termination / logout / approved recovery actions | BFF `RecoverySessionFilter` allowlist + authz `recoveryPermittedActions` |
| Deny clinical / regulatory / admin / financial / marketplace / platform ops | Filter 403 `RECOVERY_REQUIRED` + PolicyEngine Step 0 |
| Deny context/authority elevation | Gate runs before escalation grant resolution |
| Return `RECOVERY_REQUIRED` via canonical `TrustChallengeOutcome` | Authz builds outcome; `toLegacySafe` → fail-closed DENY `UNREPRESENTABLE_RECOVERY_REQUIRED` |
| Require new approved factor | Landing `/settings/security?recovery=1&continuation=…`; enrollment via Keycloak `kc_action` only (no OTP outside Keycloak) |
| Terminate recovery session after successful enrollment / fresh auth | Previous recovery session deleted on superseding authentication |
| Fresh ordinary auth before resuming protected journey | Continuation consumed only by non-recovery login |
| Opaque single-use continuation | Redis `experience:auth:continuation:*` with `getAndDelete` |
| Prevent replayed recovery use | Continuation + OIDC transaction both single-use |
| Audit without codes/tokens | Logger `impilo.trust.recovery.audit` — hashed session ref only |

## Restricted vs permitted recovery routes (BFF) — CP3 closure tightening

`RecoverySessionFilter` no longer allowlists path prefixes. Every request is mapped to a
canonical `ACTION:RESOURCE_TYPE` operation and the decision consults the canonical
allowlist (identical to `tshepo.authz.recovery-permitted-actions`). Requests that map to
no registered operation are **denied fail-closed**, including unknown operations under
formerly "permitted" prefixes.

**Exact operation dispositions during CONSTRAINED_RECOVERY:**

| Operation | Canonical | Disposition |
|---|---|---|
| `GET /internal/v1/settings/security` | `READ:ACCOUNT_SECURITY` | **allow** (sanitized factor status) |
| `DELETE /internal/v1/settings/security/sessions/{id}` | `DELETE:AUTH_SESSION` | **allow** (access-reducing) |
| `POST /internal/v1/settings/security/sessions/logout-others` | `DELETE:AUTH_SESSION` | **allow** |
| `DELETE /internal/v1/settings/security/credentials/{id}` | `DELETE:AUTH_FACTOR` | **deny** (last-factor protection; also denied in `SecuritySettingsService` with `CONSTRAINED_RECOVERY_SESSION`) |
| `POST/GET/POST /internal/v1/settings/security/recovery-cases/**` | `EXECUTE:ADMIN_RECOVERY` | **deny** (administrative recovery) |
| `GET /internal/v1/auth/oidc/authorize|callback` | `CREATE:AUTH_SESSION` | **allow** (fresh ordinary authentication) |
| `GET /internal/v1/auth/oidc/session` | `READ:AUTH_SESSION` | **allow** |
| `POST /internal/v1/auth/oidc/step-up` | `CREATE:AUTH_SESSION` | **allow** — always forces `prompt=login&max_age=0`; classification re-derived from AMR, so recovery can never launder into AAL2 |
| `POST /internal/v1/auth/oidc/action` | `CREATE:AUTH_FACTOR` | **allow only** `CONFIGURE_TOTP`, `webauthn-register`, `webauthn-register-passwordless`; `CONFIGURE_RECOVERY_AUTHN_CODES` (recovery-code regeneration) and `UPDATE_PASSWORD` return 403 `RECOVERY_ACTION_NOT_PERMITTED` |
| `POST /internal/v1/auth/oidc/logout`, `POST /internal/v1/auth/logout` | `LOGOUT:*` | **allow** |
| anything else (clinical, claims, admin, marketplace, work-context, unregistered ops) | — | **deny** `RECOVERY_REQUIRED` |

## Authz allowlist (`tshepo.authz.recovery-permitted-actions`) — tightened

Default entries: `LOGOUT:*`, `READ:ACCOUNT_SECURITY`, `READ:AUTH_FACTOR`,
`CREATE:AUTH_FACTOR`, `CREATE:AUTH_SESSION`, `READ:AUTH_SESSION`, `DELETE:AUTH_SESSION`.

`UPDATE:AUTH_FACTOR` and `DELETE:AUTH_FACTOR` were **removed** in the CP3 closure —
a recovery session may enroll a replacement factor but never mutate or delete existing
credentials. Recovery codes remain removed from `stepUpMethods`.

## Layers touched

1. Keycloak assurance mapping — `KeycloakAdapter` demotes via v1 adapter round-trip.
2. BFF session classification — `OidcSessionService` + `WebAuthSessionStore` + `OidcSessionController`.
3. Tshepo authentication-assurance — v1 contract + legacy adapter.
4. Authorization policy — `PolicyEngine` Step 0.
5. Account-security journeys — recovery landing + settings allowlist.
6. Audit — recovery audit logger + policy decision log `RECOVERY_REQUIRED`.

## Not done in this checkpoint

- Deploy of the corrected BFF/authz images (explicitly forbidden).
- Live browser proof of an authenticated recovery login (preview credential rejected; see runtime truth).
- Workforce MFA activation.
