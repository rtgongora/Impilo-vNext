# Training Alignment with Impilo Fundo — Impilo vNext

> Wave 24 Deliverable | Status: Draft | Date: 2026-03-15

## 1. Purpose

Impilo Fundo ("Fundo" = "to learn/teach" in isiZulu) is the training program for Impilo vNext. This document maps Fundo training modules to platform services, defines prerequisites and sequencing, establishes delivery logistics, and ties training completion to site readiness gates.

## 2. Fundo Module Structure

### 2.1 Module Overview

| Module | Title | Audience | Duration | Delivery Mode | Prerequisite |
|--------|-------|----------|----------|--------------|-------------|
| **Fundo-100** | Platform Overview | All staff at rollout sites | 2 hours | Online (self-paced) | None |
| **Fundo-200** | Clinical Workflows | Clinical staff (nurses, doctors, pharmacists, lab techs) | 1 day (8 hours) | In-person or virtual instructor-led | Fundo-100 |
| **Fundo-300** | Administration & Configuration | Facility managers, admin staff | 1 day (8 hours) | In-person instructor-led | Fundo-100 |
| **Fundo-400** | Offline Operations | Staff at Tier 3/4 (edge) sites | 0.5 day (4 hours) | In-person at site | Fundo-100 + Fundo-200 |
| **Fundo-500** | IT Operations | IT support staff, system administrators | 2 days (16 hours) | In-person instructor-led | Fundo-100 |
| **Fundo-600** | Train-the-Trainer | Clinical champions (super-users) | 3 days (24 hours) | In-person intensive | Fundo-100 + Fundo-200 + Fundo-300 |

### 2.2 Certification

| Module | Assessment | Pass Criteria | Certificate Validity |
|--------|-----------|--------------|---------------------|
| Fundo-100 | Online quiz (20 questions) | ≥ 80% correct | 2 years |
| Fundo-200 | Practical exam: complete 3 clinical workflows end-to-end | All 3 workflows completed correctly | 1 year |
| Fundo-300 | Practical exam: perform 5 admin tasks | All 5 tasks completed correctly | 1 year |
| Fundo-400 | Practical exam: operate offline for 1 hour, sync successfully | Offline workflow + sync without data loss | 1 year |
| Fundo-500 | Written + practical: troubleshoot 3 scenarios | ≥ 80% written + all 3 scenarios resolved | 1 year |
| Fundo-600 | Teach-back: deliver a 30-min training session to peers | Peer + instructor evaluation ≥ 4/5 | 1 year |

## 3. Module-to-Service Mapping

### 3.1 Fundo-100: Platform Overview

| Topic | Duration | Services/Components Covered |
|-------|----------|----------------------------|
| What is Impilo vNext? Architecture overview | 20 min | All (conceptual) |
| Trust-first model: why every request is verified | 15 min | tshepo-authz-service (8081), Envoy (10000) |
| Patient identity and privacy | 15 min | vito-service (8082), tshepo-consent-service (8182) |
| Navigating the UI shell | 30 min | one-ui-shell (3000) — Work, Professional, Life zones |
| Logging in, roles, and permissions | 15 min | Keycloak (8080), tshepo-authz-service |
| Getting help: in-app help, FAQ, escalation | 15 min | Support channels, L0/L1 process |
| Assessment quiz | 10 min | — |

### 3.2 Fundo-200: Clinical Workflows

| Topic | Duration | Services/Components Covered | UI Application |
|-------|----------|-----------------------------|----------------|
| Patient registration and search | 1 hour | vito-service (8082), tshepo-identity-service (8181) | one-ui-shell (3000) |
| Visit workflow: check-in → triage → consultation → discharge | 2 hours | pct-service (8088), tuso-service (8084) | pct-web (3007), ehr (3002) |
| Clinical documentation and coding | 1 hour | butano-service (8090), zibo-service (8085) | ehr (3002) |
| Orders and results: labs, imaging, referrals | 1.5 hours | oros-service (8089), pacs-adapter-service (8114) | oros-web (3009) |
| Pharmacy: prescriptions, dispensing, stock check | 1 hour | pharmacy-service (8096), inventory-service (8098), msika-service (8086) | pharmacy-web (3010) |
| Inpatient: admission, bed management, transfer, discharge | 1 hour | inpatient-service (8120), pct-service (8088) | ehr (3002) |
| Practical assessment: 3 end-to-end workflows | 0.5 hour | All above | All above |

### 3.3 Fundo-300: Administration & Configuration

| Topic | Duration | Services/Components Covered | UI Application |
|-------|----------|-----------------------------|----------------|
| User management: create, roles, deactivate | 1.5 hours | Keycloak (8080), tshepo-authz-service (8081) | ops-console (3001) |
| Facility configuration: departments, services, operating hours | 1 hour | tuso-service (8084) | ops-console (3001) |
| Provider management: credentialing, privileging | 1 hour | varapi-service (8083) | ops-console (3001) |
| Terminology and formulary management | 1 hour | zibo-service (8085), msika-service (8086) | zibo-web (3008), msika-web (3019) |
| Report generation and dashboards | 1 hour | reporting-service (8176) | ops-console (3001) |
| Consent policy management | 0.5 hour | tshepo-consent-service (8182) | ops-console (3001) |
| Billing and tariff configuration | 1 hour | costing-engine-service (8101), mushex-service (8087) | costa-console (3015) |
| Practical assessment: 5 admin tasks | 1 hour | All above | All above |

### 3.4 Fundo-400: Offline Operations

| Topic | Duration | Services/Components Covered |
|-------|----------|-----------------------------|
| When and why offline mode activates | 30 min | tshepo-offline-service (8185), offline-sync-service (8115) |
| Working offline: what you can and cannot do | 30 min | Edge data packs, O-CPID issuance |
| Patient registration offline (O-CPID) | 45 min | tshepo-offline-service, vito-service (reconciliation) |
| Clinical documentation offline | 45 min | CRDT-based local storage, butano-service (sync) |
| Reconnection and sync: what happens, what to check | 30 min | offline-sync-service (8115), conflict resolution |
| Practical assessment: 1-hour offline simulation | 1 hour | All offline components |

### 3.5 Fundo-500: IT Operations

| Topic | Duration | Services/Components Covered |
|-------|----------|-----------------------------|
| **Day 1** | | |
| Platform architecture deep-dive | 1.5 hours | All services, rings, trust model |
| Infrastructure: K8s, Helm, PostgreSQL, Redis, Kafka | 2 hours | infra/ configs, Helm charts |
| Monitoring and alerting: Grafana, Prometheus, Loki | 1.5 hours | observability-service (8210), ops-instrumentation lib |
| Health checks and service status | 1 hour | `/internal/v1/{service}/health` endpoints |
| Envoy gateway: routing, ext_authz, troubleshooting | 1 hour | Envoy (10000/9901), envoy.yaml |
| End-of-day lab: deploy a service update using Helm | 1 hour | Helm charts |
| **Day 2** | | |
| Backup and restore: PostgreSQL, Kafka, MinIO | 2 hours | scripts/dr/backup-all.sh, DR runbooks |
| Security operations: certificates, key rotation, audit | 1.5 hours | tshepo-keys-service (8184), tshepo-audit-service (8183) |
| Kafka operations: topics, consumer groups, lag monitoring | 1.5 hours | Kafka (9092), schema-registry-service |
| Incident response: triage, escalation, war room | 1.5 hours | Support operating model, PagerDuty |
| Runbook walkthrough: 3 common failure scenarios | 1 hour | Service runbooks |
| Written + practical assessment | 0.5 hour | — |

### 3.6 Fundo-600: Train-the-Trainer

| Topic | Duration | Focus |
|-------|----------|-------|
| **Day 1: Platform Mastery** | | |
| Advanced clinical workflows (edge cases, error handling) | 3 hours | All Ring 1 services |
| Cross-service scenarios (multi-department patient journey) | 2 hours | PCT → OROS → Pharmacy → MUSHEX |
| Data quality: duplicate detection, merge, consent | 1.5 hours | VITO dedup, TSHEPO consent |
| Troubleshooting: common user errors and how to guide users | 1.5 hours | Known-issue database |
| **Day 2: Training Skills** | | |
| Adult learning principles | 2 hours | — |
| Facilitation techniques for clinical environments | 2 hours | — |
| Creating site-specific training materials | 2 hours | Fundo templates |
| Managing resistance to change | 2 hours | — |
| **Day 3: Practice and Assessment** | | |
| Prepare a 30-minute training session | 3 hours | Trainee choice of topic |
| Deliver training to peer group | 3 hours | Assessed by instructor + peers |
| Feedback and certification | 2 hours | — |

## 4. Training Environment

### 4.1 Sandbox Cluster

A dedicated sandbox environment mirrors production with synthetic data:

| Component | Configuration |
|-----------|--------------|
| Namespace | `impilo-sandbox` |
| Data | 500 synthetic patients (VITO), 50 providers (VARAPI), 10 facilities (TUSO) |
| Services | All Ring 0 + Ring 1 services deployed |
| UI | All UI apps accessible at `sandbox.impilo.health` |
| Reset schedule | Daily at 02:00 — full data reset to baseline |
| Access | Fundo trainee accounts with role-based access matching their real roles |

### 4.2 Sandbox Accounts

| Role | Username Pattern | Keycloak Role | Access |
|------|-----------------|---------------|--------|
| Doctor | `fundo.doctor.{nn}` | `ROLE_CLINICIAN` | ehr, pct-web, oros-web |
| Nurse | `fundo.nurse.{nn}` | `ROLE_CLINICIAN` | ehr, pct-web |
| Pharmacist | `fundo.pharmacist.{nn}` | `ROLE_PHARMACIST` | pharmacy-web, oros-web |
| Admin | `fundo.admin.{nn}` | `ROLE_FACILITY_ADMIN` | ops-console |
| IT | `fundo.it.{nn}` | `ROLE_IT_ADMIN` | ops-console, kubectl access |
| Champion | `fundo.champion.{nn}` | `ROLE_CLINICIAN` + `ROLE_TRAINER` | All UIs |

## 5. Training Sequencing per Rollout Phase

### 5.1 Training Timeline Relative to Site Go-Live

| Timing | Activity | Module |
|--------|----------|--------|
| T-30 days | Fundo-600 (Train-the-Trainer) for site champions | Fundo-600 |
| T-21 days | Fundo-100 (Platform Overview) — all staff, self-paced | Fundo-100 |
| T-14 days | Fundo-500 (IT Operations) for site IT staff | Fundo-500 |
| T-10 days | Fundo-300 (Admin) for facility managers | Fundo-300 |
| T-7 days | Fundo-200 (Clinical Workflows) for clinical staff | Fundo-200 |
| T-5 days | Fundo-400 (Offline) for Tier 3/4 staff | Fundo-400 |
| T-3 days | End-to-end rehearsal with trained staff | — |
| T+7 days | Refresher session (champions lead, address go-live questions) | — |
| T+30 days | Post-go-live assessment (identify knowledge gaps) | — |

### 5.2 Training Capacity Model

| Phase | Sites | Champions to Train | Clinical Staff | IT Staff | Training Team Required |
|-------|-------|-------------------|----------------|----------|----------------------|
| Phase 1 (Pilot) | 3 | 6 | ~50 | ~6 | 2 trainers |
| Phase 2 (Early Adopter) | 10–15 | 20–30 | ~200 | ~20 | 4 trainers |
| Phase 3 (Majority) | 50–100 | 100–200 | ~1,000 | ~100 | 8 trainers + champion cascade |
| Phase 4 (Full Scale) | All | 400+ | ~5,000+ | ~500+ | 10 trainers + champion cascade |

> At Phase 3+, the model shifts from centralized training to **champion-led cascade**: Fundo-600 graduates deliver Fundo-200/300 at their sites, supervised by the central training team.

## 6. Training-to-Readiness Gate Integration

Training completion is a **mandatory prerequisite** in the site readiness checklist (see site-readiness-checklist.md):

| Checklist Item | Module Required | Minimum Pass |
|---------------|----------------|-------------|
| S-02: Clinical champions (≥ 2 per site) | Fundo-600 certified | Both champions pass |
| S-03: Facility manager trained | Fundo-300 certified | Manager passes |
| S-04: IT staff trained (Tier 1/2) | Fundo-500 certified | ≥ 1 IT staff passes |
| General: ≥ 80% of clinical staff trained | Fundo-200 certified | 80% completion rate |
| General: ≥ 90% of all staff aware | Fundo-100 certified | 90% completion rate |

**A site cannot proceed to cutover (T-0) if training gates are not met.**

## 7. Training Content Maintenance

### 7.1 Update Triggers

| Trigger | Action | Owner |
|---------|--------|-------|
| Ring 0/1 service UI change | Update Fundo-200/300 screenshots and workflows | Training team + Experience engineering |
| New service deployed | Create addendum or new module section | Training team + service owner |
| Post-go-live knowledge gap identified | Update relevant module + notify champions | Training team |
| Fundo certification expiry (annual) | Recertification required; updated content | Training team |
| Major release (new Ring 0 feature) | Fundo-100 refresher module published | Training team |

### 7.2 Content Versioning

| Module | Version Format | Repository Location |
|--------|---------------|-------------------|
| Fundo-100 | `fundo-100-v{YYYY}.{quarter}` | _[TBD — LMS or docs repo]_ |
| Fundo-200 | `fundo-200-v{YYYY}.{quarter}` | _[TBD]_ |
| Fundo-300 | `fundo-300-v{YYYY}.{quarter}` | _[TBD]_ |
| Fundo-400 | `fundo-400-v{YYYY}.{quarter}` | _[TBD]_ |
| Fundo-500 | `fundo-500-v{YYYY}.{quarter}` | _[TBD]_ |
| Fundo-600 | `fundo-600-v{YYYY}.{quarter}` | _[TBD]_ |

## 8. Training Metrics

| Metric | Target | Source |
|--------|--------|--------|
| Fundo-100 completion rate (per site, pre-go-live) | ≥ 90% | LMS |
| Fundo-200 pass rate | ≥ 85% on first attempt | LMS |
| Fundo-600 certification rate | 100% of designated champions | LMS |
| Time from training to go-live | ≤ 30 days (to retain knowledge) | Project tracker |
| Post-go-live support ticket rate (trained vs. untrained) | Trained staff ≤ 50% of untrained rate | Ticket system correlation |
| Champion-led session quality score | ≥ 4.0/5.0 (peer evaluation) | Survey |
| Training content currency | Updated within 30 days of service change | Content review log |

## 9. Responsibilities

| Role | Responsibilities |
|------|-----------------|
| **Training Program Lead** | Overall Fundo program management, trainer hiring, content strategy |
| **Training Developers** | Create and maintain module content, assessments, sandbox data |
| **Trainers (central)** | Deliver Fundo-500 and Fundo-600; supervise champion cascade |
| **Clinical Champions (Fundo-600 graduates)** | Deliver Fundo-200/300 at their sites; provide L1 support; feedback loop to training team |
| **Facility Managers** | Ensure staff attend training; enforce completion gates; report gaps |
| **Service Owners (engineering)** | Provide technical input for training content; review accuracy |
| **Rollout Lead** | Enforce training gates in site readiness checklist; schedule training relative to go-live |
