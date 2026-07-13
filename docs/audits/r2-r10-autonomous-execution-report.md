# R2–R10 Autonomous Execution Report

**Started:** 2026-07-13 · **Mode:** autonomous (operator away) · **Branch:** `claude/staging-ux-orchestration-remediation-Yypyl`
**Companion:** [`docs/roadmaps/functional-depth-remediation-blueprint.md`](../roadmaps/functional-depth-remediation-blueprint.md), [`docs/audits/functional-depth-gap-register.md`](functional-depth-gap-register.md)

Per operator instruction: continue wave by wave to R10 unattended. Critical decisions → take the blueprint's recommendation, log the alternatives here. Issues needing operator input → defer and move on unless they block forward progress; log them in **§ Deferred for operator**. Everything is code-only (no deploy); verified by compile + tests, committed and pushed per increment.

## Progress ledger

| Wave / gap | Status | Commit(s) | Notes |
|---|---|---|---|
| R0 QA (10/16) | ✅ done | up to `3befcd0a5` | 8 code bugs + 2 UX; 6 deferred (env/deploy-drift) |
| R1 G1 inpatient | ✅ done | up to `8528b7a9c` | admission→discharge unblocked incl. tails (audit, e2e, admit-here) |
| R1 G5 teleconsult Stage-4 | ✅ done | `d2ebf0170` + worklist | governed accept/decline both surfaces + specialist worklist |
| R2 W0 numeric-ID bridge | ✅ done | `48ac82f47` | ProviderResponse.providerId |
| R2 G7 certificates | ✅ done | `f124f2213`, `f5b77fe4c`, `10c4a3f00`, `7db73c2f8` | engine status-gate + BFF + self-service + registrar UI |
| R2 G10 lifecycle console | ✅ done | `487396555`, `9a4230828` | transition matrix + BFF + Provider-360 panel |
| R2 G8 licence renewal | ✅ done | `2b0d02b5a`, `4ddc4415e` | sweep sets DUE/LAPSED + start-renewal/restoration + UI |
| R2 G9 disciplinary+compliance | ✅ done | `bc6177ec8`, `db7444c81`, `c08a3cd43` | disciplinary engine (PIC propagation) + compliance/disciplinary BFF + provider-360 panel |
| R2 G11 credentials wiring | ✅ done | `ab07af81c` | qualifications + practice-contexts full BFF+UI+tests; affiliation/privilege client methods added (UI deferred) |
| R2 G30 PIC seam | ✅ done | `385e3f7f8` | SoR split affirmed (TUSO=assignment, VARAPI=eligibility snapshot); deprecated orphan writers; snapshot surfaced on UI |
| **R2 COMPLETE** | ✅ | — | VARAPI surfacing wave (G7–G11 + G30) all landed + pushed |
| R3 G2 subsidy wiring | ✅ done | `f9faa2aa5`, `d63920430` | BFF enrol/consume/exemption + UI value-lane section + exemption list |
| R3 G15 preauth decision | ✅ done | `f9faa2aa5`, `d63920430` | BFF `PUT /preauth/{id}/decision` (cap-denial/409 passthrough) + UI reviewer Approve/Deny |
| R3 G3 subsidy model | ⚠️ deferred (safe part done) | this commit | disambiguation doc landed; physical merge = operator architecture decision (see below) |
| R4 khuluma | pending | — | G31 delegate → G6 paging → G13 broadcast |
| R5 teleconsult completion | pending | — | G17 orders → G18 scheduling → G33 richness |
| R6 indawo geography | pending | — | G4 catchment engine → G21 geocoding |
| R7 PCT+VITO surfacing | pending | — | G12 sorting desk, G16 death chain, G14 relationships, G28 screens |
| R8 imaging | pending | — | G19 defer-doc, G22 reporting UI, G20 recording writeback |
| R9 mobile prod path | pending | — | G23-27 (CI/infra — mostly document + config, can't build APK here) |
| R10 hygiene + prod config | pending | — | G29, G32, G34, G35 |

## Key decisions (autonomous)

**G8 licence renewal:**
- *Sweep runs without a TrustContext* → writes lifecycle directly (set + deriveStatusProjections + save + outbox) rather than through the request-scoped ProviderService.transitionLifecycle (which calls TrustContextHolder.require()). Alternative considered: set a synthetic system TrustContext (pattern exists in ProviderPaymentObligationService); rejected as heavier for a pure system job.
- *Renewal window = 60-day constant.* No renewal_window_days field exists on CouncilRegulatoryConfigEntity (only JSONB blobs). Alternatives: parse workflowHintsJson (untyped, fragile) or add a migration column. Deferred config override; constant is `varapi.licence-sweep`-adjacent and easily promoted later.
- *Fee-obligation creation NOT coupled to renewal-start.* The obligation lane (ProviderPaymentObligationService.createObligation + MusheX intent) is policy-gated and can fail closed; coupling it into start-renewal would let a policy/gate failure block the lifecycle transition. Kept in the existing council-obligation UI lane. Alternative: best-effort try/catch obligation creation inside start-renewal — viable follow-up.
- *Notices lane left event-driven.* varapi `/notices` returns a hardcoded empty list (stub); the sweep emits `varapi.provider.licence_due`/`lapsed` outbox events. Converting `/notices` to derive from lifecycle_status + licence validTo is a clean follow-up but out of G8 scope.
- *Renewal transitions bypass the operator LIFECYCLE_TRANSITIONS matrix* (which intentionally excludes renewal arcs) — they are system/renewal-driven via LicenseService with explicit source-state guards.

**G3 subsidy duplicate model (register framing was wrong; merge DEFERRED):**
- *Exploration corrected the register.* The register grouped "V010 enrolments+balances vs V011/V012 enrollments+drawdowns." Actual split: Model X = `cv_subsidy_enrolments`+`balances`+`drawdowns` (V010 **and V011** — V011 drawdowns FK to the British-spelling enrolments), the value/annual-cap money lane keyed by `member_cpid`; Model Y = `cv_subsidy_enrollments` (V012, double-L), the exemption-category costing lane keyed by `client_id`. **Neither is orphan/dead** — each has distinct live consumers (Model X: SubsidyController `/enrolments*` + eligibility headroom; Model Y: SubsidyController `/enrollments*` + CoveragePlanController patient-category). No data bridge between them.
- *Decision: defer the physical merge, do the safe part now.* A true merge must fold `exemption_category` into Model X (or add a cap to Model Y) AND repoint CoveragePlanController AND reconcile the key spaces (`member_cpid` vs `client_id` — these may not be the same identifier universe). That is a data-ownership/architecture call with real migration risk, and it does NOT block G2/G15 (the models are cleanly separated at the DB layer). So I landed authoritative **disambiguation** (SoR-map note + the pre-existing controller lane comments) so nobody wires the wrong lane, and G2 wires *both* lanes explicitly labelled. **Merge itself → § Deferred for operator.**
- *G2 scope:* wired Model X (enrol + list-with-balance + consume) and Model Y (exemption enrol/list) through BFF + UI. The cap-enforced `consume` is exposed but the drawdown trigger in production is the billing/costing rail, not this admin surface — the UI surfaces balance/consumed/remaining and an admin enrol; it does not fabricate a "spend" button beyond the honest consume endpoint.
- *Subsidy outbox events NOT added.* The map showed subsidy mutations emit no outbox (preauth does). Adding a SUBSIDY aggregate + topic is a clean follow-up; not done here to keep the wave focused and avoid touching the publisher topic map unattended. Logged as a follow-up, not a blocker.

**G15 preauth reviewer decision:**
- *Reviewer queue = per-member, not cross-member.* The engine's only preauth list is member-scoped (`GET /preauths?member_cpid=`); no cross-member "all pending" endpoint exists. Rather than add a new engine endpoint unattended, I surfaced Approve/Deny on the existing per-member preauth rows (status PENDING). A dedicated cross-member reviewer worklist is a follow-up (needs a `GET /preauths/pending` engine route). Alternative considered: add the pending-list endpoint now — deferred to avoid scope creep + an un-specced query semantics decision.
- *Cap-denial surfaced honestly.* The engine can flip an APPROVED submission to DENIED (UTILIZATION_LIMIT_EXCEEDED); the BFF passes the engine's 4xx/409 through verbatim (`upstreamWrite`) so the reviewer sees the real outcome.

**G30 PIC system-of-record split (the key decision):**
- *Direction reversed from the register's first framing.* The register proposed a new VARAPI listener on `tuso.facility.pic.activated`. Exploration disproved that as the right move: the richer HPA-2017 nomination FSM, the BFF surface, and the UI all already treat **TUSO as SoR for the facility-effective assignment**, and TUSO's own `PicNominationService.activate()` writes the assignment. VARAPI's `PractitionerInChargeController` is an orphan parallel writer nothing surfaces. Adding a new VARAPI writer would *perpetuate* the dual-writer contradiction, not resolve it.
- *Chosen resolution:* **affirm the split** — TUSO owns the facility-effective PIC assignment; VARAPI is SoR only for the point-in-time eligibility-assessment snapshot each nomination captures verbatim. Documented in both `services-registry.yaml` and `system-of-record-map.md`.
- *Deprecate, don't delete.* VARAPI's 5 PIC write endpoints get `@Deprecated` + javadoc (behaviour unchanged — safe, non-breaking) rather than removal, to avoid breaking any unknown legacy caller. TUSO's `VarapiPicAssignmentConsumer` javadoc updated to mark it vestigial (kept idempotent for historical reconciliation). Alternative considered: hard-delete the endpoints + consumer — rejected as riskier during an unattended run; retirement is a clean follow-up once legacy assignment records are confirmed drained.
- *UI surfacing via the inline snapshot.* The nomination view already carries `eligibilitySnapshot` inline, so the page renders axes/reasons directly (no round-trip needed). The new BFF pass-through (`GET /pic-nominations/eligibility-snapshots/{snapshotId}` → VARAPI SoR) is wired as an on-demand "verify against registry" affordance, gated on an optional `eligibilitySnapshotRef` — if the backend DTO doesn't populate the ref, the button simply never shows (no dead UI). This keeps the SoR-audit seam real without inventing a required field.

**G11 credentials wiring:**
- *Scope split.* Qualifications + practice-contexts get full BFF+UI (the genuinely-dark 0-UI gaps registrars need). Affiliation-write and privilege-decide got VarapiServiceClient methods (reachable) but no UI panel — deferred as a documented follow-up, because a `useProviderPrivileges.ts` hook already exists and affiliations surface elsewhere; a dedicated panel is additive, not a journey unblocker.
- *Practice-context ops restricted to authorize/revoke/renew* at the BFF (rejects unknown ops 400) rather than forwarding arbitrary op strings — keeps the surface curated and matches PracticeContextController.
- *No new outbox events at BFF;* governance captured via ProviderRegistryAuditHelper (consistent with G7/G9). Engine-side outbox for qualification-verify is a follow-up if required.

**G9 disciplinary + compliance:**
- *A recorded SUSPENSION/REVOCATION disciplinary action also writes a DisciplinaryActionEntity (ACTIVE)* — because PicEligibilityAssessmentService reads that (separate) table, not the case table. This makes the sanction actually block PIC eligibility (the propagation the register/rig cares about).
- *Disciplinary-narrative masking left to the gateway rego,* consistent with the rest of RegistryController (which relies on ext_authz, not BFF field-masking). Alternatives: BFF-level field stripping on the list (mask summary/finalRecommendation) or a role-header check — both deferred as documented follow-ups. Chosen for consistency + to avoid fragile BFF role logic.
- *Compliance controller-wired methods emit no outbox events* (pre-existing gap; only the older createAction/resolveAction do). Left as-is — governance is captured by the BFF audit; adding outbox to the wired methods is a follow-up.
- *ComplianceActionType/DisciplinaryTriggerType enums are not bound to their entities* (stored as raw strings). The UI constrains to valid values; server-side enum validation is a follow-up.

## Deferred for operator

_(appended as encountered — issues genuinely needing operator input that did not block forward progress)_

**[R3/G3] Subsidy data-model merge — architecture decision.** coverage-service has two live subsidy enrolment models (Model X value/cap money lane keyed by `member_cpid`; Model Y exemption-category costing lane keyed by `client_id`) sharing `cv_subsidy_programs`, with no bridge. They are NOT a simple duplicate — each serves a distinct consumer. Options for the operator/architect:
  1. **Keep both, formalise the boundary** (what I did, non-destructively): disambiguation doc + explicit lane labelling in code/BFF/UI. Lowest risk; the "duplication" is conceptual not conflicting. *Recommended as the durable answer unless a unified subsidy record is a product goal.*
  2. **Merge into Model X**: add `exemption_category` (nullable) to `cv_subsidy_enrolments`, backfill from `cv_subsidy_enrollments`, repoint `CoveragePlanController.resolvePatientCategory` to read it, retire Model Y. Requires resolving whether `client_id` (Model Y) and `member_cpid` (Model X) are the same identifier space — if not, the backfill is unsafe.
  3. **Merge into Model Y**: give the exemption lane a cap/balance — larger rebuild of the atomic-drawdown machinery; not recommended (Model X already has correct H1/H2 money semantics).
  Blocking? **No** — G2/G15 wired around it. Needs operator input only to decide whether a physical merge is desired and to confirm the identifier-space question.
