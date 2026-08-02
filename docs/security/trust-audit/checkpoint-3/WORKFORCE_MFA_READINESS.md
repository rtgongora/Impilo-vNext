# Checkpoint 3 — Workforce MFA activation readiness

**Workforce MFA is not activated.** This matrix is readiness only.

## Matrix

| Capability | Readiness | Notes / gaps |
|---|---|---|
| Workforce role classification | READY (source) | Realm roles distinguish citizen vs workforce; release evidence: 38 workforce accounts with MFA/recovery required actions |
| First-login required action | READY (Keycloak native) | Native required actions for MFA/recovery on workforce; BFF `IMPILO_BOOTSTRAP_REQUIRE_MFA=false` — activation flag off |
| TOTP enrollment | READY (Keycloak native) | Via `kc_action=CONFIGURE_TOTP`; Tshepo local TOTP endpoint retired (HTTP 410) |
| Ordinary passkey AAL2 | READY (source) | `webauthn-register` / passwordless actions allowed in BFF; phishing-resistant AMR recognised |
| Approved hardware AAL3 | **BLOCKED (external)** | Requires approved AAGUID allowlist (not present in preview) |
| Recovery-code generation / confirmation | READY (source, constrained) | `CONFIGURE_RECOVERY_AUTHN_CODES`; constrained recovery semantics implemented in source this checkpoint |
| Lost-device recovery | PARTIAL | Two-person recovery execution path exists (`RecoveryExecutionService`); needs operational drill + SMTP for email execute-actions |
| SMTP / mail-capture readiness | **BLOCKED (external)** | No mail-capture Deployment/Service in `impilo-full-preview`; production SMTP relay not configured |
| Administrative bootstrap | PARTIAL | Bootstrap flags present (`IMPILO_BOOTSTRAP_*`); `REQUIRE_MFA=false`; one-time-token / signed-authorisation methods only |
| Rollback / support procedures | READY (artifacts) | H2 PVC, migration-backup PVC, encrypted PG dump, pre-MFA digests documented; **retention calendar window** still `INSUFFICIENT_EVIDENCE` |

## External prerequisites (explicit)

1. Production MoHCC SMTP relay.
2. Approved hardware authenticator AAGUIDs.
3. Apple Team ID (iOS distribution).
4. Android release signing fingerprint.

## Activation gate (not crossed)

Do not set workforce MFA enforcement or `IMPILO_BOOTSTRAP_REQUIRE_MFA=true` until:

- Constrained recovery is **deployed** and browser-proven with a controlled identity.
- SMTP and mail-capture ready for lost-device / execute-actions.
- Trust path (authz headers + PolicyEngine Step 0) consumes authentication assurance correctly on the deployed cohort (this checkpoint implements source; deploy not authorized).
