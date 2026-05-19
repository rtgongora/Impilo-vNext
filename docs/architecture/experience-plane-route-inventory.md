# Experience Plane Route Inventory

Date: 2026-05-14

## Inventory Scope

- Frontend routes:
  - `ui/one-ui-shell/src/app/**/page.tsx`
  - `ui/experience/src/app/**/page.tsx`
- BFF routes:
  - `services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/**/*Controller.java`

## Route Inventory Summary

- `ui/one-ui-shell` page routes discovered: **303**
- `ui/experience` page routes discovered: **301**
- `experience-bff` controller classes discovered: **186**

## BFF Route Groups (by canonical prefix)

- `/internal/v1/auth/*`
- `/internal/v1/profile/*`
- `/internal/v1/workspaces/*`
- `/internal/v1/facilities*`
- `/internal/v1/registry*`
- `/internal/v1/registry-intake/*`
- `/internal/v1/mobile/provider/*`
- `/internal/v1/mobile/citizen/*`
- `/internal/v1/clinical*`
- `/internal/v1/clinical-tools/*`
- `/internal/v1/queue/*`
- `/internal/v1/finance/*`
- `/internal/v1/product-registry/*`
- `/internal/v1/commerce/*`
- `/internal/v1/workflows/*`
- `/internal/v1/reports/*`
- `/internal/v1/public-health/*`
- `/internal/v1/integration-hub/*`
- `/internal/v1/admin/*`

## Frontend Route Groups (shells)

- Auth and onboarding (`/auth/*`, `/consent`, `/account-deletion`)
- Registry and intake (`/registry/*`, `/registry-admin`, `/registry/intake`)
- Clinical and chart routes (`/ehr/*`, `/clinical*`, `/ask`)
- Operations/workflow (`/queue/*`, `/shift/*`, `/workspace/*`, `/facility-operations/*`)
- Finance/enterprise (`/finance/*`, `/marketplace/*`, `/erp/*`, `/wallet/*`, `/coverage/*`)
- Public health/support/developer/admin (`/public-health/*`, `/support/*`, `/developer`, `/admin/*`)

## High-Risk Route Findings In This Pass

- Hardened fail-close:
  - `GET /internal/v1/identity/providers`
  - `GET /internal/v1/staffing/roster-week`
  - `GET /internal/v1/staffing/on-call`
  - `GET /internal/v1/staffing/on-call/swaps`
  - `POST /internal/v1/staffing/on-call/swaps`
  - `PATCH /internal/v1/staffing/on-call/swaps/{id}`
  - `GET /internal/v1/mobile/provider/notices`
  - `GET /internal/v1/mobile/provider/reports`
  - `GET /internal/v1/mobile/provider/reports/{reportId}/data`
  - `GET /internal/v1/governance/access/policies`
  - `GET /internal/v1/governance/access/requests`
  - `GET /internal/v1/access/landela/templates`
  - `GET /internal/v1/access/landela/documents/search`
  - `GET /internal/v1/access/notifications/recent`
  - `GET /internal/v1/omnichannel/callbacks`
  - `GET /internal/v1/omnichannel/channels`
  - `GET /internal/v1/public-health/*` (proxy-backed reads)
  - `POST /internal/v1/public-health/*` (proxy-backed writes)
  - `GET /internal/v1/notifications`
  - `PATCH /internal/v1/notifications/{id}/read`
  - `GET /internal/v1/notifications/preferences`
  - `GET /internal/v1/finance/billing`
  - `GET /internal/v1/finance/billing/{id}/payments`
  - `GET /internal/v1/finance/billing/{id}/refunds`
  - `GET /internal/v1/finance/tariffs`
  - `GET /internal/v1/finance/payments`
  - `GET /internal/v1/finance/claims`
  - `GET /internal/v1/coverage/plans`
  - `GET /internal/v1/coverage/member/{clientId}`
  - `GET /internal/v1/coverage/eligibility`
  - `GET /internal/v1/coverage/claims`
  - `GET /internal/v1/coverage/contributions`
  - `GET /internal/v1/coverage/preauths`
  - `GET /internal/v1/coverage/utilization`
  - `GET /internal/v1/coverage/appeals`
  - `GET /internal/v1/coverage/remittances`
  - `GET /internal/v1/coverage/members`
  - `GET /internal/v1/integration-hub/routes`
  - `GET /internal/v1/integration-hub/deadletters`
  - `GET /internal/v1/integration-hub/mapping-templates`
  - `GET /internal/v1/mobile/provider/labs/results`
  - `GET /internal/v1/mobile/provider/labs`
  - `GET /internal/v1/mobile/provider/labs/{id}`
  - `POST /internal/v1/mobile/provider/labs/{id}/cancel`
  - `GET /internal/v1/mobile/provider/schedule`
  - `GET /internal/v1/mobile/provider/telemedicine/sessions`
  - `POST /internal/v1/mobile/provider/telemedicine/sessions`
  - `POST /internal/v1/mobile/provider/telemedicine/sessions/{id}/join`
  - `POST /internal/v1/mobile/provider/telemedicine/sessions/{id}/end`
  - `POST /internal/v1/mobile/provider/prescriptions` (explicit `501` not implemented)
  - `POST /internal/v1/mobile/provider/prescriptions/{id}/cancel` (explicit `501` not implemented)
  - `GET /internal/v1/pharmacy/prescriptions` (fail-close on upstream errors; `400` when `patient_id` missing)
  - `POST /internal/v1/pharmacy/prescriptions` (explicit `501` not implemented pending backend)
  - `POST /internal/v1/pharmacy/prescriptions/{id}/cancel` (explicit `501` not implemented pending backend)
  - `GET /internal/v1/communication/announcements`
  - `GET /internal/v1/communication/pages`
  - `GET /internal/v1/communication/messages/channels`
  - `GET /internal/v1/search`
  - `GET /internal/v1/search/documents/{documentId}`
  - `POST /internal/v1/guidance/ask`
  - `GET /internal/v1/guidance/reminders`
  - `GET /internal/v1/fhir/metadata`
  - `GET /internal/v1/fhir/{resourceType}`
  - `GET /internal/v1/fhir/{resourceType}/{id}`

## Notes

- This document is the authoritative route inventory baseline for Experience closure.
- Public-health disabled/prototype sub-tabs now intentionally render unavailable status instead of fixture-backed demo rows.
- Prescription write/cancel remains intentionally blocked with explicit `501` envelopes until pharmacy-service exposes canonical prescription write/cancel APIs.
- Remaining per-route convergence and mock/fallback classification is tracked in:
  - `docs/architecture/experience-mock-and-demo-data-audit.md`
  - `docs/registry/mock-and-stub-register.md`
  - `docs/registry/gap-remediation-plan.md`
