# Core Transaction State Machine

Canonical implementation contract: `contracts/core-transaction.ts`.

## Main States

`DRAFT -> INITIATED -> IDENTITY_PENDING -> IDENTITY_RESOLVED -> TRUST_CONTEXT_ESTABLISHED -> SERVICE_SELECTED -> COSTING_REQUIRED -> COST_ESTIMATED -> COVERAGE_CHECK_PENDING -> COVERAGE_CONFIRMED -> PRE_SERVICE_PAYMENT_REQUIRED -> PRE_SERVICE_PAYMENT_PENDING -> PRE_SERVICE_PAYMENT_COMPLETED -> ACCESS_GRANTED -> SCHEDULED -> QUEUED -> TASKED -> TRIAGE_IN_PROGRESS -> READY_FOR_PROVIDER -> IN_SERVICE -> ORDERS_PENDING -> ANCILLARY_IN_PROGRESS -> PROVIDER_REVIEW_PENDING -> POST_SERVICE_BILLING_PENDING -> FINANCIAL_PROCESSING -> CLIENT_INSTRUCTIONS_PENDING -> CLINICAL_COMPLETION_PENDING -> SHR_UPDATE_PENDING -> FOLLOW_UP_ACTIVE -> CLAIM_PENDING -> RECONCILIATION_PENDING -> COMPLETED -> CLOSED`

## Exception and Branch States

`CANCELLED`, `NO_SHOW`, `REFERRED_OUT`, `TRANSFERRED`, `ADMITTED`, `EMERGENCY_OVERRIDE`, `PROVISIONAL_IDENTITY`, `PENDING_RECONCILIATION`, `PENDING_PAYMENT`, `PENDING_CLAIM`, `PENDING_RESULT`, `PENDING_SIGNATURE`, `PENDING_SYNC`, `FAILED_SYNC`, `DUPLICATE_SUSPECTED`, `CONSENT_DENIED`, `ACCESS_DENIED`, `PAYMENT_FAILED`, `CLAIM_REJECTED`, `SERVICE_DEFERRED`, `PRE_SERVICE_PAYMENT_FAILED`, `ACCESS_BLOCKED_PAYMENT_REQUIRED`, `EXEMPTION_CONFIRMED`.

## Emergency Flow Rule

Emergency transactions may move from `INITIATED` directly to `EMERGENCY_OVERRIDE` or `IN_SERVICE`, then later into `PROVISIONAL_IDENTITY` / `PENDING_RECONCILIATION` for post-stabilization governance.

## Payment Gate Rule

Selected services may require pre-service payment:

`PRE_SERVICE_PAYMENT_REQUIRED -> PRE_SERVICE_PAYMENT_PENDING -> PRE_SERVICE_PAYMENT_COMPLETED -> ACCESS_GRANTED`

Alternative branches:

- `PRE_SERVICE_PAYMENT_REQUIRED -> EXEMPTION_CONFIRMED -> ACCESS_GRANTED`
- `PRE_SERVICE_PAYMENT_PENDING -> PRE_SERVICE_PAYMENT_FAILED -> ACCESS_BLOCKED_PAYMENT_REQUIRED`
- emergency care may bypass this gate and reconcile later.

## Validation Helpers

Use these helpers from the canonical contract:

- `isValidCoreTransactionTransition(from, to, transactionType)`
- `getAllowedNextStates(state, transactionType)`
- `isTerminalCoreTransactionState(state)`
- `requiresTrustContext(state)`
- `requiresIdentityResolution(state)`
- `getLifecycleStageForState(state)`
- `getPersonJourneyStageForTransactionState(state)`
- `getProviderJourneyStageForTransactionState(state)`
- `getPlatformJourneyStageForTransactionState(state)`
- `getJourneyAlignmentForTransaction(transaction)`

## Invariant

No meaningful state transition is valid unless:

1. The transition is allowed by contract;
2. A corresponding event is emitted;
3. Permission/trust basis is available;
4. Audit context is captured.
