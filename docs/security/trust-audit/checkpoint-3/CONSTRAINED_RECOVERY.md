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

## Restricted vs permitted recovery routes (BFF)

**Permitted prefixes** (`RecoverySessionFilter`):

- `/internal/v1/auth/oidc/**` (session, logout, action/enrollment, authorize, callback)
- `/internal/v1/auth/logout`
- `/internal/v1/settings/security/**` (factor inspection, credential remove, session terminate)

**Denied examples** (return `RECOVERY_REQUIRED`):

- `/internal/v1/patients/**`, clinical encounters, claims, admin users, marketplace orders, work-context selection

## Authz allowlist (`tshepo.authz.recovery-permitted-actions`)

Default entries: `LOGOUT:*`, `READ|CREATE|UPDATE|DELETE:AUTH_FACTOR`, `READ:ACCOUNT_SECURITY`, `READ|DELETE:AUTH_SESSION`.

Recovery codes were **removed** from `stepUpMethods` (no longer `totp, webauthn, recovery`).

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
