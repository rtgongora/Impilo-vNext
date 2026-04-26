# Impilo vNext — Diagram Index

**Date**: 2026-03-28
**Version**: 1.0

> Master index of all high-level architecture diagrams for the Impilo vNext platform.

---

## High-Level Architecture Diagrams

| # | Diagram Name | File | Section | Purpose | State |
|---|-------------|------|---------|---------|-------|
| 1 | **Platform Context Diagram** | [vnext-high-level-diagrams.md](vnext-high-level-diagrams.md) | `## 1. Platform Context Diagram` | Shows external actors, platform boundaries, entry points, and core services at the highest abstraction level | **MIXED** |
| 2 | **Ring / Plane Architecture Diagram** | [vnext-high-level-diagrams.md](vnext-high-level-diagrams.md) | `## 2. Ring / Plane Architecture Diagram` | Depicts the 3-ring architecture (Kernel → Clinical → Supply/Data/Integration) with all 68 services classified by ring and plane | **MIXED** |
| 3 | **Major Component Landscape Diagram** | [vnext-high-level-diagrams.md](vnext-high-level-diagrams.md) | `## 3. Major Component Landscape Diagram` | Maps all implemented components by functional domain — trust, registries, clinical, finance, health record, supply, documents, libraries, UIs, infrastructure | **CURRENT STATE** |
| 4 | **Runtime Layer / Startup Order Diagram** | [vnext-high-level-diagrams.md](vnext-high-level-diagrams.md) | `## 4. Runtime Layer / Startup Order Diagram` | 10-layer startup sequence from infrastructure through UI, reflecting Docker Compose and platformctl ordering | **CURRENT STATE** |
| 5 | **Experience / App Ecosystem Diagram** | [vnext-high-level-diagrams.md](vnext-high-level-diagrams.md) | `## 5. Experience / App Ecosystem Diagram` | All 24 web UIs, 2 mobile apps, shared foundations, and their backend service dependencies | **MIXED** |
| 6 | **Solution In Diagrams** | [vnext-solution-diagrams.md](vnext-solution-diagrams.md) | Full document | Explains the vNext solution end to end: context, planes, request governance, identity model, clinical workflow, eventing, experience shell, data-centre sandbox, acceptance gates, and DevOps feedback loop | **MIXED** |

---

## State Label Convention

| Label | Meaning |
|-------|---------|
| **CURRENT STATE** | Diagram reflects only what is implemented and functional in the repo today |
| **TARGET STATE** | Diagram shows the intended future architecture — not yet implemented |
| **MIXED** | Diagram includes both implemented and target-state elements; target elements are explicitly called out with dashed borders and notes |

---

## Related Documents

| Document | Path | Purpose |
|----------|------|---------|
| Component Catalog | [vnext-component-catalog.md](vnext-component-catalog.md) | Full inventory of all 68 services, 24 UIs, 12 libraries with ports, databases, status |
| Service Dependency Map | [vnext-service-dependency-map.md](vnext-service-dependency-map.md) | Detailed inter-service dependency graph with ring rules and Kafka topics |
| Platform Operations Runbook | [../runtime/platform-operations-runbook.md](../runtime/platform-operations-runbook.md) | platformctl startup, shutdown, bootstrap, smoke test procedures |
| Solution In Diagrams | [vnext-solution-diagrams.md](vnext-solution-diagrams.md) | Narrative diagram guide for explaining Impilo vNext to leadership, engineers, implementation partners, and reviewers |
