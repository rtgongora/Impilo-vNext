# Phase 6 — Claims / Medical Aid / Remittances / Subsidies — surface audit

| Field | Value |
| ----- | ----- |
| Status | Implemented for Slices 6A–6E; subsidies first-class (V009); compose enrollment pilot-ready (2026-06-08) |
| Predecessors | [`phase-4-mushex-platform-detail.md`](../design/phase-4-mushex-platform-detail.md), [`phase-5-costa-encounter-timeline.md`](../design/phase-5-costa-encounter-timeline.md) |
| Doctrine | [`docs/doctrine/mushex-gateway-neutrality.md`](../doctrine/mushex-gateway-neutrality.md) |
| Audit register | [`costa-mushex-experience-layer-wiring-audit.md`](costa-mushex-experience-layer-wiring-audit.md) |

## 1. Why a separate audit rather than direct implementation

Phase 6 covers **four distinct surface families** (claims, medical aid / coverage schemes, remittances, subsidies). Each has its own backend posture, its own existing UI footprint, and its own safety profile:

- **Claims** — already has two production surfaces: `/finance/claims` + `/finance/claims/[id]` (general claim view) and `/finance/payer-claims` + `/finance/payer-claims/[claimId]` (payer-side queue). Both surfaces are non-trivial; redesigning them in a single batch is high-risk.
- **Medical aid / coverage** — already has a substantial 11-tab UI at `/coverage` with hooks for plans, claims, remittances, eligibility, and a number of write actions. The backend exposes four further list endpoints that the UI does not yet consume (see § 3).
- **Remittances** — `useCoverageRemittances` on `/coverage` settlement tab **and** dedicated finance hub at `/finance/remittances` (same BFF feed).
- **Subsidies** — first-class `cv_subsidy_programs` in coverage-service (`GET /internal/v1/coverage/subsidies`) with Subsidies tab on `/coverage`. Costing-engine bill-split exemptions remain a separate pathway for encounter-level copay waivers.

Mapping each family separately keeps each follow-on slice small and auditable.

## 2. Current surface inventory

### 2.1 Claims (general)

| Surface | Source of truth |
| ------- | --------------- |
| Hub | `ui/one-ui-shell/src/app/finance/claims/page.tsx` |
| Detail | `ui/one-ui-shell/src/app/finance/claims/[id]/page.tsx` |
| Hooks | Currently uses `apiClient` directly inside the page modules (no dedicated `useClaims.ts`). |
| BFF | `services/experience-bff/.../controller/FinanceController.java` and related |

### 2.2 Claims (payer queue)

| Surface | Source of truth |
| ------- | --------------- |
| Hub | `ui/one-ui-shell/src/app/finance/payer-claims/page.tsx` |
| Detail | `ui/one-ui-shell/src/app/finance/payer-claims/[claimId]/page.tsx` |
| Hooks | `ui/one-ui-shell/src/hooks/queries/usePayerClaims.ts` — list, single, submit, dispute |
| BFF | `services/experience-bff/.../controller/PayerClaimsController.java` — `GET /internal/v1/finance/payer-claims`, `GET /{id}`, `POST /{id}/submit`, `POST /{id}/dispute` |

### 2.3 Medical aid / coverage

| Surface | Source of truth |
| ------- | --------------- |
| Hub | `ui/one-ui-shell/src/app/coverage/page.tsx` (11 tabs) |
| Contracts | `ui/one-ui-shell/src/app/coverage/contracts/page.tsx` |
| Member | `ui/one-ui-shell/src/app/coverage/member/page.tsx` |
| Hooks | `ui/one-ui-shell/src/hooks/queries/useCoverage.ts` — plans, members, claims, remittances, eligibility (check + mutate), enroll member, create claim, create preauth |
| BFF | `services/experience-bff/.../controller/CoverageController.java` — `GET /internal/v1/coverage/{plans,member/{clientId},eligibility,claims,claims/{id},contributions,preauths,utilization,appeals,remittances,members}` and various `POST` writes |

### 2.4 Remittances

| Surface | Source of truth |
| ------- | --------------- |
| Direct hub | None today |
| Embedded | Three sections of `/coverage` consume `useCoverageRemittances` |
| Hooks | `useCoverageRemittances` in `useCoverage.ts` |
| BFF | `CoverageController#getRemittances` → `GET /internal/v1/coverage/remittances` |

### 2.5 Subsidies

| Surface | Source of truth |
| ------- | --------------- |
| UI | None today |
| Backend | None today as a first-class concept; references exist only inside costing-engine bill-split / exemption logic |

## 3. Backend reads available that the UI does not yet consume

Cross-referencing `CoverageController` against `useCoverage.ts`, four list endpoints are exposed by the BFF but have **no hook and no page wiring**:

| BFF endpoint | Purpose | Backend ready | UI hook today | UI page today |
| ------------ | ------- | ------------- | ------------- | ------------- |
| `GET /internal/v1/coverage/contributions` | Contributions ledger per scheme/member | Yes | **No** | No |
| `GET /internal/v1/coverage/preauths` | Preauth list (the page only uses `useCreateCoveragePreauth` for new ones) | Yes | **No** | No |
| `GET /internal/v1/coverage/utilization` | Utilization stats per scheme | Yes | **No** | No |
| `GET /internal/v1/coverage/appeals` | Appeals against denied claims / decisions | Yes | **No** | No |

These four are the natural Phase 6 additive read-only slice: they have an existing BFF passthrough, an existing role-guard pattern (FINANCE / `/coverage`), and existing UI tabs (`preauth`, `contributions`, `appeals`, `intelligence`) that today render empty or static content.

## 4. Recommended Phase 6 slice order (smallest first)

### Slice 6A — read-only coverage list hooks (**this batch**)

- Add four new read-only hooks in `useCoverage.ts`: `useCoverageContributionsList`, `useCoveragePreauthsList`, `useCoverageUtilizationList`, `useCoverageAppealsList`.
- Each is a thin TanStack query over the existing BFF passthrough, envelope-tolerant (top-level array, `{ data: [] }`, `{ items: [] }`).
- Each returns `unknown[]` (the upstream entity shapes are not yet typed end-to-end); rendering code in follow-on slices must defensively probe fields the way Phase 5 does for cost events.
- Targeted unit tests are added via `useCoverage` hook tests if/where existing patterns are established (deferred — there is no current `useCoverage.test.ts` and adding one is out of scope for an audit batch).
- **No UI surface change** in this slice. The hooks land as dead code (visible to TypeScript, exported, but not yet consumed). This is identical to the dead-code-first pattern used successfully for G-4 (RailSelectionPolicy).
- Safety: read-only, no fabricated rows, no new shells, no new write actions, no schema change.

### Slice 6B — wire the four hooks into existing `/coverage` tabs

- In `/coverage` (`page.tsx`), replace the currently-empty preauth / contributions / appeals / intelligence tab content with simple read-only tables that consume the four new hooks.
- Each tab gets the same envelope-tolerant `extractRows` helper used in Phase 4.
- Each table surfaces the BFF route it consumes (Phase 5 doctrine).
- Tests: add focused tests for the four tabs.
- Safety: still read-only; no new write actions; no schema change.

### Slice 6C — claims surface coherence pass

- Audit `/finance/claims` (general) and `/finance/payer-claims` (payer queue) for duplication, broken-link patterns, and missing dispute / submit confirmation copy.
- Targeted incremental fixes only; no parallel page family.
- Safety: any write action stays through the *already-exposed* `POST /{id}/submit` and `POST /{id}/dispute` endpoints, behind the *existing* confirmation patterns. No new write endpoints are introduced.

### Slice 6D — remittances hub (optional)

- A dedicated `/finance/remittances` page that lists `GET /internal/v1/coverage/remittances` in the same chrome the rest of the finance hub family uses.
- Read-only.
- Safety: same as Slice 6B.

### Slice 6E — subsidies (implemented as read-only lifecycle surface)

- A first-class read route is now available on COSTA lifecycle (`/costa/v1/finance/lifecycle/subsidies`) and surfaced through billing-workspace.
- Canonical UI surfaces consume this route read-only (`/finance/costa` and `/finance/costa/encounter/[encounterId]`).

## 5. What this batch does and does not do

**This batch (Slice 6A):**

- Lands the four read-only list hooks in `useCoverage.ts` so follow-on UI batches can consume them.
- Documents the full Phase 6 surface map.
- Updates the audit register and doctrine to record the Phase 6 audit status.

**This batch does *not*:**

- Modify any user-visible page.
- Add or change any backend, BFF route, OpenAPI contract, or database schema.
- Add any new write action.
- Introduce a new shell, API client, or state-management pattern.
- Touch authentication, trust headers, or workspace context.
- Make any decision that requires product/governance input.

## 6. Tests

This batch lands read-only TanStack hooks of identical shape to existing hooks (e.g., `useCoverageRemittances`) in the same file (`useCoverage.ts`). The existing file has no dedicated `useCoverage.test.ts`; adding one is a separate housekeeping task and would be the right place for the four new hook tests to live. Until then:

- TypeScript narrowing (no `any`) and linter compliance act as the in-batch correctness check.
- Each hook is shaped identically to a known-good hook (`useCoverageRemittances`) so reviewers can verify by diff.
- A follow-on Slice 6B will add UI tests that exercise the hooks end-to-end through MSW or vi.mocked TanStack queries.

## 7. Doctrine alignment

- *No silent fake data* — preserved; hooks return `unknown[]` so consumers cannot accidentally invent fields.
- *Gateway-neutral / gateway-capable / health-focused* — unaffected; the surfaces in scope are coverage, claims, and remittances, not the payment routing contract.
- *No deletion / no breakage / additive-only* — preserved; no existing export, type, or signature is changed.

## 8. Implementation update (Slices 6B–6D)

Phase 6 follow-on implementation completed the previously queued UI slices while keeping the same backend/BFF contracts:

- **Slice 6B (`/coverage` tab wiring):**
  - `Preauth`, `Contributions`, `Appeals`, and `Intelligence` tabs now consume the four Phase 6A hooks and render read-only rows from:
    - `GET /internal/v1/coverage/preauths`
    - `GET /internal/v1/coverage/contributions`
    - `GET /internal/v1/coverage/appeals`
    - `GET /internal/v1/coverage/utilization`
  - Each tab surfaces honest loading/error/empty states and does not synthesize unsupported values.
  - Coverage page tests were expanded with focused tab assertions for all four routes.

- **Slice 6C (claims coherence):**
  - Added explicit handoff actions from `/finance/claims` and `/finance/claims/[id]` into `/finance/payer-claims`.
  - `Submit claim` and `Dispute claim` actions on `/finance/payer-claims/[claimId]` now require explicit operator confirmation checkboxes before mutation calls.
  - No new endpoint was introduced; existing payer-claims write routes are reused unchanged.

- **Slice 6D (remittances hub):**
  - New canonical read-only page: `/finance/remittances`.
  - Data source is existing BFF route `GET /internal/v1/coverage/remittances`.
  - Route metadata/parity were updated (`routes.ts`, `route-parity-check.mjs`) and `/finance` now links to the remittances hub.

## 9. Implementation update (Slice 6E)

Slice 6E is now implemented as an additive read-only subsidy surface over existing COSTA bill split truth:

- New COSTA endpoint: `GET /costa/v1/finance/lifecycle/subsidies?encounter_id=...`
  - Returns encounter-scoped rows with `bill_id`, `invoice_id`, `subsidy_amount`, `write_off_amount`, `currency`, `bill_status`, timestamps, and `trace_summary`.
  - Includes only bills where `subsidy_payable > 0` or `write_off > 0`.
- New BFF passthrough: `GET /internal/v1/finance/billing-workspace/lifecycle/subsidies?encounterId=...`
- Canonical UI wiring:
  - `/finance/costa` now shows a read-only "Subsidies & exemptions" card.
  - `/finance/costa/encounter/[encounterId]` timeline now includes `SUBSIDY` rows merged with decisions, cost events, invoices, intents, and settlements.

Safety posture remains unchanged: no subsidy write actions, no schema migration, no fabricated subsidy rows.
