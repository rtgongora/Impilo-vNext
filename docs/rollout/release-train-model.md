# Release Train Model — Impilo vNext

> Wave 24 Deliverable | Status: Draft | Date: 2026-03-15

## 1. Overview

Impilo vNext uses a **ring-based release train** model aligned with the platform's service architecture. Each ring has its own release cadence, stability requirements, and rollback policy. This document defines the operational model for shipping software to production sites.

## 2. Ring Definitions and Cadence

| Ring | Services | Release Cadence | Freeze Window | Rollback Window | Stability Tier |
|------|----------|----------------|---------------|-----------------|---------------|
| **Ring 0** | TSHEPO cluster (authz, identity, consent, audit, keys, offline), VITO, VARAPI, TUSO, ZIBO, MSIKA, BUTANO, MUSHEX | Monthly | 48h before + 72h after release | 72 hours | Critical — zero tolerance for regression |
| **Ring 1** | PCT, OROS, pharmacy-service, costing-engine, inpatient-service, coverage-service, channels-service, msika-flow-service | Bi-weekly | 24h before + 48h after release | 48 hours | High — ≤ 1 P2 allowed during rollback window |
| **Ring 2** | integration-hub, notification-service, search-service, reporting-service, surveillance-service, data-pipeline-service, fhir-gateway-service, offline-sync-service, rules-service, forms-service, jobs-service | Weekly | 12h before release | 24 hours | Standard |
| **Outer** | one-ui-shell, ehr, portal, ops-console, pharmacy-web, pct-web, oros-web, all UI apps | Continuous (feature flags) | None | Instant (flag off) | Standard — flagged features can be killed instantly |

## 3. Release Process — Step by Step

### 3.1 Ring 0 Release Process

```
Week 1 (Code Complete):
  ├── Feature branches merged to main (PR approved, CI green)
  ├── All unit tests pass
  ├── All integration tests pass
  └── Golden contract tests pass (libs/contract-tests)

Week 2 (Qualification):
  ├── Automated security scan (no new Critical/High CVEs)
  ├── Performance baseline test (±10% of established baseline)
  ├── Release candidate tagged: v{service}-{YYYY}.{MM}.{patch}-rc.{n}
  └── Deploy to staging environment (auto)

Week 3 (Canary):
  ├── Staging smoke tests pass (scripts/smoke/smoke.sh)
  ├── CAB approval (Major change category — see change-control-and-cab.md)
  ├── Deploy to canary site (1 Tier 1 site)
  ├── 72-hour observation period
  │   ├── Error budget monitored (must not breach)
  │   ├── Latency p99 monitored (must not regress > 10%)
  │   └── Audit chain integrity verified (tshepo-audit-service)
  └── Canary sign-off by Platform Lead

Week 4 (Progressive Rollout):
  ├── 10% of sites → 24h observation
  ├── 25% of sites → 24h observation
  ├── 50% of sites → 24h observation
  ├── 100% of sites
  ├── Release tagged: v{service}-{YYYY}.{MM}.{patch}
  └── Post-release verification
```

### 3.2 Ring 1 Release Process

```
Day 1–2 (Code Complete):
  ├── Feature branches merged to main
  ├── All tests pass (unit + integration + contract)
  └── Release candidate tagged

Day 3–4 (Qualification):
  ├── Security scan clean
  ├── Staging deployment + smoke tests
  └── CAB approval (Normal change category)

Day 5–7 (Canary + Rollout):
  ├── Deploy to canary site
  ├── 48-hour observation
  ├── Progressive rollout: 25% → 50% → 100%
  └── Post-release verification
```

### 3.3 Ring 2 Release Process

```
Day 1 (Code Complete):
  ├── Feature branches merged to main
  ├── Tests pass
  └── Release candidate tagged

Day 2 (Qualification + Deploy):
  ├── Staging deployment + smoke tests
  ├── CAB approval (Standard — pre-approved category)
  └── Deploy to canary site

Day 3–5 (Rollout):
  ├── 24-hour canary observation
  ├── Progressive rollout: 50% → 100%
  └── Post-release verification
```

### 3.4 Outer Ring Release Process

```
Continuous:
  ├── Feature branch merged to main (PR approved, CI green)
  ├── Automated build → container image tagged
  ├── Deploy to staging (auto)
  ├── Staging E2E tests pass
  ├── Feature flag enabled for canary users (staff accounts)
  ├── Monitor error rates for 1 hour
  ├── Feature flag enabled for all users
  └── If error rate spikes → flag off (instant rollback)
```

## 4. Release Gates

### 4.1 Gate Matrix

| Gate | Ring 0 | Ring 1 | Ring 2 | Outer |
|------|--------|--------|--------|-------|
| G1: Unit tests pass | Required | Required | Required | Required |
| G2: Integration tests pass | Required | Required | Required | — |
| G3: Golden contract tests pass | Required | Required | — | — |
| G4: Security scan clean (Critical/High) | Required | Required | Required | Required |
| G5: Performance baseline (±10%) | Required | Required | — | — |
| G6: Staging smoke tests pass | Required | Required | Required | Required |
| G7: CAB approval | Major | Normal | Standard (pre-approved) | — |
| G8: Canary observation period | 72 hours | 48 hours | 24 hours | 1 hour |
| G9: Error budget check | Required | Required | Required | Required |
| G10: Audit chain integrity | Required | — | — | — |

### 4.2 Gate Failure Protocol

| Failure | Action |
|---------|--------|
| G1–G3 fails | Release blocked; defect assigned P1; fix in next sprint |
| G4 fails (new Critical CVE) | Emergency patch; bypass normal cadence (Emergency CAB) |
| G5 fails (performance regression) | Release blocked; performance investigation required |
| G6 fails (smoke test) | Staging investigation; re-deploy or release blocked |
| G7 fails (CAB rejection) | Address CAB concerns; resubmit at next CAB meeting |
| G8 fails (canary issues) | Immediate canary rollback; defect investigation |
| G9 fails (error budget breach) | Automatic rollback triggered; incident review |
| G10 fails (audit chain break) | P1 security incident; all deployments frozen |

## 5. Versioning Convention

### 5.1 Service Version Format

```
v{service-name}-{YYYY}.{MM}.{patch}[-rc.{n}]

Examples:
  v-tshepo-authz-2026.03.1          (production release)
  v-tshepo-authz-2026.03.2-rc.1     (release candidate)
  v-pct-2026.03.4                   (Ring 1 bi-weekly)
  v-integration-hub-2026.W12.1      (Ring 2 weekly)
```

### 5.2 Container Image Tags

```
registry.impilo.health/{service}:{version}
registry.impilo.health/{service}:latest-{ring}

Examples:
  registry.impilo.health/tshepo-authz:2026.03.1
  registry.impilo.health/pct:2026.03.4
  registry.impilo.health/one-ui-shell:2026.03.15-abc1234
```

## 6. Rollback Procedures

### 6.1 Ring 0 Rollback

```bash
# 1. Identify the previous known-good version
helm history tshepo-authz -n ring0

# 2. Roll back Helm release
helm rollback tshepo-authz {revision} -n ring0 --wait --timeout 10m

# 3. Verify service health
kubectl get pods -n ring0 -l app=tshepo-authz
curl -f https://{site}/internal/v1/tshepo-authz/health

# 4. Verify trust chain (critical for Ring 0)
curl -f https://{site}:10000/internal/v1/tshepo-authz/health

# 5. Verify audit chain integrity
# Query tshepo-audit-service for any chain breaks during rollback window

# 6. Notify CAB of rollback (within 1 hour)
```

### 6.2 Ring 1 Rollback

```bash
# 1. Roll back Helm release
helm rollback {service} {revision} -n ring1 --wait --timeout 5m

# 2. Verify service health + dependent services
kubectl get pods -n ring1
curl -f https://{site}/internal/v1/{service}/health

# 3. Check for in-flight clinical data
# Query event_outbox for unpublished events; ensure Kafka consumers replay
```

### 6.3 Outer Ring Rollback

```bash
# Instant: disable feature flag
curl -X PATCH https://{site}/internal/v1/feature-flags/{flag-id} \
  -H "Content-Type: application/json" \
  -d '{"enabled": false}'

# Or: roll back to previous UI bundle
helm rollback {ui-app} {revision} -n outer --wait --timeout 2m
```

## 7. Change Windows

### 7.1 Permitted Deployment Windows

| Ring | Permitted Window | Timezone | Blackout Periods |
|------|-----------------|----------|-----------------|
| Ring 0 | Tuesday 06:00–10:00 | Site local time | Month-end (last 3 business days), public holidays |
| Ring 1 | Tuesday/Thursday 06:00–10:00 | Site local time | Month-end (last 2 business days), public holidays |
| Ring 2 | Monday–Thursday 06:00–14:00 | Site local time | Public holidays |
| Outer | Monday–Friday 06:00–18:00 | Site local time | None (feature-flagged) |
| Emergency | Any time | — | None |

### 7.2 Rationale

- **Early morning**: Minimizes clinical disruption; lowest patient volume.
- **Tuesday start**: Avoids Monday (staff catching up) and Friday (no next-day support).
- **Month-end blackout**: Protects financial close processes (MUSHEX, costing-engine).

## 8. Release Train Calendar (Template)

> **ASSUMPTION**: Specific dates TBD based on Phase 1 go-live. Below is a template for a single month.

| Week | Ring 0 | Ring 1 | Ring 2 | Outer |
|------|--------|--------|--------|-------|
| W1 | Code complete | Sprint start | Release | Continuous |
| W2 | Qualification + security scan | Code complete | Release | Continuous |
| W3 | Canary deployment | Qualification + canary | Release | Continuous |
| W4 | Progressive rollout | Progressive rollout | Release | Continuous |

## 9. Hotfix Process

For defects that cannot wait for the next scheduled release:

| Severity | Process | Approval | Deployment |
|----------|---------|----------|------------|
| P1 (service down) | Emergency patch branch from release tag | CAB chair + Platform Lead (phone/chat) | Immediate; skip canary if CAB approves |
| P2 (degraded) | Hotfix branch; fast-track through gates G1–G6 | Normal CAB (expedited) | Next available window |
| P3/P4 | Normal release train | Standard process | Next scheduled release |

## 10. Coordination with Federation

For sites running as federation pods (Tier 1/2):

1. **Pod-local services** (Ring 0/1) are updated per the ring schedule above.
2. **National spine services** follow their own Ring 0 schedule.
3. **Federation protocol changes** require coordinated deployment:
   - National spine updated first.
   - Pod compatibility window: pods must support both old and new protocol for ≥ 1 release cycle.
   - Pod rollout follows the progressive model (10% → 25% → 50% → 100%).
4. **Schema changes** (Kafka event schemas via schema-registry-service) use backward-compatible evolution only; breaking changes require a new topic version.
