# Data Pipeline State Machine

## Ingestion Batch/Event

`received -> validated -> staged -> processing -> transformed -> materialised -> published -> archived`

Error/branch states:

- `validated -> rejected`
- `processing|transformed|materialised -> failed`
- `failed -> replay_pending -> processing`

## Pipeline Run

`scheduled -> running -> succeeded`

Alternate terminals/branches:

- `running -> partial`
- `running -> failed -> retrying -> running`
- `scheduled|running -> cancelled`

## Dataset/Data Product

`draft -> pending_governance -> approved -> active -> deprecated -> retired`

Control states:

- `active -> access_restricted` (policy/incident hold)
- `access_restricted -> active` (governance release)

## Data Access Request

`draft -> submitted -> under_review -> approved -> active -> expired`

Alternate:

- `under_review -> denied`
- `active -> revoked`

## Current Alignment

- `reporting-service`: run lifecycle aligns to `scheduled/running/succeeded/failed`.
- `campaigns-service`: campaign lifecycle aligns to draft/active/closed style states (mapped to dataset model where applicable).
- `surveillance-service`: signal/case statuses are present but not fully normalized to this canonical state table.
- `data-pipeline-service` and `data-ingestion-service`: state semantics exist but need explicit contract-level publication.
