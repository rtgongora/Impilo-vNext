# Enterprise finance-domain operations runbook (Phase 8C)

| Field | Value |
| ----- | ----- |
| Status | Implemented (Phase 8C) |
| Scope | MusheX + COSTA enterprise-plane finance-domain eventing, audit scope checks, and reconciliation cycle operations |
| Inputs | [`mushex-costa-outbox-event-catalogue.md`](../audits/mushex-costa-outbox-event-catalogue.md), reconciliation/audit canonical routes |

## 1. Purpose

Provide a single operator-facing runbook for enterprise-plane finance-domain event health checks and daily reconciliation execution without requiring codebase greps.

## 2. Topic health checks

- **Producer routing source of truth:** `services/mushex-service/.../kafka/OutboxPublisher.java`, `services/costing-engine-service/.../kafka/OutboxPublisher.java`.
- **Catalog view:** `docs/audits/mushex-costa-outbox-event-catalogue.md`.
- **Convention tests (Phase 8B):**
  - `mushex-service`: `OutboxPublisherTest.routingConvention_producedTypesAreExplicitOrIntentionalDefaults`
  - `costing-engine-service`: `OutboxPublisherRoutingConventionTest.producedEventTypes_routeToDedicatedTopics_notDefaultCatchAll`

## 3. Audit checks (Phase 8D)

- **List endpoint:** `GET /internal/v1/admin/audit?page=...&size=...&aggregateType=...&aggregateId=...`
- **Detail endpoint:** `GET /internal/v1/admin/audit/{id}`
- **Canonical UI:** `/admin/audit`
- **Operational pattern:** scope to a target aggregate (`PAYMENT_INTENT`, `WALLET`, `INVOICE`) before incident triage.

## 4. Triple-source reconciliation checks (Phase 8E)

- **Join endpoint:** `GET /internal/v1/finance/reconciliation/triple-match?encounterId=...`
- **Canonical UI:** `/finance/reconciliation` → “Triple-source match”
- **Join intent:** invoice rows (COSTA lifecycle) + MusheX intents (source list by bill id) + settlements (intent-filtered).

## 5. Daily cycle

1. Import latest statement lines in `/finance/reconciliation`.
2. Fetch unmatched list and clear obvious matches.
3. Run triple-source check for high-volume encounters.
4. Spot-check audit stream scoped by affected aggregate IDs.
5. Record unresolved mismatches with encounter + bill + intent ids.

## 6. Escalation signals

- Reconciliation unmatched count grows over two cycles.
- Triple-source rows show invoice without intent for finalized billing.
- Audit scoped query returns no records for expected mutation windows.
