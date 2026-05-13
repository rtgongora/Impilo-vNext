# Phase 5 — COSTA encounter timeline and controlled workflow actions

| Field | Value |
| ----- | ----- |
| Status | Implemented (first slice + follow-on — encounter timeline with invoice, MusheX source-list intent fan-in, and settlement-filter linkage) |
| Doctrine | [`docs/doctrine/mushex-gateway-neutrality.md`](../doctrine/mushex-gateway-neutrality.md) |
| Audit | [`docs/audits/costa-mushex-experience-layer-wiring-audit.md`](../audits/costa-mushex-experience-layer-wiring-audit.md) |
| Predecessors | [`phase-2-adapter-readiness.md`](phase-2-adapter-readiness.md), [`phase-3-attempt-time-rail-enforcement.md`](phase-3-attempt-time-rail-enforcement.md), [`phase-4-mushex-platform-detail.md`](phase-4-mushex-platform-detail.md) |
| Release baseline | [`docs/release/mushex-costa-finance-phase-1-release-readiness.md`](../release/mushex-costa-finance-phase-1-release-readiness.md) |

## 1. Goal

Deliver the **read-only timeline** asked for in the Phase 5 task list (*estimate → charge sheet → invoice → payment*) by merging encounter-scoped decisions/cost-events with additive invoice lifecycle rows that carry MusheX intent linkage signals.

## 2. Backend coverage audit (what is available to read today)

| Surface | Endpoint (BFF) | Encounter-scoped? | Usable in this slice? |
| ------- | -------------- | ----------------- | --------------------- |
| Service-access decisions | `GET /internal/v1/finance/service-access-decisions?encounter_id=…` | Yes | **Yes** |
| Cost events / charge-sheet rows | `GET /internal/v1/finance/costa-intel/cost-events?encounter_id=…` | Yes (or by `patient_cpid`) | **Yes** |
| Tariff lists | `GET /internal/v1/finance/costa-intel/tariff-lists` | No (catalogue) | Not encounter-scoped — already in COSTA hub counts |
| Invoices (lifecycle) | `GET /internal/v1/finance/billing-workspace/lifecycle/invoices?encounterId=...` | Yes | **Yes** (follow-on) |
| MusheX intent fan-in | `GET /internal/v1/finance/payer-ops/payment-intents?sourceType=COSTA_BILL&sourceIds=...` | Yes (source-batch) | **Yes** (follow-on) |
| Settlement linkage | `GET /internal/v1/finance/settlements?intentIds=...` | Yes (intent-filtered) | **Yes** (follow-on) |
| Settlement linkage | downstream of intents | n/a | **No** — surfaced honestly as a gap |

Settlement linkage now has an additive intent-filtered listing endpoint; deeper settlement batch/item detail remains in reconciliation surfaces.

## 3. Implementation

### 3.1 UI

- New page: `/finance/costa/encounter/[encounterId]` (`ui/one-ui-shell/src/app/finance/costa/encounter/[encounterId]/page.tsx`). Reuses `AppLayout` and `PageShell`. Three sections: encounter identity card, merged chronological timeline table, honest "Invoices/Payments/Settlement" gap card. **No write buttons** — defended by a dedicated test.
- Existing hooks reused unchanged:
  - `useServiceAccessDecisionsList(encounterId)` — already returns a typed `ServiceAccessDecisionRow[]`.
  - `useCostaIntelCostEvents(encounterId, patientCpid)` — already returns `unknown[]`; the page probes the conventional fields (`captured_at` / `event_time` / `recorded_at` / `created_at`, `service_name` / `item_name` / `description`, `total_amount` / `amount` / `cost`) and falls back to `—` rather than fabricating data.
- New per-page logic (kept local to the page; the merge contract has no reusers outside this surface yet):
  - `decisionToRow(...)` and `costEventToRow(...)` — defensive normalisers that produce a uniform `TimelineRow`.
  - `compareTimestamps(a, b)` — null timestamps sink to the bottom, then ascending ISO string sort.
  - `statusBadgeChrome(...)` — read-only styling for known statuses (`GRANTED` / `APPROVED` / `AUTHORISED` → emerald, `DEFERRED` / `PENDING` → amber, `DENIED` / `REJECTED` / `BLOCKED` → red, `EXEMPT` / `WAIVED` → sky, others → slate).
- COSTA hub (`/finance/costa`) now exposes a "View timeline →" link from the service-access decisions card **only when** an `encounterId` is in the URL handoff. The link preserves the existing handoff query string (`patientId`, `encounterId`, `source`).
- Route registry: added `/finance/costa/encounter/[encounterId]` with FINANCE role, finance sidebar, `app` layout, `work` nav zone. `EXPECTED_ROUTE_COUNT` bumped 270 → **271**. Route-parity check list updated.

### 3.2 BFF

Added one additive passthrough:

- `GET /internal/v1/finance/billing-workspace/lifecycle/invoices?encounterId=...`
  → `GET /costa/v1/finance/lifecycle/invoices?encounter_id=...`

### 3.3 Backend

Added one additive COSTA lifecycle endpoint:

- `GET /costa/v1/finance/lifecycle/invoices?encounter_id=...`

This endpoint returns encounter invoice rows enriched with:

- `invoice` (full `InvoiceEntity` payload),
- `invoice_id`,
- `bill_id`,
- `encounter_id`,
- `mushex_intent_id` (from `costa_payment_handoffs`),
- `handoff_status`,
- `payment_status` and `paid_at` (latest COSTA payment signal for the bill).

## 4. Safety properties

| Non-negotiable | Outcome |
| --------------- | ------- |
| No silent fabrication of financial data | Both row mappers return `null` / `—` when the upstream field is missing or unrecognised; the page renders empty / error / loading states honestly; the gap card explicitly names the data classes that are *not* yet shown and *why*. |
| No unsafe money movement | The page exposes zero `apiClient.{post,put,patch,delete}` calls. A dedicated test asserts there are no write-action buttons. |
| No accidental production rail routing | Not applicable; this page only reads encounter-scoped COSTA data. |
| No breaking OpenAPI changes | None. New BFF/MusheX consumption only. |
| No database migrations | None. |
| No deletion of deprecated sidecars | None. |
| No removal of legacy mobile/wellness wallet routes | None. |
| No new shell, no new API client, no new state management library, no new auth model | Reuses `AppLayout`, `PageShell`, `apiClient`, TanStack Query, and the existing role-guard pattern. |
| Idempotency behaviour | Not applicable; this is a read-only surface. |
| Docs and audit trail current | Updated in the same batch. |

## 5. Tests (added in this batch)

| File | New tests | What they assert |
| ---- | --------- | ---------------- |
| `ui/one-ui-shell/src/app/finance/costa/encounter/[encounterId]/page.test.tsx` | **7** | header + identity card quote the encounter id and patient id; decisions and cost events merge into a single chronologically-sorted table; rows without a timestamp sink to the bottom; both BFF route URLs are surfaced honestly; the gap card lists the missing list-by-encounter / list-by-source endpoints by name; **no write buttons exist**; empty payloads render honest "no rows" copy; an error from either source renders the documented "Could not load … from COSTA" copy. |
| `ui/one-ui-shell/src/lib/__tests__/routes.test.ts` | **1** | the new `/finance/costa/encounter/[encounterId]` route is registered with FINANCE role, finance sidebar, `app` layout, `work` nav zone, and `pageTitle: "COSTA encounter timeline"`. |

Run results in this batch:

- `npx vitest run src/app/finance/costa src/lib/__tests__/routes.test.ts` → **40 / 40 pass** (28 routes + 7 timeline + 5 COSTA hub).
- `node scripts/route-parity-check.mjs` → **125 / 125 routes resolve to a page file**.

## 6. Backward compatibility

- `EXPECTED_ROUTE_COUNT` raised 270 → 271 to reflect one additive route. `ROUTES.length` matches.
- The COSTA hub gains a single conditional "View timeline →" link inside the existing service-access decisions card; existing tests + sections continue to render unchanged. The link is only present when an `encounterId` is in the URL handoff.
- No public BFF or MusheX endpoint added or changed.
- No OpenAPI change.

## 7. Future work (out of scope here)

1. **Settlement detail deepening:** expose per-settlement payout batch/item summaries on a read-only endpoint so timeline settlement rows can link to richer operational detail without leaving finance timeline context.
2. **Attempt fan-in:** surface per-intent attempt summaries (`GET /mushex/v1/payment-intents/{id}/attempts`) as nested timeline details once compact attempt projection is defined.

Phase 5 controlled actions are now surfaced on `/finance/costa` using existing BFF routes:

- Register service-access decision (`POST /internal/v1/finance/service-access-decisions`)
- Issue invoice from estimate (`POST /internal/v1/finance/costa-intel/invoices/from-cost-estimate`)

Both actions require explicit operator confirmation and required reason capture in UI before submission. No new write endpoint was introduced; only existing endpoints were reused.

## 8. Doctrine alignment

- *Gateway-neutral by design* — preserved; this page reads COSTA encounter data and never touches the routing contract.
- *Gateway-capable by default* — preserved; the page never blocks care.
- *Health-focused always* — preserved; no clinical surface touched.
- *No silent fake data* — strengthened; the gap card explicitly names the data classes that are not yet shown and why.
