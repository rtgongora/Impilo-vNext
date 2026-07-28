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

Adjacent reservation does not survive on this tree. **This programme owns the `V300`–`V329` band
in every service it co-edits.** New services of its own start at `V001`.

### Why a distant band rather than a range above the head

Three ranges died under their owners in a single hour on 2026-07-26: this programme's `tuso`
V042–V048 (V042 and V043 had already landed, one of them in this programme's own anchor commit),
the emergency lane's `pct` V070–V099 (V100 landed 84 seconds before they measured), and the older
`inpatient` V037–V064 block. The failure is structural, not careless: a head is a measurement, and
on a tree with five-plus concurrent writers the measurement is stale before the reservation is
written down.

Numeric distance fixes what adjacency cannot. Nothing incremental reaches V300, so the band cannot
be overtaken by a lane reserving just above a head; ownership is legible from the version alone;
and Flyway sorts by version with gaps costing nothing. Verified 2026-07-26: the highest migration
anywhere in the repository is `pct` V100, and no V2xx or V3xx exists.

| Band | Owner |
|---|---|
| `V2xx` (V200–V229) | Emergency / Resuscitation / Acute Care pack |
| **`V300`–`V329`** | **this programme** |

| Service | Head at adoption | Reserved for this programme |
|---|---|---|
| `procedures-service` | — | V001–V030 (new service) |
| `surgery-service` | — | V001–V030 (new service) |
| `inpatient-service` | V066 | **V300–V329** |
| `oros-service` | V017 | V300–V329 |
| `scheduling-service` | V003 | V300–V329 |
| `referral-service` | V002 | V300–V329 |
| `mvumo-service` | V008 | V300–V329 |
| `clinical-knowledge-platform-service` | V006 | V300–V329 |
| `zibo-service` | V007 | V300–V329 |
| `tuso-service` | V043 | V300–V329 |
| `inventory-service` | V014 | V300–V329 |
| `varapi-service` | V038 | V300–V329 |
| `vashandi-workforce-service` | V008 | V300–V329 |
| `reporting-service` | V003 | V300–V329 |
| `costing-engine-service` | V024 | V300–V329 |
| `tshepo-authz-service` | V047 | V300–V329 |
| `notification-service` | V017 | V300–V329 |
| `coverage-service` | V020 | V300–V329 |
| `pct-service` | V100 | **none — this programme writes nothing in pct** |

The earlier adjacent claims (`inpatient` V067–V080, `oros` V018–V024, `ckp` V007–V020, `tuso`
V044–V049 and the rest) are **released**. Any lane that wants them may take them.

`inpatient` V037–V064 remains dead space — verified empty, the files jump V036 to V065 — and stays
unclaimed to preserve the historical record.

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
4. **Site and side is a sequencing constraint, not a note.** The emergency lane has sequenced its
   procedures-invocation wave **after this programme's P4**, and holds that a lateralised
   emergency procedure may not ship with laterality as free text. Their framing is sharper than
   mine and is recorded here because it should drive P4's design: elective surgery has a consent
   form, a marked limb and a scheduled Time Out. Chest drain, needle and tube thoracostomy,
   central and arterial line, thoracotomy, joint reduction, escharotomy and burr hole are done at
   the bedside, at speed, often on an unidentified patient, by a lone clinician with no second
   checker and no marking step. **P4's structured site and side must therefore work without a
   second checker and without a marking step** — a design constraint, not a nice-to-have. This
   programme flags before merging into `inpatient-service` so the emergency lane can re-run
   against the real field rather than building a parallel one.
5. **Standards traceability.** Both packs write governed content into CKP, and the coverage guard
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

- **P0 / S0 — registry rows: DO NOT REGENERATE.** Discovered at P0. `services-registry.yaml` is
  nominally generated from `services/pom.xml` plus the override map in
  `scripts/registry/seed-registry.mjs`, and `system-of-record-map.md` from the YAML — but the
  committed YAML has drifted well ahead of what the generator can reconstruct. Running
  `node seed-registry.mjs` rewrote **894 lines for a one-service addition**, stripping curated
  `continuum` fields, inline doctrine rationale and `must-not-*` comments from services including
  booking, CKP, community, participation, telemonitoring and ABIS. The seeder's own comment says
  the override map exists "so a future regeneration cannot destroy the hand-edited YAML"; it does
  not fully succeed.
  **Therefore: hand-edit the YAML and the SoR map, and mirror into the override map so a future
  regeneration is less lossy than it would otherwise be.** Never commit the output of a bare
  regeneration without diffing it for destruction first. Same applies to
  `generate-architecture-registers.mjs`.
  `config/full-boot-service-classification.yml` is generated *from* the registry and is currently
  dirty with another session's uncommitted regeneration, so this programme does not touch it —
  the new services are picked up by whoever next regenerates it from the committed registry.
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
- **tshepo-authz V029/V030's trailing-slash `path_contains` pin — CONFIRMED, not merely
  possible.** The theatre programme flagged this as a "possible latent defect" from reading a
  comment; Wave P-R proved it directly by reflection into the real `pathContainsSegment`
  (`PathContainsSegmentTest`, `tshepo-authz-service`). `pathContainsSegment` requires the
  character immediately after a match to be `/` or end-of-string. A pin ending in `/` (V029's
  and V030's own style, e.g. `"/theatre/cases/"`) can only satisfy that when the path ends
  exactly there — it never matches `/theatre/cases/{id}/...`, because the character after the
  match is the first character of the id segment. **This means V029/V030's ALLOW rules using
  that pin style do not do what their own comments say they do.** Not fixed by this programme —
  V300 avoids the pattern rather than touching V029/V030's rows — but it is no longer merely
  suspected. Whoever owns V029/V030 should re-verify what those rules actually gate.
- **A second, related PDP integration trap found and documented, not fixed at the source**:
  `AuthzInternalRequest.deriveResourceType` walks a request path backward and returns the first
  segment that is not blank, not `v1`/`api`, and not a 36-character UUID. Any route whose final
  path segment is a human-readable free-text identifier — not a UUID — derives that identifier
  as the resource type instead of the intended resource name, and no policy rule for the
  intended resource is ever found. procedures-service's catalogue codes (`PROC-LAPAROTOMY` etc.)
  hit this; any future route keyed on a non-UUID code will hit it too. Worked around here by
  route shape (the code travels as a query parameter, never a path variable) rather than by
  changing the shared derivation logic, which is estate-wide and not this programme's to change
  alone.
- MADI's ~40 entities share an undefaulted-jurisdiction pattern (trauma-owned).
- Theatre carry-forwards: two amber elective-completeness board assertions, commodities UI panels
  backend-only.

## 8. Wave index

Phase 0 audit and baseline (0.1–0.4, done) · **Wave P-R reachability (done, 7/7 — see §9)** ·
Phase P pipeline (P0–P13 done — P13 partial by design, see §17 — P14–P15 remaining — see
§10–§12, §14–§17) · **Wave P-R2 reachability re-wire (done — see §13)** · Phase S surgery
(S0–S18, not started). Full plan in
the programme
plan document; per-wave status is tracked in the pack
completion reports and in programme memory (`surgery-procedures-program-state.md`).

## 9. Wave P-R — reachability (done)

Opened when six landed backend waves (P0–P6) turned out to have no BFF proxy, no envoy route,
no authz rows, no UI, and — most consequentially — no entry in
`config/full-boot-service-classification.yml`, so a full boot would not have built or deployed
`procedures-service` at all. Closed the gap in seven pieces (P-R.1–P-R.7); full detail in
programme memory. Built in a dedicated worktree (`procedures-p-r-reachability`, kept on disk per
the never-remove-worktrees law) rather than the shared checkout, because this branch was
measured at 150 commits/day across 12+ concurrent lanes during the wave — a long-lived isolated
branch would have diverged badly, so each piece was committed, rebased onto the current tip, and
pushed individually as it went green.

**Findings worth any future wave reading before it touches the same files:**

- Two generated Helm files (`values-full-preview-runtime.generated.yaml`,
  `values-full-preview-bff-env.generated.yaml`) carry hand-authored content their own generators
  cannot reconstruct — regenerating the first dropped `pct-service`'s `secretEnv` block for its
  Keycloak service-to-service token. Diff any regeneration against a pre-run snapshot before
  trusting it; the safe default is to hand-add the one entry a wave needs.
- `docker-compose.runtime.yml` is a curated ~25-service bootstrap subset that excludes
  `inpatient-service` itself. Do not add a service there on the assumption that "real" services
  belong in compose — most do not.
- `/internal/v1/` already routes generically to `experience_bff` in `infra/envoy/envoy.yaml`.
  A new BFF-only service proxied under that prefix needs no envoy change.
- `ServiceClientConfig.ServiceEndpoints` in `experience-bff` is a positional record with two
  hand-maintained test factories sized to its exact field count. Adding a field means extending
  both factories by exactly one slot; the compiler catches a missed one immediately on
  `mvn test`.
- Journey specs under `ui/one-ui-shell/e2e/journeys/**` are excluded from the main app's
  `tsconfig.json` and must be checked against `tsconfig.e2e.json` instead — that config exists
  specifically because specs were previously "never compiled, never run", and it will catch an
  invented `AcceptancePoint` name that the main typecheck cannot see.
- A worktree's symlinked `node_modules` (pointing back into the main checkout, per
  `worktrees-need-node-modules-symlinked`) can be silently clobbered by `git stash -u` or a
  rebase mid-session. If a typecheck or test run in a worktree looks suspiciously fast or
  trivially clean, verify the symlink still resolves to real content before trusting the result.

## 10. Wave P7 — safety-pause templates and sedation continuum (done)

§9 (ten class-specific safety-pause templates, rows-not-columns confirmation items) and §10
(eight-level sedation continuum with the rescue-capability chain) landed as a read-only layer on
`procedures-service`, following the same worktree cycle as Wave P-R
(`procedures-p7-safety-pauses`, kept on disk). No reachability wiring was needed — this is
backend-internal content resolution with no new BFF/UI surface yet; it will be picked up whenever
a caller (the aftercare or readiness engine, or a future clinician surface) needs it, at which
point the P-R.4/P-R.5 route-shape and authz pattern below applies unchanged.

**Two real seed defects caught by the new runtime-proof rig, not by review:**
`scripts/runtime-proof/procedures-safety-pause-journeys.sh` asserts the migration's own stated
invariant — every template carries PATIENT, PROCEDURE and CONSENT as its irreducible minimum —
against actual seeded rows rather than trusting the comment that states it.
`SAFETY-PAUSE-TRANSFUSION` was missing PROCEDURE and `SAFETY-PAUSE-DIALYSIS` was missing CONSENT;
both fixed in the V005 seed before the commit that carries this section. Neither H2-based module
test would have caught this — the module tests exercise the service's read logic against
hand-inserted fixtures, not the shipped seed content itself.

**The route-shape trap from P-R.4 was applied pre-emptively, not rediscovered.** The initial
`SafetyPauseController` draft used `{templateCode}`/`{levelCode}` REST path variables — exactly
the shape `AuthzInternalRequest.deriveResourceType` (§7 above) mis-derives a resource type from.
Rewritten to query-param shape (`?code=`) before the controller was ever wired to authz, so
whichever wave next proxies these routes through the BFF inherits routes that are already safe
to pin, rather than a second instance of the same defect reaching a migration.

**depth_rank is deliberately not unique.** `NO_SEDATION`/`NON_PHARMACOLOGICAL` both rank 0 and
`GENERAL_ANAESTHESIA`/`REGIONAL_ANAESTHESIA` both rank 5 — parallel techniques measured on
different axes (drug depth vs. block extent), not points on one strict scale. An earlier draft of
the migration declared `UNIQUE (tenant_id, depth_rank)`, caught and removed in self-review before
the migration was ever run against Postgres.

Proof: `SafetyPauseAndSedationServiceTest` (9 tests, H2 — service read/resolve logic) +
`procedures-safety-pause-journeys.sh` (22/22, real Postgres — CHECK constraints, the composite
rescue-capability FK, the depth_rank non-uniqueness, both seed defects above). Module regression:
36/36.

## 11. Wave P8 — specimen chain of custody and implant removal/revision lifecycle (done)

Closes §13 (specimens) and §14 (devices/implants), both rated THIN/absent by the Phase 0 audit.
Unlike every prior P-wave, `procedures-service` itself is untouched — the gap was in the two
services that already own these SoRs, so the fix landed there: `inpatient-service` V301
(`procedure_specimen` custody/label-confirmation/adequacy, new `SpecimenCustodyService`) and
`inventory-service` V300 (`inv_patient_implant` patient-facing fields + removal/revision,
extending `ImplantTraceabilityService`) — inventory's first migration in this programme, joining
the V300-V329 band rather than starting a new one. Built in worktree
`procedures-p8-specimens-devices` (kept on disk).

**Honest scope, stated in the migration and service javadoc, not left implicit**: inpatient's
`TheatreService.processSpecimensFromNote` auto-collects and dispatches a specimen from the
operative note in one transaction, with no human confirmation point. This wave adds the
capability for a real person to RECORD collection, label confirmation, receipt and adequacy — it
does **not** wire a blocking gate into that automatic path, because doing so would either
auto-stamp a fake confirmation nobody made, or require redesigning theatre's dispatch flow, which
is theatre/emergency-lane workflow, not a unilateral change from this programme. Declared PARTIAL,
the same way P5 declared adolescent confidentiality PARTIAL rather than silently leaving it.

**Rig caught a constraint-evaluation-order surprise, not a schema bug**: Postgres validates every
CHECK on a row on every write, not just the one a test means to isolate. An UPDATE touching only
the field under test could trip a *different*, also-violated CHECK first (e.g. setting an invented
`status` alone tripped the removal/status-consistency CHECK before the vocabulary CHECK ever got a
chance to fire). Fixed by satisfying the other CHECKs in the same UPDATE so only the intended one
is left to fail — worth remembering for any future multi-CHECK table in this programme.

Two new REST surfaces (`TheatreController` specimen-custody routes, `ImplantController`
remove/revise routes) are backend-internal only, same as P7's `SafetyPauseController` — no new
authz/BFF/UI wiring this wave; both are queued for the next reachability pass.

Proof: `SpecimenCustodyServiceTest` (11 tests) + `ImplantTraceabilityServiceTest` extended (+5).
`procedures-p8-specimen-device-journeys.sh` (19/19, real Postgres, whole migration chain applied
to both services). Module regression: inpatient 154/154, inventory 115/115.

## 12. Wave P9 — recovery settings and aftercare templates (done)

Closes §15 (recovery, PARTIAL) and §17 (aftercare, ABSENT), both named as `procedures-service`'s
own responsibility in the boundary ADR. Built in worktree `procedures-p9-recovery-aftercare`
(kept on disk). procedures V006: `recovery_setting` (5 rows), `aftercare_template` +
`aftercare_instruction` (rows-not-columns) + `aftercare_template_channel` (a join, not a column).

**Honest sourcing, not fabricated precision**: the audit paraphrases the source spec as declaring
"twelve outputs and five delivery channels" for aftercare, but the literal enumerated list was
never vendored into this repository. The five delivery channels reused here come from this
programme's own prior audit (`audit.md` line 175), not a fabrication; the thirteen instruction
kinds are an engineering baseline grounded in R15's governing standard
(`RESULTS.CRITICAL_ACKNOWLEDGEMENT`), NOT a claimed reproduction of the spec's literal list — every
seeded template is flagged `content_maturity='ENGINEERING_SEED'` and the rig proves that flag
can't silently read `RATIFIED`. Repeats the P1 SNOMED lesson for a taxonomy instead of a code.

Same route-shape law applied pre-emptively a third time (`?code=`, never a path variable).
`RecoveryAndAftercareController` (recovery-settings, recovery-setting-detail, aftercare-templates)
has zero authz/BFF/UI wiring, same as P7's `SafetyPauseController` — both queued for Wave P-R2
below, which this wave triggers (P7+P8+P9 = three backend waves since Wave P-R, per the plan's own
"re-wire every third wave" rule).

Proof: `RecoveryAndAftercareServiceTest` (9 tests) + `procedures-recovery-aftercare-journeys.sh`
(22/22, real Postgres). Module regression: 45/45.

## 13. Wave P-R2 — reachability re-wire for P7+P9, plus a real defect fix (done)

Closed the authz/BFF/UI gap for P7's `SafetyPauseController` and P9's
`RecoveryAndAftercareController` — six routes total, all already query-param shaped (P7/P9 built
them that way from the start, so no route-shape workaround was needed here unlike catalogue-detail
in V300). tshepo-authz V301 (30 ALLOW rows), BFF client+controller extended, UI panels added to
the EXISTING `/work/clinical/procedures` detail view rather than a new page — `procedure_definition`
already carries the linkage codes (`default_sedation_level_code`, `default_recovery_setting_code`,
`default_aftercare_template_code` from P7/P9, plus the pre-existing `safety_pause_template`/
`aftercare_template` columns from V002) to key off.

**Cross-lane finding, not mine to fix alone but fixed enough to unblock this wave**:
`scripts/runtime-proof/procedures-authz-journeys.sh` (committed in P-R.4) was MISSING from this
worktree's fresh checkout. Traced to commit `90e64207f` ("feat(org-registry): source-pack seeder…
(NCZ-W1A)") — a 65-file diff with no stated reason to touch a procedures-service rig script,
almost certainly a broad non-path-scoped commit sweeping up another session's local deletion
(exactly what `shared-index-commit-law` in memory warns against). Recovered the file VERBATIM via
`git show <origin-commit>:<path>`, not rewritten from memory, then extended it for V301. Flagged
via `spawn_task` (task_b04ee443) for someone to audit whether that commit dropped anything else;
not blocking.

**A second real defect, found while wiring the UI and fixed forward** (V006 was already pushed,
so this is a new migration, not a rewrite): `procedure_definition.aftercare_template` has existed
since V002/V003 with 34 distinct SPECIFIC per-procedure codes (`AFTERCARE-LAPAROTOMY`,
`AFTERCARE-CENTRAL-LINE`, `AFTERCARE-LUMBAR-PUNCTURE`, …) — "a code with nothing to resolve to",
the exact state V005's own header describes for `safety_pause_template` before P7 correctly fixed
it by resolving that pre-existing column. P9 missed that `aftercare_template` already existed and
built a SEPARATE, coarser six-value taxonomy (`default_aftercare_template_code`) instead — a
parallel system, not the fix P7 already modelled. V007 (procedures-service) seeds five specific
templates for the same demonstration procedures P7/P9 already sedation/recovery-linked
(`AFTERCARE-LAPAROTOMY`, `AFTERCARE-CAESAREAN`, `AFTERCARE-ARTHROPLASTY`, `AFTERCARE-CENTRAL-LINE`,
`AFTERCARE-LUMBAR-PUNCTURE`); the remaining ~27 specific codes stay honestly unresolved — named as
a debt below, not silently closed. `default_aftercare_template_code` is NOT dropped (already
shipped) but is now documented as a coarse FALLBACK only, and the UI panel does real two-step
resolution: specific code first, coarse fallback labelled as generic second, "not declared" only
when both are genuinely absent.

Proof: `procedures-authz-journeys.sh` 11/11 (restored + extended for V301, including a direct
substring-collision check for the sedation-levels/recovery-settings vs their own -detail routes —
the same shape V300's catalogue/catalogue-detail pair already proved safe). BFF: 22 new tests +
full 1445-test regression, same 5 pre-existing TUSO-shift-lane failures verified via stash
isolation (zero new). `procedures-recovery-aftercare-journeys.sh` extended to 26/26 proving the
V007 fix at the row level. procedures-service module regression 45/45. UI: 14+3=17/17 (page +
integration tests), including three tests proving the two-step aftercare resolution (prefers
specific, falls back and labels generic, both-unavailable renders as unavailable not empty).

**New debt registered, not silently absorbed**: ~27 of `aftercare_template`'s 34 specific V003
codes remain unresolved (no template row). Same shape as the already-accepted "catalogue depth"
debt (only 13 of 66 procedures carry full requirement sets) — a content-population task for a
future wave, not a structural gap.

## 14. Wave P10 — complication profiles and Clavien-Dindo severity grading (done)

Closes §18 (complications, PARTIAL). Scoped by the ADR, not assumed: classification CONTENT is
procedures-service's own layer (engine-not-store, matching §9/§10/§15/§17); complication
EXECUTION records stay with the performing service (inpatient-service, the P8 precedent);
complication PATHWAY INSTANCES are named to `surgery-service` (port 8396, not yet built —
ADR decision 5/5a/6) and out of scope until that service exists; reopen-episode semantics is a
confirmed, real, separate gap in `inpatient.procedure_episode`'s own state machine (no
`REOPENED` state, no reopen method) — an inpatient-service change, not attempted here.

**Applied the P-R2 lesson from the start, not needing a fourth fix-forward migration**:
`procedure_definition.complication_profile` has existed since V002 with no seed value and no
resolving table — one step earlier in the exact "code with nothing to resolve to" state V005
found for `safety_pause_template` and V007 found+fixed for `aftercare_template`. V008 resolves
`complication_profile` against the pre-existing column directly, rather than building a parallel
one the way P9 (accidentally) did for aftercare.

**Two content axes, sourced and flagged distinctly, not blurred into one honesty level**: the
seven Clavien-Dindo severity grades are a REAL, literature-cited, internationally-used standard
(Dindo/Demartines/Clavien, Ann Surg 2004) — added to `standards-baseline.json` as
`SURGERY.CLAVIEN_DINDO.SEVERITY_GRADING`, registered `COVERED_ELSEWHERE` in
`coverage-exclusions.json` (implemented directly in procedures-service content; not a WHO DAK
artefact at all, so the traceability generator's SHIPPED path was never going to apply), and
seeded `status=PUBLISHED` with no `content_maturity` flag — this is real, not engineering-seed.
The complication TYPES within each profile have no sourced taxonomy in this repository — same
honest gap V006 already declared for aftercare instruction kinds — so `complication_profile`
itself carries `content_maturity='ENGINEERING_SEED'`, not claimed as a canonical nineteen-class
list nobody in this session has read.

Proof: `ComplicationProfileServiceTest` (8 tests) including a never-event-flag-surfaces-intact
test. `procedures-complications-journeys.sh` (25/25, real Postgres). DAK traceability guard
regenerated and green (149 artefacts, 8 COVERED_ELSEWHERE, 0 uncovered). Module regression: 53/53.

## 15. Wave P11 — IPC requirement depth (done)

Closes §21 (IPC + sterile processing), specifically the four standards this domain's own
`coverage-exclusions.json` committed to P11 BY NAME at Wave 0.4: `IPC.CORE.HAND_HYGIENE`,
`IPC.CORE.ASEPTIC_TECHNIQUE`, `IPC.CORE.STERILE_PROCESSING`, `IPC.CORE.INJECTION_SAFETY`. Their
own `revisitCondition` text already named the mechanism — "infection-prevention readiness as
catalogue requirements with named owners per unresolved item" — so this wave shipped that
literally: `requirement_kind` `IPC`/`STERILE_PROCESSING` have been valid since V002; no new
schema, service or controller was needed, only real content.

Five new requirement codes: `IPC-HAND_HYGIENE`, `IPC-PPE`, `IPC-SKIN_PREP`,
`IPC-INJECTION_SAFETY` (single-use + exposure-incident + waste folded into one, same WHO
guideline family) seeded universally across all 66 published entries — genuinely universal WHO
IPC core practices for any invasive intervention, which every catalogue entry is by
construction. `IPC-ASEPTIC` broadened from V003's original two-procedure seed to all 66
(`ON CONFLICT DO NOTHING` left the original rows untouched). `CSSD-REPROCESSING_LIMITS` (kind
`STERILE_PROCESSING`) scoped to THEATRE/ENDOSCOPY settings only (49 rows) — a reprocessed
instrument set or scope, not single-use bedside kit, is what reprocessing limits are about.

All four standards moved `DEFERRED` → `COVERED_ELSEWHERE`, each reason stating precisely what's
real and what remains named-but-absent: `STERILE_PROCESSING` still has no path for environmental
cleaning; `INJECTION_SAFETY`'s requirement is a DECLARATION only — the EXECUTION half (an actual
exposure incident routed to Rito) is inpatient-service's to build, the same content/execution
boundary P8 drew for specimens/implants and P10 drew for complications.

No new Java: `ProcedureCatalogueService` already exposes requirements via `CatalogueDetail` —
pure content depth into an already-reachable API. Extended `procedures-catalogue-journeys.sh`
(P1's own rig) rather than a new file, since the content lives entirely in
`procedure_requirement`.

Proof: 36/36 (7 new P11 assertions, real Postgres). DAK traceability matrix regenerated (149
artefacts, 12 COVERED_ELSEWHERE, 116 DEFERRED, 0 uncovered). Module regression: 53/53 unchanged
(H2 tests use hand-inserted fixtures, not the shipped seed).

## 16. Wave P12 — financial clearance gate (done)

Closes §23 (financial). Research before building found the audit's own framing misleading:
estimate/authorisation/denial/appeal are NOT absent — coverage-service and COSTA already have
that machinery (`AuthorisationService`, `EstimateService`, `AppealController`,
`ServiceAccessDecisionEntity`). What was genuinely missing was a CONNECTION:
`TheatreService`/`ProcedureEpisodeService` never referenced COSTA, payment or authorisation at
all, so "the emergency-rules invariant" was asserted in prose and enforced nowhere.

Per the ADR (procedures-service/surgery-service must never become "a second payment truth"),
this landed in **inpatient-service**, the P8 precedent (specimens→inpatient, implants→
inventory). `ProcedureEpisodeService.requireFinancialClearance`, called LAST in `startProcedure`
(after site-and-side and the full consent block, so it never reorders any existing gate):
EMERGENCY/IMMEDIATE triage bypasses before COSTA is even asked; otherwise COSTA is queried FRESH
via a new read-only `CostaServiceAccessClient` and only an explicit `BLOCKED_PENDING_PAYMENT`/
`BLOCKED_PENDING_AUTHORISATION` refuses the start. A block never cancels the episode — only
refuses the start attempt, true by the absence of any code path that could do otherwise.

`ECO.WHA76-2.FINANCING` (surgery's own baseline, `plannedWave: P12`) moved DEFERRED →
COVERED_ELSEWHERE: this wave shipped exactly what its own `revisitCondition` asked for. Honestly
scoped: estimate/authorisation as an ORIGINATING workflow (a cost estimate before booking, a
formal prior-authorisation request) is still not wired — this is the GATE that consults an
existing decision, not the origination of one.

**Cross-lane care taken**: `ProcedureEpisodeService`'s constructor gained a 17th parameter; the
two existing test files that directly construct it were updated; the emergency/theatre lane was
flagged per the plan's own workflow rule (`task_2c0d3f7f`) since this touches their shared,
heavily-tested `startProcedure` method. Full regression re-run AFTER rebase, not just before, to
catch anything concurrent lanes landed in the meantime — 164/164 both times.

Proof: `ProcedureFinancialClearanceTest` (10 tests). `procedures-financial-clearance-
journeys.sh` (9/9, real Postgres, whole inpatient migration chain). DAK matrix regenerated (149
artefacts, 13 COVERED_ELSEWHERE, 115 DEFERRED, 0 uncovered). Module regression: 164/164.

## 17. Wave P13 — FHIR Specimen resource (done, partial by design)

Closes only the SPECIMEN slice of §24 (interoperability — 8 resources named absent). Research
first: `ADR-SURGERY-AND-PROCEDURES-SERVICE-BOUNDARIES` §3 already delegates FHIR projection to
`butano-service`/`fhir-gateway-service`, and the real estate precedent (confirmed via `git log`)
is domain-service-authored — `oros-service` already built its own FHIR Observation writeback and
ImagingStudy mapping, not a centralised FHIR service doing all 8.

Of the 8 named-absent resources: ServiceRequest, Specimen, Device/DeviceUseStatement have real
DATA already (OROS orders; this session's own P8 specimen custody and implant lifecycle) — pure
mapping tasks. Task, DetectedIssue, GuidanceResponse, Provenance have NO adequate data model
anywhere yet — mapping them now would mean inventing content to have something to map, the exact
trap this programme has refused everywhere else (SNOMED, aftercare taxonomy, complication
classes). This wave closes ONLY Specimen — most directly connected to this session's own P8
work, in a service already deeply understood this session, using an EXISTING integration point
(`ButanoProcedureClient`, which already authors FHIR Procedure/DocumentReference for the same
episode).

`ButanoProcedureClient.writeSpecimen`: a real FHIR Specimen resource with a genuine vocabulary
TRANSLATION (REJECTED→entered-in-error, INADEQUATE→unsatisfactory), not a passthrough — the two
vocabularies have already diverged. `SpecimenCustodyService.recordCollection` writes it at the
point real collector/container data first exists, stores the ref in a new `fhir_specimen_ref`
column (V303, distinct from the pre-existing `butano_document_ref`, which is the eventual
pathology-report DocumentReference — a different resource, a different fact). Best-effort: a
Butano outage never blocks recording custody.

**Two real debts named, not silently absorbed**: ServiceRequest (oros-service, needs an outbound
FHIR authoring path — only inbound exists today) and Device/DeviceUseStatement
(inventory-service, which has ZERO existing FHIR/Butano integration to build on — a bigger lift
than Specimen's, which only needed a new method on an already-existing client). §25 (offline) is
ALSO not touched this wave — the ADR names `offline-sync-service`/`offline-edge-service`/
`tshepo-offline-service` as the owner, and "procedure scope" concretely means new `actionType`
constants in `OfflineRulesEngine`'s hardcoded switch (not a config table), which is that lane's
own mechanism to extend, not procedures-service's.

Proof: `ButanoProcedureClientTest` (6 tests) proves the actual FHIR resource shape — status
derivation both directions, optional-block omission, custody notes, best-effort null-on-outage.
`SpecimenCustodyServiceTest` extended (+3). `procedures-fhir-specimen-journeys.sh` (6/6, real
Postgres). Full inpatient-service regression: 173/173 (164 prior + 9 new), re-run both before
and after rebase.
