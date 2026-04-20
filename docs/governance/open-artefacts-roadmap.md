# Open Artefacts Roadmap

## Purpose

This roadmap defines the first artefacts that could be extracted from the proprietary monorepo and later published under **Apache 2.0**, without relicensing the platform spine.

This is a preparation document only. It does **not** open-source the current repository.

## Extraction Principles

Only artefacts that are safe for partner and ecosystem reuse should move into an Apache 2.0 extraction path.

Required principles:

- only artefacts safe for partner or ecosystem reuse
- no internal security policy logic
- no production deployment internals
- no monetisable control-plane logic
- no sensitive tenant or admin workflows
- no hidden dependency on private operational topology

## First Apache 2.0 Extraction Candidates

### 1. Contracts Package

The clearest first extraction boundary is the `contracts/` tree, especially:

- `contracts/openapi/`
- `contracts/asyncapi/`
- `contracts/schemas/`
- `contracts/health-os-identifiers.ts`

These materials already present as public-facing interface definitions, which makes them strong candidates for a partner-ready contracts repository.

### 2. Selected External-Facing Libraries

The strongest obvious candidates under `libs/` are:

- `libs/federation-connector/`
- `libs/offline-sdk/`
- `libs/tshepo-sdk/`

These appear more ecosystem-facing than control-plane-facing and are plausible candidates for future Apache 2.0 extraction after dependency and policy review.

### 3. Public SDKs To Be Split Later

The repository also suggests likely future SDK lines that should be separated before relicensing:

- partner-facing TSHEPO client SDK surfaces
- offline or edge synchronization client SDK surfaces
- federation connector SDKs
- event-envelope and contract-consumption client helpers that do not embed private governance logic

### 4. Public Schemas, FHIR Profiles, and Event Contracts

Likely future candidates include:

- public OpenAPI definitions
- public AsyncAPI/event envelopes
- selected JSON schemas
- public FHIR profile packages and interoperability artefacts, if separated from internal service implementations

### 5. Public Integration Quickstarts

Potential later extraction candidates:

- sanitized quickstarts for integrating with contracts
- sample payload collections
- partner onboarding examples
- public reference adapters that do not reveal production deployment assumptions

## What Must Stay Out

The following should not move into an Apache extraction unless radically reduced and sanitized:

- service implementations under `services/`
- platform operations and runtime topology under `infra/`, `helm/`, and `ops/runtime/`
- Experience/admin UI shells under `ui/`
- internal security baselines and policy packs
- internal observability and enforcement libraries
- finance, claims, procurement, registry governance, and tenant-admin workflows

## Readiness Gates Before Any Extraction

Before any candidate is relicensed:

1. dependency review to confirm no private runtime coupling
2. security review to remove internal policy or trust logic
3. commercial review to keep monetisable control-plane logic private
4. trademark and branding review
5. contributor-rights review to confirm relicensing authority
6. packaging review so the extracted artefact stands alone cleanly

## Current Position

The roadmap is directional only.

> **No artefact in this monorepo is Apache 2.0 licensed yet unless and until it is explicitly relicensed.**

## Intended Next Phase

The intended next phase for real Apache 2.0 extraction is:

1. isolate `contracts/` into a partner-safe package first
2. review `libs/federation-connector`, `libs/offline-sdk`, and `libs/tshepo-sdk` for dependency and policy separation
3. extract only after rights, trademark, security, and commercial reviews are clean
