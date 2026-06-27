# 01 — Citizen Zero-to-One Journey Map

Discovery → access. Each step lists the intended experience, what is BUILT, and the gap.
Line-cited from the worktree `/opt/impilo/repos/impilo-czo`.

## The journey, end to end

```
[0] DISCOVER ──▶ [1] ARRIVE ──▶ [2] SIGN UP ──▶ [3] REQUEST ──▶ [4] TEMP ID ──▶ [5] VERIFY ──▶ [6] VERIFIED ──▶ [7] HIGH-ASSURANCE
 (public web)   (landing)     (no Health ID)  (Health ID)    (receive care)  (upgrade)      (My Health)      (download/share/delegate)
```

| Step | Intended | BUILT? | Evidence / Gap |
|------|----------|--------|----------------|
| **0. Discover** | Find Impilo via web/search; learn what it is; find a facility/service; emergency + public-health info — all WITHOUT login | ❌ **GAP (Blocking)** | No public landing. `app/page.tsx:4` `redirect("/home")`; `middleware.ts:12-24` public set has no `/` or discovery route. |
| **1. Arrive** | Public L0 home: get-started, sign in / create account / request Health ID, language + accessibility options | ❌ **GAP (Blocking)** | Same as above. The only public surfaces are `/auth`, `/verify`, `/share`, legal pages. |
| **2. Sign up (no Health ID)** | Create an account without already having a Health ID | ✅ **BUILT** | `app/auth/register/page.tsx:86-137` POST `/internal/v1/auth/register`; Health ID field optional (`page.tsx:250-294`). Readiness-gated (`useRegistrationReadiness`). Mobile: `auth/SignUpScreen.tsx:44-62`. |
| **2b. Choose assurance** | Pick BASIC / TEMPORARY / FULL path | ⚠️ **BUILT, leaky** | `app/auth/register/assurance/page.tsx:53-62`. TEMPORARY tier collects DOB + national ID with **no step-up** (`page.tsx:172-208`) — flagged (G-CZO-13). |
| **3. Request Health ID** | Submit identity request; dedup initiated | ✅ **BUILT** | `app/citizen/health-id/request/page.tsx:40-53` POST via `citizenPortalApi.requestId()`. Vito lifecycle `IdentityStatus.DRAFT/PROVISIONAL/PENDING_*`. Dedup `MatchingEngine` (0.95 auto / 0.70–0.94 operator queue). |
| **4. Temporary Health ID** | Receive care, show temp ID/QR, book selected services; NO sensitive records | ⚠️ **PARTIAL** | QR built (`app/citizen/health-id/qr/page.tsx:22-43`). But policy does not actually distinguish temp-vs-verified at the gate because of the **LOA propagation break** (see [02](02-trust-ladder.md), G-CZO-01). |
| **5. Verify / upgrade** | Registry match / facility / trusted-doc / OTP / assisted review | ⚠️ **PARTIAL** | identity-assurance upgrade workflow real & persisted (`AssuranceService.decideUpgrade`, dual-control, V002). **But the upgrade does not change what policy sees** (G-CZO-01). |
| **6. Verified — My Health** | Health summary, appointments, selected results, prescriptions, referrals, care plans per policy | ✅ **BUILT (web+mobile)** | `app/home` CitizenHome; mobile `ReferralsSection`/`CarePlansSection`/`ImmunizationsSection` wired to `CitizenClinicalRecordsController`. Gated by policy *if* G-CZO-01 fixed. |
| **7. High-assurance actions** | Download/share records, manage trusted contacts/delegates, sensitive results, high-risk account settings — require step-up | ❌ **GAP (High)** | Backend enforces `STEP_UP_REQUIRED` (401+methods) but citizen UI gives no challenge prompt (G-CZO-04). Delegation not built (G-CZO-03). |

## Failure / edge paths in the journey

| Path | Intended | BUILT? | Evidence / Gap |
|------|----------|--------|----------------|
| Possible duplicate on request | Safe confirmation or assisted review; never reveal clinical detail | ✅ | `MatchingEngine` PENDING→`/v1/match/pending` operator queue; `MatchDisposition`. Identity-safe (demographic fields only). |
| Lose connection mid-request | Resume without losing progress | ❌ **GAP (Med)** | No resumable-form / draft persistence found in `app/citizen/health-id/request`. |
| Disability / low literacy | Complete request with a11y options, no insecure workaround | ⚠️ **PARTIAL** | High-contrast tokens exist but not user-exposed; partial ARIA. See [08](08-accessibility-audit.md). |
| No smartphone | Facility-assisted onboarding | ⚠️ **PARTIAL** | `ClientVerificationState.PROVIDER_CAPTURED` + `/kiosk` public path exist; no audited end-to-end assisted-onboarding journey surfaced. |
| Suspicious login | Only low-risk actions or step-up | ⚠️ **PARTIAL** | PolicyEngine risk scoring + step-up trigger exist (`PolicyEngine:193-199`); no UI to complete the challenge (G-CZO-04). |

## Provider-who-is-also-citizen separation (Persona H)

`app/home/page.tsx:506-525,811-827` switches **Work / My Professional / My Life** on
`identity.hasWorkAccess` / `isCitizenOnly` / `hasProfessionalAccess`. Tshepo resolves
person/provider/facility/role/workspace per request. Context separation is BUILT at the shell level;
the audit confirms no provider permission leaks into citizen record reads in the home composition
(role-gated components only render for the active role). This is the one cross-cutting journey that
is in good shape — preserve it.
