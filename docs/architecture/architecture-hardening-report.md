# Architecture Hardening Report

## Current Problems Found

- Mixed plane vocabulary (`finance`, `marketplace`, `ops`, `knowledge`) coexisted with canonical seven-plane doctrine.
- `services-registry.yaml` used `plane` as uncontrolled source-of-truth without explicit SoR and forbidden responsibility boundaries.
- Registry metadata drifted from actual Maven service inventory, leaving ownership gaps for modules not in the older registry list.
- Deployment namespace labels were repeatedly treated as architecture ownership signals.
- Backend/frontend wiring had known route drift (`/internal/v1/clinical-knowledge/*` callers vs `/internal/v1/clinical/*` BFF mapping).
- Production-path placeholders/fallback behavior existed in several services and required explicit visibility in readiness outputs.

## Decisions Made

- Canonicalized ownership to exactly seven planes: `trust`, `registry`, `clinical`, `data`, `integration`, `experience`, `enterprise`.
- Upgraded registry schema with explicit production ownership/readiness fields, including SoR and forbidden responsibilities.
- Kept compatibility alias `plane` generated from `primary_plane` for migration safety.
- Rebuilt registry generation flow from a hardened seed model aligned to `services/pom.xml`.
- Generated canonical architecture and ownership registers directly from registry metadata to remove duplicate manual ownership tables.
- Enforced doctrine that deployment namespace is a deployment concern, not ownership taxonomy.

## Plane Taxonomy Finalized

See:

- `docs/architecture/planes/00-production-plane-doctrine.md`
- `docs/architecture/planes/01-trust-identity-assurance-governance.md`
- `docs/architecture/planes/02-registry-sovereign-identity-spine.md`
- `docs/architecture/planes/03-clinical-execution-shared-health-record.md`
- `docs/architecture/planes/04-data-intelligence-public-health.md`
- `docs/architecture/planes/05-integration-interoperability-edge.md`
- `docs/architecture/planes/06-experience-workflow-orchestration.md`
- `docs/architecture/planes/07-enterprise-resource-market-operations.md`

## Services Remapped

- Legacy labels mapped into canonical model:
  - `finance` -> `primary_plane: enterprise`, `domain: finance`
  - `marketplace` -> `primary_plane: enterprise`, `domain: marketplace`
  - `ops` -> `primary_plane: integration`, `domain: platform-ops`
  - `knowledge` -> split by capability into `clinical`, `data`, or `registry` domain intent
- Full mapping outputs:
  - `docs/registry/service-plane-map.md`
  - `docs/registry/service-ownership-matrix.md`
  - `docs/registry/system-of-record-map.md`
  - `docs/registry/forbidden-responsibilities-map.md`

## Ambiguous Services Resolved

- Added ownership entries for reactor services that were missing from the old registry baseline.
- Included non-reactor but deployable service modules discovered from `services/*/pom.xml` for visibility and governance tracking.
- Flagged services still requiring architectural decision in readiness outputs where runtime ownership remains uncertain.

## Services Needing Merge/Retirement/Split (Decision Queue)

- `tshepo-service` vs decomposed TSHEPO services: legacy/compatibility boundaries should be retired on explicit migration completion.
- `national-data-repository-service` vs `ndr-service`: potential overlap in analytics repository ownership requires consolidation decision.
- `mushe-wallet-service` vs `mushex-service`: ownership and build/deploy baseline requires explicit enterprise-finance boundary decision.
- Parallel web surfaces (`ui/experience` and `ui/one-ui-shell`) need final canonical route ownership closure.

## Risks and Mitigations

- **Risk:** Schema migration breaks downstream tooling.  
  **Mitigation:** `plane` compatibility alias retained while primary consumers migrate to `primary_plane`.
- **Risk:** Heuristic remapping may over-classify uncertain services.  
  **Mitigation:** readiness and remediation docs mark unresolved boundaries as explicit architectural decisions.
- **Risk:** Production placeholder behavior persists despite taxonomy cleanup.  
  **Mitigation:** mock/stub register and remediation plan isolate production-path risks with priority actions.
- **Risk:** Namespace confusion reappears via deployment docs.  
  **Mitigation:** classification matrix explicitly separates primary plane/domain from deployment namespace.
