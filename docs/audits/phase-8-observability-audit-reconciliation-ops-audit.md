# Phase 8 — Observability, audit, reconciliation, operations — surface audit

| Field | Value |
| ----- | ----- |
| Status | Implemented through slices 8A–8F (repository-owned scope). |
| Predecessors | Phase 6 / 7 audits and ledgers. |
| Doctrine | [`docs/doctrine/mushex-gateway-neutrality.md`](../doctrine/mushex-gateway-neutrality.md), [`docs/doctrine/costa-mushex-billing-timing.md`](../doctrine/costa-mushex-billing-timing.md) |
| Companion | [`mushex-costa-outbox-event-catalogue.md`](./mushex-costa-outbox-event-catalogue.md) |
| Audit register | [`costa-mushex-experience-layer-wiring-audit.md`](./costa-mushex-experience-layer-wiring-audit.md) |

## 1. Scope

Phase 8 covers four observability-adjacent surface families:

- **Observability** — Kafka outbox event publishing, structured logs, metrics, traces.
- **Audit** — finance-relevant audit trail (`/internal/v1/admin/audit` BFF + UI at `/admin/audit`).
- **Reconciliation** — three-way reconciliation (intent ⇄ rail settlement ⇄ COSTA invoice) via `/internal/v1/finance/reconciliation` BFF + UI at `/finance/reconciliation`.
- **Operations** — runtime runbooks, start-order docs, troubleshooting guides.

This audit produces a **gap map** for each family, identifies the safest single additive deliverable (the cross-service outbox event catalogue), and queues operational follow-ons.

## 2. Current state — observability (Kafka outbox)

### 2.1 MusheX — `OutboxPublisher#routeTopic`

12 known event types map to dedicated topics:

| Event type | Kafka topic |
| ---------- | ----------- |
| `INTENT_CREATED` | `mushex.payment.intent.created` |
| `STATUS_CHANGED` | `mushex.payment.status.changed` |
| `REFUND_REQUESTED`, `REFUND_COMPLETED`, `REFUND_FAILED` | `mushex.refund.status.changed` |
| `CLAIM_SUBMITTED` | `mushex.claim.submitted` |
| `CLAIM_ADJUDICATED`, `CLAIM_PAID` | `mushex.claim.adjudicated` |
| `SETTLEMENT_BATCH_RELEASED` | `mushex.settlement.batch.released` |
| `FRAUD_FLAGGED` | `mushex.fraud.flagged` |
| `REMITTANCE_ISSUED` | `mushex.remittance.issued` |
| `REMITTANCE_CLAIMED` | `mushex.remittance.claimed` |
| `WALLET_CREATED` | `mushex.wallet.created` |
| `WALLET_TRANSACTION_RECORDED` | `mushex.wallet.transaction.recorded` |
| `REMITTANCE_REQUESTED` | `mushex.remittance.requested` |
| (anything else) | `mushex.events` |

Other `publishEvent` call-sites in `mushex-service` use event types that fall through to the default `mushex.events` topic. These are catalogued in § 5 / the companion event catalogue.

### 2.2 COSTA — `OutboxPublisher#routeTopic`

15 known event types map to dedicated topics:

| Event type | Kafka topic |
| ---------- | ----------- |
| `BILL_DRAFT_CREATED` | `costa.bill.draft.created` |
| `BILL_APPROVAL_REQUESTED` | `costa.bill.approval.requested` |
| `BILL_APPROVED` | `costa.bill.approved` |
| `BILL_FINALIZED` | `costa.bill.finalized` |
| `BILL_VOIDED` | `costa.bill.voided` |
| `INVOICE_ISSUED` | `costa.invoice.issued` |
| `PAYMENT_INTENT_CREATED` | `costa.payment.intent.created` |
| `PAYMENT_STATUS_CHANGED` | `costa.payment.status_changed` |
| `PAYMENT_ALLOCATED` | `costa.payment.allocated` |
| `CHARGE_CREATED` | `costa.charge.created` |
| `INVOICE_REFUND_APPLIED` | `costa.invoice.refund_applied` |
| `REFUND_CREATED` | `costa.refund.issued` |
| `CLAIM_PACK_CREATED` | `costa.claim.pack.created` |
| `ESTIMATE_CREATED` | `costa.estimate.created` |
| `RULESET_PUBLISHED` | `costa.ruleset.published` |
| (anything else) | `costa.events` |

### 2.3 Gaps identified

- There is **no single document** that catalogues the union of all MusheX + COSTA outbox events in one place. Operators and integrators have to grep two source trees to know what to subscribe to.
- There is no documented **consumer map** — who subscribes to which topic, downstream of these two services.
- There is no convention test that asserts every `publishEvent(...)` call-site is also wired into `routeTopic(...)`. Today, the default `mushex.events` / `costa.events` fall-through is the only safety net.

The **outbox event catalogue** (§ 5 / companion doc) closes the first gap and partially closes the second.

## 3. Current state — audit

- BFF: `AuditController` exposes `GET /internal/v1/admin/audit` (list) and `GET /internal/v1/admin/audit/{id}` (detail).
- UI: `ui/one-ui-shell/src/app/admin/audit/page.tsx` (list) and `[id]/page.tsx` (detail) consume the BFF.
- Gap: no end-to-end **audit trail join** between a payment-intent id and the audit-controller rows. The audit page does not surface "events related to intent X" or "events related to wallet Y"; it shows global activity only. Closing this needs an API query parameter or a new join endpoint and is deferred.

## 4. Current state — reconciliation

- BFF: `ReconciliationController` exposes `POST /import-statement`, `GET /unmatched`, `POST /match`.
- UI: `ui/one-ui-shell/src/app/finance/reconciliation/page.tsx` consumes the BFF.
- Gap: reconciliation is only **MusheX-side**; COSTA-invoice-side matching (intent ⇄ invoice ⇄ rail-settlement triple) is not yet wired. Closing this needs a new join endpoint and is deferred — same blocker as the "MusheX list-by-source payment intents" gap identified in the Phase 5 audit.

## 5. Current state — operations runbooks

- `docs/runtime/platform-operations-runbook.md`, `platform-startup-architecture.md`, `platform-startup-quickstart.md`, `platform-troubleshooting-guide.md`, `platform-component-start-order.md` cover platform startup, troubleshooting, and operational order-of-events.
- Gap: no dedicated **finance-plane operations runbook** that names the MusheX/COSTA outbox topics, the audit query commands, and the reconciliation cycle. Closing this is a documentation slice and can be done autonomously, but the prerequisite is the outbox event catalogue.

## 6. This batch — concrete deliverable

This batch delivers the **cross-service outbox event catalogue** at [`mushex-costa-outbox-event-catalogue.md`](./mushex-costa-outbox-event-catalogue.md). It records, in one place:

- Every event type emitted by MusheX or COSTA `publishEvent(...)` call-sites.
- The Kafka topic each event lands on (per the two `routeTopic` switches).
- The originating service.
- The aggregate type.
- The known payload-shape keys (where the source is unambiguous — for the new Phase 5 G-4 `rail_selection` keys, the payload schema is documented in [`g4-rail-selection-policy.md`](../design/g4-rail-selection-policy.md)).

The catalogue is **definitional**: it does not configure dashboards, alerts, or consumers. The catalogue is the prerequisite for follow-on operational slices (8B–8F).

## 7. Recommended Phase 8 slice order

| Slice id | What | Risk |
| -------- | ---- | ---- |
| 8A | Cross-service outbox event catalogue. | Implemented |
| 8B | Convention test in each service (`mushex-service`, `costing-engine-service`) that asserts event types are explicitly routed (or intentionally defaulted, for MusheX). | Implemented |
| 8C | Finance-plane operations runbook. | Implemented |
| 8D | Audit-page aggregate scoping filter (`aggregateType`, `aggregateId`) through BFF + UI. | Implemented |
| 8E | COSTA-invoice ⇄ MusheX-intent ⇄ rail-settlement triple reconciliation join endpoint + UI read panel. | Implemented |
| 8F | Per-topic consumer map (who subscribes to which event topic). | Implemented |

## 8. Doctrine alignment

- *Health-focused* — preserved; this batch produces only documentation infrastructure.
- *No fabricated data* — preserved; the catalogue lists only the event types actually emitted by current code (the `publishEvent` call-sites and `routeTopic` switches were read directly).
- *No deletion / no breakage / additive-only* — preserved.

## 9. Implementation update (8B–8F)

- **8B:** Added outbox routing convention tests in MusheX and COSTA; COSTA mapping now includes `PAYMENT_CANCELLED` on `costa.payment.status_changed` (no silent default fallback).
- **8C:** Added finance-plane operator runbook at [`docs/runbooks/finance-plane-operations-runbook.md`](../runbooks/finance-plane-operations-runbook.md).
- **8D:** Added aggregate filters to BFF audit list endpoint and canonical `/admin/audit` UI.
- **8E:** Added BFF `GET /internal/v1/finance/reconciliation/triple-match?encounterId=...` plus canonical UI section in `/finance/reconciliation`.
- **8F:** Added topic consumer map at [`phase-8-topic-consumer-map.md`](./phase-8-topic-consumer-map.md).
