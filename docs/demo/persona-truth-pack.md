# Persona Truth Pack — named demo identities for journey acceptance

> **Rule**: journeys are proven as the persona who really does the job — never as
> system admin. Every persona below is seeded idempotently by
> `scripts/operator/seed-persona-truth-pack.sh` (realm users + varapi providers +
> workforce assignments + vashandi profiles) and verified login→anchor→provider→
> assignment on every run.
>
> Password for all personas: `ImpiloTest123!` (preview-only; realm policy min12+special).
> Facility 1 = Harare Central Hospital · Facility 2 = Parirenyatwa Group of Hospitals.

## Provider personas (work access via ACTIVE wgv assignment)

| Login | Name | Realm roles | Provider ID | Cadre | Facility | Golden journey |
|---|---|---|---|---|---|---|
| `dr.mapfumo` | Tendai Mapfumo | CLINICIAN | PROV-ZW-00001 | General practitioner | 1 | Clinical day: queue → encounter → orders → close; orders imaging |
| `nurse.chienda` | Rumbidzai Chienda | NURSE | PROV-ZW-00007 | Registered nurse | 1 | Triage/vitals in the clinical-day journey |
| `clerk.dube` | Nyasha Dube | SUPPORT_AGENT | PROV-ZW-00008 | Health records clerk | 1 | Walk-in registration → add patient to queue (no clinical surfaces) |
| `dr.gwena` | Rudo Gwena | CLINICIAN | PROV-ZW-00009 | Specialist physician | **2** | Receives cross-facility teleconsult; MDT participant |
| `rad.nkomo` | Sipho Nkomo | CLINICIAN | PROV-ZW-00010 | Radiographer | 1 | Imaging worklist → perform → result/report return |
| `pharm.zimba` | Faith Zimba | PHARMACIST | PROV-ZW-00004 | Pharmacist | 1 | Prescription → dispense (existing seed-04 provider, now with assignment) |
| `trainer.chikafu` | Fungai Chikafu | CLINICIAN + **TRAINER** | PROV-ZW-00011 | Health educator | 1 | Fundo author: Course Studio → New Course → publish → assign |
| `learner.tembo` | Kudzai Tembo | NURSE | PROV-ZW-00012 | Enrolled nurse | 1 | Fundo learner: enrol → 40% → logout/resume → quiz → certificate |
| `msika.seller` | Simba Chikore | MARKETPLACE_SELLER | PROV-ZW-00014 | Pharmacist (community) | 1 | Msika seller: create listing (priced) → moderate → publish → receive order |

## Governance / citizen personas (no provider record needed)

| Login | Name | Realm roles | Golden journey |
|---|---|---|---|
| `citizen.moyo` | Tatenda Moyo | CITIZEN | Patient: teleconsult join, result-ready notification, inbox |
| `admin.harare` | Harare Admin | FACILITY_ADMIN | Facility claim/config, staff assignment views |
| `admin.central` | Central Admin | SYSTEM_ADMIN | National admin oversight (not a journey driver) |
| `regulator.hpcz` | Chipo Marimo | **HIE_ADMIN** | Registry plane + IATG console review without SYSTEM_ADMIN superpowers |
| `iatg.gono` | Nomsa Gono | SYSTEM_ADMIN + HIE_ADMIN | IATG decisions: access requests, facility claims, invitations |
| `dispatcher.chirwa` | Tapiwa Chirwa | DISPATCH_COORDINATOR | Nhume dispatch command: mission creation, assignment, fleet, Dispatch Ops console |
| `courier.banda` | Blessing Banda | COURIER | Field courier: accept mission, pickup/drop-off sign-off, proof capture (mobile-first) |
| `hr.dziva` | Rumbidzai Dziva | HR_OFFICER | Vashandi workforce management: assignments lifecycle, rosters, leave decisions, access review |
| `msika.operator` | Tariro Mutasa | MARKETPLACE_OPERATOR | Msika operations: vendor governance (suspend/reinstate), review queues, audit feed, substitution decisions |
| `msika.vendor` | Rutendo Mhofu | VENDOR | Msika vendor fulfilment: accept → ready → out-for-delivery → complete; auto-bound via `/commerce/vendor/me` |

## What each persona should see (Start Menu truth)

Executable version of this table:
`ui/one-ui-shell/src/lib/shell/__tests__/app-registry.test.ts` ("persona visibility").

- **Everyone**: Home, Simba wellness, Ubomi life events, Ask/search surfaces, quick actions
  `Claim facility`, `Request provider access`.
- **CLINICIAN/NURSE**: Clinical Hub, Queue, Telemedicine, Registry, diagnostics deep
  actions (`Diagnostic orders`, `Imaging worklist`), `Register patient`.
- **SUPPORT_AGENT (clerk)**: Queue + `Register patient` / `Add patient to queue` — and
  deliberately *no* Clinical Hub.
- **TRAINER**: `New course` + `Course Studio` quick actions (LEARNING_AUTHOR group).
- **FACILITY_ADMIN**: admin surfaces incl. `Workforce intake`; also satisfies
  LEARNING_AUTHOR (facility training officers author courses).
- **HIE_ADMIN**: registry governance plane (`cmd-registry-plane`), `Issue Provider ID`,
  IATG Trust Console (when merged), Mvumo consent.
- **PHARMACIST**: Pharmacy app (DISPENSER group).
- **MARKETPLACE_OPERATOR/SELLER/VENDOR**: Marketplace tile (`MARKETPLACE_ACCESS`) plus their persona command —
  `Marketplace operations` (ops), `Seller centre` (seller), `Vendor fulfilment` (vendor).

## Realm-role gaps this pack closes

| Role | Why it was missing/needed |
|---|---|
| `TRAINER` | No authoring role existed; Course Studio was reachable only by admins. Pairs with the new `LEARNING_AUTHOR` group in `role-groups.ts`. |
| `HIE_ADMIN` | Frontend `REGISTRY_ADMIN`/`ADMIN_OR_HIE` groups referenced it, but it never existed in the realm — the registry-governance plane was SYSTEM_ADMIN-only in practice. |
| `PUBLIC_HEALTH_OFFICER` | `PUBLIC_HEALTH` group was unsatisfiable by non-admins (ENV_HEALTH/CHW still absent — add when those personas exist). |
| (fix) `OPERATIONS` | Was a launcher gate with no group and no realm role — Dispatch Ops was invisible to everyone. Re-gated to `OPERATIONS_AGGREGATE`; a registry test now forbids unknown gates. |
| `DISPATCH_COORDINATOR` | Ndila gates referenced it but no realm role or persona existed — dispatch journeys were only testable as admin. Dispatch Ops now gates on the `DISPATCH_OPERATIONS` group. |
| `COURIER` | The courier side of the two-ended sign-off lifecycle had no identity of its own. |
| `HR_OFFICER` | Vashandi was gated `ADMIN`-only; workforce management had no persona of its own. Gates now use the `WORKFORCE_ADMIN` group. |
| `MARKETPLACE_OPERATOR` | Msika ops (vendor governance, reviews, audit, substitutions) gated on the blanket `COMMERCE` group with no dedicated operator identity. Now `MARKETPLACE_OPS`. |
| `MARKETPLACE_SELLER` | Seller centre had no seller-of-record identity; listing authoring was `COMMERCE`-gated. Now `MARKETPLACE_SELL`; seller identity derives from linked Provider ID. |
| `VENDOR` | Vendor fulfilment had no vendor actor; the queue was operator-simulated by ID. Now `VENDOR_FULFILMENT` with `/commerce/vendor/me` auto-binding. |

## Seeding & verification

```bash
# on the preview VM (idempotent, exits non-zero if any chain link breaks)
bash scripts/operator/seed-persona-truth-pack.sh
```

Chain proven per provider persona: Keycloak login → `health_id` anchor claim →
`/identity/linked-ids` returns the Provider ID → ≥1 ACTIVE workforce assignment
(drives `hasWorkAccess`/Work tab). Governance personas: login carries the expected
realm role.

Files: realm `tools/auth/impilo-realm.json` · varapi seed
`scripts/seed/14-seed-persona-truth-pack-varapi.sql` · wgv seed
`scripts/seed/15-seed-persona-truth-pack-wgv.sql` (wired into
`scripts/deploy/seed-full-preview-sovereign-data.sh`).
