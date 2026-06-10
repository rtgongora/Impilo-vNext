# Nompilo Tool Registry and Permissioning

## Tool Registry

Nompilo tool sources include:

- VITO_CLIENT_REGISTRY
- VARAPI_PROVIDER_REGISTRY
- TUSO_FACILITY_REGISTRY
- MSIKA_SERVICE_PRODUCT_CATALOGUE
- MSIKA_FLOW_FULFILMENT
- INDAWO_PUBLIC_HEALTH_SITES
- ZIBO_TERMINOLOGY
- FUNDO_LEARNING
- IMPILO_LIVE (events, discovery, replays, session routing)
- BUTANO_CLINICAL_SUMMARY
- COSTA_COSTING
- MUSHEX_PAYMENTS_CLAIMS
- DATA_PLANE_ANALYTICS
- DOCUMENT_SERVICE
- SUPPORT_HELPDESK
- NOMPILO_KNOWLEDGE_BASE
- APPROVED_EXTERNAL_SOURCE
- GOVERNED_INTERNET_SEARCH

## Permission Model

- Tool invocation requires explicit permission check against role, purpose, consent, facility/workspace context, and policy.
- Restricted sources return deny with reason and no payload leakage.
- External and internet sources default to deny unless explicitly enabled by policy.

## Audit Rules

- Each invocation logs source, scope, query intent, actor role, outcome, and policy reference.
- Denied attempts are auditable and visible to platform operations.
