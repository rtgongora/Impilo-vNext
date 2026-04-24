# Workforce governance (organisations, jurisdictions, assignments)

## Purpose

The **workforce-governance-service** (`services/workforce-governance-service`) is the bounded context for:

- Organisation and organisational unit hierarchy  
- Facility ↔ organisation and Indawo site ↔ organisation linkages (by reference ID; Tuso/Indawo remain registries of record)  
- Jurisdictions, jurisdiction links, multi-site groups  
- Role definitions (allowed assignment targets, governance flags)  
- Assignments (`USER` / `PROVIDER` subject types) with status history  
- Facility scope evaluation for **assignment-aware** authorization  

## APIs

Internal base path: **`/v1/internal/governance`** (trust headers via `TrustContextFilter`).

Notable endpoints:

| Method | Path | Description |
|--------|------|-------------|
| POST | `/organisations` | Create organisation (DRAFT) |
| PATCH | `/organisations/{id}/status` | Lifecycle |
| POST | `/onboarding/single-facility` | Org + default unit + primary facility link |
| POST | `/onboarding/multi-facility` | Org + N facility links |
| POST | `/assignments` | Create assignment |
| POST | `/assignments/{id}/transition` | Status change + history |
| GET | `/assignments/search` | Filter by subject / status |
| GET | `/assignments/{id}/history` | Status history |
| POST | `/scope/evaluate-facility` | Whether actor may act at Tuso facility |
| GET | `/summaries/organisation/{id}` | Counts |
| GET | `/summaries/facility/{facilityId}` | Active org links for facility |

## Tshepo integration

- Envoy (or callers) should send **`x-tuso-facility-id`** with the numeric Tuso facility id when facility context applies.  
- Tshepo **`PolicyEngine`** calls Workforce Governance **`/scope/evaluate-facility`** when governance base URL is configured (`impilo.services.governance.base-url` in `tshepo-service`).  
- Workspace validation uses Tuso internal workspace API and compares workspace `facilityId` to `x-tuso-facility-id` when both are present.

## Varapi integration

Optional: `varapi.governance.enabled=true` and `varapi.governance.base-url` — exposes  
`GET /v1/internal/providers/{providerPublicId}/workforce-assignments-summary` (proxies assignment search).

## Experience BFF & UI

- BFF: `/internal/v1/workforce-governance/*` → `WorkforceGovernanceController`  
- UI: `/organization-admin/governance` (list) and `/organization-admin/governance/[id]` (summary)

## Events

Transactional outbox table **`wgv_event_outbox`**. When `impilo.governance.kafka-events-enabled=true`, **`GovernanceOutboxPublisher`** publishes JSON envelopes to **`impilo.governance.events`** (configurable).

## Assumptions / gaps

- **PIC**: Varapi remains PIC lifecycle source of truth; Tuso mirrors for facility view. A governance role definition **`PRACTITIONER_IN_CHARGE`** exists for optional parallel assignment records — full dual-write from Varapi to governance is a follow-up.  
- **Strict mode**: Tshepo currently **allows** when governance is disabled or unreachable (degraded allow); production may want fail-closed configuration.  
- **Costa/PCT facility UUID vs Tuso Long**: cross-service encounter identifiers remain a separate normalisation track.

## Manual smoke test

1. Start Postgres; create DB `impilo_workforce_governance` (or rely on compose).  
2. Run `workforce-governance-service` on port **8165**.  
3. Configure Tshepo `WORKFORCE_GOVERNANCE_SERVICE_URL` / `impilo.services.governance.base-url`.  
4. POST `/v1/internal/governance/onboarding/single-facility` with trust headers + body (see `GovernanceOnboardingWebMvcTest`).  
5. POST an `ACTIVE` assignment for `USER` subject to `FACILITY` target.  
6. POST `/scope/evaluate-facility` — expect `allowed: true`.  
7. Open Experience `/organization-admin/governance` with BFF `impilo.services.workforce-governance-base-url` set.
