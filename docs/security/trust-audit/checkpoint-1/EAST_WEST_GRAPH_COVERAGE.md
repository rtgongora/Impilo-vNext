# East–west graph coverage — Checkpoint 1 closure

**Observation window (UTC):** `2026-08-01T09:42:27Z`
**Namespace:** `impilo-full-preview`
**Live inventory source:** kubectl get deploy,statefulset,cronjob,job -o json

## Observation window and telemetry blind spots

| Item | Status |
|---|---|
| Capture time | `2026-08-01T09:42:27Z` |
| Method | Point-in-time Kubernetes inventory + ConfigMap/env inspection |
| Service-mesh / mTLS telemetry | **ABSENT** — no Istio/Linkerd; no mTLS metrics |
| NetworkPolicy logs | **ABSENT** — 0 NetworkPolicies in namespace |
| Kafka consumer lag / ACL audit | **ABSENT** — PLAINTEXT Kafka, no ACLs |
| Continuous flow capture (eBPF/PCAP) | **NOT PERFORMED** — edges below are source-declared or DNS/config inferred |
| Envoy access logs on live path | **N/A** — Envoy not on ingress path |
| East-west request auth success/fail rates | **INSUFFICIENT_EVIDENCE** without mesh or app audit correlation |

## Node coverage (every controller kind)

| Kind | Count |
|---|---|
| CronJob | 2 |
| Deployment | 113 |
| Job | 14 |
| **Total nodes** | **129** |

### Full node list

| Kind | Name | ServiceAccount | Registry id match | Images |
|---|---|---|---|---|
| CronJob | `estate-health-watch` | `estate-health-watch` | `—` | `mirror.gcr.io/curlimages/curl:8.11.1` |
| CronJob | `postgres-backup` | `default` | `—` | `postgres:16-alpine` |
| Deployment | `abis-service` | `default` | `abis-service` | `127.0.0.1:5000/impilo/abis-service@sha256:b70f81f782d1c3caf86fe3f3c...` |
| Deployment | `ai-model-registry-service` | `default` | `ai-model-registry-service` | `127.0.0.1:5000/impilo/ai-model-registry-service@sha256:78692566c4af...` |
| Deployment | `analytics-pipeline-service` | `default` | `analytics-pipeline-service` | `127.0.0.1:5000/impilo/analytics-pipeline-service@sha256:5922e54c189...` |
| Deployment | `asset-registry-service` | `default` | `asset-registry-service` | `127.0.0.1:5000/impilo/asset-registry-service@sha256:d9743322a41ebd3...` |
| Deployment | `audit-ledger-service` | `default` | `audit-ledger-service` | `127.0.0.1:5000/impilo/audit-ledger-service@sha256:87806a69083dd2ea2...` |
| Deployment | `booking-service` | `default` | `booking-service` | `127.0.0.1:5000/impilo/booking-service@sha256:1e72f57ebd690a05a6ead1...` |
| Deployment | `butano-fhir` | `default` | `butano-fhir` | `127.0.0.1:5000/impilo/butano-fhir@sha256:a0d05ae4f9665372424148b6c8...` |
| Deployment | `butano-service` | `default` | `butano-service` | `127.0.0.1:5000/impilo/butano-service@sha256:37d9a856b9ebd64eb414d92...` |
| Deployment | `campaigns-service` | `default` | `campaigns-service` | `127.0.0.1:5000/impilo/campaigns-service@sha256:17bcd29748a7e235aca8...` |
| Deployment | `card-print-agent` | `default` | `card-print-agent` | `127.0.0.1:5000/impilo/card-print-agent@sha256:f6e2ba2643ff0336ccea0...` |
| Deployment | `channels-service` | `default` | `channels-service` | `127.0.0.1:5000/impilo/channels-service@sha256:ccdd0ffbff4ee587a31c2...` |
| Deployment | `clinical-knowledge-platform-service` | `default` | `clinical-knowledge-platform-service` | `127.0.0.1:5000/impilo/clinical-knowledge-platform-service@sha256:fa...` |
| Deployment | `community-service` | `default` | `community-service` | `127.0.0.1:5000/impilo/community-service@sha256:ea195af7c5ba26efebf6...` |
| Deployment | `connector-fhir-adapter` | `default` | `connector-fhir-adapter` | `127.0.0.1:5000/impilo/connector-fhir-adapter@sha256:a93778b9017a141...` |
| Deployment | `costing-engine-service` | `default` | `costing-engine-service` | `127.0.0.1:5000/impilo/costing-engine-service@sha256:a92b4d26be810a9...` |
| Deployment | `coverage-service` | `default` | `coverage-service` | `127.0.0.1:5000/impilo/coverage-service@sha256:9477166c88962d82dbbad...` |
| Deployment | `credential-verification-service` | `default` | `credential-verification-service` | `127.0.0.1:5000/impilo/credential-verification-service@sha256:09ebb4...` |
| Deployment | `daidzai-service` | `default` | `daidzai-service` | `127.0.0.1:5000/impilo/daidzai-service@sha256:e31553d698e341a19e7ae9...` |
| Deployment | `data-access-governance-service` | `default` | `data-access-governance-service` | `127.0.0.1:5000/impilo/data-access-governance-service@sha256:6b6678a...` |
| Deployment | `data-governance-service` | `default` | `data-governance-service` | `127.0.0.1:5000/impilo/data-governance-service@sha256:631059f5240dd3...` |
| Deployment | `data-ingestion-service` | `default` | `data-ingestion-service` | `127.0.0.1:5000/impilo/data-ingestion-service@sha256:118b033474fb045...` |
| Deployment | `data-pipeline-service` | `default` | `data-pipeline-service` | `127.0.0.1:5000/impilo/data-pipeline-service@sha256:b59aa713ffe96e58...` |
| Deployment | `data-warehouse-service` | `default` | `data-warehouse-service` | `127.0.0.1:5000/impilo/data-warehouse-service@sha256:c5c6896094d0aee...` |
| Deployment | `developer-portal-service` | `default` | `developer-portal-service` | `127.0.0.1:5000/impilo/developer-portal-service@sha256:68d4c93c354fc...` |
| Deployment | `dispatch-service` | `default` | `dispatch-service` | `127.0.0.1:5000/impilo/dispatch-service@sha256:42b8f98c37ad5f21f85bb...` |
| Deployment | `document-service` | `default` | `document-service` | `127.0.0.1:5000/impilo/document-service@sha256:c21f04fcb28d228d7d340...` |
| Deployment | `envoy` | `default` | `—` | `envoyproxy/envoy:v1.31-latest` |
| Deployment | `experience-bff` | `default` | `experience-bff` | `127.0.0.1:5000/impilo/experience-bff@sha256:1948d8d355b5a3456ed0bbd...` |
| Deployment | `fhir-gateway-service` | `default` | `fhir-gateway-service` | `127.0.0.1:5000/impilo/fhir-gateway-service@sha256:8081d24cf865dcff6...` |
| Deployment | `forms-service` | `default` | `forms-service` | `127.0.0.1:5000/impilo/forms-service@sha256:f20041d6620233ed7d95e238...` |
| Deployment | `general-ledger-service` | `default` | `general-ledger-service` | `127.0.0.1:5000/impilo/general-ledger-service@sha256:312963d3eba2257...` |
| Deployment | `guidance-service` | `default` | `guidance-service` | `127.0.0.1:5000/impilo/guidance-service@sha256:5077164f86e9acb71eee1...` |
| Deployment | `hapi-fhir` | `default` | `—` | `hapiproject/hapi:v7.4.0` |
| Deployment | `hr-payroll-service` | `default` | `hr-payroll-service` | `127.0.0.1:5000/impilo/hr-payroll-service@sha256:0b7afc361c44ca3c741...` |
| Deployment | `identity-assurance-service` | `default` | `identity-assurance-service` | `127.0.0.1:5000/impilo/identity-assurance-service@sha256:dabbeb2743a...` |
| Deployment | `indawo-service` | `default` | `indawo-service` | `127.0.0.1:5000/impilo/indawo-service@sha256:9c5a992447d1d4a64d0d26c...` |
| Deployment | `inpatient-service` | `default` | `inpatient-service` | `127.0.0.1:5000/impilo/inpatient-service@sha256:473b87e7074d6146a2cf...` |
| Deployment | `integration-hub` | `default` | `integration-hub` | `127.0.0.1:5000/impilo/integration-hub@sha256:cd1fb0d96e900168c739fc...` |
| Deployment | `inventory-elmis-adapter` | `default` | `inventory-elmis-adapter` | `127.0.0.1:5000/impilo/inventory-elmis-adapter@sha256:ca604bf962789b...` |
| Deployment | `inventory-service` | `default` | `inventory-service` | `127.0.0.1:5000/impilo/inventory-service@sha256:ec05ca0da427283b3d30...` |
| Deployment | `iot-ingestion-service` | `default` | `iot-ingestion-service` | `127.0.0.1:5000/impilo/iot-ingestion-service@sha256:8034eb76b45a82cf...` |
| Deployment | `jobs-service` | `default` | `jobs-service` | `127.0.0.1:5000/impilo/jobs-service@sha256:e69a859d79ae75ac010ab73bd...` |
| Deployment | `kafka` | `default` | `—` | `apache/kafka:3.7.1` |
| Deployment | `keycloak` | `default` | `—` | `127.0.0.1:5000/impilo/keycloak@sha256:70f0af3d5a9352c1d62cf6ea05943...` |
| Deployment | `khuluma-service` | `default` | `khuluma-service` | `127.0.0.1:5000/impilo/khuluma-service@sha256:397135a232c9543f1ccb2b...` |
| Deployment | `landela-adapter-service` | `default` | `landela-adapter-service` | `127.0.0.1:5000/impilo/landela-adapter-service@sha256:ebe285ea0a5825...` |
| Deployment | `learning-service` | `default` | `learning-service` | `127.0.0.1:5000/impilo/learning-service@sha256:7cb3d1171a9168d9b6bee...` |
| Deployment | `live-service` | `default` | `live-service` | `127.0.0.1:5000/impilo/live-service@sha256:8c3072f7929244bc179eb7a3f...` |
| Deployment | `livekit` | `default` | `—` | `mirror.gcr.io/livekit/livekit-server:v1.13.3` |
| Deployment | `livekit-egress` | `default` | `—` | `mirror.gcr.io/livekit/egress:v1.13.0` |
| Deployment | `llm-orchestration-service` | `default` | `llm-orchestration-service` | `127.0.0.1:5000/impilo/llm-orchestration-service@sha256:c46371f5735c...` |
| Deployment | `madi-service` | `default` | `madi-service` | `127.0.0.1:5000/impilo/madi-service@sha256:4c1627115a6106fe07ac8784f...` |
| Deployment | `matcher-engine` | `default` | `matcher-engine` | `127.0.0.1:5000/impilo/matcher-engine:preview` |
| Deployment | `minio` | `default` | `—` | `minio/minio:latest` |
| Deployment | `msika-apps-service` | `default` | `msika-apps-service` | `127.0.0.1:5000/impilo/msika-apps-service@sha256:954ad75b010beef8240...` |
| Deployment | `msika-flow-service` | `default` | `msika-flow-service` | `127.0.0.1:5000/impilo/msika-flow-service@sha256:be80b9f9ed1f8f34b9b...` |
| Deployment | `msika-service` | `default` | `msika-service` | `127.0.0.1:5000/impilo/msika-service@sha256:dfc721ecc423356e780f5936...` |
| Deployment | `mushe-wallet-service` | `default` | `mushe-wallet-service` | `127.0.0.1:5000/impilo/mushe-wallet-service@sha256:cffb855fe54b602c0...` |
| Deployment | `mushex-service` | `default` | `mushex-service` | `127.0.0.1:5000/impilo/mushex-service@sha256:00d24394f0ed173020fb0e9...` |
| Deployment | `mvumo-service` | `default` | `mvumo-service` | `127.0.0.1:5000/impilo/mvumo-service@sha256:ade83fa189b43b7824293655...` |
| Deployment | `national-data-repository-service` | `default` | `national-data-repository-service` | `127.0.0.1:5000/impilo/national-data-repository-service@sha256:6b4a2...` |
| Deployment | `ndila-martin` | `default` | `—` | `ghcr.io/maplibre/martin:v0.15.0` |
| Deployment | `ndila-service` | `default` | `ndila-service` | `127.0.0.1:5000/impilo/ndila-service@sha256:998eff10ffc05642ec2c0330...` |
| Deployment | `ndr-service` | `default` | `ndr-service` | `127.0.0.1:5000/impilo/ndr-service@sha256:4222a8d713a8a5ca49e44f0e80...` |
| Deployment | `nhume-service` | `default` | `nhume-service` | `127.0.0.1:5000/impilo/nhume-service@sha256:beb4113faa935dbc8574a216...` |
| Deployment | `notification-service` | `default` | `notification-service` | `127.0.0.1:5000/impilo/notification-service@sha256:02f39539ccb4fba47...` |
| Deployment | `observability-service` | `default` | `observability-service` | `127.0.0.1:5000/impilo/observability-service@sha256:0a24076647e4d5b0...` |
| Deployment | `offline-edge-service` | `default` | `offline-edge-service` | `127.0.0.1:5000/impilo/offline-edge-service@sha256:294ee9b9ab65d981f...` |
| Deployment | `offline-sync-service` | `default` | `offline-sync-service` | `127.0.0.1:5000/impilo/offline-sync-service@sha256:40f18ecacb291c86d...` |
| Deployment | `one-ui-shell` | `default` | `—` | `127.0.0.1:5000/impilo/one-ui-shell@sha256:d264a0c1ebbf11fe675d893f9...` |
| Deployment | `organization-registry-service` | `default` | `organization-registry-service` | `127.0.0.1:5000/impilo/organization-registry-service@sha256:c821e41e...` |
| Deployment | `oros-service` | `default` | `oros-service` | `127.0.0.1:5000/impilo/oros-service@sha256:71eaf434f9634d935b7c0b667...` |
| Deployment | `orthanc` | `default` | `—` | `mirror.gcr.io/jodogne/orthanc-plugins:1.12.4` |
| Deployment | `pacs-adapter-service` | `default` | `pacs-adapter-service` | `127.0.0.1:5000/impilo/pacs-adapter-service@sha256:b612e8dee4de47449...` |
| Deployment | `participation-service` | `default` | `participation-service` | `127.0.0.1:5000/impilo/participation-service@sha256:eb12b6a933bfe6d4...` |
| Deployment | `patient-safety-service` | `default` | `patient-safety-service` | `127.0.0.1:5000/impilo/patient-safety-service@sha256:1958481665802f4...` |
| Deployment | `pct-service` | `default` | `pct-service` | `127.0.0.1:5000/impilo/pct-service@sha256:364c104c80ee19448a4082a91a...` |
| Deployment | `pharmacy-elmis-adapter` | `default` | `pharmacy-elmis-adapter` | `127.0.0.1:5000/impilo/pharmacy-elmis-adapter@sha256:8a008d6c1807e87...` |
| Deployment | `pharmacy-service` | `default` | `pharmacy-service` | `127.0.0.1:5000/impilo/pharmacy-service@sha256:9206eea3de5302aab5307...` |
| Deployment | `postgres` | `default` | `—` | `postgres:16-alpine` |
| Deployment | `procurement-service` | `default` | `procurement-service` | `127.0.0.1:5000/impilo/procurement-service@sha256:33b6f63c90177bd6ee...` |
| Deployment | `product-registry-service` | `default` | `product-registry-service` | `127.0.0.1:5000/impilo/product-registry-service@sha256:8759cda4abb1e...` |
| Deployment | `public-website` | `default` | `—` | `127.0.0.1:5000/impilo/public-website@sha256:de1235a219801a1e37569ff...` |
| Deployment | `redis` | `default` | `—` | `redis:7-alpine` |
| Deployment | `referral-service` | `default` | `referral-service` | `127.0.0.1:5000/impilo/referral-service@sha256:d3866f284cc002c986b46...` |
| Deployment | `reporting-service` | `default` | `reporting-service` | `127.0.0.1:5000/impilo/reporting-service@sha256:000d695d570fc5ff617e...` |
| Deployment | `rito-quality-safety-service` | `default` | `rito-quality-safety-service` | `127.0.0.1:5000/impilo/rito-quality-safety-service@sha256:704ec0bd65...` |
| Deployment | `rtc-gateway-service` | `default` | `rtc-gateway-service` | `127.0.0.1:5000/impilo/rtc-gateway-service@sha256:a8037e48d62b3a5d48...` |
| Deployment | `rules-service` | `default` | `rules-service` | `127.0.0.1:5000/impilo/rules-service@sha256:4347054f437afa32a70c0048...` |
| Deployment | `scheduling-service` | `default` | `scheduling-service` | `127.0.0.1:5000/impilo/scheduling-service@sha256:04b6e89ab8779bd375c...` |
| Deployment | `schema-registry-service` | `default` | `schema-registry-service` | `127.0.0.1:5000/impilo/schema-registry-service@sha256:fea0933bfb4ccd...` |
| Deployment | `search-service` | `default` | `search-service` | `127.0.0.1:5000/impilo/search-service@sha256:4d36474e3d24d159bac97ed...` |
| Deployment | `security-hardening-service` | `default` | `security-hardening-service` | `127.0.0.1:5000/impilo/security-hardening-service@sha256:482a84f2495...` |
| Deployment | `share-slip-service` | `default` | `share-slip-service` | `127.0.0.1:5000/impilo/share-slip-service@sha256:c2427005dcdeaa608d5...` |
| Deployment | `simba-service` | `default` | `simba-service` | `127.0.0.1:5000/impilo/simba-service@sha256:cd82b50d003242d90cb5b830...` |
| Deployment | `support-service` | `default` | `support-service` | `127.0.0.1:5000/impilo/support-service@sha256:2ed21c83c58c0ecb695689...` |
| Deployment | `surveillance-service` | `default` | `surveillance-service` | `127.0.0.1:5000/impilo/surveillance-service@sha256:423d0f2746cf6e76f...` |
| Deployment | `telemonitoring-service` | `default` | `telemonitoring-service` | `127.0.0.1:5000/impilo/telemonitoring-service@sha256:8442e18480eaf91...` |
| Deployment | `tshepo-audit-service` | `default` | `tshepo-audit-service` | `127.0.0.1:5000/impilo/tshepo-audit-service@sha256:bba09c3926e5106fa...` |
| Deployment | `tshepo-authz-service` | `default` | `tshepo-authz-service` | `127.0.0.1:5000/impilo/tshepo-authz-service@sha256:4da33b6f60ae10e64...` |
| Deployment | `tshepo-consent-service` | `default` | `tshepo-consent-service` | `127.0.0.1:5000/impilo/tshepo-consent-service@sha256:42add39afe9af90...` |
| Deployment | `tshepo-identity-service` | `default` | `tshepo-identity-service` | `127.0.0.1:5000/impilo/tshepo-identity-service@sha256:8277bb5e8f8b5a...` |
| Deployment | `tshepo-keys-service` | `default` | `tshepo-keys-service` | `127.0.0.1:5000/impilo/tshepo-keys-service@sha256:5974067cd6e9f2775f...` |
| Deployment | `tshepo-offline-service` | `default` | `tshepo-offline-service` | `127.0.0.1:5000/impilo/tshepo-offline-service@sha256:285377523fd1cc8...` |
| Deployment | `tuso-service` | `default` | `tuso-service` | `127.0.0.1:5000/impilo/tuso-service@sha256:f5669dcb3d22a0b6e45b9d11e...` |
| Deployment | `ubomi-service` | `default` | `ubomi-service` | `127.0.0.1:5000/impilo/ubomi-service@sha256:ace694d39b9895ace0509a1e...` |
| Deployment | `varapi-service` | `default` | `varapi-service` | `127.0.0.1:5000/impilo/varapi-service@sha256:216b86409a0a8444449afc8...` |
| Deployment | `vashandi-workforce-service` | `default` | `vashandi-workforce-service` | `127.0.0.1:5000/impilo/vashandi-workforce-service@sha256:18e79d0f84a...` |
| Deployment | `vito-service` | `default` | `vito-service` | `127.0.0.1:5000/impilo/vito-service@sha256:2909c790f1f8f09194a4d1423...` |
| Deployment | `wellness-service` | `default` | `wellness-service` | `127.0.0.1:5000/impilo/wellness-service@sha256:e510f1c73132047d96dce...` |
| Deployment | `workflow-service` | `default` | `workflow-service` | `127.0.0.1:5000/impilo/workflow-service@sha256:d0d4c4519275594539d07...` |
| Deployment | `workforce-governance-service` | `default` | `workforce-governance-service` | `127.0.0.1:5000/impilo/workforce-governance-service@sha256:283c2ef8c...` |
| Deployment | `zibo-service` | `default` | `zibo-service` | `127.0.0.1:5000/impilo/zibo-service@sha256:ef58e0db5b12b49a7bb2150fe...` |
| Job | `estate-health-watch-29756370` | `estate-health-watch` | `—` | `mirror.gcr.io/curlimages/curl:8.11.1` |
| Job | `estate-health-watch-29756850` | `estate-health-watch` | `—` | `mirror.gcr.io/curlimages/curl:8.11.1` |
| Job | `estate-health-watch-29759590` | `estate-health-watch` | `—` | `mirror.gcr.io/curlimages/curl:8.11.1` |
| Job | `keycloak-bootstrap-admin-mfa` | `default` | `—` | `127.0.0.1:5000/impilo/keycloak@sha256:70f0af3d5a9352c1d62cf6ea05943...` |
| Job | `keycloak-create-reconciler-mfa` | `default` | `—` | `127.0.0.1:5000/impilo/keycloak@sha256:70f0af3d5a9352c1d62cf6ea05943...` |
| Job | `keycloak-grant-event-reader-mfa` | `default` | `—` | `127.0.0.1:5000/impilo/keycloak@sha256:70f0af3d5a9352c1d62cf6ea05943...` |
| Job | `keycloak-h2-export-mfa-20260801-0313` | `default` | `—` | `quay.io/keycloak/keycloak:25.0` |
| Job | `keycloak-h2-snapshot-mfa-20260801-0313` | `default` | `—` | `busybox:1.36` |
| Job | `keycloak-pg25-import-mfa-20260801-0313` | `default` | `—` | `quay.io/keycloak/keycloak@sha256:82c5b7a110456dbd42b86ea572e7288785...` |
| Job | `keycloak-remove-bootstrap-admin-mfa` | `default` | `—` | `127.0.0.1:5000/impilo/keycloak@sha256:70f0af3d5a9352c1d62cf6ea05943...` |
| Job | `livekit-egress-bucket-init` | `default` | `—` | `mirror.gcr.io/minio/mc:RELEASE.2025-08-13T08-35-41Z` |
| Job | `postgres-backup-29756130` | `default` | `—` | `postgres:16-alpine` |
| Job | `postgres-backup-29757570` | `default` | `—` | `postgres:16-alpine` |
| Job | `postgres-backup-29759010` | `default` | `—` | `postgres:16-alpine` |

## Registry ↔ Kubernetes reconcile

| Set | Count |
|---|---|
| Registry services (`docs/registry/services-registry.yaml`) | 104 |
| K8s controller names | 129 |
| Exact name intersection | 100 |
| Registry ids with no exact K8s name | 4 |
| K8s names with no registry id | 29 |

### Registry services without exact Deployment/CronJob name match

These are **not** automatically absent — naming drift (suffixes, infra-only, bundled) is common. Classified as reconcile gaps, not runtime proof of absence.

- `mental-health-service`
- `procedures-service`
- `surgery-service`
- `tshepo-service`

### K8s controllers without registry id

- `envoy`
- `estate-health-watch`
- `estate-health-watch-29756370`
- `estate-health-watch-29756850`
- `estate-health-watch-29759590`
- `hapi-fhir`
- `kafka`
- `keycloak`
- `keycloak-bootstrap-admin-mfa`
- `keycloak-create-reconciler-mfa`
- `keycloak-grant-event-reader-mfa`
- `keycloak-h2-export-mfa-20260801-0313`
- `keycloak-h2-snapshot-mfa-20260801-0313`
- `keycloak-pg25-import-mfa-20260801-0313`
- `keycloak-remove-bootstrap-admin-mfa`
- `livekit`
- `livekit-egress`
- `livekit-egress-bucket-init`
- `minio`
- `ndila-martin`
- `one-ui-shell`
- `orthanc`
- `postgres`
- `postgres-backup`
- `postgres-backup-29756130`
- `postgres-backup-29757570`
- `postgres-backup-29759010`
- `public-website`
- `redis`

## Declared / observed edges

Evidence classes: `SOURCE` (code/config), `RUNTIME` (live env/IngressRoute/Service), `UNKNOWN` (no proof in this window).

| Source | Destination | Protocol | Credential | Evidence | Classification |
|---|---|---|---|---|---|
| Internet | traefik | TLS | public | RUNTIME IngressRoute | ENFORCED edge TLS |
| traefik | experience-bff:8160 | HTTP | none at edge | RUNTIME IngressRoute /internal | RUNTIME |
| traefik | one-ui-shell:3000 | HTTP | none at edge | RUNTIME IngressRoute / | RUNTIME |
| traefik | keycloak:8080 | HTTP | OIDC | RUNTIME IngressRoute /realms | RUNTIME |
| traefik | public-website | HTTP | none | RUNTIME IngressRoute /.well-known | RUNTIME |
| Client | envoy:10000 | — | — | RUNTIME no IngressRoute | DISCONNECTED |
| experience-bff | ~domain ClusterIPs | HTTP | JWT|CC|none | SOURCE ServiceClientConfig | SOURCE |
| experience-bff | tshepo-authz-service:8081 | HTTP | service token / headers | RUNTIME TSHEPO_AUTHZ_BASE_URL | SOURCE+RUNTIME |
| experience-bff | tshepo-consent-service | HTTP | internal | SOURCE TshepoConsentServiceClient | SOURCE |
| experience-bff | mvumo-service | HTTP | internal | SOURCE | SOURCE |
| experience-bff | tshepo-audit-service | HTTP | internal | SOURCE | SOURCE |
| experience-bff | keycloak | HTTP | admin/client credentials | RUNTIME KEYCLOAK_URL | SOURCE+RUNTIME |
| tshepo-authz-service | tshepo-consent-service /v1/consent/evaluate | HTTP POST | none (broken) | SOURCE ConsentClient | SOURCE; DISCONNECTED live path |
| tshepo-authz-service | OPA | — | — | RUNTIME opaMode=OFF | DISCONNECTED |
| mvumo-service | tshepo-consent-service POST /v1/consent | HTTP | JWT|CC | SOURCE TshepoConsentClient | SOURCE |
| pct-service | oros / clinical-knowledge | HTTP | optional CC | SOURCE | SOURCE |
| domain services | Kafka | PLAINTEXT | none | RUNTIME | ABSENT auth |
| Kafka consumers | domain services | internal | none | SOURCE outbox pattern | SOURCE; UNKNOWN per-consumer matrix |
| CronJob/Job workers | domain APIs / DB | mixed | UNKNOWN | INSUFFICIENT_EVIDENCE per job | UNKNOWN |
| All pods | each other | HTTP | none / shared SA=default | RUNTIME 0 NetPol | BYPASSABLE lateral |

## Isolated / special nodes

- **envoy** Deployment exists but has **no IngressRoute** and ConfigMap has extAuthz disabled — isolated from live trust path.
- **Jobs** (completed/failed seed/migrate jobs) are included as nodes above; they are not persistent PEPs.
- **matcher-engine** is the only Impilo image still tag-pinned (`:preview`).

## Coverage statement

Every Deployment (113), CronJob (2), and Job (14) present in `impilo-full-preview` at `2026-08-01T09:42:27Z` appears as a node above. StatefulSet/DaemonSet count was 0. Edge evidence is incomplete for per-consumer Kafka and per-Job callers — those edges remain **UNKNOWN** rather than invented.
