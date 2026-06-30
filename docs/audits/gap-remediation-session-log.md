# Gap-Remediation Session Log

Branch: `claude/crazy-merkle-3ad1a1` (off PT `claude/staging-ux-orchestration-remediation-Yypyl`).
Mode: **Absence mode** from 2026-06-29 — PO off-desk; doctrine + conservative-safe defaults applied;
PO-gated items parked (see `po-decision-index.md`), session never blocked.

## Phase 0 — substrate
- `0fa7630d4` fix(learning) — restore `learning.security.oauth2-enabled` (prod boot-blocker; suite 60/60).
- `eec9ed001` docs — record adversarial verification findings (full gap register).
- `8d9b42f7c` docs(governance) — lift CZO PolicyEngine single-writer lock.

## Phase 1 — SYS-1 per-domain policy enforcement (Java DB-rule engine)
- `6490794d7` **V019** inpatient clinical-write ext_authz rules (Flyway→v019, IT 4/4).
- `3c3c7e482`/`6e5253ee2` **GAP-4** closed — removed orphaned client `cadreEngine` (Java engine = single SoR).
- `58dfacaa0` keycloak — MCAZ/safety realm roles (canonical taxonomy).
- `cc5e43841`/`f4ebf6b4e` **V020** patient-safety ext_authz RBAC + spec/register reconcile (Flyway→v020).
- `f10fa85f1`/`72830ce92`/`441851ae9` **V021** Rito ext_authz RBAC + REGULATOR role + reconcile (Flyway→v021).
- `051fd04e7`/`6cc99fd77` **V022** OROS diagnostics RBAC (Flyway→v022).
- `6ad033c1a` patient-safety **in-service citizen-own report binding** (closes IDOR; suite 6/6).
- `bcec705d7` **V023** narrow OROS citizen-results (corrective — closed an over-broad grant).
- `7db6fa85f` **V024** narrow Rito citizen case-read (corrective — closed an IDOR pending binding).
- `0b037c2b2` **PolicyEngine Step 4.6** provider self-treatment block, G-PX-01 (PolicyEngineTest 38/38).

All migrations Flyway-proven against CLI Postgres (`→v024`); chokepoint + patient-safety guard unit-tested;
all session-introduced IDORs closed.

## Phase 1 — resolution of all outstanding items (2026-06-29, absence mode)

- **Rito client case-read binding — RESOLVED** (`59febcce0`): own-subject in-service guard + reactivated
  rule (V025); sub-reads inherit via get(); list own-only; tested 12/12; Flyway→v025.
- **Patient-safety / Rito facility-scope + restricted-phi masking — DEFERRED (infra-dependent).**
  Both need the actor's *role* in-service to distinguish facility-focal (scope to own facility) from
  MCAZ (see all) and to detect `restricted-phi`. The shared `TrustContext` carries `facilityId` but
  **not roles**, and no role header is forwarded to services. Adding a `roles` field is a positional
  change across **11 `new TrustContext(...)` sites + tests in 6 services** — too high-blast for an
  absence-mode tail. Conservative default kept: gateway role-gating already restricts reads to trusted
  safety staff (citizens are own-subject-bound); cross-facility scoping among safety staff is a
  refinement. **Next:** add `roles` to `TrustContext` (one filter populates it; widen call sites) as a
  dedicated infra slice, then scope + mask.
- **OROS finer per-action gating (release vs view) — DEFERRED (refinement).** Core diagnostic-journey
  RBAC is enforced (V022); per-action narrowing is a later refinement.
- **Fundo training-gate — PARKED (PO-20260629-01) + advisory is the buildable next.** Blocking-vs-warn
  is a PO/clinical decision (parked). The *advisory* consumer (vashandi check-in queries the fundo
  training-gate, surfaces a readiness flag, does not block) is the conservative resolution and needs no
  PO input — recommended as the next cross-service build under the parked decision.

**Net:** every outstanding Phase-1 item has a disposition. Done: Rito. Deferred-with-reason
(roles-infra / refinement): facility-scope, restricted-phi, OROS-finer. Parked (PO): Fundo blocking.

## Phase 1 follow-on — deferred refinements CLOSED via obligation consumption (2026-06-29)

PO returned; chose graduated **levels of permission** for Fundo + confirmed the obligations pivot.

- `22308c447` **feat(fundo)** — graduated training-gate levels (ADVISORY/SOFT/HARD → ALLOW/ADVISE/
  CONDITIONAL/BLOCK), resolves **PO-20260629-01** (gate half of G-FU-02). FundoTrainingGateServiceTest 8/8.
- **Architecture course-correction:** the deferred facility-scope + restricted-PHI items do NOT need a
  `roles` field on `TrustContext` (that would duplicate the PDP's existing visibility-obligation
  mechanism). The reuse-correct seam is consuming `maxScope`/`suppressFields`/`piiAccess` obligations
  (already emitted by `VisibilityObligationComposer`, already in `VisibilityContextHolder`). The gap was
  consumption — patient-safety + rito ignored it. See [[visibility-obligations-are-the-masking-seam]].
- `f320bb77a` **feat(patient-safety)** — honor FACILITY_SCOPE on report list (deny-empty when facility
  unknown). `f7917cf90` — shape read/list via JsonRepresentationShaper (suppressFields/piiAccess,
  fail-closed). Suite 8/8. **G-PS-01 CLOSED.**
- `b22c10248` **feat(rito)** — facility-scope on list + shape all 6 case reads (§3 sensitive-category
  identity redaction). Rito 8/8. **G-RT-01 CLOSED.**
- `e4d699b27` docs — register G-PS-01 + G-RT-01 → CLOSED.

## Phase 2 toolchain — READY (2026-06-29)

Correcting the earlier "JS generators not runnable in sandbox" claim: they ARE. The product-truth
generators need only `js-yaml`; everything else is node built-ins + the built-in `node --test` runner.
`cd scripts/completeness && npm ci` (from the committed lock) → `node --test __tests__/` = **13/13**, and
the suite generates `product-truth.json` and asserts on its maturity/baseline. So Phase 2 (SYS-2
capability-matrix + probeEvidence) is now runtime-verifiable here — the metric can be confirmed to move.
(Web `one-ui-shell` tsc/vitest is a separate, larger setup for Phase 3.)

## Phase 2 — SYS-2 capability-grained Product Truth + probeEvidence — COMPLETE (2026-06-29)

- `e26f37d11` **feat(product-truth)** — wire `probeEvidence` → REAL_PROVEN. New `probe-evidence.json`
  (serviceId→{passed,suites,tests,command,evidence,commit}); generator loads it + attaches to records;
  `classifyMaturity` already lifts a passing service to REAL_PROVEN. Seeded with the 4 services proven
  in-session (tshepo-authz, patient-safety, rito, learning) → metric moved **0→4**. Test evolved to
  enforce the real guarantee (no REAL_PROVEN without a passing probe entry). `node --test` 13/13.
- `e3b4a64e9` **feat(product-truth)** — `capability-matrix.json`. `capabilityKeyFor` + `classifyCapability
  Disposition` (pure) in product-truth-gaps.mjs; `buildCapabilityMatrix` joins routes×frontend×BFF×contract
  into **3681 capabilities** (real 3139 / partial 141 / real-proven 207 / fixture 194). In-service additions
  are now visible; fixtures (frontend-only/all-stub) are pinpointed. `node --test` 16/16. **SYS-2 CLOSED.**

## Phase 3 — SYS-3 patient lane — IN PROGRESS (2026-06-29)

- `6f2578616` **feat(patient-lane)** — unified citizen visit/inpatient status endpoints + message catalog.
  BFF read-composition (`CitizenVisitStatusController` → `PatientLaneService` → PCT/inpatient clients);
  `PatientMessageCatalog` (typed, plain-language, i18n-ready) maps every JourneyState + inpatient status
  to a person-journey stage. Honest degradation (unknown/outage → available:false). PatientLaneServiceTest
  5/5 + CitizenVisitStatusControllerTest 2/2.
- `d4e5d7bf3` **test(patient-lane)** — persona journey progression ARRIVED→TRIAGED→QUEUED→IN_SERVICE→
  ADMITTED→DISCHARGED; patient lane reflects the right stage + plain language at each step. Suite 6/6.
- `4927ec0f9` **feat(patient-lane)** — citizen web screens `/citizen/visit/[transactionId]` +
  `/citizen/inpatient/[admissionRef]` on the new endpoints (live refresh; honest loading/error/unavailable;
  no fabricated data); registered in the route registry. **Web toolchain set up** (npm workspace at `ui/`,
  `npm ci`): `tsc --noEmit` clean for the screens (one pre-existing error in serviceBranding.ts spun off as
  task_80b6459e); `vitest` 3/3. **G-CT-01 CLOSED** — patient lane built end-to-end (backend + web).
- **Remaining SYS-3 (broad):** the live cross-service WireMock e2e (BFF IT Redis+Postgres harness) and the
  other waves' persona journey ITs (G-CZO-16 / G-PX-07 / G-OR-05) — proof-depth, not missing features.

## Phase 4 — Nompilo continuity addendum — cores built (2026-06-29)

- `fc329bef2` **feat(nompilo)** — AI→human handoff lifecycle in guidance-service. V003 `nompilo_handoff`
  (runtime-proven V001→V003 on Postgres 16) + `NompiloHandoffService` (QUEUED→ACCEPTED→ESCALATED→CLOSED,
  illegal-transition guards, safe normalization) emitting lifecycle events (canonical
  `core.nompilo.handoff.requested`) via the guidance outbox; `NompiloHandoffController`. Test 6/6.
- `81b5ea056` **feat(khuluma)** — `SafeDisclosureService` recipient-aware safe disclosure. Fail-safe to
  redact; full content only for SELF or a consented delegate on a non-restricted category. Test 8/8 incl.
  combinatorial no-leak proof. **Security-relevant.**
- `fcf61b7ff` **feat(khuluma)** — `FeedbackRoutingService` quality/safety feedback → exact Rito
  quality-signal body (closes the G-RT-03 loop). Test 5/5.
- **G-KH-05/06** moved → cores built/proven. **Remaining (cross-service wiring):** BFF requestNompiloHandoff
  → guidance (replace stub) · mobile handoff client · mvumo-resolve + consent into the dispatch path ·
  the Rito HTTP post at the routing call site.

## Phase 5 — Khuluma comms W4–W8 — W4 done (2026-06-29)

- `170d37e3f` **feat(khuluma) W4** — escalation/routing/SLA (G-KH-01). V003 escalation + sla_policy
  (runtime-proven V001→V003 PG16); EscalationService lifecycle + SLA targets (policy + priority defaults)
  + accept-stops-clock + escalate-bump + `@Scheduled` breach sweep (OROS pattern); full controller.
  EscalationServiceTest 6/6. **G-KH-01 W4 done.**
- `3e7ba29c5` **feat(khuluma) W5** — channels/communities/broadcast (G-KH-02). V004 conversation scope
  (runtime-proven V001→V004 PG16); ChannelService create/discover/join/leave/broadcast (OWNER-gated) +
  ChannelController. ChannelServiceTest 6/6. **G-KH-02 W5 done.**
- `526dcb4b3` **W6** external channel adapters (G-KH-03). V005 adapter + delivery_attempt (proven V001→V005);
  DeliveryService honesty seam (external NOT_CONFIGURED → SKIPPED, never fake send). 6/6.
- `25d15b42d` **W7** secure WS push + on-call (G-KH-04). WsTokenValidator + hardened handshake (closed the
  query-param identity-spoof hole); V006 duty_status + OnCallService (proven V001→V006). 12/12.
- `9ebc8c3c1` **W8** comms-ops admin UI + BFF proxies. /admin/comms-ops screen (escalation queue + adapter
  status + on-call) on KhulumaBffController read-proxies. tsc clean + vitest 1/1 + BFF compile.

**Phase 5 COMPLETE (W4–W8).** All migrations runtime-proven on PG16; ~6 new test suites green.

## Phase 6 — per-wave High/Med closures — IN PROGRESS (2026-06-30)

Closed (provable):
- `6779de98c` **G-CZO-14** — removed the fake biometric door (simulated 2s verify + placeholder creds
  that only 401); honest "not available yet" state instead.
- `6d2f4514b` **G-CZO-13** — temp tier no longer collects DOB+National-ID it discards; honest
  facility-verification note (the stub upgrade backend can't gate them yet).
- `f98a6847c` **G-TI-01** — *found + fixed a real cross-tenant IDOR*: `FacilityService.getFacility(id)`
  had no tenant check (every other single-read did). Added the guard + FacilityReadIsolationTest 3/3.
- `5e9b2b87e` **G-PS-02** — PatientSafetySignalConsumerTest 4/4 (signal feed: parse/fallback/snake-case/
  malformed-swallow).
- `57375956d` **G-FU-03** — wired the native Fundo CPD egress consumer (certificate.issued.v1 →
  ingestCompletion), guarded to CPD-eligible PROVIDER certs, idempotent. Listener test 4/4.

Remaining Phase-6 dispositions: **all consolidated in
[`deferred-for-live-testing.md`](deferred-for-live-testing.md)** — the single authoritative list of
everything deferred for live testing / dedicated waves (live external counterparties · live
multi-service e2e + ENFORCE · UI/mobile waves · honest-safe feature builds). Summary:
- **Feature-completeness (buildable, not dishonest):** G-PX-03 contact-resolve seam (already honest
  uniform-deny, no leak). G-CT-02 closed this turn (structured completion-note + no-closure-without-audit).
- **Environment-gated (cannot close in-sandbox):** G-OR-02/03 live FHIR/HL7/DICOM/LIMS soak · MADI e2e.
- **Larger UI/mobile waves:** G-PX-05/06 · G-TI-04 · G-RT-02 · patient-lane mobile · Khuluma W8 tail.

## Later phase (7)
OPA-as-PDP migration · (deferred cross-service wiring from P3/P4).

## PO decisions parked
See `docs/audits/po-decision-index.md`.
- **PO-20260629-01** — Fundo training-gate enforcement (block vs warn + requirement mapping). Conservative
  default applied: kept existing behaviour (no blocking gate). Consumer/seam deferred. Remediation continued.
