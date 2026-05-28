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

## Route maturity notes

- Doctrine journey pages on web (`/core-transaction`, `/client-journey`, `/provider-workspace`, `/platform-journey`) are now live-BFF only where backend feeds exist, with explicit loading/error/empty states and maturity labels.
- Mobile includes broad service surfaces, but parity depth is uneven across route families.
- Any new route must include journey, plane, BFF endpoint, trust context, and maturity declaration in parity docs.
