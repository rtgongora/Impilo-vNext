# Impilo vNext Data Centre Sandbox Deployment Guide

## Purpose

This guide turns the sandbox deployment brief into repository guidance for data centre engineers, platform engineers, and DevOps contributors. It is the target posture for the Impilo vNext sandbox, integration, and performance validation environment.

Docker Compose files in this repository are for local development, bootstrap, and narrow runtime validation. They are not the data centre deployment method for the sandbox.

The data centre sandbox must be production-shaped:

- Kubernetes, OpenShift, Rancher-managed Kubernetes, VMware Tanzu Kubernetes, or Canonical Kubernetes.
- Independently deployed services from the monorepo.
- Dedicated stateful infrastructure for PostgreSQL, Kafka, Redis, object storage, PACS, and observability.
- Gateway-only public access.
- TSHEPO/OPA/Envoy policy enforcement before protected services.
- Synthetic data only.
- Full observability, stress testing, failure testing, backup, and restore validation.

## Deployment Doctrine

Use the monorepo for engineering coordination and shared contracts. Do not deploy it as one giant runtime.

The runtime model is:

```text
one source monorepo
many independently deployable services
multiple workload-specific node pools
separate stateful infrastructure
strong security boundaries
strong observability
synthetic national-scale data
measured service weight
```

Do not assume that a service is lightweight because its name looks simple. Runtime weight must be measured from CPU, memory, database, cache, Kafka, network, and workflow behaviour.

## Minimum Viable Sandbox Phase

The full target environment may be large. The first accepted data centre phase should be large enough to reveal platform behaviour without requiring the full 24-worker-node estate on day one.

### Phase 1 Objective

Phase 1 proves the core Health OS control path, integration path, and measurement loop:

- Public traffic enters only through ingress/gateway.
- Gateway calls TSHEPO/OPA policy before protected services.
- Trust headers are propagated.
- Ring 0 registry and trust services run with real databases.
- Experience BFF/UI exercise real service calls.
- FHIR/HAPI and document flows are present.
- Kafka/outbox, Redis, and PostgreSQL are observable.
- Synthetic load can identify saturation points.
- Deployment and rollback are GitOps-controlled.

### Phase 1 Suggested Footprint

```text
Kubernetes/OpenShift workers: 6-8 nodes
CPU per worker: 24-32 vCPU
RAM per worker: 96-128 GB
Storage per worker: 1-2 TB SSD/NVMe

PostgreSQL HA: 3 nodes
Kafka HA: 3 brokers
Redis HA: 3 nodes or managed Redis equivalent
Object storage: 4 nodes or managed S3-compatible storage
Observability/SecOps: 2-3 nodes
CI/CD runners: 2 nodes
```

This phase is not a small demo. It is the minimum platform-shaped environment where bottlenecks are meaningful.

### Phase 1 Workloads

Deploy these first:

- Gateway/access: Envoy Gateway, ingress controller, OPA where used.
- Trust: Keycloak, TSHEPO Authz, Identity, Consent, Audit, Keys.
- Registry spine: VITO, VARAPI, TUSO, ZIBO.
- Clinical core: PCT, OROS, Pharmacy, Inpatient, Wellness, Document Service.
- FHIR/SHR: HAPI FHIR, BUTANO, FHIR Gateway.
- Experience: Experience BFF, One UI Shell, EHR UI, Portal/Ops shell as needed.
- Integration: Integration Hub, Jobs, Notification, Offline Sync.
- Observability: Prometheus, Grafana, logs, traces, Alertmanager.

Defer bulk analytics, large AI/GPU workloads, very large DICOM ingestion, and full external adapter load until the Phase 1 control path is proven.

### Phase 1 Acceptance

Phase 1 may be accepted when:

- All deployed namespaces have quotas, limit ranges, RBAC, service accounts, secrets, labels, and network policies.
- All service traffic is blocked by default except documented allow rules.
- No database, Kafka, Redis, object store, or internal service port is public.
- Gateway-only access is proven.
- TSHEPO/OPA enforcement is proven.
- Trust header propagation is proven.
- Per-service database users are in use.
- Outbox/event publishing is proven for at least Ring 0 and clinical flows.
- Synthetic smoke and baseline load tests run with dashboards.
- Backup and restore has been tested for PostgreSQL and object storage.

## Target Node Pools

The full sandbox should move toward workload-specific node pools:

| Node pool | Purpose |
|---|---|
| gateway-ingress | Public and internal ingress, Envoy, rate limiting |
| trust-security | Keycloak, TSHEPO services, signing/key workloads |
| registry-spine | VITO, VARAPI, TUSO, ZIBO, related registry services |
| clinical-execution | PCT, OROS, pharmacy, inpatient, workflow services |
| fhir-shr | HAPI FHIR, BUTANO, FHIR Gateway |
| finance | COSTA, MUSheX, coverage, claims and finance services |
| documents-imaging | document service, MinIO/S3 clients, Orthanc/PACS adapter |
| integration | adapters, notification, jobs, replay workers |
| data-analytics | reporting, surveillance, NDR, warehouse, search |
| offline-federation | offline sync, edge, federation and replay |
| experience | BFFs, UI servers, support/developer consoles |
| observability-secops | Prometheus, Grafana, logs, traces, SIEM forwarders |
| cicd-build | self-hosted runners, image build and scan workloads |

Node pool placement must be enforced with labels, taints/tolerations where appropriate, topology spread, anti-affinity, and resource quotas.

## Required Namespaces

Create at least:

```text
impilo-gateway
impilo-trust
impilo-registry
impilo-clinical
impilo-fhir
impilo-finance
impilo-documents
impilo-imaging
impilo-integration
impilo-data
impilo-offline
impilo-experience
impilo-observability
impilo-security
impilo-cicd
impilo-devtools
```

Each namespace must have ResourceQuota, LimitRange, deny-by-default NetworkPolicy, service accounts per workload, approved secret references, monitoring/logging labels, and pod disruption budgets for critical services.

## Service Classification Matrix

The initial service classification baseline is maintained in [service-classification-matrix.md](service-classification-matrix.md).

Every new deployable service must update that matrix before it is promoted to the data centre sandbox. The classification must cover namespace, criticality, statefulness, security sensitivity, runtime weight, default replicas, resource profile, database ownership, Kafka topics, ingress exposure, and scaling behaviour.

## Impilo-Specific Enforcement Gates

The acceptance gates are maintained in [data-centre-enforcement-gates.md](../acceptance/data-centre-enforcement-gates.md).

No sandbox promotion should be accepted unless the gates prove Envoy to TSHEPO/OPA enforcement, mandatory trust headers, no direct public service exposure, no UI-to-database access, per-service database credentials, audit/outbox behaviour, synthetic-only PII, and FHIR/BUTANO PII separation.

## DevOps Governance

This document is a living deployment control document. DevOps changes must keep it current.

Update this guide, the service matrix, or the enforcement gates when any change:

- Adds, removes, renames, or splits a service.
- Changes service namespace or plane ownership.
- Adds a database, schema, Kafka topic, cache, object bucket, external adapter, or public route.
- Changes gateway, auth, policy, trust header, consent, audit, or RBAC behaviour.
- Changes default replicas, memory, CPU, JVM, health checks, probes, or HPA rules.
- Changes the CI/CD promotion path or GitOps release model.
- Changes stress-test targets or observed capacity baselines.

Pull requests that touch deployment, Helm, Kubernetes, gateway, security, service contracts, data persistence, eventing, observability, or CI/CD should include one of:

```text
Data centre sandbox docs updated.
Data centre sandbox docs reviewed; no update required.
```

## Relationship To Local Runtime Files

Use local files for local workflows only:

- `docker-compose.yml`
- `docker-compose.runtime.yml`
- `compose/experience/docker-compose.yml`
- `ops/runtime/docker-compose.*.yml`
- `scripts/runtime/platformctl.sh`
- `tools/dev/up.sh`
- `tools/dev/up.ps1`

For the data centre sandbox, use Helm/Kubernetes/OpenShift manifests, GitOps, private image registry, proper secrets, network policies, resource quotas, observability, and acceptance gates.
