# Phase 4 Production Completion Bar

> **Authority:** Phase 4 Full Experience Completion  
> **Reconciles:** [`docs/frontend/GAP_CLOSURE_RULES.md`](../frontend/GAP_CLOSURE_RULES.md), [`docs/product/VNEXT_EXPERIENCE_QUALITY_CRITERIA.md`](./VNEXT_EXPERIENCE_QUALITY_CRITERIA.md)  
> **Generated:** 2026-06-07

Impilo vNext is **production-ready** for a core transaction journey only when every criterion below is satisfied. This bar governs Phase 4 classification, re-baseline measurement, and remediation batches.

---

## 1. Journey-level definition of done

A journey is **production-ready** (`transaction-complete`) only when **all** of the following hold:

| # | Requirement | Proof |
|---|-------------|-------|
| 1 | **Golden thread** | `route/screen → hook/client → BFF → sovereign service → contract` is wired and demonstrable |
| 2 | **Canonical client** | UI uses the bounded-context hook/client (not a generic substitute from another domain) |
| 3 | **Product UI** | Lists, detail, and mutations render typed/domain fields; users can complete the intended workflow |
| 4 | **No production stubs** | No mocks, fixture fallbacks, `JSON.stringify` dumps, empty handlers, or "coming soon" as primary UX |
| 5 | **Tests** | At least one unit, integration, or e2e test proves the happy path (or documented authz denial path) |
| 6 | **Trust & audit** | Meaningful mutations carry TSHEPO authz, trust headers, and audit trace |
| 7 | **Honest maturity** | Surface labelled **Live** or scoped **Partial**; never **Live** on stub/fixture data |
| 8 | **Evidence registry** | Journey listed in `COMPLETION_EVIDENCE` with BFF endpoints, UI routes, and on-disk test files |

**Not sufficient:**

- Route file exists (`page.tsx` parity alone)
- BFF returns hard-coded / fallback JSON pretending to be upstream data
- Matrix or doc manually asserts `transaction-complete` without evidence gate
- Backend contract closed but frontend write UX missing

---

## 2. Experience quality dimensions (journey assessment)

From [`VNEXT_EXPERIENCE_QUALITY_CRITERIA.md`](./VNEXT_EXPERIENCE_QUALITY_CRITERIA.md) — assess **journeys**, not isolated pages:

| Dimension | Production bar |
|-----------|----------------|
| **Intelligent** | Nompilo/search explain transaction state; `/ask` receives route context |
| **Intuitive** | Entry matches actor mental model; steps follow lifecycle stages |
| **Coherent** | Surface knows actor, context, intent, transaction; orchestration ≠ orphan |
| **Flowing** | Next action visible; `transaction_id` threads entry → completion |
| **Relevant** | Live BFF data; role hides irrelevant capability; no fixture fallback |
| **Safe** | Trust headers, guards, consent, audit on meaningful actions |
| **Complete** | Journey reaches `completionState`; mobile parity where doctrine expects it |

---

## 3. Classification ladder (measured, not asserted)

The generator derives `completionClassification` from code signals. Manual literals are forbidden except gap notes in `JOURNEY_GAP_OVERRIDES`.

| Classification | Meaning | Typical signals |
|----------------|---------|-----------------|
| `transaction-complete` | Production-ready per §1 | In `COMPLETION_EVIDENCE`; all evidence files exist; guard passes |
| `backend-ready-but-frontend-incomplete` | Sovereign + BFF green; UI/mobile write or orchestration gap | Contract matrix clean for journey services; stub/hotspot on routes |
| `backend-partial` | Missing OpenAPI ops or AsyncAPI channels for journey services | Contract matrix violations for mapped backend services |
| `frontend-route-exists-but-disconnected` | Routes match; no BFF hook chain detected | Routes present; BFF endpoints absent or unreferenced |
| `mobile-missing` | Web exists; expected mobile screens absent | Web routes > 0; mobile screens = 0 where parity expected |
| `trust-security-incomplete` | Break-glass / emergency authz path incomplete | Emergency journey tag; trust signals fail |
| `event-data-incomplete` | Kafka/event chain incomplete for journey | AsyncAPI gaps on journey event channels |
| `unclear-intent` / `unknown-needs-review` | Cannot classify from signals | Manual PO review required |

Promotion to `transaction-complete` requires **explicit evidence entry** — see [`scripts/guard/check-core-transaction-completion-evidence.sh`](../../scripts/guard/check-core-transaction-completion-evidence.sh).

---

## 4. Forbidden patterns (zero tolerance)

From [`GAP_CLOSURE_RULES.md`](../frontend/GAP_CLOSURE_RULES.md) §2:

**UI:** `JSON.stringify` primary body, empty `onClick`, wrong bounded-context hooks, "coming soon" primary UX  
**BFF:** Static dev fallbacks masquerading as upstream, bypassing composition services, fake 200 empty lists  
**Docs:** "Closed" status for stubs; inflated completion metrics

---

## 5. Verification gates (must pass before deploy authorization)

| Gate | Command |
|------|---------|
| Completion evidence | `bash scripts/guard/check-core-transaction-completion-evidence.sh` |
| Contract implementation | `bash scripts/guard/check-contract-implementation.sh` |
| Backend–frontend parity | `bash scripts/guard/check-backend-frontend-parity.sh` |
| Frontend stub guard | `bash scripts/guard/check-frontend-mocks-and-stubs.sh` |
| UI type-check | `cd ui/one-ui-shell && npm run type-check` |
| No-stub guard | `cd ui/one-ui-shell && npm run test:no-stubs` |
| Route parity | `cd ui/one-ui-shell && npm run test:routes` |
| Stub audit | `bash scripts/stub-audit/run-all.sh` |
| Local quality gates | `bash scripts/pipeline/run-local-quality-gates.sh` |

---

## 6. Phase 4 wave structure

| Wave | Scope |
|------|-------|
| **4.0** | Re-baseline: measured classifier, honest matrix, verification report (this document) |
| **4.1+** | Journey remediation batches per [`FIRST_COMPLETION_BATCH_PLAN.md`](./FIRST_COMPLETION_BATCH_PLAN.md) and derived backlog |

---

## Related docs

- [`CORE_TRANSACTION_COMPLETION_MATRIX.md`](./CORE_TRANSACTION_COMPLETION_MATRIX.md) — generated journey counts
- [`PHASE_4_0_REBASELINE_REPORT.md`](./PHASE_4_0_REBASELINE_REPORT.md) — Phase 4.0 measurement output
- [`docs/audits/CORE_TRANSACTION_HONEST_GAP_AUDIT.md`](../audits/CORE_TRANSACTION_HONEST_GAP_AUDIT.md) — honest gap audit
- [`docs/audits/CORE_TRANSACTION_METRIC_FRAUD_INCIDENT_2026-06-05.md`](../audits/CORE_TRANSACTION_METRIC_FRAUD_INCIDENT_2026-06-05.md) — why evidence gating exists
