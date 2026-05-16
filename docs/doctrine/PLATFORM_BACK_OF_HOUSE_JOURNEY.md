# Platform / Back-of-House Journey

The platform journey coordinates identity, trust, workflow, cost/payment, record continuity, analytics, and audit so user experience remains coherent.

## Canonical Stages

Receive Trigger -> Resolve Identity -> Establish Trust Context -> Resolve Service and Workflow -> Determine Costing/Coverage/Payment Rules -> Apply Pre-Service Payment Gate -> Manage State Machine -> Compose Experience View -> Execute Clinical/Service Action -> Execute Financial/Enterprise Flow -> Update Record and Continuity -> Emit Events and Audit -> Feed Reporting and Intelligence -> Handle Failure/Offline/Reconciliation.

## Rules

- Platform orchestration must not create duplicate source-of-truth models.
- Every transition emits observable events and auditable context.
- Offline and federated modes remain governed and eventually reconciled.
- Nompilo operations insights may surface blockers and trends, never hidden policy decisions.
