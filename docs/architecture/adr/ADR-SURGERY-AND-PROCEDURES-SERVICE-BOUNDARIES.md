# ADR-SURGERY-AND-PROCEDURES-SERVICE-BOUNDARIES

- Status: Accepted (Product Owner decision, 2026-07-26)
- Date: 2026-07-26
- Decision owners: Clinical Plane Architecture / Product Owner
- Scope: `procedures-service` (new), `surgery-service` (new), `inpatient-service`,
  `oros-service`, `clinical-knowledge-platform-service`, `zibo-service`
- Supersedes the open question in
  [`clinical-procedure-or-context-map.md`](../clinical-procedure-or-context-map.md)
- Evidence: [procedures pipeline audit](../../clinical/procedures-pipeline/audit.md) ·
  [surgical pack audit](../../clinical/surgical-domain-pack/audit.md)

## Context

`docs/architecture/clinical-procedure-or-context-map.md` closed with an explicitly deferred
decision:

> Remaining decision: whether to keep procedure episode workflow state in PCT
> (coordinator-only) with references, or introduce a future sovereign procedure/theatre service
> via ADR.

Two programmes now force that decision:

1. **Clinical Procedures Pipeline** — a cross-cutting platform capability that must be reusable by
   Emergency Care, Medicine, Surgery, Theatre, Anaesthesia, Paediatrics, Reproductive and
   Maternity care, Critical Care, Mental Health, Dentistry, Rehabilitation, Nursing, allied
   health, Laboratory, and imaging and interventional services.
2. **Surgery and Surgical Specialties** — a clinical domain pack spanning twenty journey stages
   and fifteen specialties, of which theatre is one location and one phase.

The audits establish the constraint that shapes this ADR. A procedure execution engine already
exists in real, live-proven depth: `inpatient.procedure_episode` plus roughly twenty satellite
tables, two API faces over one aggregate, behind ten live runtime-proof rigs. It is not missing.
It is *located in* a subordinate care-path service and *named* theatre.

Meanwhile `oros-service` already owns the procedure **request**: `order_type=PROCEDURE` since
`V001`, and a deny-by-default `ProcedureWorkflow` guard with nineteen states since `V011`.

So the real question is not "where do we build a procedures pipeline". It is "how do we expose a
cross-cutting pipeline without either duplicating a proven system of record or migrating one".

### Forces

- **No duplicate system-of-record functionality** (project guardrail). Rebuilding the execution
  engine or the request record elsewhere violates this directly.
- **A cross-cutting platform capability should not be owned by a subordinate care-path service.**
  Under the care-continuum doctrine, `inpatient-service` is a component of PCT's continuum.
  Fifteen specialties plus Dentistry, Rehabilitation, Laboratory and Nursing depending on it for
  bedside procedures is a boundary inversion.
- **The estate is triple-gated and green.** Theatre §22 and trauma Gate-1 are both verified;
  deployment is held by the Product Owner. A migration of `procedure_episode` would put ten live
  rigs, the BFF passthrough, the UI, the mobile screen, the trauma cross-links
  (`trauma_episode_id`, the identity-repoint hook, daidzai phase registration) and the COSTA and
  reporting Kafka consumers on `inpatient.events` all at risk for no clinical gain.
- **Nothing owns surgical disease.** Confirmed by search: no service has a surgical episode,
  condition, staging, indication, outcome or surveillance model.

## Decision

### 1. `procedures-service` is created — port 8395, clinical plane

Port 8395 is unallocated in `docs/runbooks/port-allocation.md`, was flagged as a next free slot in
`docs/audits/rito/capability-audit.md`, and is bound nowhere in the repository.

It owns **only capabilities that have no owner today**:

| # | Owns | Specification |
|---|---|---|
| 1 | Canonical procedure catalogue — definition, requirements, version, approval, governance | Pipeline §3 |
| 2 | Appropriateness and duplication engine | §5 |
| 3 | Competence and privilege resolution over VARAPI and Vashandi | §7 |
| 4 | Readiness engine | §8 |
| 5 | Safety-pause template service | §9 |
| 6 | Sedation and anaesthesia requirement profiles | §10 (requirements side) |
| 7 | Aftercare template generation | §17 |
| 8 | Procedure execution index — the correlation spine | §4, §30 |
| 9 | Pipeline analytics projection | §26 |

### 2. The engine-not-store rule

**`procedures-service` evaluates; the executing service persists.**

Readiness verdicts and safety-pause completions are computed in `procedures-service` from
catalogue requirements plus peer state, and recorded by whoever is executing the procedure.
`inpatient.procedure_readiness_check` and `inpatient.procedure_checklist_item` remain the
per-episode record of truth — they become the persisted verdict of a `procedures-service`
evaluation instead of the output of hardcoded local logic.

This is the rule that makes a new service safe. Without it, a readiness table in
`procedures-service` and a readiness table in `inpatient-service` would be two records of the
same fact, which is precisely the duplication the guardrails forbid.

### 3. `procedures-service` does NOT own — delegation table

| Capability | Owner | Action in this programme |
|---|---|---|
| Procedure request record + fulfilment lifecycle | `oros-service` | extend `ProcedureWorkflow` with PROPOSED, ABORTED, FAILED, PARTIALLY_COMPLETED, REPEATED + a reason-and-next-action invariant |
| Procedure execution record, specimens, devices, counts, recovery | `inpatient-service` | generalise in place (decision 4) |
| Consent | `mvumo-service` (+ `tshepo-consent-service`) | deepen per §6 |
| Terminology, code systems, value sets | `zibo-service` | expand the surgical CodeSystem; add SNOMED CT mapping |
| Clinical decision logic | `clinical-knowledge-platform-service` | add surgical and procedure rule content to `clinical.rule_definitions` |
| Facility and procedure capability | `tuso-service` | add the capability dimensions |
| Commodities, implants stock, controlled drugs | `inventory-service` (Dura) | reuse |
| Blood | `madi-service` | reuse |
| Money | `coverage-service` (Ruvimbo), `costing-engine-service` (COSTA) | extend after clinical prioritisation, never before |
| Offline | `offline-sync-service` | add procedure scopes |
| FHIR projection | `butano-service`, `fhir-gateway-service` | add the resource mappings |
| Referral and transport | `ndila-service`, `nhume-service` | reuse |
| Person care continuum anchor | `pct-service` | every procedure resolves a PCT anchor per CC-5 |

### 4. `inpatient-service` remains the procedure execution system of record

No data migration. `procedure_episode` is generalised **additively**:

- `catalogue_ref` — the governing `procedures-service` catalogue entry
- `request_ref` — the originating OROS order
- `setting` — `THEATRE | BEDSIDE | CLINIC | WARD | CRITICAL_CARE | ENDOSCOPY | CATH_LAB |
  INTERVENTIONAL_RADIOLOGY | DIALYSIS | INFUSION | DENTAL | OPHTHALMIC | DERMATOLOGICAL | …`
- `lifecycle_state` — the generalised lifecycle **beside the preserved theatre `status`**, so
  every existing query, rig, projection and consumer keeps working unchanged
- theatre-only gates become profile-gated rather than unconditional

Its registry entry is corrected to state that it owns **perioperative and procedure execution**.
This leaves an honest naming debt: a service named `inpatient-service` executing a clinic-based
dermatology excision reads wrong. A rename is a separate, optional wave, explicitly **not** in
this programme, and must not be inferred as approved.

### 5. `surgery-service` is created — port 8396, clinical plane

System of record for surgical **disease**, which nothing owns today:

surgical episode · surgical condition · anatomical site · laterality · disease stage · urgency ·
operative indication · non-operative option · planned versus performed procedure · surgical
decision record · prehabilitation and optimisation plan · complication pathway instances ·
histology closure gate · implant, drain, stoma and wound as longitudinal objects · functional
outcome and patient-reported outcomes · surveillance plan · recurrence · reoperation · the fifteen
specialty extensions.

It is PCT-anchored per care-continuum doctrine CC-5, references
`inpatient.procedure_episode` for each operation, and consumes `procedures-service` for the
catalogue. It is **not** a second theatre workflow and must never create one.

### 5a. AMENDMENT 2026-07-26 — CC-2 narrows `surgery-service`

Decision 5 as originally written would have violated CC-2. Found by verifying a doctrine citation
the emergency lane made about a different question (who owns a serial burn assessment); the
citation was correct, and checking it showed it lands harder here than there.

**CC-2 verbatim, on what a component MUST NOT own:**

> person-level longitudinal clinical registries (problems, care plans, allergies, growth,
> immunisation doses, birth/death summaries) — these are PCT's
>
> the clinical **decision** that opens or closes a phase of care — the admission handshake is the
> model: PCT owns the admission decision, inpatient owns the physical census

Four items in decision 5 fall on the wrong side of that, and PCT already has the structures:
`pct_problems` carries `diagnostic_certainty`, severity (V060), `last_recurrence_at`, `evidence`,
`review_date` and — decisively — **`responsible_service`**. `pct_care_plans` and
`pct_care_plan_goals` exist. Building `surgical_condition`, a surveillance plan, an outcome
registry and a decision-to-operate record inside `surgery-service` would have been four duplicate
person-level registries, which is exactly what this ADR's own guardrails forbid.

**Corrected split.**

`surgery-service` **MUST NOT own** — these are PCT's, and surgery attaches to them:

| Concept | PCT owner | How surgery participates |
|---|---|---|
| Surgical condition, diagnosis, certainty, severity, staging, recurrence | `pct_problems` | writes with `responsible_service = surgery`; staging as problem attributes |
| Surveillance plan | `pct_care_plans` + `pct_care_plan_goals` | proposes and executes; does not own |
| The decision that opens or closes a phase of care | PCT, per the admission-handshake model | owns the surgical *reasoning* and the options considered; PCT records the decision |
| Functional outcome, patient-reported outcome | PCT person-level | contributes measurements |
| Serial burn assessment | `pct.burn_assessment` (emergency lane, pct V2xx) | writes through it; no parallel acute copy |

`surgery-service` **MAY own** — operational and phase truth of surgical care:

surgical episode as a *management course record* that **attaches to** PCT journeys and never
contains them · structured surgical assessment content · operative indication and the
non-operative options weighed · prehabilitation and optimisation execution · planned-versus-
performed reconciliation · complication pathway **instances** as workflow, with the resulting
problem landing in `pct_problems` · waiting-list clinical revalidation state · the fifteen
specialty extensions, their indications, operative content, templates and maps.

**The line is containment versus attachment.** CC-5 requires every clinical episode to carry a
resolvable PCT anchor, which presumes components may own clinical episodes; `inpatient` owns
`procedure_episode` and `daidzai` owns the trauma spine under delegation. A surgical episode is
legitimate precisely so long as it references journeys and person-level facts rather than holding
them.

This narrows the service substantially and improves it. It also changes S1 from "build a surgical
disease model" to "extend PCT's problem and care-plan model with surgical semantics, and own the
surgical management record" — which is the same shape the paediatric pack took, where growth,
immunisation and newborn records are PCT-owned person-level registries and the pack owns the
decision support and the workflow.

### 6. Boundary statement

| Domain | Owns |
|---|---|
| **Surgery** (`surgery-service`) | surgical disease, diagnosis, operative and non-operative management, prioritisation, follow-up, outcomes; specialty-specific indications and operative content |
| **Theatre** (`inpatient-service`, theatre face) | theatre scheduling, room readiness, team readiness, intraoperative workflow, theatre safety, counts, recovery handoff |
| **Trauma** (`daidzai-service` + `inpatient-service` resuscitation) | injury assessment, trauma resuscitation, injury-specific acute pathways |
| **Emergency Care** (`pct-service` ED) | initial acuity, stabilisation, emergency investigation, disposition |
| **Procedures Pipeline** (`procedures-service` + `oros-service` + `inpatient-service` execution) | common intervention lifecycle, consent controls, site and side, equipment, checklist, execution metadata, specimens, devices, recovery and aftercare structure |

Emergency surgery remains owned by the theatre/perioperative face, referenced by trauma through
`procedure_episode.trauma_episode_id` — the arrangement already proven in both directions by the
theatre and trauma programmes. This ADR does not disturb it.

## Consequences

### Accepted

- Two new services to wire, deploy, secure and observe.
- `inpatient-service` keeps a misleading name until a separate rename wave.
- `procedures-service` is a synchronous dependency on the readiness and competence path, so it
  must fail safe: an unavailable `procedures-service` must block a procedure it cannot clear
  rather than allow it, except under an audited emergency override.
- The catalogue becomes governed national content. A change to a procedure's requirements is a
  content release, not a deployment — consistent with how CKP already treats clinical thresholds.

### Rejected alternatives

1. **Govern existing owners only, no new service.** Catalogue into CKP, request into OROS,
   engine generalised in `inpatient-service`. Cheapest and doctrine-compliant on duplication, but
   leaves a cross-cutting platform capability owned by a subordinate care-path service, and gives
   the pipeline no place of its own to stand. Rejected by the Product Owner in favour of a real
   platform layer.
2. **Big-bang extraction.** Move `procedure_episode` and its twenty satellite tables into
   `procedures-service`. Correct end-state; weeks of high-risk refactor across ten rigs, the BFF,
   the UI, mobile, the trauma cross-links and two Kafka consumers, with no clinical gain and a
   live triple-gated estate at stake. Rejected.
3. **Procedure episode state in PCT with references** (the original deferred option). PCT is the
   continuum coordinator, not an execution engine; this would either duplicate the execution
   record or reduce the pipeline to a pointer table. Rejected.

### Guardrails

- No second procedure request table. OROS is the order system of record.
- No second procedure execution aggregate. `inpatient.procedure_episode` is the one.
- No second theatre workflow. `surgery-service` orchestrates by reference.
- No readiness or checklist *record* in `procedures-service` — engine-not-store.
- Every procedure resolves a PCT anchor (journey, encounter, or the admission handshake).
- `procedures-service` and `surgery-service` are composition and domain services respectively;
  neither becomes a second patient, provider, facility, terminology, payment or consent truth.
- Financial state must never masquerade as clinical cancellation, and must never delay emergency
  surgery.
