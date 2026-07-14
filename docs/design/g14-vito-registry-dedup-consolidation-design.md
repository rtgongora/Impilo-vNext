# Design — G14: Consolidate the duplicated VITO client-registry plumbing

**Status:** design-first (scheduled, not yet built). **Type:** consolidation refactor of live systems.
**SoR:** unchanged — `vito-service` remains the single person-identity SoR.

> Scheduled per PO decision (2026-07-14). Lower risk than G4 (no data migration — this is
> API/UI plumbing consolidation), but it touches **live UI paths**, so it is staged consumer-by-
> consumer with both stacks kept working until each is migrated.

## Grounding corrections (two register entries were stale)

1. **Not "two VITO stacks / two services."** There is **one** `vito-service` and **one** client
   store. The duplication is **two parallel BFF controllers + two UI hook families** that both proxy
   to the *same* backend (`ClientIdentityOperationsController` at `/v1/client-registry`).
2. **Not a "household/relationships = 0 UI" gap.** Relationship + guardian-assisted-intake UI now
   exists (`app/registry/clients/[id]/page.tsx` relationships tab + graph; `GuardianAssistedIntakePanel.tsx`).
   So G14 is **purely a dedup/consolidation refactor**, not a missing surface.
3. **No single-SoR violation.** Both stacks read/write the same vito store — there is no second MPI.
   The problem is redundant code paths and divergent API surface, not competing sources of truth.

## What is actually duplicated

| # | Duplication | Stack A (keep) | Stack B (retire) |
|---|---|---|---|
| 1 | **BFF client-registry controller** | `ClientRegistryController` → `/internal/v1/client-registry` (**typed** `VitoServiceClient`) | `VitoClientRegistryBffController` → `/internal/v1/vito/client-registry` (**raw** passthrough) |
| 2 | **UI hook family** | `useClientRegistry.ts` | `useVitoClientRegistry.ts` (same ops, divergent names: `useAddClientRelationship` vs `useAddRelationship`, etc.) |
| 3 | **Merge/dedup model (inside vito)** | `DedupCaseEntity` + `MergeService` (`/v1/internal/dedup`, has true **unmerge** + probabilistic `MatchingEngine`) | `ClientMergeCaseEntity` merge-cases (`/v1/client-registry/merge-cases`) |
| 4 | **Registry mode (inside vito)** | `StandaloneRegistryService` (default) | `OpenCrAdapter` (only when `VITO_REGISTRY_MODE=OPENCR`) |

Consumers today:
- **Stack A** — web `app/registry/clients*`, `app/operations/vito/page.tsx`, intake components, **and mobile** (`clientRegistryService.ts`). More consumers, typed client.
- **Stack B** — web `app/operations/vito/registration*` + `EditDemographicsForm.tsx` (uses `PUT /v1/clients/{healthId}` for demographics). Fewer consumers.

## Design decisions

- **Canonical BFF = Stack A** (`/internal/v1/client-registry`, typed). It has the most consumers
  (incl. mobile) and a typed client contract. Retire Stack B.
- **Canonical hook family = `useClientRegistry.ts`.** `useVitoClientRegistry.ts` is migrated then deleted.
- **Merge/dedup (item 3) is a separable, deeper decision** — deferred to its own phase. Preliminary
  lean: keep `DedupController`/`MergeService` as the canonical dedup engine (it has real unmerge +
  the probabilistic matching engine + a queue) and make the `ClientMergeCaseEntity` merge-case
  workflow either delegate to it or be retired. This needs a focused follow-up analysis (two entity
  models, both wired) — **do not fold it into the plumbing consolidation.**
- **Registry mode (item 4) is NOT duplication-by-accident** — it's a deliberate pluggable adapter
  (Standalone vs OpenCR). Leave as-is; just document that OpenCR is inactive unless configured. Out
  of scope for this refactor.

## Staged rollout (both stacks stay live until a consumer is migrated)

- **Phase 0 — parity audit (no behaviour change).** Enumerate every operation Stack B's consumers
  use (notably the **demographics `PUT /v1/clients/{healthId}`** behind `EditDemographicsForm`).
  Confirm Stack A (typed controller + `useClientRegistry.ts`) covers each; **add any missing typed
  method/hook** (e.g. a typed `updateDemographics`) so Stack A is a superset. Nothing removed yet.
- **Phase 1 — migrate Stack B consumers to Stack A.** Repoint `app/operations/vito/registration*`
  and `EditDemographicsForm.tsx` to `useClientRegistry.ts`. **Preserve every `data-testid` / selector**
  (the e2e suite pins them — selector-stability law); this is a plumbing swap, not a UX change.
  Run the registry/operations e2e specs green before/after.
- **Phase 2 — delete the redundant plumbing.** Once no consumer references Stack B: remove
  `VitoClientRegistryBffController` and `useVitoClientRegistry.ts`, and drop the
  `/internal/v1/vito/client-registry` routes from the BFF security config + any OpenAPI. Vito backend
  untouched.
- **Phase 3 (separable) — reconcile the two dedup/merge models.** Its own design pass (see decision
  above). Do not start until Phases 0–2 land.

## Blast radius / risk

- **Low data risk** — no schema or data migration; the vito backend and its store are untouched.
- **UI-path risk** — live pages move from Stack B hooks to Stack A. Mitigation: keep both stacks
  live through Phase 1; migrate one page at a time; preserve selectors; gate on e2e.
- **Demographics parity is the sharp edge** — Stack B uses a demographics `PUT` that Stack A must
  match before `EditDemographicsForm` can move. Verify in Phase 0.
- **Dedup reconciliation (Phase 3) is the real complexity** — two entity models (`DedupCaseEntity`
  vs `ClientMergeCaseEntity`), both live and both with UI. Deliberately isolated so the low-risk
  plumbing win (Phases 0–2) is not blocked by it.

## Register update

Amend the G14 entry: it is **not** a "household/relationships = 0 UI" gap (that UI exists) and
**not** "two services." It is (a) duplicated BFF+UI client-registry plumbing over one vito backend
[this design, Phases 0–2] and (b) a duplicated intra-vito merge/dedup model [Phase 3, separable].
