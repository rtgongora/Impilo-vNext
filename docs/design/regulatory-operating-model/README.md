# Regulatory Operating Model (ROM) — Spec Pack

The design pack behind
[`docs/doctrine/regulatory-operating-model-doctrine.md`](../../doctrine/regulatory-operating-model-doctrine.md):
HPA + the eight professional councils as nine distinct regulatory organisations on one governed
substrate, each process delivered as both an applicant journey and a regulator workflow.

## Pack contents

| File | Purpose |
|---|---|
| [`ownership-rulings.md`](ownership-rulings.md) | The eight binding ownership rulings (R1–R8): org identity vs council profile, appointments, jurisdiction, registers, complaints/disciplinary firewall, committees/hearings, applications/correspondence/payments, CPD/reporting/oversight — with deprecation paths |
| [`generic-model-spec.md`](generic-model-spec.md) | The one generic model all nine organisations run on: entities, the three state machines (application incl. RFI loop, disciplinary, hearing), context-resolution chain, report classes, audit events, public contracts |
| [`context-and-isolation-spec.md`](context-and-isolation-spec.md) | The login seam: appointment → vashandi mirror → org-session (WORK_CONTEXT) → authz org/jurisdiction dimensions (SHADOW→ENFORCE) → shell `regulatory_work` mode; the per-council isolation policy matrix |
| [`build-waves.md`](build-waves.md) | ROM-W0–W11 build program: migrations ledger, services touched, key classes, UI routes, seeds, tests, and the applicant+regulator slice each wave proves |
| [`orgs/`](orgs/) | Nine equal-depth organisation parameter files (template-identical): statutory basis, professions, registers, committees, application types, renewal cycle, CPD rules, fee references, registration-number pattern, jurisdiction, `council_regulatory_configs` seed block |

## Organisation files

| Code | Organisation | File |
|---|---|---|
| HPA | Health Professions Authority | [`orgs/hpa.md`](orgs/hpa.md) |
| MDPCZ | Medical & Dental Practitioners Council of Zimbabwe | [`orgs/mdpcz.md`](orgs/mdpcz.md) |
| NCZ | Nurses Council of Zimbabwe | [`orgs/ncz.md`](orgs/ncz.md) |
| PCZ | Pharmacists Council of Zimbabwe | [`orgs/pcz.md`](orgs/pcz.md) |
| AHPCZ | Allied Health Practitioners Council of Zimbabwe | [`orgs/ahpcz.md`](orgs/ahpcz.md) |
| EHPCZ | Environmental Health Practitioners Council of Zimbabwe | [`orgs/ehpcz.md`](orgs/ehpcz.md) |
| MRPCZ | Medical Rehabilitation Practitioners Council of Zimbabwe | [`orgs/mrpcz.md`](orgs/mrpcz.md) |
| MLCSCZ | Medical Laboratory & Clinical Scientists Council of Zimbabwe | [`orgs/mlcscz.md`](orgs/mlcscz.md) |
| NTCZ | Natural Therapists Council of Zimbabwe | [`orgs/ntcz.md`](orgs/ntcz.md) |

## Migration-number ledger (reserved 2026-07-22; verify heads before each wave)

| Service | Head at reservation | Reserved for ROM |
|---|---|---|
| varapi-service | V027 | **V028–V036** (org link + seed; registers ×2; application sections/RFI/messages; fees/payment; renewal+CPD gate; disciplinary FSM; hearings/dockets/appeals; oversight grants) |
| tuso-service | V038 | **V039–V040** (practice establishment case; appeal linkage) |
| organization-registry-service | V005 | **V006–V008** (appointment + role vocabulary + jurisdiction; 9-org seed; committee organs + membership) |
| vashandi-workforce-service | V008 | **V009** (regulatory org assignment + appointment consumer) |
| tshepo-identity-service | V004 | **V005** (only if WORK_CONTEXT claims change) |
| tshepo-authz-service | V044 | **V045–V047** (org/jurisdiction dimension + isolation seeds; committee-docket dimension; HPA oversight policies) |
| reporting-service | V002 | **V003** (regulatory report definitions by class) |
| zibo-service | V004 | **V005** (regulatory-jurisdiction value set) |
| forms-service | V002 | **V003** (regulatory application section schemas) |
| rito-quality-safety-service | V007 | **V008** (report-unregistered-practice intake + referral echo) |

experience-bff stays stateless (no migrations). `services/tshepo-service` is NO-TOUCH; all
policy lands as tshepo-authz seed migrations + rego through the CZO single-writer channel,
SHADOW before ENFORCE.

## Status

- ROM-W0 (this pack + doctrine + registry ownership edits): **authored**, pending PO review of
  the nine organisation parameter files (registers/committees marked `TO_CONFIRM` need registrar
  confirmation before their W1/W3/W8 seeds are cut).
- ROM-W1+ per [`build-waves.md`](build-waves.md).
