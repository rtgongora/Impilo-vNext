# Integration Adapters

## Principle

Impilo vNext should remain native-first and integration-ready. Core workflows should not hardcode a single external vendor path.

## Current Adapter Surface (Observed)

- `integration-hub` and related workflow modules provide the cross-system mediation plane.
- Domain adapters present in repo include examples such as:
  - `connector-fhir-adapter`
  - `pharmacy-elmis-adapter`
  - `inventory-elmis-adapter`
  - `pacs-adapter-service`
  - additional registry/workflow interfaces across services

## Adapter Categories to Govern

- Clinical systems: PACS, LIMS, EMR/FHIR
- Supply chain: eLMIS/logistics integrations
- Public health: DHIS2 and surveillance partners
- Financial rails: payment gateways, remittance/claims switches
- Identity and civil systems: national ID/CRVS
- Communications: SMS/voice/email/chat providers
- Mapping/geospatial: map tiles, routing engines
- AI providers: Gemini (default), OpenAI, Anthropic, DeepSeek

## Required Production Controls

- Adapter registration and enable/disable controls
- Contract versioning and compatibility checks
- Tenant/workspace/facility scoping
- Retry, timeout, and circuit-breaker behavior
- Correlation IDs and audit trails
- Idempotency for mutation flows
- Observability: status, latency, error rates, backlog

## Immediate Follow-up

- Standardize adapter metadata model and operational status endpoints.
- Ensure each adapter has smoke checks and a fallback mode.
- Wire adapter health and reconciliation views into operations consoles.
