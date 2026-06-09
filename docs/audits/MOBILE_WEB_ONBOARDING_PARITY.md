# Mobile ↔ Web onboarding parity

> 2026-06-07 — citizen signup journey.

## Web (canonical for this batch)

| Step | Route | BFF |
|------|-------|-----|
| Readiness | `/auth/register` | `GET /internal/v1/auth/register/readiness` |
| Sign-up | `/auth/register` | `POST /internal/v1/auth/register` |
| Assurance | `/auth/register/assurance` | `POST /internal/v1/identity/assurance/upgrade/request` |
| Consent | `/consent` | policy consent mutations |
| Status | `/auth/register/status` | auth store + orchestration rail |
| Home | `/home` | facility/work context |

E2E proof: `ui/one-ui-shell/e2e/citizen-signup-flow.spec.ts`

## Mobile (citizen-app)

| Step | Status | Remediation |
|------|--------|-------------|
| Account creation | **Live** | `SignUpScreen` + `citizenRegistrationService` → readiness + register BFF |
| Assurance tier choice | **Live** | `AssuranceChoiceScreen` → `POST /internal/v1/identity/assurance/upgrade/request` |
| Citizen sign-up | **Live** | `SignUpScreen` → `POST /internal/v1/auth/register` + `establishFromTokenResponse` |
| Post-signup status | **Partial** | `HealthIdSection` exists; full onboarding rail not mirrored |

Operator finance (MusheX/COSTA) remains **web-first** by design — not a parity gap for this batch.
