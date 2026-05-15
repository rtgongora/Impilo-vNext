# Public Health Operations Model

## Ownership Split

- Data/Public Health owns:
  - surveillance operations (signals, cases, counters, alerts),
  - campaign operations and monitoring,
  - aggregate indicators and programme dashboards,
  - operational public-health worklists and escalation metadata.
- Clinical owns:
  - individual screening/community/referral/follow-up encounters,
  - patient-level diagnosis/treatment/procedure decisions.
- Registry owns:
  - facility/site/provider/geography references used by public-health workflows.
- Experience owns:
  - user-facing public-health dashboards and operations screens.
- Integration owns:
  - external surveillance/reporting feed connectors and adapters.
- Trust owns:
  - authorization, purpose-of-use, policy evaluation, audit.

## Canonical Flow

`signal or campaign target -> governed data product -> operational worklist -> field action or clinical follow-up -> clinical encounter (if patient-level care) -> governed outcome event/aggregate back to Data Plane`

## Boundaries Enforced in This Pass

- BFF routes no longer return synthetic success for governance/reporting/mobile-governance failures.
- Weekly aggregate and selected public-health write routes now use dedicated surveillance public-health lifecycle APIs and fail-close (`502`) on upstream failure.
- Public-health read routes remain governed orchestration over surveillance/campaigns/indawo.
- Sovereign lifecycle endpoints are active in `surveillance-service`: `/internal/v1/public-health/weekly-idsr`, `/outbreaks`, `/field-operations`.

## Residual Blockers

- Lifecycle endpoints are implemented; residual work is dataset richness, additional domain states, and broader contract/test depth across the long-tail public-health surface.
