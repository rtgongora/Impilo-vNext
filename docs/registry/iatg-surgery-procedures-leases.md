# Surgery + Clinical Procedures Pipeline — Lease Record

Delivery-boundary record for the **Surgery and Surgical Specialties** domain pack and the
**Clinical Procedures, Interventions and Therapeutic/Diagnostic Actions Pipeline** (opened
2026-07-26, base anchor `09b28436e`, branch
`claude/staging-ux-orchestration-remediation-Yypyl`). This file is the single source of truth for
what a concurrent session may and may not touch while this programme is in flight on the
**shared checkout**.

Audits: [procedures pipeline](../clinical/procedures-pipeline/audit.md) ·
[surgical pack](../clinical/surgical-domain-pack/audit.md).
Boundary: [ADR-SURGERY-AND-PROCEDURES-SERVICE-BOUNDARIES](../architecture/adr/ADR-SURGERY-AND-PROCEDURES-SERVICE-BOUNDARIES.md).
Peer leases recorded and honoured: [`iatg-trauma-leases.md`](iatg-trauma-leases.md) ·
[`iatg-emergency-leases.md`](iatg-emergency-leases.md) — the emergency lane has already recorded
this programme's claims and reserved around them; where its prose and its table disagree, the
table is authoritative.

---

## 1. Programme-wide invariants

> **Many writers share one working tree on one branch.** At the time of opening, `git worktree
> list` shows dozens of live worktrees and the tree carries ~105 modified or untracked paths
> belonging to other sessions. Same-checkout collision is the top hazard, ahead of any technical
> risk in the programme itself.

- **Path-scoped commits only.** `git commit -- <explicit paths>`. Never `git add -A`, never a bare
  `git commit`. A bare commit takes the whole index and has previously swallowed another session's
  work.
- **`git pull --ff-only`.** Never `--rebase` on shared history. If ff-only fails, stop and
  reconcile deliberately.
- **`git show --stat HEAD` before every push.** Confirm the commit contains only this programme's
  paths.
- **Migration numbers:** only from the reserved block in §3. Never renumber a migration that has
  been pushed.
- **Build from `$PWD`.** The image build script defaults `REPO_PATH` to the main checkout, so a
  build launched from a worktree silently ships another session's jars. Pass `REPO_PATH=$PWD`.
- **Repackage the migration-owning service before any rig.** `-am` on sibling modules does not
  rebuild it; a stale jar means missing migrations and a wall of spurious failures.
- **Clean build before any preview deploy**, plus a repo-wide duplicate-migration scan.
- **Deployment is Product-Owner-authorised.** The estate is already triple-gated (theatre §22,
  trauma Gate-1, trauma web completeness) and held. This programme does not self-authorise a
  fullboot.

## 2. Lane ownership

### 2.1 Exclusively this programme's

- `services/procedures-service/**` (new, port 8395)
- `services/surgery-service/**` (new, port 8396)
- `docs/clinical/procedures-pipeline/**`, `docs/clinical/surgical-domain-pack/**`
- `docs/architecture/adr/ADR-SURGERY-AND-PROCEDURES-SERVICE-BOUNDARIES.md`
- `scripts/runtime-proof/procedures-*.sh`, `scripts/runtime-proof/surgery-*.sh`
- `reports/journeys/procedures-*`, `reports/journeys/surgery-*`
- `ui/one-ui-shell/src/app/work/clinical/surgery/**`, `ui/.../app/surgery/**`
- `ui/one-ui-shell/src/components/clinical/surgery/**`
- `e2e/journeys/surgery-*.spec.ts`, `e2e/journeys/procedures-*.spec.ts`

### 2.2 Co-edited — announce before touching

`inpatient-service` is the shared heart again, exactly as it was for theatre and trauma. The
class-level split from the trauma lease §2a **remains in force** and is extended:

| Area | Owner |
|---|---|
| `core/{TheatreService, ProcedureEpisodeService, TheatreReadinessBoardService, TheatreCommoditiesService, AnaesthesiaScoringEngine}`, `procedure_*` tables, `Theatre*`/`ProcedureEpisode*` controllers, theatre integration clients, PACU | **this programme** (inherits the theatre lane, which is complete) |
| `resuscitation_*` entities/repos/services, ED activation, `resuscitation_event`, daidzai trauma tables | **trauma lane — never touch** |
| `AdmissionController` + admission tables | **shared — flag before editing** |

Other co-edited services, all additive-only from this programme:
`oros-service` (workflow guard + catalogue-adjacent columns) · `mvumo-service` (consent depth) ·
`varapi-service` and `vashandi-workforce-service` (privilege and roster reads — prefer zero
writes) · `clinical-knowledge-platform-service` (rule content) · `zibo-service` (code systems) ·
`tuso-service` (capability dimensions) · `inventory-service` (implant registry) ·
`scheduling-service` and `referral-service` (waitlist and referral depth) ·
`reporting-service` and `costing-engine-service` (projections) ·
`experience-bff` (new proxy groups only — never trauma's `EdWorkflow` / `CareEmergencyInpatient` /
`Daidzai` / `Madi` controllers).

### 2.3 Never touched by this programme

`madi-service` internals (trauma-owned; the blanket jurisdiction-default fix is theirs to
coordinate) · `daidzai-service` (call-only) · `vito-service` (call-only) · `pct-service` writes
beyond an anchor read.

## 3. Reserved migration blocks

Heads verified at `09b28436e`. **Reserve before writing; announce if a block needs extending.**

| Service | Head today | Reserved for this programme |
|---|---|---|
| `procedures-service` | — | V001–V030 (new service) |
| `surgery-service` | — | V001–V030 (new service) |
| `inpatient-service` | V066 | **V067–V080** |
| `oros-service` | V017 | V018–V024 |
| `scheduling-service` | V003 | V004–V010 |
| `referral-service` | V002 | V003–V006 |
| `mvumo-service` | V008 | V009–V014 |
| `clinical-knowledge-platform-service` | V006 | V007–V020 |
| `zibo-service` | V007 | V008–V014 |
| `tuso-service` | **V043** | **V044–V049** |
| `inventory-service` | V014 | V015–V020 |
| `varapi-service` | V038 | V039–V042 |
| `vashandi-workforce-service` | V008 | V009–V012 |
| `reporting-service` | V003 | V004–V008 |
| `costing-engine-service` | V024 | V025–V028 |
| `tshepo-authz-service` | V047 | V048–V056 |
| `notification-service` | V017 | V018–V020 |
| `coverage-service` | V020 | V021–V024 |

Note on `inpatient-service`: trauma's historical block was V035–V064 and the theatre programme
took V065–V066. V037–V064 are **dead space** — verified empty, the files jump V036 to V065 — but
stay unclaimed to preserve the historical record. This programme starts at **V067** and stops at
V080; the emergency lane holds V081–V110.

**Two corrections found on 2026-07-26 when the emergency lane opened its lease:**

- **`tuso-service` V042–V048 was never mine to claim.** V042 (`emonc_signal_function_readiness`)
  and V043 (`readiness_assessment_programme`) had already landed from the facility-readiness lane
  — V043 in `09b28436e`, this programme's own anchor commit — so the range collided from birth.
  Corrected to **V044–V049**, which is what is actually free below the emergency lane's V050.
  The lesson is that a head measured on a busy shared tree is a snapshot, not a reservation:
  re-check immediately before writing, not only when opening a lease.
- **`pct-service` head is V100, not V057.** This programme reserves **nothing** in pct — it reads
  a care-continuum anchor and writes nothing — so nothing is affected, but the stale figure is
  corrected rather than left to mislead. Note the numbering there is not contiguous (V058 absent,
  a gap from V061 to V099, V060 flagged by the emergency lane as an untracked exception).

## 3b. Cross-programme contract with the emergency lane (agreed 2026-07-26)

Accepted in full. Recorded here so it binds the waves that implement it.

1. **No parallel procedure episode.** Unchanged from the ADR and from trauma lease §2a.
   `surgery-service` owns surgical *disease* — episode, condition, staging, indication, decision,
   outcome, surveillance. It owns no operative record and no theatre workflow, and references
   `inpatient.procedure_episode` per operation. If any reviewer finds a second operative record
   anywhere in `surgery-service`, that is a defect to raise, not a design variation.
2. **Handover acceptance.** On disposition `TO_THEATRE` / `ADMIT_SURGERY` the emergency episode
   stays `OPEN_AWAITING_ACCEPTANCE` until the accepting side writes back. **Creating the
   `procedure_episode` is the acceptance**: this programme calls
   `POST /v1/emergency/handovers/{id}/accept` with `accepting_ref = procedure_episode_id`.
   - Implemented at the single funnel — `ensureProcedureEpisode` — so every entry path
     (booking, waiting list, emergency intake) accepts, and no path can create an episode
     without accepting.
   - **Idempotent and keyed**, because re-entry and replay are normal here.
   - **Best-effort on the outbound leg only**: an unavailable emergency service must never block
     the creation of an emergency surgical episode. A failed acceptance is retried from the
     outbox, never dropped silently.
   - Note for the emergency lane: emergency-surgery intake will now carry **two** callbacks —
     this acceptance, and the existing daidzai `THEATRE` phase registration from theatre W5b.
     They are independent and must both remain idempotent.
3. **Acute abdomen.** The emergency lane classifies and hands over; this programme owns surgical
   decision-making from that point. No surgical CDS content will be built on the eleven
   `V005__ed_emergency_pathways.sql` UUIDs until the emergency lane's W4 repair lands — eight
   have zero `pathway_steps` and four cite the wrong source section.
4. **Standards traceability.** Both packs write governed content into CKP, and the coverage guard
   now reads **per-domain** registers at `docs/clinical-governance/<domain>/coverage-exclusions.json`
   rather than only the rmnp file. Content only counts as covering a standard when it carries
   `dakRef` plus `adaptation`; a rule with neither leaves its standard sitting at `UNCOVERED` and
   fails the guard.

## 4. Test-runner law — 118 dead integration tests

`maven-failsafe-plugin` is configured **nowhere** in the repository, and Surefire's default
includes (`*Test.java`, `Test*.java`, `*Tests.java`, `*TestCase.java`) exclude `*IT.java`. So all
**118** `*IT.java` files under `services/` never execute. They are a false coverage signal, not
coverage.

**Law for this programme: every test is named `*Test.java`.** No new `*IT.java`, ever.

Fourteen dead tests sit in services this programme touches:

| Service | Dead test |
|---|---|
| `inpatient-service` | `InpatientClinicalDepthIT`, `InpatientTenantIsolationIT`, **`ProcedureEpisodeIT`**, `WardRoundIT`, `GoldenContractIT` |
| `oros-service` | `OrosGoldenContractIT` |
| `scheduling-service` | `SchedulingSlotServiceIT` |
| `mvumo-service` | `MvumoCrossServiceFlowIT` |
| `varapi-service` | `VarapiGoldenContractIT` |
| `vashandi-workforce-service` | `VashandiWorkforceIT` |
| `clinical-knowledge-platform-service` | `ClinicalKnowledgePathwayApiIT` |
| `zibo-service` | `ZiboGoldenContractIT` |
| `tuso-service` | `TusoGoldenContractIT` |
| `inventory-service` | `InventoryGoldenContractIT` |

**`ProcedureEpisodeIT` is resurrected in P4** — it tests the exact aggregate P4 generalises, so it
becomes a regression guard rather than decoration. The rename is scoped to that one file; a
repo-wide resurrection is a separate concern and belongs to whichever session owns that debt.

## 5. Regression gate — the ten theatre rigs

Any wave touching `inpatient-service` must leave all ten green:

```
scripts/runtime-proof/theatre-{elective,elective-completeness,clinical-safety,commodities,
recovery-reporting,emergency,alt,authz,persistence,queue-drainage}-journeys.sh
```

Recorded live results at programme open: elective 36/36 · clinical-safety 18/18 · commodities
23/23 · elective-completeness 14/16 (2 known amber board assertions) · recovery-reporting 16/16 ·
emergency 26/26 · alt 34/34 · authz 11/0 · persistence 5/0 · drainage 14/14.

**P4 is the highest-risk wave in the programme** — it generalises a 2,072-line service behind
those rigs.

## 6. Obligations deferred to specific waves

Recorded here so they cannot be quietly dropped:

- **P0 / S0** — registry rows. `docs/registry/services-registry.yaml` is generated from
  `services/pom.xml` plus the override map in `scripts/registry/seed-registry.mjs`, and
  `system-of-record-map.md` is generated from the YAML. So both new services get their entries
  *with* their pom modules, mirrored into the override map, then regenerated. Adding YAML rows for
  a module that does not exist would be dropped on the next regeneration.
- **P0 / S0** — the `inpatient-service` system-of-record row is corrected to state that it owns
  perioperative **and procedure execution**, per the ADR.
- **P0 / S0** — full new-service wiring: `pom.xml`, Dockerfile, image-strategy lane,
  `config/full-boot-service-classification.yml` (a regeneration silently undeploys anything
  missing), the build/containerisation/Helm deployability matrices, Helm chart and values,
  docker-compose, `envoy.yaml` route, tshepo-authz policy rows, `event_outbox` and Kafka topic,
  experience-bff proxy group, offline-sync scope, observability scrape.
- **P5** — `SPECIALLY_PROTECTED` is decorative. One production reference,
  `ResourceSensitivityClassifier:53`, collapsing it into `FULL_IDENTIFIED_CLINICAL`. Adolescent
  confidentiality cannot be honestly built on it: either it is fixed first or P5 ships an explicit
  PARTIAL that says so.
- **P12 / S14** — the "financial state must never delay emergency surgery" invariant needs a rig
  assertion, not prose.

## 7. Known defects inherited, not caused

- `PolicyEngine` DENY ignores conditions.
- tshepo-authz V029/V030 use a trailing-slash `path_contains` pin; `pathContainsSegment` is
  segment-bounded, so the pin may not match `/theatre/cases/{id}/...`. Possible latent PDP defect,
  flagged by the theatre programme and still unfixed.
- MADI's ~40 entities share an undefaulted-jurisdiction pattern (trauma-owned).
- Theatre carry-forwards: two amber elective-completeness board assertions, commodities UI panels
  backend-only.

## 8. Wave index

Phase 0 audit and baseline (0.1–0.4) · Phase P pipeline (P0–P15) · Phase S surgery (S0–S18).
Full plan in the programme plan document; per-wave status is tracked in the pack completion
reports.
