# Lovable Reference Source Map

> **Created**: 2026-03-16
> **Purpose**: Maps all Lovable prototype reference materials to their canonical locations and classifies their authority level.

---

## Classification Key

| Level | Meaning |
|-------|---------|
| **CANONICAL** | Authoritative spec — implementation must conform |
| **INDEX** | Points to canonical sources — not self-contained |
| **RECONSTRUCTED** | Derived from summaries due to missing original detail |
| **SECONDARY** | Supporting context — informative but not binding |
| **WEAK/STUB** | Placeholder or incomplete — cannot drive implementation |

---

## Lovable Prototype Index Layer (`docs/prototype/final/`)

These 8 files form the **index and contract layer**. They describe *what should exist* but contain no detailed page specs, component props, or route definitions. The originals apparently had richer content that was not preserved.

| File | Classification | What It Contains | Gap |
|------|---------------|-----------------|-----|
| `00_executive_summary.md` | INDEX | Platform overview, numerical constraints (98 routes, 15 zones, 4 layouts, 6 golden paths) | No detailed feature specs |
| `01_site_map.md` | INDEX → RECONSTRUCTED | Zone list, route count — points to `routes.ts` | Original listed "98 routes" but none by name |
| `02_page_by_page_spec.md` | INDEX → RECONSTRUCTED | Page architecture overview — points to `src/app/` | Original said "complete UI inventory" but had no page specs |
| `03_component_inventory.md` | INDEX → RECONSTRUCTED | Component groups, layout variants, sidebar contexts | Original said "props interface, internal state" but had none |
| `04_api_surface_map.md` | INDEX → RECONSTRUCTED | BFF controllers, endpoint list | Original said "60 Supabase tables, 30 RPC functions" — replaced by BFF |
| `05_state_and_storage.md` | INDEX | 4 Zustand stores, continuity storage model, guard chain | Reasonably complete |
| `06_golden_paths.md` | INDEX | 6 golden paths with step-by-step tables | Adequate for verification |
| `07_opus_execution_contract.md` | INDEX | 11 phases, quality gates | Original said "11 phases" with no details |

### Key Insight
The `docs/prototype/final/` directory is an **index layer**, not a specification set. The original Lovable prototype apparently contained detailed page-by-page specs, component inventories with props, and full route listings, but these were **lost or never transferred** into the repo. Implementation was driven by:
- Summary numerical constraints (98 routes, 15 zones, 4 layouts)
- Golden path flows
- Service catalog and build plan
- Architecture specs

---

## Canonical Implementation Specs (`docs/plan/`)

| File | Classification | Lines | What It Drives |
|------|---------------|-------|---------------|
| `IMPILO_VNEXT_BUILD_PLAN.md` | CANONICAL | 727 | 27 platform components, 6 bundles, dependency graph |
| `SERVICE_CATALOG.md` | CANONICAL | 282 | All services, ports, rings, responsibilities |
| `API_CONVENTIONS_V11.md` | CANONICAL | 439 | Header contract, error envelope, idempotency, pagination |
| `EVENTING_AND_TOPICS.md` | CANONICAL | 539 | Kafka topics, EventEnvelope, outbox pattern |
| `TESTING_CONVENTIONS.md` | CANONICAL | 426 | Testing pyramid, golden contract suite |
| `EXECUTION_PLAN_P20_P22.md` | CANONICAL | 160 | Phase execution ordering |
| `SPEC_DELTA_REPORT.md` | SECONDARY | 164 | Gap analysis vs vNext V3 + Tech Companion 2.0 |

---

## Architecture Specs (`docs/architecture/v1.1/`)

| File | Classification | What It Drives |
|------|---------------|---------------|
| `00-compliance-summary.md` | CANONICAL | v1.1 compliance requirements |
| `01-gap-analysis.md` | CANONICAL | 18 requirements, 13 NOT IMPLEMENTED |
| `02-migration-plan.md` | SECONDARY | Legacy migration path |
| `03-architecture-diagram.md` | CANONICAL | 6-plane topology, trust-first flow |
| `04-service-boundaries.md` | CANONICAL | Ring definitions, service boundaries |
| `05-event-schema-template.md` | CANONICAL | Event envelope schema |
| `06-consistency-classes.md` | CANONICAL | Class A/B/C consistency |
| `07-federation-protocol.md` | SECONDARY | Cross-pod federation |

---

## Prior Fidelity Audit Materials (`docs/clinical/`)

| File | Classification | Relevance |
|------|---------------|-----------|
| `lovable-fidelity-audit-ehr-and-telemedicine.md` | SECONDARY | Prior EHR/telemedicine audit — identified 13 gaps |
| `lovable-fidelity-gap-list.md` | SECONDARY | Gap list from prior audit |
| `lovable-reference-usage-report.md` | SECONDARY | Analysis of how Lovable was used |
| `ehr-fixes-applied.md` | SECONDARY | Prior EHR remediation log |
| `ehr-end-to-end-gap-analysis.md` | SECONDARY | End-to-end gap analysis |
| `ehr-steel-thread-matrix.md` | SECONDARY | Steel thread verification |

---

## Implementation Source of Truth

| Artifact | Location | Authority |
|----------|----------|-----------|
| Route registry | `ui/experience/src/lib/routes.ts` | CANONICAL — all 98 routes defined here |
| Route parity check | `ui/experience/scripts/route-parity-check.mjs` | CANONICAL — verifies page.tsx existence |
| Page implementations | `ui/experience/src/app/` | CANONICAL — actual UI behavior |
| Layout components | `ui/experience/src/components/` | CANONICAL — AppLayout, EHRLayout, AuthLayout, MinimalLayout |
| Query hooks | `ui/experience/src/hooks/queries/` | CANONICAL — data fetching layer |
| Stores | `ui/experience/src/hooks/` | CANONICAL — Zustand state management |
| BFF controllers | `services/experience-bff/src/main/java/.../controller/` | CANONICAL — API endpoints |
| API client | `ui/experience/src/lib/api-client.ts` | CANONICAL — trust header injection |

---

## Mobile App References

| Artifact | Location | Authority |
|----------|----------|-----------|
| Citizen App feature map | `docs/mobile/citizen-app/feature-map.md` | CANONICAL — 35 features, 6 domains |
| Provider App feature map | `docs/mobile/provider-app/feature-map.md` | CANONICAL — 36 features, 4 modes |
| Provider App mode matrix | `docs/mobile/provider-app/mode-matrix.md` | CANONICAL — Provider/Outreach/Supervisor/Offline |
| Citizen App domain matrix | `docs/mobile/citizen-app/domain-matrix.md` | CANONICAL — Profile/Personal/Social/Marketplace/Messaging/Telehealth |
| App platform audit | `docs/mobile/app-platform-audit.md` | SECONDARY — runtime verification |

---

## Auxiliary UI References

| UI | Location | Status |
|----|----------|--------|
| Support Console | `ui/support-console/` | Implemented — Dashboard, Tickets, Incidents, Knowledge, Messages |
| Developer Console | `ui/developer-console/` | Implemented — Dashboard, Clients, Certification, API Catalog, Sandbox, Federation |
| Self-Service Portal | `ui/self-service/` | Implemented — Verify Credential, Claim Documents, My Documents, My Credentials |
| EHR (legacy) | `ui/ehr/` | DEPRECATED — replaced by `ui/experience/src/app/ehr/` |
| Ops Console | `ui/ops-console/` | Implemented — operational views |
| Various admin UIs | `ui/mushex-*`, `ui/msika-*`, `ui/costa-*`, etc. | Implemented — domain-specific admin interfaces |

---

## Spec Conflicts Register

8 documented spec conflicts in `compose/experience/SPEC_CONFLICTS.md`:

| # | Conflict | Resolution |
|---|---------|------------|
| 1 | "98 routes" listed with no names | Routes reconstructed from zones + golden paths |
| 2 | Route count discrepancy (96 explicit + 2 redirects = 98) | Accepted |
| 3 | "2 browser persistence keys" → continuity now uses browser storage plus cookies | Updated to match current runtime model |
| 4 | Full Keycloak/OIDC not specified | Deferred to Stage-2 |
| 5 | "60 Supabase tables, 30 RPC functions" → BFF pattern | Replaced with Spring Boot BFF |
| 6 | Component props/state not specified | Implemented from layout descriptions |
| 7 | "6 React contexts" → 4 Zustand stores | Simplified (2 contexts unspecified) |
| 8 | "11 phases" with no details | Derived from plan specs |

---

## Conclusion

**The Lovable prototype index layer is necessary but insufficient** as a standalone reference. The original detailed specifications were either lost or never committed. Implementation was driven primarily by:
1. Summary numerical constraints
2. Golden path step tables
3. Service catalog + build plan
4. Architecture specs
5. Codebase patterns and zone structure

This means **fidelity assessment must compare against the combined intent** expressed across all these sources, not just the `docs/prototype/final/` index files alone.
