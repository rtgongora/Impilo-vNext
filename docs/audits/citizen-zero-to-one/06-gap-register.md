# 06 — Citizen Zero-to-One Gap Register

Classified: **Blocking** (journey cannot work) / **High** / **Medium** / **Cosmetic** / **Future**.
Each row cites the disproving/confirming evidence. Rows are also appended to
`docs/audits/product-truth-full-gap-register.md`.

| ID | Sev | Title | Evidence (verified) | Fix locus |
|----|-----|-------|---------------------|-----------|
| **G-CZO-01** | 🔴 Blocking | LOA upgrade does not reach policy | `KeycloakAdapter.extractLoaLevel:182-197` (ACR only, immutable); `PolicyEngine:380-387` uses `loaLevel`; `:436-443` uses `assuranceLevel` string; BFF `ServiceClientConfig:273-275` forwards `X-Assurance-Level` but **no code sets it from identity-assurance `current_level`** | BFF populates `X-Assurance-Level` from identity-assurance + PolicyEngine reads it as effective LOA (ACR fallback) |
| **G-CZO-02** | 🔴 Blocking | No public L0 entry | `app/page.tsx:4` redirect; `middleware.ts:12-24` public set = `/auth /kiosk /verify /share /privacy /terms /consent /account-deletion` only | New public landing + middleware paths |
| **G-CZO-03** | 🟠 High | L5 delegated/caregiver not built | only role constants + `CitizenLongtailService.stubDelegatedPickup` (no persistence/authz/audit/scope/expiry); no "acting for X" banner | **Design + SoR question → STOP for PO** |
| **G-CZO-04** | 🟠 High | No step-up UI on sensitive citizen actions | backend returns 401+`stepUpMethods` (`AuthorizeController:186-190`); citizen routes show plain token inputs or silent fail; `usePolicyDecision` + `/share/claim` already implement the pattern but citizen routes don't use it | Reuse `usePolicyDecision` + a `StepUpChallenge` component |
| **G-CZO-05** | 🟠 High→Med | Mobile dashboard has no assurance banner | `HomeScreen.tsx` no trust display; `authStore.ts:115-132` never reads `assuranceLevel`; `mobile-trust/types.ts:66` defines it unused | Add mobile banner + read assurance post-login |
| **G-CZO-06** | 🟡 Med | Policy consent capture not persisted | `PolicyConsentController` accept/revoke/sms/ussd/operator are log-only (BFF has no datasource); `/status`,`/history` return `[]` | Route capture to a sovereign service (NOT the BFF) |
| **G-CZO-07** | 🟡 Med | No real citizen consent history/revoke feedback | `/settings/privacy` revoke wired but `usePolicyConsentStatus/History` resolve to `[]` stubs | Real status/history once G-CZO-06 persists |
| **G-CZO-08** | 🟡 Med | High-contrast / a11y not user-exposed | contrast tokens in CSS but no toggle UI; partial ARIA; Shona/Ndebele design-ready only | Expose a11y settings panel |
| **G-CZO-09** | 🟡 Med | No resumable / offline form continuation | no draft persistence in `health-id/request`; no offline auth path | Local draft + resume |
| **G-CZO-10** | 🟡 Med | No low-data mode | no text-first/deferred-image mode found | Low-data toggle |
| **G-CZO-11** | 🟡 Med | No SMS fallback for primary auth | SMS-OTP exists only as step-up adapter, not a login door | Wire phone-OTP login |
| **G-CZO-12** | ⚪ Cosmetic | No LOA4 banner state | banner tops out at `FULLY_VERIFIED` (renders null); LOA4 has no distinct UI | Add LOA4/high-assurance state |
| **G-CZO-13** | 🟠 High | TEMPORARY assurance tier collects DOB+national-ID with no verification/step-up | `app/auth/register/assurance/page.tsx:172-208` | Gate sensitive capture behind verification |
| **G-CZO-14** | ⚪ Cosmetic | Biometric web login is simulated | `app/auth/login/biometric/page.tsx:42-81` hardcoded creds | Replace with real WebAuthn or hide |
| **G-CZO-15** | 🔵 Future | Vito↔identity-assurance level sync | Vito `ClientEntity.identityAssuranceLevel` int vs canonical `AssuranceLevel`; no raise-hook | Sync hook (post-propagation) |
| **G-CZO-16** | 🟡 Med | Citizen routes untested | only login+register have tests; 6 `/citizen/*` + `/home` untested; no LOA-propagation e2e | Add RTL/MockMvc per slice |

## Severity roll-up

- **Blocking: 2** (G-CZO-01, G-CZO-02) — the journey does not actually distinguish trust levels or admit
  a guest without these.
- **High: 4** (G-CZO-03 design-only, G-CZO-04, G-CZO-05, G-CZO-13).
- **Medium: 7** (G-CZO-06/07/08/09/10/11/16).
- **Cosmetic: 2** (G-CZO-12, G-CZO-14). **Future: 1** (G-CZO-15).

## Sequencing rule

Fix **G-CZO-01 first** — it is the keystone. Until policy sees the real assurance level, the temporary-vs-
verified boundary (the heart of the mission) is unenforced and every dashboard/record claim is theatre.
Then G-CZO-02 (admit the guest), then the High items. G-CZO-03 stops for PO before any build.
