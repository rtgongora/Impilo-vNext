# Impilo vNext — Health Operating System (HOS)

A national-grade, security-first digital health platform for Zimbabwe.

## Architecture

Six architectural planes:

| Plane | Services |
|-------|----------|
| **Trust & Governance** | TSHEPO (PDP), Envoy ext_authz (PEP), Audit, Consent, Device Risk |
| **Registry Spine** | VITO (Client), VARAPI (Provider), TUSO (Facility), ZIBO (Terminology), Product Registry |
| **Clinical Execution** | BUTANO (SHR/FHIR), PCT (Patient Care Tracker), OROS (Orders & Results), Pharmacy, Inpatient |
| **Finance** | Costing Engine, MUSheX (Payments/Claims) |
| **Integration/Ops** | Integration Hub, Offline Sync, Document Service, Notification, Jobs, PACS Adapter |
| **Experience** | One UI Shell (WORK/EHR/CONTROL/MY PROFESSIONAL/MY LIFE), Ops Console, EHR, Portal |

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
