# Nhume Dispatch & Movement Orchestration — completion wave report

Date: 2026-07-10 · Branch: `claude/staging-ux-orchestration-remediation-Yypyl`
Directive: complete Nhume into a practical dispatch/movement workspace — not a thin ambulance
screen — that moves patients, teams, ambulances, commodities, medicines, vaccines, specimens,
blood, equipment, outreach teams and documents, with real capture, persistence, state
progression, audit and logical outcomes. No deploy; no Ndila/PMTiles changes; minimise collisions
on a shared branch.

## 1. The audit reframe (three agents, verified)

Nhume was **already a comprehensive multi-cargo delivery-orchestration platform**, backend and
frontend, wired end-to-end — not the ambulance-only demo the brief anticipated:

- **Backend** (`nhume-service`, port 8210): 26+ tables, `DeliveryType` covering all 12 mission
  categories (medicine, lab-sample, vaccine, cold-chain, blood, equipment, facility-transfer,
  document, CHW, programme-commodity, emergency-supply, general…), a 39-state machine, 18
  transport modes, entities for assignments/tracking/proofs/chain-of-custody/exceptions/fleet/
  couriers/zones/autonomous-missions, a v1.1 outbox with 30 event types, BFF wiring
  (`NhumeServiceClient` + web/provider/citizen controllers), TrustLayerGuard authz, and seed data.
- **Frontend** (`ui/one-ui-shell`): 17 Nhume routes all fetching real data; full lifecycle
  (create→submit→approve→assign→accept→pickup→transit→deliver→fail→return) as real mutations;
  dispatcher console with real assignment; chain-of-custody, tracking, proof capture, analytics,
  autonomous missions; Ndila-backed maps; **mobile provider-app courier parity** (accept/pickup/
  proof/custody/fail).

So the completion work was a **focused set of verified gaps**, not a rebuild. Nothing was renamed
(existing route conventions kept: `/nhume/deliveries`, `/nhume/dispatcher`, `/nhume/fleet`,
`/nhume/couriers`).

## 2. What this wave delivered (all committed + pushed, tests green)

| Slice | Gap → fix | Evidence |
|---|---|---|
| **NA** | **Dead buttons**: `/nhume/fleet` & `/nhume/couriers` "Add" linked to `?create=1` with no form (the exact "opens but doesn't save" P0). The `useCreateFleetAsset`/`useCreateCourier` hooks + backend POSTs existed but were never invoked. Added wired create panels — fleet captures type/mode/**capabilities** (cold-chain, specimen, hazmat, GPS) so cargo is matched to a suitable resource, not just ambulances; courier captures kind/contact/Varapi ref/verification. | `CreatePanels.test.tsx` 3/3 (save + validation) |
| **NB** | **Not cargo-aware**: one generic (patient-shaped) create form. Added a "What are you moving?" selector with **10 cargo profiles** (patient, specimen, blood, vaccine, medicine, commodity, equipment, outreach team, documents, general), each pinning the right delivery type, handling defaults and type-specific fields. A specimen/commodity mission is no longer forced through a patient form; blood/vaccine capture group/units/batch/temperature. Maps onto the **existing** `CreateDeliveryRequest` (typed flags + `clinical_context_ref` + `metadata`) — zero schema change. | `cargo-profiles.test.ts` 6/6; `new-delivery.test.tsx` proves a blood mission persists cold-chain+custody+MADI link |
| **NC** | **No receiving end**: added `/nhume/inbound` (Journey 7). Shows movements en route to a destination and confirms handover with a cargo-aware label (lab receives specimen, store receives stock, clinician receives patient, cold-chain focal receives vaccines). Handover **records proof-of-delivery** — an auditable event, not UI-only state. | `inbound.test.tsx` 2/2 (records proof) |
| **ND** | **Integrations invisible**: delivery detail now has an honest **Linked records** panel reading `metadata.links` captured at creation and deep-linking to the owning systems (OROS lab order, MADI blood order, Dura requisition, PCT referral) — Nhume never rewrites those. Plus an explicit **Escalate-to-Daidzai** handoff (Daidzai owns emergency incidents), prominent for URGENT/EMERGENCY. Nhume hub gained "Start a movement" cargo quick-launch tiles (`?cargo=`). | link round-trip tested; detail response fix below |
| **NE** | **Detail response didn't expose links** — the panel could never populate. Surfaced `clinical_context_ref`/`programme_context_ref`/`metadata` on `DeliveryResponse` + safe `metadata_json` parse. **V005 seed**: six Zimbabwe cargo journeys (TB specimen, O-neg blood, MR vaccine, oxytocin commodity, obstructed-labour transfer, oxygen concentrator) across states, carrying `metadata.links`. Port-allocation doc note. | nhume-service 12/12; **V001–V005 validated on real Postgres 16** (all apply; links round-trip) |

Commits: `02fb5bb14` (NA) · `60ff03010` (NB) · `40b11e060` (NC+ND) · `517ac43ba` (detail response + seed + doc) · `0b35c6a2c` (test fix).

## 3. Journeys now demonstrable end-to-end (persisted, state-progressing)

- **Fleet/courier onboarding** — register a cold-chain van / specimen courier with capabilities;
  persists and appears in the list.
- **Multi-cargo mission creation** — open a specimen, blood, vaccine, commodity, patient or
  equipment mission with the *right* fields; persists with typed handling flags + integration links.
- **Dispatch lifecycle** (pre-existing, verified wired) — submit → approve → assign → accept →
  pickup → transit → deliver / fail / return, each a real transition with audit events.
- **Inbound handover** — receiving facility sees incoming movements and confirms receipt, recording
  proof-of-delivery.
- **Integration coordination** — a mission shows its linked OROS/MADI/Dura/PCT record and deep-links
  out; emergency movement escalates to Daidzai.

Seeded demo data (`NHM-2001…2006`) makes all of the above visible in a browser immediately.

## 4. Honest gaps / deferrals (not smoothed)

- **Daidzai escalation is a deep-link handoff**, not yet a cross-service incident-creation write. A
  real `POST …/escalate` (new BFF `DaidzaiServiceClient` creating an incident and storing
  `metadata.links.daidzaiIncidentRef`) is the next step; deferred to avoid adding a new cross-service
  client mid-concurrent-session. The core definition-of-done journeys don't require it.
- **Integration links are references + deep-links, not live cross-service writes.** By design and per
  the brief ("do not fake Dura/MADI/OROS/PCT updates unless the API supports it"): completing a Nhume
  mission does **not** yet call `Dura fulfill` / `OROS specimen receive` / `MADI issue` / `PCT accept`.
  The BFF clients + endpoints for those write-backs are the recommended next slice (all target APIs
  exist per the integration audit).
- **Cargo-type surfaces** are delivered as type-aware creation + hub quick-launch + list, not seven
  duplicate pipelines (reuse over duplication, per the brief). Dedicated filtered lenses per cargo
  type on `/nhume/deliveries` are a small follow-up if desired.
- **Dispatch roles**: Nhume routes keep existing gates (`ADMIN` for dispatcher/fleet/couriers/
  policies/analytics; `auth` for deliveries/inbound/courier/track). A richer realm taxonomy
  (DISPATCH_COORDINATOR, SPECIMEN_COURIER, COLD_CHAIN_OFFICER, BLOOD_BANK_OFFICER, …) was **not**
  added: new frontend gates without matching Keycloak realm roles create dead gates (the exact
  defect fixed in the prior wave). Recommend seeding realm roles + a `DISPATCH_OPERATIONS` group as a
  coordinated change.
- **Port default collision**: `nhume-service` and `data-ingestion-service` both default to
  `SERVER_PORT:8210`. In-cluster this is harmless (separate pods; BFF uses `nhume-service:8210`), but
  local co-run needs a distinct override. Documented in `port-allocation.md`; a port reassignment was
  deliberately avoided on a shared branch.
- **Backend test depth**: nhume-service still has modest test coverage (12 tests). This wave made no
  backend behaviour changes except the additive response fields (compile + suite green); the
  cargo/handover/link logic is covered on the frontend. Deeper backend state-machine/handover tests
  are a follow-up.
- **Mobile**: untouched and unbroken. Provider-app courier flow (proof/custody/fail) already exists;
  the new dispatcher functions (fleet/courier create, cargo-aware creation, inbound handover) are
  web-dispatcher surfaces by design.

## 5. Verification summary

- UI: `tsc --noEmit` clean; route parity **697/697**; launcher dead-end gate PASS; Nhume vitest **13/13**.
- Backend: `mvn -pl nhume-service test` **12/12**; migrations **V001–V005 applied cleanly on Postgres 16**
  with `metadata.links` round-tripping.
- No deploy performed (per brief). No Ndila/PMTiles files touched. All commits staged Nhume-only paths
  and pushed to the working branch with rebase-on-pull to stay clear of the concurrent session.
