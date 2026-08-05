# Architecture Documentation Index

## Canonical Plane Doctrine

- `planes/00-production-plane-doctrine.md`
- `planes/01-trust-identity-assurance-governance.md`
- `planes/02-registry-sovereign-identity-spine.md`
- `planes/03-clinical-execution-shared-health-record.md`
- `planes/04-data-intelligence-public-health.md`
- `planes/05-integration-interoperability-edge.md`
- `planes/06-experience-workflow-orchestration.md`
- `planes/07-enterprise-resource-market-operations.md`

## Core Transaction Alignment

- `core-transaction-plane-map.md`
- `core-transaction-event-model.md`
- `core-transaction-service-ownership.md`
- `core-transaction-data-ownership.md`
- `core-transaction-bff-composition.md`
- `core-transaction-ui-alignment.md`
- `core-transaction-offline-federated-model.md`
- `core-transaction-security-audit-model.md`
- `core-transaction-failure-modes.md`
- `core-transaction-service-compliance.yaml`
- `three-journey-core-transaction-map.md`
- `nompilo-journey-companion-architecture.md`
- `nompilo-accessibility-omnichannel-feedback.md`
- `nompilo-search-voice-command-architecture.md`
- `nompilo-tool-registry-and-permissioning.md`
- `nompilo-analytics-reporting-assistant.md`
- `nompilo-fundo-learning-assistant.md`
- `nompilo-support-escalation-model.md`
- `ui-current-state-audit.md`
- `ui-experience-doctrine.md`
- `nompilo-command-ui-pattern.md`
- `role-aware-workspaces.md`
- `transaction-context-panel.md`
- `accessibility-ui-patterns.md`
- `mobile-kiosk-ui-patterns.md`
- `ui-route-journey-map.md`
- `ui-refinement-implementation-summary.md`
- `impilo-ui-visual-design-system.md`
- `impilo-brand-theme-tokens.md`
- `impilo-mobile-web-visual-style.md`
- `impilo-rounded-healthcare-design-language.md`
- `impilo-subtle-african-design-signature.md`
- `impilo-ui-theme-implementation-summary.md`

## Ndila — Geospatial Intelligence

- `NDILA_IMPLEMENTATION_NOTE.md` — Ndila audit, scope, integrations, tests, operational follow-ups.

---

## Architecture Governance Hierarchy

The existing plane doctrine remains part of the Impilo vNext architecture.

The following successor documents govern cross-plane architecture, product
capabilities and technical standards:

1. [`hybrid-federated-target-architecture-v1.3.4.md`](./hybrid-federated-target-architecture-v1.3.4.md)
   — the complete, versioned, historically durable document (exactly one active copy).
   Convenience pointer at the legacy path: [`vnext-hybrid-federation-target-architecture.md`](./vnext-hybrid-federation-target-architecture.md).
   Working controlling baseline for federation, trust domains, Hospital Nodes,
   deployment profiles, authority, record topology and the Experience Plane.
   This version carries the v1.3.2 integrity correction, the v1.3.3
   freeze-review correction and the v1.3.4 correction of v1.3.3's own
   refused freeze, and remains not architecture-frozen until Product
   Owner sign-off. v1.3.1, v1.3.2 and v1.3.3 are archived working
   drafts; none of them was ever frozen.

2. [`product-capability-architecture-v2.0.md`](./product-capability-architecture-v2.0.md)
   Defines the National Health Operating System vision, planes, capability
   portfolio, product boundaries and ring-based maturity model.

3. [`../standards/technical-standards-catalogue-v1.0.md`](../standards/technical-standards-catalogue-v1.0.md)
   Defines implementation standards for APIs, trusted request context,
   eventing, consistency, offline operation, federation, finance, AI,
   observability and release governance.

4. [`ARCHITECTURE_PRECEDENCE.md`](./ARCHITECTURE_PRECEDENCE.md)
   Defines precedence and conflict-resolution rules between architecture,
   standards, domain packs, ADRs and implementation artefacts.

5. [`supersession-notice-v1.0.md`](./supersession-notice-v1.0.md)
   Records the treatment of the earlier V3 and Technical Companion documents.

The earlier PDFs remain under [`archive/`](./archive/) for provenance. They are
not controlling where they conflict with the successor hierarchy.

### Two-repository ownership (truth recovered 2026-08-04)

- **This repository owns the current user-facing product**: the public and
  authenticated web shell (`ui/one-ui-shell`, every human-visible route on
  `impilo.mohcc.gov.zw`), the citizen and provider mobile applications
  (`apps/mobile`), all services, and the deployment manifests — including
  the Traefik manifest that deploys the legacy website image
  (`deploy/tls/mohcc-gov/public-website.yaml`).
- **The sibling repository (`zimttech/impilo-website`) remains load-bearing
  for exactly one path prefix**: `/.well-known/*` (mobile universal-link
  association files), served at Traefik priority 50000. Every other route it
  contains is shadowed by the shell's catch-all and its redirects.
- **It must not be declared obsolete** until that path is migrated and proven
  live elsewhere. **Recommended target (Option A, not yet implemented):**
  move `/.well-known/*` into this repository's shell, validate the Android
  (`assetlinks.json`) and Apple (`apple-app-site-association`) files through
  the public host, then retire the legacy Deployment.

### Relationship to the Plane Doctrine

The plane documents define detailed doctrine within their respective planes.
The Hybrid/Federated Target Architecture defines the cross-plane deployment,
authority, federation and experience model.

A plane document may refine its own domain but may not contradict a controlling
cross-plane invariant. Any genuine conflict must be resolved through an ADR or
an explicit amendment to the higher-level architecture.
