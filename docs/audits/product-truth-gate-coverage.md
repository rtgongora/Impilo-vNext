# Product Truth gate — what it now catches, and what it still does not

**Measured**: 2026-08-08 against `b1845e1be` (branch `phase0/i-gate-truth`).
**Method**: every claim below was produced by running the scanner and guard, not by reading them.
A grep of a detector proves the detector exists; it does not prove the detector fires.

## The state this replaced

On `b1845e1be` the gate reported **104 services, `Gaps: 0`, violations=0, blockers=0/1, GUARD PASS** —
on the same estate that holds open msika-flow callbacks, a `butano-fhir` store with 0 rows, and a
`RoleGuardInterceptor` enforcing nothing.

The zero was not a clean estate. It was four independently dead detectors. None of them was a
*missing* detector — every one was written, wired and incapable of emitting:

| # | Defect | Why it could never fire |
|---|--------|--------------------------|
| 1 | `triState(count, 0)` on `database`, `serviceLayer`, `bffWiring`, `mobileUi`, `tests` | `triState` returns `thin` for `count <= thinThreshold`. With a threshold of 0, `thin` is **unreachable** — the five dimensions were binary, and one file scored the same `real` as a hundred. Hence `tests: real` for **104/104** services, and the datasource-less `experience-bff` scoring `database: real` off 45 migrations with **0 entities**. |
| 2 | Gap **H** (dead actions) and gap **F-medium** (hardcoded data) | `classifySurfaceGaps` tested `surface.deadActionHints?.length > 0`, but `buildFrontendSurface` stores that field as a **count**. `(5)?.length > 0` is `false` for every number. Measured: 3 surfaces carried `hardcodedDataHints > 0` and **not one** gap was emitted. |
| 3 | The dedicated UI-without-backend check | The surface-level E gap was severity `high`, while the guard blocks on `severity=="blocker" && category=="E"`. The only blocker+E emitter is the *service*-level E, which needs a UI surface with no detected backend at all — no service has one. The combination was unproducible. |
| 4 | The same check, again | It iterated only `services`, never `frontendSurfaces` where UI surfaces actually live — and was gated behind `PRODUCT_TRUTH_BLOCK_UI_WITHOUT_BACKEND`, defaulting to `0`. |

A fifth, in the pipeline rather than the scanner: `PREVIEW_GATES_SKIP_BACKEND=1` recorded
`backend PASS` with `PIPELINE_FAIL=0`. Because `backend` is a mandatory gate, every mandatory gate
then read as passed and `deploy_recommended` came out **true** — a gate that executed nothing
authorising a deploy.

## What it catches now

Gap total moved **0 → 8** on unchanged source. All 8 are newly *exposed*, not newly *introduced*:

- **3 × F-medium** hardcoded dashboard/data — `/facility/[id]/regulators`, `/telemedicine/new`,
  `/my-life/feedback/respectful-maternity`. These were being detected and then silently dropped.
- **5 × D-medium** partial frontend/BFF wiring, from dimensions that can now read `thin`.
- **1 × phase6 incomplete**: `butano-fhir` (`database`/`serviceLayer`/`bffWiring`/`tests` all `thin`
  → maturity `UNKNOWN`). Corroborated independently: it holds 0 rows and is not a functioning FHIR
  server. The tri-state fix found it without being told to look.

### Red-proved, not assumed

Per the rule that a gate which has never gone red is not known to work:

| Gate | Break applied | Result |
|------|---------------|--------|
| UI-without-backend blocker | Registered `/zz-gate-redproof`, a page reaching no backend | `category: E, severity: blocker` emitted; `GUARD FAIL UI-without-backend blockers=1 > baseline=0`; **exit 1** |
| Same, reverted | Page and registry entry removed | `GUARD PASS`, **exit 0**, `ui/` tree clean |
| Total-violations ratchet | Same unbacked page | `GUARD FAIL … violations=9 > baseline=0`; **exit 1** |
| Skipped-gate honesty | `PREVIEW_GATES_SKIP_{FRONTEND,BACKEND}=1` | `SKIPPED ×3`, `Verdict: FAIL`, `deploy_recommended: false`, `MISSING: 0` |
| Skipped-gate honesty (positive control) | switches unset | passing phase records `PASS`, failing phase records `FAIL` — normal paths intact |

The hardcoded-data fix is proved differently: it emitted **3 true positives on identical source**
where it previously emitted none. Scanner unit tests: **18/18**.

## What is still NOT covered

Partial is fine. Silent partial is not. These are real holes, listed so nobody reads a green gate as
a clean estate.

1. ~~**`bffOrphanRoutes` is still hardcoded to `0`.**~~ **RESOLVED** on `phase0/j-route-resolution`
   (2026-08-08). It is measured from the path literals BFF clients actually call, checked against
   each service's own routes, and gap **L** now fires on **3 hand-verified services**: msika-apps
   (7 paths — it serves no `/marketplace/*` route at all), dispatch (2 — no `tasks` handler), ndila
   (2 — the BFF calls `/internal/v1/ndila/tiles/...` while the service serves them under
   `/v1/public/` and `/api/v1/`). Gap L also had to drop its `bffWiring === 'thin'` conjunct, which
   made it unreachable a second time: every service that actually has orphaned calls wires many
   clients and reads `real`.
   ⚠️ **The count is a floor, not a total** — it reads literals, so paths built by concatenation or
   from constants are invisible to it.

2. **BFF route paths are not resolvable end-to-end — `extractClassBase` can never find a class-level
   prefix.** Root-caused, not inferred. In `spring-route-extractor.mjs`:

   ```js
   const classPos = before.lastIndexOf('class ');
   const searchStart = classPos >= 0 ? before.lastIndexOf('\n', classPos) : 0;
   const preamble = before.slice(searchStart > 0 ? searchStart : 0, mappingIndex);
   const match = preamble.match(/@RequestMapping\s*\([\s\S]*?\)/);
   ```

   The preamble starts at the newline *before the `class` keyword* and runs forward to the method's
   annotation. But a class-level `@RequestMapping` sits **above** the `class` keyword, so it is
   outside the searched window by construction. Confirmed on
   `AccessChannelsController.java`: `@RequestMapping("/internal/v1/access")` on line 22, `public
   class` on line 23, and the extractor emits `get /landela/templates` — the real route is
   `/internal/v1/access/landela/templates`. Class-level `@RequestMapping` annotations are separately
   emitted as routes in their own right (`all /internal/v1/access`), which is why the route *count*
   looked plausible.

   **RESOLVED** on `phase0/j-route-resolution` (2026-08-08). The base is now resolved **once per
   file**, over text with comments and string literals blanked out. Scanning backwards from each
   mapping to the nearest `class` keyword was tried and rejected: `WalletController` carries the
   comment *"see class javadoc"* in its body, `\bclass\s+\w` matches it, and every handler below
   that line silently lost its prefix again. Class mappings declaring several prefixes
   (`@RequestMapping({"/internal/v1/ai", "/internal/v1/ai-governance"})`) fan out to all of them,
   and the class-level annotation is labelled `classLevel` so consumers can drop the phantom
   prefix-route.

   BFF handler routes carrying a versioned prefix went from ~0% to **100%**, with **0**
   double-prefixed.

   **Measured blast radius** (before → after, isolated by swapping only the extractor on this same
   branch): gap categories D/F unchanged, `byProductStatus` unchanged, phase6 unchanged — but two
   downstream consumers move a long way, and both moves are toward the truth.

   - **Capability buckets fell 5677 → 3279.** Roughly 2,400 "capabilities" were phantom fragments
     of unprefixed paths that `capabilityKeyFor` could not collapse. No guard gates on that number
     (only a `> 0` test assertion).

   - 🔴 **Contract-implementation violations rose 76 → 1413**, and this one needs a decision.
     `implemented` actually **improved** to 4904 of 4936; the rise is almost entirely
     `orphanHandlers`, which went from at most 76 to **1320** — handlers with no OpenAPI operation.
     `routeMatchesOperation` matches on **suffix** in both directions
     (`normOp.endsWith(r.normalized)`), which was a compensating hack for the broken extractor:
     bare fragments like `/{equipment_id}` suffix-matched unrelated operations and reported a
     contract as implemented when it was not.

     Verified by hand: `asset-registry.openapi.yaml` declares 11 paths, **none** mentioning
     `/equipment`, while `EquipmentOperationsController` maps `/internal/v1/equipment` with several
     handlers. Before, that produced no orphan; now it does. The 1320 are real contract gaps, and
     the gate's own remediation text agrees — *"add OpenAPI operation — complete contract; do not
     delete handler"*.

     **Not addressed in this workstream.** The `api-contracts` gate was already failing on `main`
     at 76, so this does not turn a green gate red — but retuning `routeMatchesOperation` now that
     paths are exact, and setting a threshold against 1320 real gaps, is a separate decision with
     its own baseline. Do not "fix" it by loosening the matcher back.

   ⚠️ A naive matcher run against the old fragments reported 5135 of 6294 surface path references
   (82%) as unmatched. That number was an **artifact of the fragments, not a finding**, and is
   recorded here only so nobody rediscovers it and believes it.

3. **`apiClient-dynamic` counts as backing, and matched 919 of 945 surfaces.** It is added when any
   *transitively imported* module contains `apiClient.get(...)`. It proves a page **can** reach the
   API layer, not that it reaches a live route. Surfaces backed only by it are not distinguished from
   concretely-wired ones, so the "thin/floating UI" shape is only partly covered.

4. **Unregistered pages bypass `classifySurfaceGaps` entirely.** A `page.tsx` present in `src/app`
   but absent from `routes.ts` gets a hardcoded `category: 'D', severity: 'low'` gap and nothing
   else — so an unregistered page with no backing **cannot** raise the UI-without-backend blocker.

5. **Internal-only services short-circuit every category.** `classifyServiceGaps` returns after Q/K
   for the 26 internal-only services, so no A/B/C/D/E/F/I/N/O/P gap can ever be raised against them.
   This matters more than it sounds: **every** `absent`/`thin` dimension on `b1845e1be` — all 14
   `bffWiring: absent`, all 4 `authzAudit` absent/thin, all 12 `frontendUi: thin` — fell on
   internal-only services. That, plus defect 1, is the whole mechanism of the original `Gaps: 0`.

6. **Backend-without-UI (category A) keys off `frontendExpected`,** which is derived from the
   registry rather than measured. A service mis-declared in the registry is invisible to it.

7. **Static, not runtime.** `REAL_CODE_NOT_PROBED` means code present and wired, never that it
   works. Only a supplied probe artifact yields `REAL_PROVEN` — 4 of 104 services today.

8. **The scanner mutates tracked files as it runs.** `check-product-truth.sh` regenerates
   `reports/product/*.json` and seven `docs/audits/*` files, so running the gate dirties the tree.
   This is the same "a check that rewrites what it inspects cannot report drift honestly" defect
   already recorded against the full-boot gates in `run-local-quality-gates.sh`.

9. **`tools` is mandatory but skippable.** `run-preview-gates.sh` defaults
   `PREVIEW_GATES_SKIP_TOOLCHECK` to `1`, which now records `SKIPPED` and therefore fails the
   pipeline. That is honest, but it means that entry point needs a decision rather than a default.

## Baseline and ratchet

`reports/product/product-truth-baseline.json`:

| Key | Was | Now | Direction |
|-----|-----|-----|-----------|
| `gapBaseline` | 0 | **8** | re-anchored to measured truth (exposed debt, not absorbed debt) |
| `blockerBaseline` | 1 | **0** | **down** |
| `uiWithoutBackendBlockerBaseline` | — | **0** | pinned; the next unbacked surface fails immediately |
| `phase6IncompleteBaseline` | 0 | **1** | `butano-fhir` |

Re-anchoring `gapBaseline` upward is the one number that moved the wrong way. It follows the
precedent set by the 2026-06-27 hermeticity re-anchor: when detection is *repaired*, previously
invisible pre-existing debt becomes visible, and the ledger records it rather than hiding it. The
doctrine forbids raising the baseline to absorb **new** debt, and no source was weakened to reach
this number. Every one of the 8 is itemised in `knownDebt`. **Ratchet them down.**
