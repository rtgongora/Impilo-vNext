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

## 7. Billing-category vocabulary alignment (patient_category / facility_category)

COSTA charging rules and tariffs match on two string dimensions — `patient_category` and
`facility_category`. These strings are **not** defined by COSTA; they are **sourced upstream**
and must be kept in sync with the rule/tariff conditions admins configure, or exemptions and
facility-tier pricing silently fail to match.

**Where each value originates (system of record):**

| Dimension | Source of truth | How it is produced |
| --------- | --------------- | ------------------ |
| `patient_category` | **coverage-service** | `GET /internal/v1/coverage/patient-category/{cpid}` resolves, in order: an **active subsidy enrolment**'s `exemption_category` (`cv_subsidy_enrollments`) → the active **coverage plan**'s `plan_type` (`cv_coverage_plans`) → `CASH` (self-pay). |
| `facility_category` | **tuso-service** | `facility.facility_category` on the facility record, exposed via `GET /v1/internal/facilities/{id}` (falls back to `level`). |

**Flow into pricing:** experience-bff composes both onto the teleconsult referral → PCT persists
them and emits them on the `TELECONSULT_COMPLETED` value-trigger (`clinical.teleconsult.value`) →
COSTA reads them into the `RuleContext` evaluated by `ChargingRuleEngine`.

**Operational rule:** when an admin authors a charging rule or tariff condition such as
`{"patient_category": "INDIGENT"}` or `{"facility_category": "CENTRAL"}`, the matching value
**must already exist** as:

- a subsidy enrolment `exemption_category` (or coverage `plan_type`) string for `patient_category`, and
- a facility `facility_category` (or `level`) string for `facility_category`.

Strings are compared exactly (COSTA uppercases nothing on the rule side); coverage-service
uppercases `exemption_category`/`plan_type` on resolution, so author tariff/rule conditions in
**UPPER CASE**. Mismatched casing or vocabulary is the most common reason an exemption "does not
fire" despite an active enrolment.

**Before adding a new patient/facility category to tariff/rule config:** confirm the same string
is produced by the source above (enrol a member, or set the facility's `facility_category`).
Categories with no upstream producer will never match and should not be added to rules.
