# 00 — Executive Summary (Index & Contract)

> **Document type**: Index document — points to canonical spec sources.
> **Last updated**: 2026-03-11
> **Status**: CANONICAL INDEX — enforced by `scripts/spec-integrity-check.sh`

---

## Purpose

This document serves as the **entry point** for the Impilo vNext Experience Platform specification set. The detailed specifications that drive implementation live in the canonical spec directories listed below. This file and its siblings (`01` through `07`) act as an **index and contract layer** — they define what must exist, where it lives, and how it maps to the implementation.

## Platform Overview

Impilo vNext is a Health Information Exchange (HIE) and Digital Health Platform. The system is organized into 15 zones with 4 authentication pathways and 39 kernel admin routes. The auth model follows a hierarchical tenancy structure: Auth → Facility → Workspace → Shift. The UI is built around 4 layout variants and 11 sidebar contexts, using a design system based on shadcn/ui, Tailwind CSS, Lucide icons, and Framer Motion for animations.

## Canonical Spec Source Map

The Experience Platform implementation was built from the following canonical spec set. These are the **authoritative sources of truth** for implementation decisions.

### Primary Implementation Specs (`docs/plan/`)

| File | Lines | Covers |
|------|-------|--------|
| [IMPILO_VNEXT_BUILD_PLAN.md](../../plan/IMPILO_VNEXT_BUILD_PLAN.md) | 727 | 27 platform components across 6 bundles, dependency graph, rollout sequence |
| [SERVICE_CATALOG.md](../../plan/SERVICE_CATALOG.md) | 282 | All services (legacy + v1.1-native + Outstanding 27), ports, rings, responsibilities |
| [API_CONVENTIONS_V11.md](../../plan/API_CONVENTIONS_V11.md) | 439 | v1.1 API conventions: header contract, error envelope, idempotency, pagination |
| [EVENTING_AND_TOPICS.md](../../plan/EVENTING_AND_TOPICS.md) | 539 | Kafka topic naming, EventEnvelope schema, outbox pattern, consistency classes |
| [TESTING_CONVENTIONS.md](../../plan/TESTING_CONVENTIONS.md) | 426 | Testing pyramid, GoldenContractSuite, TestContainers patterns, CI gates |
| [EXECUTION_PLAN_P20_P22.md](../../plan/EXECUTION_PLAN_P20_P22.md) | 160 | Prompts 20-22 execution plan, dependency ordering |
| [SPEC_DELTA_REPORT.md](../../plan/SPEC_DELTA_REPORT.md) | 164 | Gap analysis: current repo vs vNext V3 + Tech Companion Spec 2.0 |

### Architecture Specs (`docs/architecture/v1.1/`)

| File | Lines | Covers |
|------|-------|--------|
| [00-compliance-summary.md](../../architecture/v1.1/00-compliance-summary.md) | 223 | v1.1 compliance audit results |
| [01-gap-analysis.md](../../architecture/v1.1/01-gap-analysis.md) | 350 | 18-requirement gap analysis, 13 NOT IMPLEMENTED findings |
| [02-migration-plan.md](../../architecture/v1.1/02-migration-plan.md) | 419 | Legacy service migration to v1.1 compliance |
| [03-architecture-diagram.md](../../architecture/v1.1/03-architecture-diagram.md) | 278 | System topology, 6 planes, trust-first flow |
| [04-service-boundaries.md](../../architecture/v1.1/04-service-boundaries.md) | 110 | Ring definitions, service boundaries, dependency rules |
| [05-event-schema-template.md](../../architecture/v1.1/05-event-schema-template.md) | 412 | Event envelope schema, versioning, evolution |
| [06-consistency-classes.md](../../architecture/v1.1/06-consistency-classes.md) | 193 | Class A/B/C consistency definitions |
| [07-federation-protocol.md](../../architecture/v1.1/07-federation-protocol.md) | 303 | Cross-pod federation protocol |

### Experience-Specific Documentation

| File | Lines | Covers |
|------|-------|--------|
| [ONLINE_VERIFICATION.md](../../experience/ONLINE_VERIFICATION.md) | 164 | Online verification guide for Experience Platform |
| [experience-platform-acceptance-pack.md](../../acceptance/experience-platform-acceptance-pack.md) | 257 | Acceptance criteria, test commands, golden path checklist |
| [SPEC_CONFLICTS.md](../../../compose/experience/SPEC_CONFLICTS.md) | 79 | 8 documented spec conflicts and their resolutions |

### Spec Conflict Documentation

| File | Lines | Covers |
|------|-------|--------|
| [v3-align-spec-conflicts.md](../../spec-conflicts/v3-align-spec-conflicts.md) | ~86 | v3 + Tech Companion alignment conflicts (8 items, 4 resolved, 4 open) |

## Implementation Artifacts

| Component | Path | File Count |
|-----------|------|------------|
| Experience BFF (Java/Spring Boot) | `services/experience-bff/` | 52 Java files |
| Experience UI (Next.js/TypeScript) | `ui/experience/` | 125 TS/TSX files |
| Docker Compose | `compose/experience/` | docker-compose.yml + smoke tests |

## Key Numerical Constraints (from original prototype summaries)

These numbers were sourced from the `docs/prototype/final/` original summaries and used as design constraints:

- **98 routes** across 15+ zones
- **4 layout variants**: AppLayout, EHRLayout, AuthLayout, MinimalLayout
- **11 sidebar contexts**: dynamically resolved from URL path
- **4 authentication pathways**: Email, Provider ID + Biometric, SSO, Emergency
- **6 golden paths**: Login, Clinical workflow, Admin, Marketplace, Registry, Reports
- **6 React contexts** (implemented as 4 Zustand stores — see Spec Conflict #7)

## Contract Statement

> Any replication prompt, build task, or CI check that references `docs/prototype/final/*` MUST follow the links in this index to reach the canonical detailed content. The `docs/prototype/final/` directory is an **index and contract layer**, not a self-contained specification set. The canonical detailed specs live in `docs/plan/` and `docs/architecture/v1.1/`.
