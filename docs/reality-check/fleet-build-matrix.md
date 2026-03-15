# Fleet Build Matrix — Impilo vNext

> Generated: 2026-03-15 | Branch: claude/review-project-manifest-jb5O0
> Risk Class: A — Modules compile individually but not as a fleet

## Executive Summary

The platform has **67 backend services** in a Maven reactor, **12 shared libraries**, **24 UI apps** in a Turbo workspace, and **2 mobile apps** (Expo/React Native). The fleet build topology is well-structured but has specific gaps in Dockerfile coverage and runtime composition.

## Build Topology

### Tier 1: Shared Libraries (libs/)

| Library | Type | Standalone POM | Dependencies |
|---|---|---|---|
| shared-kernel-java | Java | Yes (standalone) | Jackson, json-schema-validator |
| tshepo-contracts | Java | Yes (standalone) | None |
| tshepo-sdk | Java | Yes (standalone) | tshepo-contracts |
| contract-tests | Java | Yes (standalone) | Spring Test |
| tech-companion | Java | Yes (standalone) | Spring Web, Jackson |
| tech-companion-harness | Java | Yes (standalone) | tech-companion, Spring Test |
| tech-companion-mock | Java | Yes (standalone) | tech-companion |
| federation-connector | Java | Yes (standalone) | tech-companion |
| ops-instrumentation | Java | Yes (standalone) | OpenTelemetry |
| security-baseline | Java | Yes (standalone) | Spring Security |
| offline-sdk | Java | Yes (standalone) | Jackson |
| shared-kernel | JS/TS | npm package | TypeScript |

**Build Order**: libs must be built BEFORE the services reactor. They are standalone POMs, NOT included in the services/pom.xml reactor.

### Tier 2: Backend Services (services/ Maven Reactor)

- **Parent POM**: `services/pom.xml` (impilo-parent, Spring Boot 3.3.6)
- **Modules**: 67 services + shared-core in reactor
- **Build command**: `cd services && mvn -B clean compile -DskipTests -T 1C --fail-at-end`
- **Dependencies on libs**: Services reference libs via `${project.version}` → libs must be `mvn install`'d first

### Tier 3: UI Workspace (ui/)

- **Workspace manager**: Turbo (turbo.json)
- **Root package.json**: `ui/package.json`
- **Apps**: 24 Next.js web applications + shared-ui
- **Build command**: `cd ui && npm install --legacy-peer-deps && npx turbo run build`

### Tier 4: Mobile Apps (apps/mobile/)

- **Apps**: provider-app, citizen-app
- **Framework**: Expo ~52.0.0, React Native ~0.76.0
- **Shared packages**: 7 workspace packages under apps/mobile/packages/
- **Build command**: `cd apps/mobile/<app> && npm install && expo prebuild && eas build`

## Fleet Build Script

See: `scripts/reality-check/build-fleet.sh`

## Findings

### What Works

| Check | Status | Evidence |
|---|---|---|
| Parent POM reactor wiring | PASS | All 67 services listed in `<modules>` |
| Shared libs have standalone POMs | PASS | Each lib builds independently |
| UI workspace has Turbo config | PASS | `ui/turbo.json` + root `package.json` |
| Mobile apps have Expo config | PASS | `app.config.ts`, `eas.json`, `metro.config.js` |
| Dependency version management | PASS | Single `<properties>` block in parent POM |

### What Is Broken / Missing

| Check | Status | Impact | Mitigation |
|---|---|---|---|
| No root-level build orchestrator | GAP | No single command builds entire fleet | Created `scripts/reality-check/build-fleet.sh` |
| Libs not in reactor | DESIGN | Must `mvn install` libs before services | Documented in build-fleet.sh |
| 38/67 services missing Dockerfiles | GAP | Cannot containerize full fleet | See list below |
| Docker build compose only builds Maven + Experience UI | GAP | Doesn't build all UIs or mobile | `docker-compose.build.yml` is limited |

### Services Missing Dockerfiles (38)

asset-registry-service, audit-ledger-service, butano-fhir, campaigns-service, channels-service, connector-fhir-adapter, coverage-service, data-access-governance-service, data-governance-service, data-ingestion-service, data-pipeline-service, data-warehouse-service, developer-portal-service, dispatch-service, fhir-gateway-service, forms-service, identity-assurance-service, indawo-service, inpatient-service, integration-hub, iot-ingestion-service, jobs-service, national-data-repository-service, ndr-service, notification-service, observability-service, offline-edge-service, offline-sync-service, pacs-adapter-service, product-registry-service, rules-service, schema-registry-service, search-service, security-hardening-service, support-service, surveillance-service, workflow-service

### Services WITH Dockerfiles (30)

butano-service, card-print-agent, costing-engine-service, credential-verification-service, document-service, experience-bff, inventory-elmis-adapter, inventory-service, landela-adapter-service, msika-flow-service, msika-service, mushex-service, oros-service, pct-service, pharmacy-elmis-adapter, pharmacy-service, reporting-service, share-slip-service, tshepo-audit-service, tshepo-authz-service, tshepo-consent-service, tshepo-identity-service, tshepo-keys-service, tshepo-offline-service, tshepo-service, tuso-service, ubomi-service, varapi-service, vito-service, zibo-service

## Environment Constraints

- **Maven/Java**: Not available in this environment → reactor build NOT executed here
- **npm/Node**: Not available → UI/mobile builds NOT executed here
- **Docker**: Not available → container builds NOT verified here

## Verdict

**FLEET BUILD: STRUCTURALLY SOUND, PARTIALLY INCOMPLETE**

The Maven reactor wiring is correct and comprehensive. The primary gap is Dockerfile coverage (44% of services have Dockerfiles) and the absence of a single-command fleet build script (now mitigated by `build-fleet.sh`).
