# Impilo vNext - Health Operating System (HOS)

A sovereign-grade, security-first digital health platform designed for deployment across health systems in multiple jurisdictions.

## Licensing Status

This monorepo is proprietary by default. All rights are reserved by **Impilo Technologies Private Limited (a State Owned Enterprise), registered in the Republic of Zimbabwe** except where a specific artefact is explicitly relicensed.

Certain future ecosystem-facing artefacts may later be extracted and separately licensed, but no reuse rights exist today except where they are expressly granted.

## Architecture

Seven architectural planes:

| Canonical Plane | Services |
|-------|----------|
| **Trust** (Trust, Identity Assurance & Governance) | TSHEPO (PDP), Envoy ext_authz (PEP), Audit, Tshepo Consent (FHIR), **Mvumo** (sovereign consent orchestration), Device Risk |
| **Registry** (Registry & Sovereign Identity Spine) | VITO (Client), VARAPI (Provider), TUSO (Facility), ZIBO (Terminology), Product Registry |
| **Clinical** (Clinical Execution & Shared Health Record) | BUTANO (SHR/FHIR), PCT (Patient Care Tracker), OROS (Orders & Results), Pharmacy, Inpatient |
| **Data** (Data, Intelligence & Public Health) | NDR, Data Warehouse, Reporting, Surveillance, Search |
| **Integration** (Integration, Interoperability & Edge) | Integration Hub, Offline Sync, Document Service, Notification, Jobs, PACS Adapter |
| **Experience** (Experience, Workflow & Orchestration) | **Impilo web experience** (single orchestration layer on **3000** shipped as `one-ui-shell`), mobile journeys, and **Experience BFF** (`:8160`) orchestration |
| **Enterprise** (Enterprise Resource & Market Operations) | Costing Engine, MUSheX, Coverage, Claims/Billing flows, General Ledger, HR & Payroll, Procurement, marketplace operations |

Canonical doctrine and ownership maps are maintained under `docs/architecture/planes/` and `docs/registry/`.

Core Transaction doctrine references:

- `docs/doctrine/CORE_TRANSACTION_DOCTRINE.md`
- `docs/doctrine/CORE_TRANSACTION_STATE_MACHINE.md`
- `docs/doctrine/THREE_CORE_JOURNEYS.md`
- `docs/doctrine/PERSON_JOURNEY.md`
- `docs/doctrine/PROVIDER_JOURNEY.md`
- `docs/doctrine/PLATFORM_BACK_OF_HOUSE_JOURNEY.md`
- `docs/doctrine/NOMPILO_INTELLIGENT_JOURNEY_COMPANION.md`
- `docs/architecture/core-transaction-plane-map.md`
- `docs/architecture/core-transaction-event-model.md`
- `docs/architecture/three-journey-core-transaction-map.md`
- `docs/architecture/nompilo-journey-companion-architecture.md`
- `docs/architecture/nompilo-accessibility-omnichannel-feedback.md`
- `docs/templates/CORE_TRANSACTION_FEATURE_ALIGNMENT_CHECKLIST.md`

Canonical service governance and classification references:

- `docs/architecture/SERVICE_ARCHITECTURE_REGISTER.md`
- `docs/architecture/services-registry.yaml`
- `docs/architecture/service-update-policy.md`
- `docs/architecture/ring-plane-taxonomy.md`
- `docs/architecture/service-boundary-violations.md`

## Deployment Model

Impilo is designed as a hybrid, federated platform: centrally governed where national truth is required, and locally executable where service continuity is required.

The architecture distinguishes between:

- `tenant`: legal and policy boundary
- `pod`: deployment/runtime boundary
- `facility`: care delivery site
- `workspace`: operational unit inside a facility

Facilities are not treated as one-size-fits-all. Zimbabwe facility tiers from Community through Quinary Hospital, plus Virtual Hospital, are intended to drive deployment posture, enabled services, continuity requirements, and user experience shape. See [docs/architecture/facility-operating-model.md](docs/architecture/facility-operating-model.md) and [contracts/facility-operating-model.ts](contracts/facility-operating-model.ts).

For the production-shaped sandbox and data-centre deployment posture, see [docs/deployment/data-centre-sandbox-deployment.md](docs/deployment/data-centre-sandbox-deployment.md). Docker Compose remains local/dev bootstrap only; the data-centre sandbox is expected to use Kubernetes/OpenShift, independent service deployment, network policies, GitOps, observability, and the enforcement gates in [docs/acceptance/data-centre-enforcement-gates.md](docs/acceptance/data-centre-enforcement-gates.md). The initial service classification baseline is maintained in [docs/deployment/service-classification-matrix.md](docs/deployment/service-classification-matrix.md).

## Repository Segmentation Intent

The strategic platform spine remains closed by default. Over time, selected ecosystem-facing artefacts such as contracts, schemas, public SDKs, and partner-safe integration materials may be extracted into separately licensed packages.

That future extraction path does not change the current legal position of this repository as a whole: the monorepo remains proprietary unless a subproject is explicitly relicensed.

## Tech Stack

- **Backend**: Java 21, Spring Boot 3.3.x, PostgreSQL 16, Redis 7, Kafka 3.7.x
- **Clinical**: HAPI FHIR 7.x, Orthanc PACS
- **Frontend**: Next.js 14.2.x, TypeScript 5.x, TailwindCSS 3.4.x, Radix UI
- **Identity**: Keycloak 25.x, MOSIP (phased)
- **Gateway**: Envoy 1.31.x (primary), mTLS internal
- **Infra**: Docker, Kubernetes, Helm, GitHub Actions

## Quick Start

```bash
# 1. Copy environment file
cp .env.example .env

# 2. Start infrastructure
docker compose up -d

# 3. Run a service (e.g., TSHEPO)
cd services/tshepo-service
./mvnw spring-boot:run

# 4. Run the UI shell
cd ui/one-ui-shell
npm install && npm run dev
```

## Project Structure

```
contracts/          API contracts (OpenAPI, AsyncAPI, schemas)
services/           Java microservices (one per bounded context)
ui/                 Frontend applications (Next.js)
infra/              Envoy, Kubernetes, observability configs
scripts/            Seed data and smoke tests
docs/               Architecture docs and runbooks
```

## Implementation Integrity Rules (Web + Mobile)

To avoid floating frontend and fake capability drift:

1. **Trace every feature end-to-end**: web route/mobile screen -> hook/client -> BFF endpoint -> backend service -> contract -> test.
2. **Do not present fixture data as live**. Use explicit maturity labels (`Live`, `Partial`, `Fixture`, `Not wired`) in dev/internal surfaces.
3. **Do not ship dead-end actions**. Buttons must either perform real actions or be disabled with a clear reason.
4. **Wire both surfaces intentionally**. If a feature is web-only or mobile-only, document that explicitly in `docs/audits/*`.
5. **Use canonical contracts** where possible (`contracts/*`) and avoid duplicate local enums/types.
6. **Nompilo capabilities must be grounded** in real API/service support; do not imply unsupported actions.
7. **Validate continuously** with lint/tests/build before calling work complete.

For current evidence-based integrity status and remediation backlog, see:

- `docs/audits/IMPLEMENTATION_INTEGRITY_AUDIT.md`
- `docs/audits/WEB_MOBILE_PARITY_AUDIT.md`
- `docs/audits/REMEDIATION_SUMMARY.md`
