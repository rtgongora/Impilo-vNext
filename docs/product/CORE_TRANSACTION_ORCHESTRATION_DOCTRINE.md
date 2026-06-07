# Core Transaction Orchestration Doctrine

> Generated: 2026-06-07T23:09:09.857Z
> Branch: `claude/staging-ux-orchestration-remediation-Yypyl`
> Phase: **2 — Core Transaction Mapping**
> Canonical predecessor: [CORE_TRANSACTION_DOCTRINE.md](../doctrine/CORE_TRANSACTION_DOCTRINE.md)

## Foundational statement

**The Core Transaction Doctrine is the spine of Impilo vNext.**

Every major feature must support a core transaction or be explicitly classified as supporting/internal infrastructure. A capability is not complete because an API exists, a page exists, or a button exists.

A transaction is complete only when the intended actor can enter the correct context, start the relevant core transaction, use real backend capability, move through a coherent frontend/mobile journey, complete the transaction end-to-end, and understand what comes next.

## The seven orchestration questions

Every core transaction mapping must answer:

| Question | Orchestration field |
|----------|---------------------|
| Who or what is initiating? | **Initiating actor** — citizen, provider, nurse, device, scheduled job, external system, etc. |
| Who or what is responding? | **Responding actor** — sovereign service or composed BFF façade |
| What is being transacted? | **Transaction object** — Health ID, Encounter, Order, Claim, Consent, etc. |
| In what context? | **Transaction context** — facility, workspace, shift, purpose-of-use, assurance level |
| What services must cooperate? | **Required orchestration** — BFF composes; sovereign services own truth |
| What records/events/trust apply? | **Data, events, trust checks, audit** |
| What is the completion state? | **Completion state** — canonical `CoreTransactionState` from `contracts/core-transaction.ts` |

## Orchestration model

```
Actor → Context → Transaction Type → Lifecycle Stage → Cooperating Services
  → BFF Composition → Web/Mobile Journey → State Transition → Events + Audit → Next Action
```

### Initiating actor

Actors are not always human persons. Valid initiators include: client/citizen, provider, nurse, pharmacist, laboratory user, radiology user, facility administrator, registry administrator, community health worker, courier, device, mobile app, AI assistant (Nompilo), scheduled job, facility pod, external integration system, offline edge node.

### Responding actor

The **responding actor** is the sovereign service that owns the transaction object's source of truth, composed through Experience BFF. BFF orchestrates; it does not become SoR for clinical, registry, trust, or finance truth.

### Transaction object

Bound to `CoreTransactionType` in [`contracts/core-transaction.ts`](../../contracts/core-transaction.ts): e.g. `FACILITY_WALK_IN`, `APPOINTMENT`, `TELEMEDICINE`, `PHARMACY`, `LABORATORY`, `IMAGING`, `REFERRAL`, `MARKETPLACE`, `WELLNESS`, `TRAINING_OR_COMPETENCY`, `COMMUNITY_OUTREACH`, `CHRONIC_CARE`, `EMERGENCY`, `ADMINISTRATIVE_HEALTH`.

### Transaction context

Mandatory trust headers (see `CompanionHeaders` / `api-client.ts`): tenant, pod, actor, facility, workspace, shift, purpose-of-use, assurance level. Context activation precedes transaction start.

### Required orchestration

1. Envoy → TSHEPO `ext_authz` before any service
2. Experience BFF composes sovereign calls under `/internal/v1/*`
3. State machine tracked via `/internal/v1/core-transactions/*` where applicable
4. Outbox → Kafka for reliable event emission

### Completion state

Canonical states in `contracts/core-transaction.ts`. Terminal success: `COMPLETED`, `CLOSED`. Failure/denial: `ACCESS_DENIED`, `CONSENT_DENIED`, `PAYMENT_FAILED`, etc.

### Audit / trust / security requirements

- Every meaningful action: permission decision + audit event
- Break-glass and emergency override: explicit audit chain
- Consent evaluation before clinical/financial actions where required
- No PII in BUTANO SHR (CPID only)

### Event / data outputs

- Domain events via `event_outbox` tables
- Core transaction dual-emit: `core.transaction.events` (see `contracts/asyncapi/core-transaction-events.asyncapi.yaml`)
- Reporting aggregates: `analytics.reporting.aggregate`

### Frontend / mobile journey implications

- **One UI Shell** (`ui/one-ui-shell`) is the canonical web orchestration surface
- Mobile: `citizen-app` + `provider-app` with mode-specific journeys
- Journey must be coherent: entry → steps → completion → next action (Nompilo may explain)
- Fixture-backed doctrine pages (`/core-transaction`, `/client-journey`) are **not** transaction-complete

### Completion priority (Phase 3+)

**Prioritize the clinical spine and complete orchestration on existing surfaces first** — wire fixtures to BFF, close write-contract gaps, extend mobile parity on routes that already exist.

This is a **sequencing preference**, not a ban on new UI:
- **New UI is in scope** when it completes an orchestrated core transaction journey (new step, screen, or route required by the journey map).
- **Do not** add cosmetic pages, mock demos, or disconnected shells that bypass the transaction spine.
- **Do not** delete working routes or replace API-integrated flows with static UI — extend and wire what exists.

## Classification rule

| Class | Meaning |
|-------|---------|
| **Core transaction journey** | User-facing end-to-end flow mapped in journey maps |
| **Supporting service** | Infrastructure, analytics, adapter, or internal plumbing — not a standalone journey |
| **Internal-only** | Trust enforcement, audit, observability — no direct UI required |

## Phase 2 outputs

| Artifact | Path |
|----------|------|
| Journey maps | [CORE_TRANSACTION_JOURNEY_MAPS.md](./CORE_TRANSACTION_JOURNEY_MAPS.md) |
| Completion matrix | [CORE_TRANSACTION_COMPLETION_MATRIX.md](./CORE_TRANSACTION_COMPLETION_MATRIX.md) |
| Journey JSON | [core-transaction-journey-maps.json](../../reports/product/core-transaction-journey-maps.json) |
| Matrix JSON | [core-transaction-completion-matrix.json](../../reports/product/core-transaction-completion-matrix.json) |

## References

- [`docs/doctrine/CORE_TRANSACTION_DOCTRINE.md`](../doctrine/CORE_TRANSACTION_DOCTRINE.md)
- [`docs/architecture/three-journey-core-transaction-map.md`](../architecture/three-journey-core-transaction-map.md)
- [`docs/templates/CORE_TRANSACTION_FEATURE_ALIGNMENT_CHECKLIST.md`](../templates/CORE_TRANSACTION_FEATURE_ALIGNMENT_CHECKLIST.md)
- Phase 1: [PRODUCT_TRUTH_RECOVERY_MAP.md](./PRODUCT_TRUTH_RECOVERY_MAP.md)
