# Core Transaction Offline and Federated Model

## Required Offline Behavior

Core transactions must support:

- offline capture with explicit `offlineSyncStatus`;
- provisional identity flows;
- local queue/task progression;
- delayed sync and reconciliation;
- conflict visibility;
- audit continuity.

## Governance Constraints

Offline mode is not a shadow system:

1. state transitions remain explicit;
2. audit context remains required;
3. sync failure is visible (`PENDING_SYNC`, `FAILED_SYNC`);
4. reconciliation is mandatory for emergency/provisional cases.

## Federated Runtime

Tenant/pod/facility/workspace context must be preserved in every event and every composed view so federated deployments retain sovereignty and traceability.
