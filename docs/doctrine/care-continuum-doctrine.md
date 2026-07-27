# Care Continuum Doctrine

> **Doctrine line**: One person, two continua — PCT owns the Care Continuum, the cradle-to-grave
> clinical journey; Simba owns the peer Wellness Continuum; every other care-path service is a
> subordinate component of PCT's continuum; in code, "journey" means a facility visit, never the
> continuum.

**Authority**: Product-owner ruling, 2026-07-26. Codified as logical subordination — doctrine,
registry hierarchy and mandatory anchoring — not physical module consolidation (PO-confirmed).
Rulings carry `CC-n` identifiers so registers, guards and reviews can cite them.

---

## CC-0 — Terminology and disambiguation

The repository uses "journey" in three established senses, and this doctrine deliberately adds
none of them a fourth:

| Term | Meaning | Where |
|---|---|---|
| **Care Continuum** | The person's entire clinical/health journey from birth to death, owned by `pct-service`, keyed by CPID | This doctrine |
| **Wellness Continuum** | The person's wellness and lifestyle journey, owned by `simba-service`, peer in rank to the Care Continuum | This doctrine |
| journey (code/API/DDL) | A single **facility visit** — `pct_journeys`, `JourneyEntity`, `/v1/journeys`, arrival→discharge | PCT code |
| transaction journey | The flow of one core transaction across planes | `CORE_TRANSACTION_DOCTRINE.md` |
| person/provider/platform journey | The experience-narrative arcs | `THREE_CORE_JOURNEYS.md` |

**Ruling**: new code MUST NOT name a visit-scoped construct "continuum", and MUST NOT name a
lifelong construct "journey". The existing visit-scoped `journey` naming in PCT is retained; the
continuum is the composition above it. Simba's registry phrase "wellness journeys" reads as
member journeys *within* the Wellness Continuum.

## CC-1 — The two continua

`pct-service` owns the **Care Continuum**: the full clinical continuum of care from the newborn
birth record (V055) to the death pathway (V022/V027), spanning outpatient, inpatient, theatre,
trauma, paediatric, emergency, community and every other standard care path, present or future.
Its person-level registries — problems, care plans, allergies (V052), growth (V053),
immunisations (V054), newborn records (V055), death pathway, community care context (V019) — and
its visit journeys and encounters are the continuum's operational spine.

`simba-service` owns the **Wellness Continuum** — wellness journeys, lifestyle plans, self-care,
habit and coaching workflows, longitudinal wellness progress — and is the **only service of
equivalent rank** to PCT.

The two continua join on CPID. The sanctioned bridge is Simba's `CareLinkageService`
(wellness→clinical escalation routes into PCT; clinical work never migrates into Simba). Neither
continuum may absorb the other: PCT carries `must-not-own-wellness-continuum`; Simba already
carries `must-not-own-clinical-encounter-lifecycle`.

## CC-2 — Subordination semantics

Every service on a standard patient care path is a **component of the Care Continuum**,
subordinate to PCT. Subordination is logical, not physical: components remain separate
deployables with their own schemas, ports and pipelines.

A component **MAY own** its operational and phase truth:

- physical ward/bed census and bed-days (`inpatient-service`);
- theatre/procedure logistics, anaesthesia and PACU state (`inpatient-service`);
- prehospital ePCR and EMS mission state (`daidzai-service`);
- booking and appointment transaction records (`booking-service`);
- monitoring plans, alert episodes and device-band observation writing (`telemonitoring-service`);
- blood-bank and mortuary operations (`madi-service`);
- form definitions and versions (`forms-service`).

A component **MUST NOT own**:

- containment of the continuum or of any care journey — components attach to journeys, never
  contain them;
- person-level longitudinal clinical registries (problems, care plans, allergies, growth,
  immunisation doses, birth/death summaries) — these are PCT's;
- the clinical **decision** that opens or closes a phase of care — the admission handshake is the
  model: PCT owns the admission decision, inpatient owns the physical census (V018);
- cross-phase correlation *authority* (CC-4);
- wellness-domain truth (Simba's).

The exemplars already in the estate — the inpatient `pct_admission_id` handshake and
telemonitoring's `must-not-own-task-source-of-truth # PCT keeps task SoR` — are the normative
pattern for every future component.

## CC-3 — Continuum vs record: BUTANO

`butano-service` remains the canonical longitudinal **Shared Health Record** owner (FHIR, CPID-only).
The PCT↔BUTANO relationship is **contribution and projection, not subordination**: PCT owns the
continuum's operational state; BUTANO owns the durable record that state contributes to. BUTANO's
registry role is `record-authority`, defined precisely so this doctrine is never misread as
demoting the SHR, and so nobody later marks BUTANO a "component".

## CC-4 — Delegated correlation: the trauma episode spine

`daidzai-service` operates the `trauma_episode` correlation spine **as a delegated capability on
behalf of the Care Continuum**, covering the prehospital and multi-facility window in which no
PCT anchor yet exists. The delegation's terms:

- phase owners (pct/inpatient/madi) keep their SoR rows and stamp `trauma_episode_id` — unchanged;
- once the patient reaches the continuum (facility arrival, ED registration), the episode MUST
  become resolvable to a PCT anchor; today it anchors on `subject_cpid` only, which is a
  registered violation-to-close (see register);
- daidzai MUST NOT describe the spine as ranking above, containing, or being stamped *by* the
  continuum. Registry wording is adjusted accordingly, and a guard tripwires the word
  "delegated" out of that line ever being removed.

## CC-5 — The anchoring rule

**Every clinical episode, order, procedure or admission MUST carry a resolvable PCT anchor**: a
`journey_id`, an `encounter_ref`/`encounter_id`, the `pct_admission_id` handshake, or a
`trauma_episode_id` that itself resolves to a PCT anchor. A clinical record with no resolvable
anchor is an orphan and is rejected at review (see the feature checklist, item 8a).

Registered current violations (grandfathered, closed by the follow-up wave; the guard warns on
them and will fail once closed):

| # | Violation | Closing change |
|---|---|---|
| V-1 | `ProcedureEpisodeEntity` (inpatient/theatre): `encounter_id`, `admission_ref`, `trauma_episode_id` all nullable — an elective theatre case can exist with no PCT anchor | DB CHECK requiring at least one anchor + elective-path encounter resolution |
| V-2 | `oros` orders: `encounter_ref` nullable | Validation + migration for clinical order types, with backfill |
| V-3 | ~~`dai_trauma_episode`: anchors on `subject_cpid` only, no journey/encounter back-link~~ **CLOSED 2026-07-27** | Nullable PCT back-link + link-on-facility-arrival flow — **delivered**: `dai_trauma_episode.pct_journey_id` (daidzai V200), `TraumaEpisodeService.continuumLink` (`c5ec2e7fe`), and PCT calling it on facility arrival (`03b805ad0`). Enforcement is a sweep (`unanchoredInFacility`), not a CHECK — a CHECK on the live-writer table broke the phase-advance and was withdrawn (V201). |

## CC-6 — Ghost and alias services

- **`referral-service`** (16-file stub) MUST NOT claim "Referral canonical records": referral SoR
  is PCT's (migrations V008, V021, V032, V033, V045–V050, including the transition ledger and
  offline store-and-forward). Disposition — retire vs read-model over PCT — is an open PO
  decision; until then the stub carries `must-not-claim-referral-canonical-records`.
- **`community-service`** MUST NOT fork the community care context PCT owns (V019/V027).
  Same disposition question; carries `must-not-fork-community-care-context`.
- **`wellness-service`** is a compatibility alias of Simba and MUST NOT fork the Wellness
  Continuum; retirement is desirable but gated on folder-move approval. Carries
  `must-not-fork-wellness-continuum`.

## CC-7 — Booking is a component, not a container

`booking-service` owns the booking/appointment **transaction record**. It is a component of the
Care Continuum, never a container of care: the continuum links journeys to appointments
(PCT V031), not the reverse. The registry phrase "booking transaction container" reads as
*containing booking data*, not the journey. Whether wellness bookings route through Simba as a
dual-continuum concern is an open PO question; until ruled, booking is a care-continuum
component.

## CC-8 — Registry codification

Three flat fields in `docs/registry/services-registry.yaml` (hand-curated; mirrored into
`scripts/registry/seed-registry.mjs` `DOCTRINE_OVERRIDES` so any future regeneration preserves
them — the standing law remains *never run the generator*):

- `continuum:` `care` | `wellness` — which continuum the service participates in;
- `continuum_role:` `owner` | `component` | `correlator` | `record-authority`;
- `continuum_parent:` the owner a component/correlator is subordinate to; `null` for owners and
  record-authorities.

| Service | continuum | continuum_role | continuum_parent |
|---|---|---|---|
| pct-service | care | owner | null |
| simba-service | wellness | owner | null |
| butano-service, butano-fhir | care | record-authority | null |
| inpatient-service | care | component | pct-service |
| oros-service | care | component | pct-service |
| booking-service | care | component | pct-service |
| telemonitoring-service | care | component | pct-service |
| referral-service | care | component | pct-service |
| community-service | care | component | pct-service |
| forms-service | care | component | pct-service |
| madi-service | care | component | pct-service |
| daidzai-service | care | correlator | pct-service |
| wellness-service | wellness | component | simba-service |

`sovereign`/`sovereign_group` are deliberately untouched: that taxonomy groups product families
(TSHEPO, BUTANO, VITO…), and overloading it for care hierarchy would corrupt its semantics.
Rank is carried by `continuum_role: owner` alone.

## CC-9 — Enforcement

- `scripts/guard/check-care-continuum-doctrine.sh` (wired into the change-safety gates) asserts
  the registry fields, the prose rulings, the doctrine cross-references and the overrides mirror;
  anchoring violations are WARN-only until the closing wave lands, then flip to FAIL. **V-3 is
  now CLOSED** (2026-07-27) — the guard reports it silent (closed) keyed on the flow being wired
  (PCT calling continuum-link), not on a method merely existing; V-1 and V-2 remain WARN.
- `docs/templates/CORE_TRANSACTION_FEATURE_ALIGNMENT_CHECKLIST.md` item **8a** requires every
  feature to name its continuum and the PCT anchor each new clinical record carries.
- `docs/registry/system-of-record-map.md` carries the summary ruling **G-CC1**.

## Open PO decisions

1. referral-service: retire vs convert to read-model over PCT referral SoR (CC-6).
2. community-service: same question (CC-6).
3. wellness-service retirement (folder-move approval required) (CC-6).
4. Optional `PCT`/`SIMBA` sovereign product groups (not needed for rank; cosmetic).
5. Booking dual-continuum question (CC-7).
6. Optional continuum composition API in PCT (`/v1/continuum/{cpid}`) exposing the person-level
   registries + visit journeys as one read surface.

## Violations-to-close register

See CC-5 table (V-1..V-3) plus CC-6 dispositions. Each closure is an ordinary feature change
citing this doctrine; none requires a doctrine change. The paediatric domain pack
(`docs/clinical/paediatric-domain-pack/implementation-report.md`) is CC-compliant by
construction: growth, immunisation, newborn and death records are PCT-owned person-level
registries, and the neonatal episode work is precisely the continuum's cradle end.
