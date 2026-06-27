# 03 — Login Assurance Matrix

Login method → risk → trust level → access outcome. "Many doors are allowed, but not every door opens
every room" — the BFF/trust-plane/policy layer shapes what's available after login.

## Login methods (exist or supportable)

| Method | BUILT? | Evidence | Resulting session assurance |
|--------|--------|----------|------------------------------|
| Username/email + password (+OTP) | ✅ web | `app/auth/login/page.tsx:54` `useLogin()` | ACR-derived `loaLevel` (see caveat below) |
| Provider ID + PIN | ✅ web | `app/auth/login/provider-id/page.tsx:47-84` | provider context, not citizen |
| Device biometric | ⚠️ **simulated** | `app/auth/login/biometric/page.tsx:42-81` is `setTimeout` + hardcoded `biometric@impilo.local` | not real |
| Keycloak PKCE (mobile) | ✅ mobile | `mobile-auth/keycloakClient.ts:49-71`, `LoginScreen.tsx:58-91` | token; **assuranceLevel claim not read** (`authStore.ts:115-132`) |
| Health ID + OTP | ⚠️ supportable | recovery flow uses Impilo ID + step-up token; no dedicated Health-ID+OTP login screen | — |
| Phone OTP / Email OTP | ⚠️ supportable | SMS-OTP step-up adapter exists (`NotificationOtpDeliveryAdapter`); not wired as a primary login | — |
| Passkey | ❌ | no WebAuthn registration/assertion flow found in `one-ui-shell` | — |
| Device-bound returning session | ⚠️ partial | mobile token rotation (`tokenManager.ts`); no device-binding attestation surfaced | — |
| QR/token from facility visit | ✅ verify path | `/verify`, `/share/claim` public flows | claim-scoped |
| Recovery via trusted contact / facility proofing | ⚠️ partial | `app/citizen/id-recovery/page.tsx:18-52` start/verify with step-up token; trusted-contact graph not built | — |
| Provider-mediated identity confirmation | ✅ | `ClientVerificationState.PROVIDER_CAPTURED` | provider-captured |
| Offline / low-connectivity continuation | ❌ | no resumable/offline auth path (G-CZO-09) | — |

## The assurance caveat that breaks the matrix (G-CZO-01)

Session `loaLevel` is extracted **once**, at token validation, from the Keycloak `acr` claim
(`KeycloakAdapter.extractLoaLevel:182-197`) and is **immutable** for the token's life. PolicyEngine
compares policy `min_loa` against this frozen value (`PolicyEngine:380-387`). A separate
`account_assurance_required` check reads the `X-Assurance-Level` **string** header
(`PolicyEngine:436-443`) — but nothing populates that header from identity-assurance. So **login method
sets the floor; a later verification upgrade cannot lift it** until G-CZO-01 is fixed.

## Risk → outcome (PolicyEngine, verified)

`PolicyEngine` step order (`core/PolicyEngine.java`): RBAC/ABAC → consent (clinical resources, fail-closed
`ConsentClient`) → risk scoring → step-up trigger. Step-up fires when `riskScore ≥ stepUpTrigger` (default 61)
**and** the action is sensitive (`DELETE, EXPORT, BULK, MERGE, RECOVERY`) and no recent step-up exists
(`PolicyEngine:193-199`), or for `BREAK_GLASS` purpose (`:230`). Response: **HTTP 401**, body
`{verdict:"STEP_UP_REQUIRED", errorCode, stepUpMethods:[...], riskScore}` (`AuthorizeController:186-190`,
`AuthzResponse.stepUp`). Default methods `["MFA","BIOMETRIC","SUPERVISOR_APPROVAL"]` (`AuthzProperties`).

## Method → trust → access outcome

| Session trust | Low-risk read (dashboard, profile, book) | Clinical read (results, Rx) | Sensitive action (download/share, delegate, high-risk setting) |
|---------------|------------------------------------------|------------------------------|-----------------------------------------------------------------|
| Account-only (LOA1) | ✅ allow | ❌ deny (min_loa) | ❌ deny |
| Temporary (LOA2) | ✅ allow + book selected | ❌ deny (must stay below sensitive ceiling) | ❌ deny |
| Verified (LOA3) | ✅ allow | ✅ allow per consent | ⚠️ **step-up required** (401) — UI gap G-CZO-04 |
| High-assurance (LOA4 / stepped-up) | ✅ | ✅ | ✅ allow (after challenge) |
| Suspicious/high-risk | reduced; step-up | step-up | step-up or deny |

**The matrix is correct in policy and broken in propagation+UI:** the rows above are what *should*
happen; today every authenticated session is effectively pinned to its ACR `loaLevel`, and the step-up
rows have no UI to complete the challenge.
