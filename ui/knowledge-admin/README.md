# Knowledge admin console (bootstrap)

This folder reserves the **national knowledge governance UI** (source upload, extraction review, rule harness, version compare, audit viewer).

## Current state

v1 admin flows are exercised via **OpenAPI** on `clinical-knowledge-platform-service` (`/swagger-ui.html` when running locally on port 8270). A dedicated Next.js console will land here in a follow-up change-set once OIDC roles for `KNOWLEDGE_CURATOR` and `CLINICAL_GOVERNANCE` are wired through the Experience BFF.

## APIs to wire first

- `POST /internal/v1/clinical/assistant/ask` — scenario testing
- `GET /internal/v1/clinical/assistant/traces/{id}` — audit inspection
- `GET /internal/v1/clinical/pathways` — pathway registry
- `POST /internal/v1/clinical/audit/overrides` — override capture

See `docs/runbooks/clinical-knowledge-platform-dev.md`.
