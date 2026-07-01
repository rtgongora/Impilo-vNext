# Intelligent Budgeting & Budget Tracking — Discovery, Ownership & Boundaries

## 1. Discovery result

Budgeting is **already owned** in vNext — it was NOT built as a new service.

| Capability | Existing owner | Decision |
|------------|----------------|----------|
| Operational budget allocations, cost centres, spend tracking | **COSTA** (`costing-engine-service`, 8101) | **EXTEND** — COSTA is the operational/programme budgeting spine |
| Account-level (chart-of-accounts) budget lines | **general-ledger-service** (`gl.budget_lines`) | Left as the accounting mirror; referenced by string (`gl_account_ref`), never written |
| Billing / invoices / charges | COSTA | Consumed as actuals (`COSTA_INVOICE`); not duplicated |
| Payments / receipts | **MusheX** | Consumed as actuals (`MUSHEX_PAYMENT`); not duplicated |
| Marketplace / orders / stock / assets / training / emergency | Msika / Oros / Dura / asset svc / Fundo / Daidzai | Consumed as **commitment references** (owner_system + owner_reference); truth stays with the owner |
| Trust / policy | Tshepo + OPA | New `impilo.budget` policy added |
| Comms / guidance | Khuluma / Nompilo | Budget events emitted; guidance surfaced in UI |

**Doctrine applied:** no duplicate system-of-record, no new service where an owner exists.
The pre-existing thin `costa_budget_allocations` table + its 3 endpoints are preserved
byte-for-byte and repurposed as a denormalised **availability-control projection**.

## 2. What was built (all in COSTA unless noted)

- **Rich model** wrapping the flat allocation: `costa_budget` → `_version` → `_line`,
  `_funding_source`, `_approval` (transition/audit log), `_commitment`, `_actual_reference`,
  `_variance_snapshot`, `_forecast_snapshot`, `_recommendation`, `_reconciliation_exception`,
  `_threshold`, `_assumption`, `_revision`, `_period_close` (migrations V015–V018).
- **Lifecycle FSM** (`BudgetLifecycle`): DRAFT→SUBMITTED→UNDER_REVIEW→APPROVED→ACTIVE→
  (REVISED via new version)→FROZEN→CLOSED→ARCHIVED. Segregation of duties on approve.
- **Execution:** commitments (obligated ≤ committed, liquidated ≤ committed invariants);
  actuals as an idempotent reference ledger (unique on `source, source_reference`);
  availability control (ALLOW / WARN / HARD_STOP / EMERGENCY_OVERRIDE — clinical emergency
  never HARD_STOPped).
- **Intelligence (transparent, no ML):** variance vs elapsed-time spend pace with an
  explainable classification; LINEAR_YTD + BURN_RATE forecasts with all drivers persisted
  to `inputs_json`; rule-based recommendations (OVERSPEND_RISK, UNDERABSORPTION,
  THRESHOLD_BREACH) each carrying `evidence_json`; nightly snapshot sweep.
- **Governance:** revisions/reprogramming (VIREMENT nets to zero), thresholds,
  reconciliation exceptions, funding-source absorption, period close + carry-forward.
- **Events:** 15 `BUDGET_*` outbox types on the existing COSTA outbox; financially-material
  ones dual-emit to `core.transaction.events`.
- **Surfaces:** BFF `ManagedBudgetBffController` (pass-through, safe partial on failure);
  web `/budgets` dashboard + `/budgets/[id]` detail (budget-vs-actual, variance, forecast,
  recommendations, registers); mobile provider budget-summary + variance + approval slice;
  OPA `impilo.budget` policy (+ tests).

## 3. Ownership boundaries (must not duplicate)

- **Costa** remains billing/invoice truth; budgeting consumes invoice references as actuals.
- **MusheX** remains payment truth; budgeting consumes payment references as actuals.
- **Dura / Msika / Oros / Fundo / Daidzai / PCT / asset svc** remain their own truth;
  budgeting stores only commitment references keyed on their document ids.
- **GL** remains account-level budget truth; budgeting references GL accounts by string.
- Budgeting creates **no** payment, invoice, stock ledger, marketplace, clinical or ERP record.

## 4. Forecasting & recommendation logic (explainable)

- `elapsed_fraction = days_elapsed / days_in_period`
- `LINEAR_YTD projected = actual_ytd / elapsed_fraction`
- `BURN_RATE: burn = actual_ytd / days_elapsed; projected = actual_ytd + burn × days_remaining`
- `projected_overrun = projected − budgeted`
- Variance classification compares `actual` to `budgeted × elapsed_fraction` (spend pace),
  with OVERSPENT when `actual > budgeted` and UNDERSPENT_STALE when > 20% behind pace after
  25% elapsed.
- Recommendation rules fire from these numbers only; each persists the drivers that fired it.
- `SEASONAL_NAIVE` is not yet implemented and **honestly falls back to BURN_RATE** (no fake curve).

## 5. Deferred-seam register (none faked)

| Area | Classification | Seam / owner | Built surface | Deferred depth | No-fake guarantee |
|------|----------------|--------------|---------------|----------------|-------------------|
| MusheX payments → actuals | substrate exists + wiring-deferred | `BudgetActualConsumer` on `mushex.payment.status.changed` | posts an actual only when the payment is **budget-tagged** (`budgetId`+`budgetLineId`) | upstream tagging of disbursements to budget lines; generic patient payments are intentionally ignored | generic payments are never treated as budget spend — no fabricated expenditure |
| COSTA invoices → actuals | owner-routed hook | actuals API `source=COSTA_INVOICE` | invoice reference posted as an actual via API | automatic in-process handler on every invoice event | reference only; invoice truth stays in COSTA |
| External ERP / PFMS → actuals | owner-routed hook / honest import | actuals API `source=EXTERNAL_ERP, ingest=IMPORT` | imported references flagged as external | live ERP/PFMS connector | imported, never posted as an executed vNext transaction |
| Oros/Msika/Dura/Fundo/Daidzai/PCT → commitments | owner-routed hook | commitments API `owner_system + owner_reference` | commitments recorded against lines by reference | per-owner Kafka consumers upserting commitments from PO/req/contract events | owner record never duplicated; COSTA holds a pointer + amount |
| Advanced (seasonal/ML) forecasting | partially built + depth-deferred | `BudgetForecastService` | LINEAR_YTD + BURN_RATE, fully explainable | seasonal curve / ML | `SEASONAL_NAIVE` falls back to BURN_RATE — never a fabricated projection |
| Khuluma budget alerts | owner-routed hook | `BUDGET_THRESHOLD_BREACHED` / `_RECOMMENDATION_RAISED` outbox events | events emitted for delivery | Khuluma dispatch wiring / preference UI | delivery owned by Khuluma; budgeting only emits |
| Mobile tab wiring | substrate exists + wiring-deferred | `BudgetSummaryScreen` + `budgetService` | functional screen (real data + approval task) | registry/slug-driven provider tab entry | screen is real, not a placeholder; only the tab entry is deferred |
| Web component tests | partially built + depth-deferred | `/budgets` pages | pages typecheck (tsc clean); golden path proven at service layer | vitest render tests for the pages | pages call real BFF endpoints; no mock data baked in |

**Guarantee:** every seam is a real hook to the named owner, a partially-built spine with
depth deferred, or an existing substrate not yet fully wired — none is a fake UI/API.
No payment, invoice, disbursement, commitment, actual, forecast or recommendation is fabricated.
