# Licensing Strategy

## Purpose

This document defines the intended legal segmentation of the Impilo vNext repository and makes the current position explicit:

> **Current implementation status: the entire monorepo is proprietary unless explicitly relicensed.**

The goal is to preserve the strategic platform spine as proprietary by default, while preparing clearly bounded ecosystem-facing artefacts for future Apache 2.0 extraction where that is commercially and operationally appropriate.

## Licensing Buckets

### 1. Proprietary Core / Platform Spine

This bucket covers the strategic control plane, sovereign platform logic, operational workflows, deployment internals, monetisable platform capabilities, and system-scale orchestration layers.

Typical characteristics:

- trust, governance, and policy enforcement logic
- registry spine and system-of-record implementations
- clinical and finance execution services
- control-plane orchestration and operational consoles
- deployment, infrastructure, runtime, and tenancy internals

### 2. Apache 2.0 Ecosystem / Integration Artefacts

This bucket is intended for later extraction of partner-safe artefacts that help integrators, vendors, and ecosystem participants build against the platform without exposing core proprietary logic.

Typical characteristics:

- external-facing API contracts
- public schemas and event envelopes
- public SDKs and connector libraries
- public FHIR profiles, interoperability guides, and reference quickstarts

These artefacts are **not Apache 2.0 yet in this monorepo**. They remain proprietary unless and until they are explicitly extracted or relicensed.

### 3. Optional Future AGPL Community Modules

This bucket is reserved only for future modules that are genuinely community-oriented, separable, and strategically appropriate for reciprocal licensing.

Current position:

- no AGPL module is being introduced in this task
- no current repo area is being relicensed to AGPL
- AGPL should only be considered later if a clearly separable community module emerges

## Current Repository Segmentation

| Repo Area | Intended Licensing Category | Rationale | Current State | Future Action |
|---|---|---|---|---|
| `services/` | Proprietary core/platform spine | Contains the sovereign service estate, execution logic, control-plane logic, policy enforcement, and commercially sensitive workflows across all seven planes | Proprietary in this monorepo | Keep closed by default; extract only narrowly scoped partner-safe adapters or SDK surfaces later |
| `ui/` | Proprietary core/platform spine | Contains Experience shell, admin/ops portals, finance consoles, vendor flows, and platform-sensitive workflows | Proprietary in this monorepo | Keep closed by default; only selected public-facing reference artefacts may be split later |
| `infra/` | Proprietary core/platform spine | Contains gateway, identity, Kafka, and deployment/security internals not suitable for open reuse as-is | Proprietary in this monorepo | Keep closed; publish only high-level docs if needed, not operational internals |
| `helm/` | Proprietary core/platform spine | Encodes deployment and runtime packaging of platform services and operational assumptions | Proprietary in this monorepo | Keep closed; no blanket relicensing planned |
| `ops/runtime/` | Proprietary core/platform spine | Contains runtime topology, compose manifests, environment packaging, and operations orchestration | Proprietary in this monorepo | Keep closed; share only sanitized deployment guidance externally if needed |
| `contracts/` | Apache 2.0 extraction candidate | The tree already contains `openapi/`, `asyncapi/`, `schemas/`, and contract helper files that are natural ecosystem boundary material | Proprietary in this monorepo | Prepare for later extraction as a partner-facing contracts package with explicit Apache 2.0 licensing |
| `libs/` | Mixed: mostly proprietary now, with selected Apache 2.0 extraction candidates | Some libraries are clearly internal (`security-baseline`, `ops-instrumentation`, `shared-kernel`), while others look ecosystem-facing (`federation-connector`, `offline-sdk`, `tshepo-sdk`) | Proprietary in this monorepo | Split internal libraries from partner-safe SDK/connector artefacts before any Apache release |
| `docs/` | Mixed: mostly proprietary now, with selected Apache 2.0 documentation candidates later | Contains internal architecture, governance, rollout, and operational material, plus some ecosystem-facing references and contracts guidance | Proprietary in this monorepo | Keep the full docs tree closed; later extract public integration and standards documentation only |
| `apps/mobile/` | Proprietary core/platform spine | Contains product applications and distribution logic, not just neutral SDKs | Proprietary in this monorepo | Keep closed unless a separate public SDK/demo app is intentionally created later |
| `scripts/` | Mixed but currently proprietary | Includes internal bootstrap, runtime, smoke, compliance, and production-readiness automation | Proprietary in this monorepo | Keep closed; extract only partner-safe quickstarts or developer bootstrap helpers if needed |
| `tools/` | Mixed but currently proprietary | Includes operational, Maven, auth, static verification, and internal tooling surfaces | Proprietary in this monorepo | Keep closed; later extract only general-purpose partner tooling if clearly separable |

## Notes on Obvious Extraction Candidates

The current tree already contains several areas that look structurally suitable for future Apache 2.0 extraction, subject to security and commercial review:

- `contracts/openapi/`
- `contracts/asyncapi/`
- `contracts/schemas/`
- `contracts/health-os-identifiers.ts`
- `libs/federation-connector/`
- `libs/offline-sdk/`
- `libs/tshepo-sdk/`

`libs/tech-companion/` may contain reusable enforcement primitives, but it should be treated cautiously because it may embed platform-specific governance or operational assumptions that are not yet safe for general external release.

## Operating Rule

Until a subproject is explicitly extracted and relicensed, the legal position is simple:

> **This repository is proprietary by default, and no open-source licence grant should be inferred from repository structure alone.**
