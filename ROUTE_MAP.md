# Route Map (Web + Mobile)

## Canonical route registries

- **Web:** `ui/one-ui-shell/src/lib/routes.ts`
- **Web route count invariant:** `EXPECTED_ROUTE_COUNT = 370`
- **Detailed surfacing cleanup:** `docs/frontend/ROUTE_SURFACING_CLEANUP.md`
- **Journey group definitions:** `ui/one-ui-shell/src/lib/ui-route-journey-map.ts`
- **Mobile navigation:** custom tab/mode state in app stores and tab components (not React Navigation stacks)

## Web route families by journey

### Person / Client

- `/`, `/home/*`, `/citizen/*`, `/wellness/*`, `/monitoring/*`
- `/telemedicine/*`, `/marketplace/*`, `/wallet`, `/scheduling`, `/discover/*`
- `/social`, `/communities`, `/groups`, `/pages`, `/nhume/track/*`
- `/consent`, `/privacy`, `/support/*`

### Provider

- `/provider-workspace`, `/provider/activate`
- `/shift/*`, `/queue/*`, `/ehr/[patientId]/*`, `/clinical*`, `/clinical-tools/*`
- `/lab/*`, `/pharmacy/*`, `/communication/secure-messaging`, `/learning`

### Platform / Back-of-house

- `/platform-journey`, `/operations/*`, `/reports/*`, `/intelligence/*`
- `/finance/*`, `/enterprise/*`, `/registry/*`, `/organization-admin`, `/admin/*`, `/developer/*`
- `/public-health/*`, `/nhume/*`, `/marketplace/apps/*`

### Cross-cutting

- `/core-transaction`, `/client-journey`, `/ask`, `/search`, `/auth/*`, `/settings`

`/platform-journey` now supports URL query filters for operator telemetry drill-down:

- `wfStatus` (workflow status)
- `wfType` (workflow type)
- `dpStatus` (dispatch status)

`/provider-workspace` now supports provider-operations telemetry filters:

- `pWfStatus` (provider workflow status)
- `pWfType` (provider workflow type)
- `pDpStatus` (provider dispatch status)

Operational drill-down pages also support URL query filters and focused-row context:

- `/operations/workflows`: `status`, `type`, `focus`
- `/operations/dispatch`: `status`, `focus`

Operational backend capability surfacing now includes:

- `/operations/workflows`: telemetry + workflow definitions + workflow instances + workflow start/transition commands
- `/operations/dispatch`: telemetry + dashboard/console/deliveries/fleet/couriers/missions + delivery-create, delivery-action, and task create/assign/complete commands
- `/registry`: registry hub cards + live identity operations for VITO/VARAPI search, patient resolve/register/recovery, and provider lookup/create commands
- `/coverage`: guided coverage tabs + live guided command console for eligibility, member enrollment, claim submission, preauth, and canonical appeal submit/review/decision
- `/finance/payer-ops`: unified payer workflow for coverage claims/remittance with intent-linked attempts, receipts, settlement state, and refund drill-downs under `/finance/settlements` and `/finance/refunds`

## Mobile route/screen map

## Citizen app (`apps/mobile/citizen-app`)

### Top tabs

- `home` -> `HomeScreen`
- `personal` -> `PersonalScreen`
- `social` -> `SocialHubScreen`
- `marketplace` -> `MarketplaceScreen`
- `messaging` -> `MessagingInboxScreen`
- `public_health` -> `PublicHealthScreen`
- `telehealth` -> `TelehealthListScreen` (registered and deep-linked, not shown in bottom bar)

### Personal workspace sections

profile, health-id, allergies, conditions, immunizations, referrals, care-plans, appointments, prescriptions, results, records, reminders, timeline, wellness, finance, challenges, programs, wallet, monitoring, queue, sos, coverage, consent, comms-prefs, support, settings, assessments, care-team, id-recovery, record-sharing, claim, verify, delegated-pickup, nhume-track, privacy, terms.

Provider discovery is now reachable in personal sections as `discover-providers` ("Find Provider").

### Global overlays

- `NompiloAssistantScreen`
- `NhumeTrackingScreen`

## Provider app (`apps/mobile/provider-app`)

### Mode router

- `provider`
- `outreach`
- `supervisor`
- `offline`
- `courier`

### Provider mode tabs

- `dashboard`, `patients`, `encounter` (when active encounter exists), `results`, `queue`, `messaging`, `social`, `tools`, `apps`, `professional`

### Clinical tools sub-tabs

soap, triage, telemedicine, drugs, orders, care, mar, cds, paging, barcode, workspaces, inpatient, facility, reports, finance, billing, pacs, schedule, pharmacy, lab, marketplace, admin, ops_reports, developer_hub, prof_settings, prof_channels, discharge, learning, core_transaction, workflow_dispatch, ph_field_tasks.

Encounter start is now explicitly journey-anchored: `/ehr/[patientId]/encounters` asks for the required PCT journey ID before calling `/internal/v1/encounters`, and provider mobile patient lookup requires the same journey anchor before calling `/internal/v1/mobile/provider/encounters`. Provider mobile queue management calls canonical `/internal/v1/queue/entries*`; the mobile encounter tab renders live triage capture in-place; and mobile vitals/triage calls use the typed mobile BFF payload shape.

`workflow_dispatch` ("Flow/Ops") now surfaces provider mobile workflow/dispatch feeds plus command controls for workflow start/transition, dispatch task create/assign/complete, and delivery actions.

`coverage` in citizen Personal and `finance` in provider tools now include payer-ops workspaces with claims/remittance/appeals/settlement reconciliation plus durable provisional coverage command queues.

`id-recovery` in citizen Personal now exposes live identity search, Health ID resolve, recovery start, and recovery verify against `/internal/v1/identity/*`.

`admin` ("Admin & Registry") in provider tools now exposes mobile identity operations plus facility lifecycle application controls, locality proposal review, registry intake/import, product registry search, ZIBO terminology resolve, trust policy/consent reads, and registry service health probes. The mobile screen groups those controls by registry family so operators are not left with one flat console. Direct facility identity create/update remains intentionally unsupported; facility changes go through `/internal/v1/facility-registry/*` lifecycle workflows.

Web registry product pages use `/internal/v1/product-registry/*`; terminology pages use `/internal/v1/registry/zibo/artifacts/resolve`; Mvumo registry administration uses typed `/internal/v1/mvumo-admin/*`; `/admin/federation` and `/admin/keys` are explicitly unavailable until matching typed BFF contracts are introduced.

## Provider / Clinical / Place integration wave (`integration/provider-clinical-place`)

> Status labels are taken from the consolidated gap register
> (`docs/audits/provider-clinical-place/consolidated-gap-register.md`). `Live` = lane code real
> and green per independent verification. `Partial`/`Missing` reflect the open GAP IDs and are NOT
> inflated. Policy enforcement of the new rules is **spec-only (GAP-6)** and rides the existing
> ext_authz — the per-route Trust column reflects header/context propagation, not new fine-grained
> rego. Patient-facing surfaces (GAP-8) and mobile parity (GAP-19) for these capabilities are
> **Missing** and are listed as such in the parity matrices.

### Backend + BFF routes (web/mobile → hook → BFF → service → contract → test)

| Capability | Web surface / hook | BFF route(s) | Service route(s) | Status |
|---|---|---|---|---|
| Encounter cadre decision | `AdaptiveEncounterCockpit.tsx` → `useCadreDecision.ts` | `POST /internal/v1/encounters/cadre-decision` | pct `POST /v1/cadre/decision`, `GET /v1/cadre/contract` | **Partial** — engine Live & green; two cadre authorities to unify (GAP-4); cadre-specific form *content* Partial (GAP-10) |
| Front-door sorting desk | (provider shell visit-type step) | — | pct `GET /v1/sorting-desk/visit-types`, `POST /v1/sorting-desk/journeys/{id}/sort`, `GET /v1/sorting-desk/journeys/{id}` | **Partial** — sort step Live; unifying `sorting_session` entity Missing (GAP-11) |
| Clinical problems list | EHR problems surface | — | pct `GET/POST /v1/problems`, `POST /v1/problems/{id}/resolve` | **Partial** — list Live; ICD-11/SNOMED readiness Missing (GAP-15) |
| OPD care plans | care-plan surface | — | pct `GET/POST /v1/care-plans`, `GET /v1/care-plans/{id}`, `PATCH .../status`, `POST .../goals`, `PATCH .../goals/{id}/status` | **Live** |
| Community context | (provider/outreach) | — | pct `GET /v1/community/households*`, `POST /v1/community/{households,visits,screenings,reconcile}` | **Live** (offline reconcile included) |
| Telemedicine completeness | telehealth surfaces | — | pct `POST /v1/referrals/{id}/respond-structured`, `.../route`; telehealth lifecycle | **Live** — structured response + routing pools + telemed→value |
| PCT↔inpatient admission handshake | n/a (server) | — | pct `POST /v1/journeys/{id}/admit`, `/admissions/{id}/{approve,admit,assign-bed}` | **Live** — idempotent, Kafka-redelivery safe |
| Facility Mode context/cockpit | `/facility/[id]/cockpit` → `useFacilityMode.ts` | `GET /internal/v1/facility-mode/{facilityId}/context` | tuso `GET /v1/internal/facility-mode/{facilityId}/context` | **Live** |
| Facility setup wizard | `/facility/[id]/setup` → `SetupWizard.tsx` | `GET .../{facilityId}/setup`, `POST .../setup/steps` | tuso `GET /v1/internal/facilities/{facilityId}/setup`, `POST .../setup/steps` | **Live** |
| Facility units / departments | `/facility/[id]/departments` | `GET/POST .../{facilityId}/units` | tuso `GET/POST /v1/internal/facilities/{facilityId}/units` | **Live** |
| Facility service-points | (setup) | `GET/POST .../{facilityId}/service-points` | tuso `GET/POST/DELETE /v1/internal/facilities/{facilityId}/service-points` | **Live** |
| Facility control tower | `/facility/[id]/control-tower` → `useControlTower.ts` | `GET .../control-tower/aggregate`, `.../{facilityId}/control-tower/{summary,alerts}`, `POST .../control-tower/alerts/{id}/acknowledge` | tuso `/v1/internal/control-tower/*` | **Live** |
| Facility↔regulator (multi-council) | `/facility/[id]/regulators` → `useRegulators.ts` | `GET/POST .../{facilityId}/regulators`, `PATCH .../regulators/{id}/status` | governance `/v1/internal/governance/facilities/{facilityId}/regulators*`, `/councils/{id}/facilities` | **Live** — status transitions validated |
| Indawo place mode / surveillance | `/indawo`, `/indawo/surveillance` → `usePlaceMode.ts` | `GET .../place-mode/summary`, `.../{alerts,outbreaks,cases,field-teams}`, `POST` create + status | indawo `/internal/v1/surveillance/*` | **Live** — status-transition + double-deploy guards |
| Indawo outbreaks / field-teams | `/indawo/outbreaks`, `/indawo/field-teams` | `POST .../outbreaks`, `.../field-teams/{teamId}/deploy`, `.../deployments/{id}/recall` | indawo surveillance routes | **Live** |
| Provider bootstrap chain | (activation flow) | — | varapi `POST /v1/internal/providers/bootstrap/{preload,claim/preview,claim}` | **Live** — self-claim token atomically single-use |
| Council / EC resolver | (resolution) | — | varapi `GET /v1/internal/providers/resolve-council-number` | **Live** |
| Silent identifier resolution | (auth-session) | `POST /internal/v1/auth/*`, `GET /internal/v1/identity/*` | tshepo-identity `POST /v1/identity/resolve-identifier` | **Partial** — Health/Impilo/Provider-ID/council Live; phone/email/invite deny-safe (GAP-5) |
| Work-context (WHERE/WHAT) | ContextRail → `useWorkContext.ts` | `GET /internal/v1/work-context` | vashandi `GET /v1/internal/vashandi/work-context` | **Live** (read-model) |
| Ad-hoc check-in | (attendance) | — | vashandi `POST /v1/internal/vashandi/attendance/adhoc-check-in` | **Live** |
| COSTA emergency reconciliation | (finance) | — | costa `GET/POST /costa/v1/emergency-reconciliation/deferred-charges*` (`link-person`, `reconcile`) | **Live** — dedup + outbreak-safe rollback |
| COSTA waiver CRUD | (finance) | — | costa `GET/POST /costa/v1/waivers`, `/{id}/{approve,reject,revoke}` | **Live** |
| Teleconsult → value | n/a (event) | — | costa charge on stable `referralId` (dual-emit CHARGE_CREATED) | **Live** — C8 leakage closed; MADI-blood signal still a known gap |
| Coverage subsidy + cap | coverage surface | — | coverage `POST /internal/v1/coverage/subsidies/enrolments`, `.../enrolments/{id}/consume`, `GET .../subsidies*` | **Live** — drawdown concurrency-safe + idempotent |

Policy ENFORCEMENT of the new cadre / facility-mode / provider-self-claim / work-requires-assignment /
self-treatment-block rules is **spec-only (GAP-6, CZO-locked)** — not authored as fine-grained rego in this
wave. The product-truth scanner correctly surfaces this as `security/crypto/authz placeholder in product
path` hits on pct-service and vashandi-workforce-service.

### New web routes (one-ui-shell)

- `/facility` (modified — entry list), `/facility/[id]/cockpit`, `/facility/[id]/setup`,
  `/facility/[id]/departments`, `/facility/[id]/regulators`, `/facility/[id]/control-tower` — **Live** web; **mobile parity Missing (GAP-19)**.
- `/indawo`, `/indawo/surveillance`, `/indawo/outbreaks`, `/indawo/field-teams` — **Live** web (place mode); **mobile parity Missing (GAP-19)**.
- Adaptive Encounter Cockpit renders from the cadre decision (component, surfaced inside the EHR encounter route) — **Partial** (form content GAP-10).
- **No patient-facing surfaces** were added for any stage of this journey — queue-status/check-in/orders-status/referral-status/inpatient-updates/outcome remain **Missing (GAP-8)**.

## Route maturity notes

- Doctrine journey pages on web (`/core-transaction`, `/client-journey`, `/provider-workspace`, `/platform-journey`) are now live-BFF only where backend feeds exist, with explicit loading/error/empty states and maturity labels.
- Mobile includes broad service surfaces, but parity depth is uneven across route families.
- Any new route must include journey, plane, BFF endpoint, trust context, and maturity declaration in parity docs.
