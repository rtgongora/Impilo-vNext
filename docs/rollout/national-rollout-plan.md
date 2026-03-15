# National Rollout Plan — Impilo vNext

> Wave 24 Deliverable | Status: Draft | Date: 2026-03-15

## 1. Rollout Strategy

### 1.1 Guiding Principles

1. **Trust-first**: Every site must pass TSHEPO trust-chain verification before going live.
2. **Ring-aligned**: Services deploy in ring order (0 → 1 → 2 → Outer); no site skips rings.
3. **Offline-ready**: Tier 3/4 sites must prove offline reconciliation before cutover.
4. **Reversible**: Every phase includes a rollback window; no big-bang cutovers.
5. **Data-sovereign**: PII stays in VITO at the site/pod level; only CPIDs flow to BUTANO (SHR).

### 1.2 Phase Model

| Phase | Name | Sites | Duration | Entry Criteria | Exit Criteria |
|-------|------|-------|----------|---------------|---------------|
| 0 | **Internal Validation** | 1 staging + 1 dev pod | 4 weeks | All Wave 19–23 exit criteria met | Smoke tests pass, federation sync verified, DR failover tested |
| 1 | **Controlled Pilot** | 3 sites (1× Tier 1, 1× Tier 2, 1× Tier 3) | 8 weeks | Phase 0 exit, site readiness assessments pass, Fundo-600 champions trained | ≥ 95% uptime, L1/L2 support proven, clinical workflow sign-off |
| 2 | **Early Adopter** | 10–15 sites across 2 provinces | 12 weeks | Phase 1 exit, help desk scaled, release trains running | All rings deployed, CAB process running, error budgets holding |
| 3 | **Majority Rollout** | 50–100 sites (remaining Tier 1/2) | 16 weeks | Phase 2 exit, provincial IT buy-in, training pipeline at capacity | Provincial federation mesh operational, ≤ P2 open defects |
| 4 | **Full Scale** | All remaining sites (Tier 3/4 focus) | 20 weeks | Phase 3 exit, offline-sync proven at scale, mobile device fleet deployed | National coverage target met, steady-state ops model running |

### 1.3 Province Sequencing

> **ASSUMPTION**: Province sequencing is not defined in the repository. The following is a template to be completed with Ministry input.

| Cohort | Province(s) | Phase Entry | Rationale |
|--------|------------|-------------|-----------|
| A | _[TBD — pilot province]_ | Phase 1 | Strongest IT infrastructure, willing clinical leadership |
| B | _[TBD — 2 provinces]_ | Phase 2 | Geographic diversity, mix of urban/rural |
| C | _[TBD — 3–4 provinces]_ | Phase 3 | Scale testing, federation mesh stress |
| D | _[TBD — remaining]_ | Phase 4 | Full national coverage |

## 2. Rollout by Service Ring

### 2.1 Ring 0 — Kernel (Trust & Registry)

| Service | Port | Rollout Order | Rationale |
|---------|------|--------------|-----------|
| tshepo-authz-service | 8081 | First | All other services depend on trust decisions |
| tshepo-identity-service | 8181 | First | CPID resolution required for patient operations |
| tshepo-keys-service | 8184 | First | Signing keys needed for audit chain |
| tshepo-audit-service | 8183 | First | Audit ledger must be recording from day 1 |
| tshepo-consent-service | 8182 | First | Consent checks gate clinical data access |
| tshepo-offline-service | 8185 | First | O-CPID issuance needed for Tier 3/4 |
| vito-service | 8082 | Second | Client registry; depends on TSHEPO trust chain |
| varapi-service | 8083 | Second | Provider registry |
| tuso-service | 8084 | Second | Facility registry, hierarchy |
| zibo-service | 8085 | Second | Terminology; must be loaded before clinical services |
| msika-service | 8086 | Second | Product/formulary catalog |
| butano-service | 8090 | Third | SHR; CPID-only, requires VITO operational |
| mushex-service | 8087 | Third | Finance engine; can follow registries |

**Ring 0 deployment window**: Per-site, completed before any Ring 1 service is enabled.

### 2.2 Ring 1 — Clinical Execution

| Service | Port | Dependencies | Go-Live Trigger |
|---------|------|-------------|-----------------|
| pct-service | 8088 | VITO, TUSO, ZIBO, BUTANO | Ring 0 verified, clinical staff Fundo-200 complete |
| oros-service | 8089 | PCT, ZIBO, BUTANO | PCT operational |
| pharmacy-service | 8096 | OROS, MSIKA, inventory-service | OROS order flow verified |
| costing-engine-service | 8101 | MSIKA, PCT | Tariff rules loaded |
| inpatient-service | 8120 | PCT, TUSO | Bed/ward data loaded |
| coverage-service | 8140 | VITO, MUSHEX | Payer configuration loaded |
| channels-service | 8130 | VITO, notification-service | SMS/WhatsApp gateway configured |
| msika-flow-service | 8100 | MSIKA, VITO | Vendor catalog loaded |

### 2.3 Ring 2 — Platform & Integration

| Service | Port | Deployment Mode |
|---------|------|----------------|
| integration-hub | 8110 | Deploy with Ring 0; routes activated per ring |
| notification-service | 8111 | Deploy with Ring 0; templates loaded per phase |
| search-service | 8120 | Deploy after Ring 1; indexes built post-data-load |
| reporting-service | 8160 | Deploy in Phase 2+; dashboards per site tier |
| surveillance-service | 8118 | Deploy in Phase 2+; eIDSR config per province |
| data-pipeline-service | 8140 | Deploy in Phase 2+; NDR feeds activated per province |
| fhir-gateway-service | 8113 | Deploy when external HIE integration required |
| offline-sync-service | 8115 | Deploy for Tier 3/4 sites; CRDT reconciliation |

### 2.4 Outer Ring — Experience Layer

| Application | Port | Deployment Trigger |
|-------------|------|--------------------|
| one-ui-shell | 3000 | Ring 1 services operational at site |
| ehr | 3002 | PCT + OROS operational |
| portal | 3003 | VITO + channels-service operational |
| ops-console | 3001 | Ring 0 operational (admin access) |
| pharmacy-web | 3010 | pharmacy-service operational |
| pct-web | 3007 | PCT operational |
| oros-web | 3009 | OROS operational |

## 3. Cutover Process Per Site

### 3.1 Pre-Cutover (T-14 to T-1 days)

| Day | Activity | Owner |
|-----|----------|-------|
| T-14 | Site readiness assessment complete (see site-readiness-checklist.md) | Rollout Team |
| T-14 | Hardware deployed and burn-in tested | Infrastructure Team |
| T-10 | Ring 0 services deployed and verified | Platform Team |
| T-10 | Terminology packs loaded (ZIBO) | Clinical Informatics |
| T-7 | Registry data loaded (VITO, VARAPI, TUSO) | Data Migration Team |
| T-7 | Ring 1 services deployed and smoke-tested | Platform Team |
| T-5 | Fundo-200/300 training delivered to site staff | Training Team |
| T-3 | End-to-end workflow test (patient registration → visit → orders → results) | QA + Clinical Champions |
| T-1 | Go/no-go decision meeting | Rollout Lead + Site Lead |

### 3.2 Cutover Day (T-0)

| Step | Activity | Duration | Rollback Trigger |
|------|----------|----------|------------------|
| 1 | Legacy system read-only mode | 1 hour | — |
| 2 | Final data delta migration | 2–4 hours | Migration errors > 0.1% |
| 3 | Impilo service health check (all rings) | 30 min | Any Ring 0 service unhealthy |
| 4 | Trust-chain verification (TSHEPO ext_authz end-to-end) | 15 min | Authorization failures |
| 5 | Clinical champion walkthrough (3 test patients) | 1 hour | Workflow blockers |
| 6 | Go-live announcement | — | — |
| 7 | Hypercare: on-site support active | 8 hours | — |

### 3.3 Post-Cutover (T+1 to T+14)

| Period | Activity |
|--------|----------|
| T+1 to T+3 | On-site support team present; daily standup with site lead |
| T+1 to T+7 | Daily error-budget review; auto-rollback if SLO breach |
| T+7 | First-week retrospective; defect triage |
| T+14 | Hypercare exit; transition to standard L1/L2 support |

## 4. Rollback Strategy

### 4.1 Rollback by Ring

| Ring | Rollback Method | Data Handling | Max Time |
|------|----------------|---------------|----------|
| Ring 0 | Helm rollback to previous chart version | Event outbox replay from Kafka | 72 hours |
| Ring 1 | Helm rollback + feature flag disable | Clinical data preserved in BUTANO; delta reconciliation | 48 hours |
| Ring 2 | Helm rollback or pod scale-to-zero | Idempotent replay from event log | 24 hours |
| Outer | Feature flag off; instant revert to previous UI bundle | No data impact (BFF-backed) | Instant |

### 4.2 Full Site Rollback

If a site requires complete rollback to legacy:

1. Impilo services set to read-only (feature flag).
2. Delta data exported from BUTANO/VITO in FHIR Bundle format.
3. Legacy system restored from pre-cutover snapshot.
4. Delta data imported into legacy (manual reconciliation for clinical records).
5. Site reclassified and re-queued for next phase.

> **Expectation**: Full site rollback is a last resort. Ring-level rollback should resolve most issues.

## 5. Risk Register

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Connectivity failure during cutover | Medium | High | Offline-first mode for Tier 3/4; cutover only during stable connectivity for Tier 1/2 |
| Insufficient clinical champion buy-in | Medium | High | Fundo-600 Train-the-Trainer; identify 2+ champions per site minimum |
| Data migration quality issues | Medium | High | Pre-cutover data quality audit; reconciliation reports at T+1 |
| Ring 0 regression during rollout | Low | Critical | Monthly release cadence; 72-hour rollback window; canary site first |
| Power failure during cutover | Low | High | UPS + generator required; cutover only when power verified |
| Help desk overwhelmed at scale | Medium | Medium | Phase-gated rollout; scale L1/L2 ahead of each phase |

## 6. Governance

| Meeting | Cadence | Attendees | Purpose |
|---------|---------|-----------|---------|
| Rollout Standup | Daily (during active phase) | Rollout lead, site leads, platform lead | Status, blockers, go/no-go |
| Phase Gate Review | Per phase boundary | Program director, clinical director, IT director, security officer | Phase exit approval |
| CAB | Weekly (see change-control-and-cab.md) | CAB members | Change approval |
| Retrospective | End of each phase | All rollout team | Lessons learned, process improvement |

## 7. Success Metrics

| Metric | Target | Measurement |
|--------|--------|-------------|
| Site go-live success rate | ≥ 90% (no rollback needed) | Per-site go/no-go outcome |
| Time to go-live per site (Tier 1/2) | ≤ 14 calendar days from assessment to live | Project tracking |
| Time to go-live per site (Tier 3/4) | ≤ 7 calendar days | Project tracking |
| Post-cutover P1 incidents | ≤ 1 per site in first 14 days | Incident tracking |
| SLO compliance during hypercare | ≥ 99.5% for Ring 0 services | SLO dashboard |
| Training completion rate | ≥ 95% of designated staff before go-live | Fundo LMS |
| User satisfaction (post-go-live survey) | ≥ 3.5/5.0 at T+14 | Survey |
