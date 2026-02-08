# Impilo vNext — Bounded Staleness Rules & Class A/B/C Mapping

**Date**: 2026-02-08

---

## Clinical Safety Consistency Classes (v1.1 Law 6)

### Class Definitions

| Class | Name | Rule | Enforcement | Failure Mode |
|---|---|---|---|---|
| **A** | Hard-Truth Required | Synchronous Kernel validation OR signed entitlement + freshness proof | Block action if check fails (unless break-glass) | Deny + audit |
| **B** | Bounded-Stale Allowed | Proceed on projections if staleness ≤ threshold; log decision evidence | Warn if approaching threshold; deny if exceeded | Log + allow within bounds |
| **C** | Always Allowed Offline | Signed offline entitlement + audit logging + post-sync reconciliation | Validate entitlement token; queue for reconciliation | Allow + reconcile later |

---

## Initial Action Classification Table

### TSHEPO (Trust & Policy)

| Action | Class | Staleness Limit | Rationale |
|---|---|---|---|
| `tshepo.policy.evaluate` | A | 0 (sync) | Policy decisions are truth — no stale allowed |
| `tshepo.consent.revoke` | A | 0 (sync) | Consent revocation must be immediately effective |
| `tshepo.consent.evaluate` | A | 0 (sync) | Consent checks gate data access |
| `tshepo.break_glass.activate` | A | 0 (sync) | Break-glass requires real-time audit |
| `tshepo.step_up.verify` | A | 0 (sync) | Step-up auth is security-critical |
| `tshepo.offline.issue_entitlement` | A | 0 (sync) | Entitlement issuance must verify current state |

### VITO (Client Registry)

| Action | Class | Staleness Limit | Rationale |
|---|---|---|---|
| `vito.client.register` | A | 0 (sync) | New client creation requires dedup check |
| `vito.client.merge` | A | 0 (sync) | Merge affects identity truth |
| `vito.client.lookup` | B | 5 min | Routine lookups can use cached projections |
| `vito.cpid.resolve` | B | 5 min | CPID mapping can be bounded-stale |

### VARAPI (Provider Registry)

| Action | Class | Staleness Limit | Rationale |
|---|---|---|---|
| `varapi.privilege.check` | A | 0 (sync) | Clinician privilege verification is safety-critical |
| `varapi.privilege.revoke` | A | 0 (sync) | Revocation must be immediately effective |
| `varapi.provider.lookup` | B | 15 min | Routine provider info can be stale |
| `varapi.licensure.verify` | A | 0 (sync) | Licensure status gates clinical actions |

### PCT (Patient Care Tracker)

| Action | Class | Staleness Limit | Rationale |
|---|---|---|---|
| `pct.journey.create` | B | 10 min | New journey requires patient lookup (bounded stale OK) |
| `pct.triage.record` | C | offline allowed | Emergency triage must always be possible |
| `pct.vitals.record` | C | offline allowed | Vitals capture must never be blocked |
| `pct.encounter.start` | B | 10 min | Encounter needs patient context |
| `pct.encounter.complete` | B | 10 min | Completion needs order/result context |
| `pct.death.record` | A | 0 (sync) | Death recording is identity-affecting, irreversible |
| `pct.discharge.finalize` | B | 5 min | Discharge needs blocker clearance check |
| `pct.admission.create` | B | 10 min | Admission needs bed availability context |

### OROS (Orders & Results)

| Action | Class | Staleness Limit | Rationale |
|---|---|---|---|
| `oros.order.place.controlled` | A | 0 (sync) | Controlled substance orders require privilege + formulary sync check |
| `oros.order.place.high_risk` | A | 0 (sync) | High-risk procedure orders require sync privilege verification |
| `oros.order.place.routine` | B | 15 min | Routine orders can proceed on bounded-stale catalog/formulary |
| `oros.order.cancel` | B | 5 min | Cancellation needs current order state |
| `oros.result.enter` | B | 15 min | Result entry can tolerate bounded staleness |
| `oros.result.critical.flag` | A | 0 (sync) | Critical result flagging requires immediate acknowledgement routing |
| `oros.order.place.emergency` | C | offline allowed | Emergency orders must always be capturable |

### COSTA (Costing Engine)

| Action | Class | Staleness Limit | Rationale |
|---|---|---|---|
| `costa.bill.finalize` | A | 0 (sync) | v1.1 explicitly classifies billing finalization as Class A |
| `costa.claims.submit` | A | 0 (sync) | Claims submission finalization is Class A |
| `costa.bill.draft` | B | 30 min | Draft billing can use stale tariffs |
| `costa.estimate` | B | 30 min | Cost estimates are advisory |
| `costa.tariff.update` | A | 0 (sync) | Tariff changes affect pricing truth |

### Pharmacy

| Action | Class | Staleness Limit | Rationale |
|---|---|---|---|
| `pharmacy.dispense.controlled` | A | 0 (sync) | Controlled substance dispensing requires real-time checks |
| `pharmacy.dispense.routine` | B | 15 min | Routine dispensing can use bounded-stale stock |
| `pharmacy.dispense.emergency` | C | offline allowed | Emergency dispensing must be possible offline |
| `pharmacy.stock.adjust` | B | 15 min | Stock adjustments can tolerate bounded staleness |
| `pharmacy.substitution.approve` | B | 10 min | Substitution needs formulary context |

### MUSHEX (Finance Engine)

| Action | Class | Staleness Limit | Rationale |
|---|---|---|---|
| `mushex.payment.authorize` | A | 0 (sync) | Payment authorization is financial truth |
| `mushex.payment.finalize` | A | 0 (sync) | Payment finalization is Class A |
| `mushex.claim.adjudicate` | A | 0 (sync) | Claims adjudication affects financial record |
| `mushex.settlement.release` | A | 0 (sync) | Settlement release requires step-up + audit |
| `mushex.remittance.issue` | A | 0 (sync) | Remittance issuance is financial truth |
| `mushex.fraud.flag` | B | 5 min | Fraud flagging can use bounded-stale signals |
| `mushex.receipt.generate` | B | 15 min | Receipt generation is informational |

### MSIKA (Product Registry)

| Action | Class | Staleness Limit | Rationale |
|---|---|---|---|
| `msika.catalog.publish` | A | 0 (sync) | Catalog publication affects pricing truth |
| `msika.tariff.update` | A | 0 (sync) | Tariff changes are financial truth |
| `msika.item.lookup` | B | 30 min | Item lookups can be bounded-stale |
| `msika.pack.assign` | B | 15 min | Pack assignments affect available items |

### ZIBO (Terminology)

| Action | Class | Staleness Limit | Rationale |
|---|---|---|---|
| `zibo.artifact.publish` | A | 0 (sync) | Publishing makes terminology nationally authoritative |
| `zibo.validation.sync` | B | 30 min | Terminology validation can use cached value sets |
| `zibo.mapping.resolve` | B | 30 min | Code mapping can use cached concept maps |

---

## Bounded Staleness Thresholds Summary

| Domain | Max Staleness (Class B) | Rationale |
|---|---|---|
| Identity (VITO CPID) | 5 minutes | Patient identity is high-sensitivity |
| Provider Privileges (VARAPI) | 5 minutes | Privilege state affects clinical safety |
| Facility Data (TUSO) | 15 minutes | Facility topology changes infrequently |
| Product Catalog (MSIKA) | 30 minutes | Catalog changes are versioned with effective dates |
| Terminology (ZIBO) | 30 minutes | Terminology updates are governed and versioned |
| Order Context (OROS) | 15 minutes | Order state changes frequently during execution |
| Billing (COSTA) | 30 minutes | Draft billing is advisory; finalization is Class A |
| Stock Levels (Pharmacy) | 15 minutes | Stock levels change with each dispense |

---

## Enforcement Implementation

### Gateway-Level (Envoy + TSHEPO Authz)

```
Request arrives at Envoy
  → ext_authz call to tshepo-authz-service
  → PolicyEngine:
    1. Identify action from (HTTP method, path, headers)
    2. Look up action_classification table
    3. If Class A:
       a. Execute sync Kernel checks (privilege, consent, staleness=0)
       b. If check fails → check break-glass state
       c. If break-glass → allow with elevated audit
       d. If no break-glass → DENY with reason
    4. If Class B:
       a. Check projection staleness header (x-projection-staleness-ms)
       b. If staleness > threshold → DENY with reason
       c. If within bounds → ALLOW with logged evidence
    5. If Class C:
       a. Validate offline entitlement token (signature, scope, expiry, device)
       b. If valid → ALLOW with reconciliation queue flag
       c. If invalid → DENY
    6. Log DecisionEvidence to audit service
    7. Return verdict + obligations to Envoy
```

### Service-Level (TrustContextFilter)

```java
// After gateway enforcement, services receive:
// x-consistency-class: A|B|C
// x-projection-staleness-ms: <value>
// x-decision-evidence-id: <audit-event-uuid>
// x-break-glass: true|false

// Services can assert:
TrustContext ctx = TrustContextHolder.get();
assert ctx.consistencyClass() == ConsistencyClass.A : "Expected Class A for this action";
```

---

## Break-Glass Protocol

| Step | Action | Requirement |
|---|---|---|
| 1 | Class A check fails | System denies action normally |
| 2 | Clinician activates break-glass | Provides explicit justification text |
| 3 | System grants time-bounded elevated access | Default: 30 minutes, configurable per action |
| 4 | All actions under break-glass are audit-logged | Mandatory, cannot be suppressed |
| 5 | Compliance review workflow triggered | Break-glass events routed to compliance team |
| 6 | Post-hoc review required | Compliance must review within 24 hours |
