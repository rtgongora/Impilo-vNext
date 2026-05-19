# Facility operations — Experience layer surfacing audit

**Branch context:** `claude/staging-ux-orchestration-remediation-Yypyl`  
**Primary UI:** `ui/experience` (One UI navigation via `ExperienceSidebar` + `AppLayout`)  
**Authoritative catalog:** `docs/plan/SERVICE_CATALOG.md`

## Executive summary

vNext already contains **substantial backend** capability for queues (PCT), appointments (BFF façade toward scheduling/Tuso concepts), staffing/shifts, and inpatient bed reads. The main gap was a **coherent operational command surface** in the Experience app: modules existed (`/queue`, `/scheduling`, `/clinical/control-tower`, `/shift`) but were not presented as a single **facility command layer**.

This audit adds:

- **`/facility-operations`** — role-filtered hub linking queues, control tower, scheduling, rosters, beds, patient flow, tasks, alerts, and reports.
- **`/facility-operations/patient-flow`** — facility-scoped **patient flow board** built only from **live** queue queries + queue stats + optional appointment counts (empty states when APIs return nothing).

**Follow-up implemented (this iteration):** `scheduling-service` **MVP module** (slot templates + in-memory holds), **district/national aggregate** UI via `POST /internal/v1/operations/facility-queue-snapshots`, **PCT-accurate queue stats** aggregation in the BFF, **transfer → PCT `transferQueueItem`**, **LWBS/abandon → `LEFT`**, **BFF-local audit trail** for queue mutations, **Tuso resource board** route, and **queue-type journey lanes** on the patient flow board. Deep analytics warehouse roll-ups and PCT-native audit persistence remain future work.

---

## 1. Backend services (minimum inspection)

| Service | Path | Catalog status | Experience touchpoint |
|---------|------|----------------|------------------------|
| pct-service | `services/pct-service` | LIVE | BFF `/internal/v1/queue/**`, `/internal/v1/timeline`, encounters |
| tuso-service | `services/tuso-service` | SKELETON | Gateway `/api/v1/facilities`, `/workspaces`, `/resources`; booking IDs on appointments |
| varapi-service | `services/varapi-service` | SKELETON | Gateway `/api/v1/providers`; staffing roster rows |
| scheduling-service | `services/scheduling-service` | **NEW** | BFF `/internal/v1/appointments*` (facade) |
| inpatient / ubomi | `services/ubomi-service` | SKELETON | BFF `/internal/v1/beds*` |
| notification-service | `services/notification-service` | LIVE | BFF `/internal/v1/notifications/**` |
| rules-service | `services/rules-service` | LIVE | Enforcement primarily server-side; `/admin/policies` for ABAC |
| integration-hub | `services/integration-hub` | LIVE | Not directly surfaced in ops hub UI |
| vito-service | `services/vito-service` | LIVE | MPI / patient pickers |
| oros-service | `services/oros-service` | LIVE | Orders/results; linked from queue workflows |
| mushex / costing | mushex + costing-engine | LIVE | Enterprise plane (finance domain); optional platform-ops constraints |

---

## 2. Frontend surfaces audited

| Package | Role today | Navigation visibility |
|---------|------------|------------------------|
| `ui/experience` | **Primary** operational UX | Work zone: Queue, Scheduling; Facility ops category on Home; **new** `/facility-operations` |
| `ui/one-ui-shell` | Shell / lighter home | Separate app — link Experience for deep ops |
| `ui/ehr`, `ui/pct-web`, `ui/shared-ui`, consoles | Specialized / legacy | Out of scope for this Experience change; referenced for parity |

---

## 3. Capability-by-capability notes

### Facility Control Tower

- **Route:** `/clinical/control-tower`
- **Data:** `useFacilityWards`, `useFacilityBeds`, `useFacilityQueueWaiting`, `useFacilityQueueStats`, `useFacilityActiveShiftCount` (`useFacilityOperations.ts`).
- **Mocks:** None — empty states when facility not selected or APIs return `[]`.
- **Gap:** No-shows, telemedicine counts, and “expected appointments today” should be merged into the same page once APIs are stable.

### Queues

- **Routes:** `/queue`, `/queue/waiting`, `/queue/triage`, `/queue/scheduled`, `/queue/walk-in`, `/queue/search`, `/queue/incoming-referrals`.
- **API:** BFF queue controller proxies PCT; local fallback list in dev.
- **Gap:** Full transition set (hold, transfer, abandoned, emergency override) needs UI parity with PCT contract.

### Appointments & scheduling

- **Route:** `/scheduling` (+ roster/on-call subroutes).
- **API:** `/internal/v1/appointments*`, resources + availability endpoints.
- **Gap:** Recurrence, automated reminders through `notification-service`, strict structured programme/room fields.

### Workforce & rosters

- **Routes:** `/shift`, `/shift/active`, `/shift/handover`, `/scheduling/roster`, `/scheduling/on-call`.
- **Gap:** Service-point coverage matrix; district staffing aggregates.

### Beds & rooms

- **Data:** `/internal/v1/beds`, `/internal/v1/beds/wards` consumed by control tower.
- **Gap:** Dedicated **resource board** (rooms, theatres) tied to Tuso resource calendar.

### Patient flow board

- **Route:** `/facility-operations/patient-flow`
- **Data:** `useQueueEntries` for `WAITING`, `CALLED`, `IN_PROGRESS` + `useFacilityQueueStats` + appointments list for **today** (same filter logic as scheduling page). No invented counts.

### Tasks & handover

- **Tasks:** `/shell/task-manager` (shell) — linked from hub; unified BFF task list for facility ops still **missing**.
- **Handover:** `/shift/handover` — existing clinical ops path.

### Operational alerts

- Derived alerts in `buildOperationalAlerts` use **real** queue/bed snapshots only.

---

## 4. Role model (Experience)

Uses `useRoleGroup()` (`QUEUE`, `CLINICAL`, `ADMIN`, …). The facility hub shows:

- **Queue / clinical / dispenser:** full operational card set.
- **Finance:** reporting + coverage shortcuts where relevant (not patient queue write).
- **Admin:** includes link to legacy **`/operations`** platform ops (Vito/Butano/assets).

District/national personas: **not yet** modeled in `useRoleGroup`; aggregate dashboards documented as **gap** in traceability matrix.

---

## 5. Fake data hunt (operations)

Automated search targets: `mock`, `fake`, `sample`, `placeholder`, `TODO`, `coming soon`, `hardcoded` in `ui/experience/src/app/queue`, `scheduling`, `clinical/control-tower`, `shift`, `facility-operations`.

**Rule:** operational KPI tiles must either bind to API responses or show **em dashes / zero with explanatory footnotes** (control tower already follows this pattern). Patient flow board uses the same rule.

---

## 6. Tests & build

After changes, run from `ui/experience`:

`npm run lint` · `npm run type-check` · `npm test` · `npm run build`

Record failures in PR notes; fix before merge.

---

## 7. Follow-up (prioritised)

1. Single BFF aggregate endpoint for **control tower** (replace parallel ward/bed/queue client calls).
2. **PCT-persisted audit** for queue items (replace BFF in-memory deque); wire Tshepo audit export.
3. **Wire BFF** `SchedulingController` availability to `scheduling-service` HTTP client when slot engine should own capacity math.
4. **Tuso numeric ↔ Experience UUID** alignment for resources and facility registry rows.
5. **Warehouse / NDR** aggregate dashboards for national KPIs beyond live PCT snapshots.
