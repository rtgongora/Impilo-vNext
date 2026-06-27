# 05 — Current Implementation Audit

Every citizen-journey route/screen/API/policy: reachable? real backend? states? trust-aware? leaks?
mobile? a11y? tested? Verified by read, line-cited.

## Web routes (`ui/one-ui-shell/src`)

| Route | Reachable | Real backend | States (L/E/Empty) | Trust-aware | Leak risk | Tested | Notes |
|-------|-----------|--------------|--------------------|-------------|-----------|--------|-------|
| `/` | auth-gated→`/home` | n/a redirect | n/a | no | no | no | `app/page.tsx:4` |
| `/auth/login` | public | ✅ `useLogin` | ✅/✅/n/a | no | no | ✅ `page.test.tsx` | |
| `/auth/login/provider-id` | public | ✅ | ✅/✅/n/a | no | no | ❌ | |
| `/auth/login/biometric` | public | ⚠️ **simulated** | ✅/✅/n/a | no | no | ❌ | hardcoded creds `:42-81` |
| `/auth/register` | public | ✅ `/internal/v1/auth/register` | ✅/✅/✅ | no | no | ✅ | Health ID optional; readiness-gated |
| `/auth/register/assurance` | public | ✅ upgrade req | ✅/✅/✅ | ⚠️ | ⚠️ **DOB+nat-ID, no step-up** | ❌ | G-CZO-13 |
| `/citizen` (hub) | auth | static + banner | n/a | ✅ banner | no | ❌ | |
| `/citizen/health-id/request` | auth | ✅ `requestId()` | ✅/✅/✅ | no | no | ❌ | no resumable draft (G-CZO-09) |
| `/citizen/health-id/qr` | auth | ✅ `getHealthIdQr()` | ✅/✅/✅(not-registered) | partial | no (opaque token) | ❌ | |
| `/citizen/id-recovery` | auth | ✅ start/verify | ✅/✅/steps | ⚠️ 401→manual token | no | ❌ | step-up = plain text input, no challenge UI (G-CZO-04) |
| `/citizen/record-sharing` | auth | ✅ shares CRUD | ✅/✅/✅ | ✅ healthId gate | no | ❌ | no step-up on share (G-CZO-04) |
| `/citizen/delegated-pickup` | auth | ⚠️ create/redeem hit BFF stub | ✅/✅/✅ | ⚠️ manual token | OTP shown in UI | ❌ | BFF `stubDelegatedPickup` no persistence (G-CZO-03) |
| `/home` (+CitizenHome) | auth | ✅ many real queries | ✅/partial/✅ | ✅ role switch | no | ❌ | strong; untested |
| `/settings/privacy` | auth | ⚠️ revoke wired; status/history `[]` | ✅/✅/✅ | n/a | no | ❌ | consent UI exists but stub-backed (G-CZO-07) |
| `/consent` | public | ✅ accept (not persisted) | — | n/a | no | — | capture lost (G-CZO-07) |
| `/share/claim` | public | ✅ **uses step-up** `usePolicyDecision` | ✅/✅ | ✅ | claim-scoped | partial | **the reuse template for G-CZO-04** |

## Mobile (`apps/mobile/citizen-app/src/screens`)

| Screen | Real backend | States | Trust-aware | Tested | Notes |
|--------|--------------|--------|-------------|--------|-------|
| `LoginScreen` | ✅ Keycloak PKCE | ✅ | ⚠️ generic badge | ❌ | `assuranceLevel` claim not read |
| `auth/SignUpScreen` | ✅ register | ✅ | partial | ❌ | |
| `auth/AssuranceChoiceScreen` | ✅ upgrade/request | ✅ | ✅ tier | ❌ | |
| `HomeScreen` | ✅ appts/Rx/labs | ✅ empty states | ❌ **no banner** | ❌ | G-CZO-05 |
| `personal/ReferralsSection` | ✅ `/mobile/citizen/referrals` | ✅/✅/✅ | no | ❌ UI test | **wired, not a stub** |
| `personal/CarePlansSection` | ✅ `/mobile/citizen/care-plans` | ✅/✅/✅ | no | ❌ UI test | **wired, not a stub** |
| `personal/ImmunizationsSection` | ✅ `/mobile/citizen/immunizations` | ✅/✅/✅ | no | service test only | **wired, not a stub** |

**Mobile reconciliation verdict:** the clinical sections are FULLY WIRED to
`CitizenClinicalRecordsController` (`/immunizations`→Pct, `/care-plans`→Inpatient, `/referrals`→Pct;
identity from `X-Actor-ID`, server-side). **No duplicates** (grep found one copy of each). The brief's
"stub vs wired" concern is **stale** — only the assurance banner remains (G-CZO-05).

## Backend / policy

| Component | State | Evidence |
|-----------|-------|----------|
| Vito identity lifecycle + dedup | ✅ real | `IdentityStatus`, `MatchingEngine` (0.95/0.70 thresholds, demographic-only), `MatchController /v1/match/pending` |
| identity-assurance LOA + upgrade | ✅ real, persisted, dual-control | `AssuranceService:85-118`, V002 `assurance_record`+`assurance_upgrade_request` |
| Tshepo PolicyEngine | ✅ real ABAC + consent + risk + step-up | `PolicyEngine`, `AuthorizeController`, `AuditPublisher` |
| Step-up methods | TOTP ✅, SMS-OTP ✅(opt-in), biometric seam(fail-closed), supervisor ✅ | `StepUpMode`, `StepUpService`, `StepUpVerificationDispatcher`, `StepUpProvidersConfig` |
| **LOA→policy propagation** | ❌ **BROKEN** | `KeycloakAdapter:182-197` (ACR only); BFF never sets `X-Assurance-Level` from identity-assurance |
| Consent gating (clinical) | ✅ enforced fail-closed | `PolicyEngine:176-188`, `ConsentClient:51-89` |
| Consent capture (policy) | ⚠️ **not persisted** | `PolicyConsentController` accept/revoke/sms/ussd/operator log-only (BFF no datasource) |
| data-governance DSR/privacy prefs | ✅ persisted | `PrivacyRightsController` + `DataSubjectRequestService`/`PrivacyPreferenceService` |
| BFF `IdentityAssuranceController` | ✅ real proxy | `IdentityAssuranceServiceClient` → `/internal/v1/assurance/status` |
| BFF `CitizenClinicalRecordsController` | ✅ real | mobile immunizations/care-plans/referrals |
| Delegated/caregiver model | ❌ stub | `CitizenLongtailService.stubDelegatedPickup` no persistence (G-CZO-03) |

## Test coverage reality

- Web: only `/auth/login` and `/auth/register` have `page.test.tsx`. All 6 `/citizen/*` routes + `/home`
  are **untested** (~14% of citizen routes).
- Mobile: service-layer test for clinical records; **no screen/component tests**.
- Backend: `PolicyEngineTest`, `AssuranceServiceTest` exist but **no end-to-end LOA-propagation test**
  (the exact thing G-CZO-01 needs as proof).
