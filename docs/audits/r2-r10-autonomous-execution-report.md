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
| R2 G11 credentials wiring | pending | — | qualifications/practice-contexts/affiliations/privileges |
| R2 G30 PIC seam | pending | — | consume tuso.facility.pic.activated + deprecate + snapshot |
| R3 coverage subsidy | pending | — | G3 reconcile → G2 wire → G15 preauth |
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

**G9 disciplinary + compliance:**
- *A recorded SUSPENSION/REVOCATION disciplinary action also writes a DisciplinaryActionEntity (ACTIVE)* — because PicEligibilityAssessmentService reads that (separate) table, not the case table. This makes the sanction actually block PIC eligibility (the propagation the register/rig cares about).
- *Disciplinary-narrative masking left to the gateway rego,* consistent with the rest of RegistryController (which relies on ext_authz, not BFF field-masking). Alternatives: BFF-level field stripping on the list (mask summary/finalRecommendation) or a role-header check — both deferred as documented follow-ups. Chosen for consistency + to avoid fragile BFF role logic.
- *Compliance controller-wired methods emit no outbox events* (pre-existing gap; only the older createAction/resolveAction do). Left as-is — governance is captured by the BFF audit; adding outbox to the wired methods is a follow-up.
- *ComplianceActionType/DisciplinaryTriggerType enums are not bound to their entities* (stored as raw strings). The UI constrains to valid values; server-side enum validation is a follow-up.

## Deferred for operator

_(appended as encountered — issues genuinely needing operator input that did not block forward progress)_
