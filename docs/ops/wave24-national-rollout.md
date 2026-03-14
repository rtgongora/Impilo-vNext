# Wave 24 — National Rollout Program

> Status: Not Started | Date: 2026-03-14

## Goal

Adoption at scale without chaos. Sites are assessed for readiness, staff are trained, release trains are established, and change control processes are running.

**Note**: This wave is non-code heavy. Primary deliverables are operational processes, documentation, and organizational readiness.

## Prerequisites

| Wave | Dependency |
|------|-----------|
| 19 | Production readiness gate (SLOs, alerting, security posture verified) |
| 20 | Disaster recovery (backup/restore, RPO/RTO measured, failover tested) |
| 21 | Federation pilot (pod registration, authority enforcement working) |
| 22 | Offline pilot (edge workflow proven, reconciliation working) |
| 23 | Dual-mode ecosystem (developer portal, partner onboarding working) |

## Deliverables

### 1. Site Readiness + Connectivity + Power

#### Site Readiness Assessment Checklist

| Category | Check | Minimum Requirement |
|----------|-------|-------------------|
| **Connectivity** | Primary internet link | ≥ 10 Mbps symmetric, ≥ 99% monthly uptime |
| **Connectivity** | Backup internet link | ≥ 5 Mbps, different ISP/last mile |
| **Connectivity** | LAN infrastructure | Managed switches, ≥ 100 Mbps to endpoints |
| **Connectivity** | WiFi coverage | Full clinical area coverage, WPA3 |
| **Power** | Primary power | Grid or generator with ≥ 99% uptime |
| **Power** | UPS | ≥ 30 min runtime for server + network |
| **Power** | Generator (Tier 1/2 sites) | Auto-start, ≥ 8h fuel capacity |
| **Hardware** | Server (if Pod site) | Per Pod hardware spec |
| **Hardware** | Client devices | Per client device spec |
| **Hardware** | Printers (if required) | Network-attached, label + A4 |
| **Environment** | Server room | Ventilated, locked, fire suppression |
| **Environment** | Temperature monitoring | ≤ 27°C sustained |
| **Staff** | IT support | ≥ 1 designated IT contact per site |
| **Staff** | Clinical champions | ≥ 2 trained clinical super-users per site |

#### Site Classification

| Tier | Description | Infrastructure | Connectivity Model |
|------|-------------|---------------|-------------------|
| Tier 1 | Provincial Hospital | Full Pod + local DB | Always-on + federation |
| Tier 2 | District Hospital | Full Pod + local DB | Always-on + federation |
| Tier 3 | Primary Health Centre | Thin client | Online-first + offline fallback |
| Tier 4 | Community Outpost | Mobile device only | Offline-first + periodic sync |

#### Verification Checklist

- [ ] Site readiness assessment template finalized
- [ ] Assessment completed for first rollout cohort
- [ ] Connectivity baseline measured per site
- [ ] Power resilience verified per site tier
- [ ] Hardware procurement/deployment plan per site

### 2. Training & Support Operations (Impilo Fundo Alignment)

#### Training Program Structure

| Module | Audience | Duration | Delivery |
|--------|----------|----------|----------|
| **Fundo-100**: Platform Overview | All staff | 2 hours | Online (self-paced) |
| **Fundo-200**: Clinical Workflows | Clinical staff | 1 day | In-person or virtual |
| **Fundo-300**: Admin & Configuration | Facility managers | 1 day | In-person |
| **Fundo-400**: Offline Operations | Edge site staff | 0.5 day | In-person (at site) |
| **Fundo-500**: IT Operations | IT support staff | 2 days | In-person |
| **Fundo-600**: Train-the-Trainer | Clinical champions | 3 days | In-person |

#### Support Operations Model

| Tier | Scope | Response Time | Channel |
|------|-------|--------------|---------|
| L1 | User questions, password resets, basic troubleshooting | ≤ 4 hours | Help desk (phone/chat) |
| L2 | Configuration issues, workflow problems, data queries | ≤ 8 hours | Ticket escalation |
| L3 | Bug investigation, integration issues, performance | ≤ 24 hours | Engineering team |
| L4 | Platform issues, infrastructure, security incidents | Per severity SLA | On-call engineering |

#### Verification Checklist

- [ ] Training modules developed and reviewed by clinical advisors
- [ ] Training environment provisioned (sandbox)
- [ ] Fundo-600 (Train-the-Trainer) delivered to first cohort
- [ ] Help desk operational with L1/L2 staff
- [ ] Escalation paths tested end-to-end

### 3. Release Trains (Ring-Based Rollout)

#### Release Cadence

| Ring | Services | Release Frequency | Stability | Rollback Window |
|------|----------|------------------|-----------|-----------------|
| Ring 0 | TSHEPO, VITO, VARAPI, TUSO, ZIBO | Monthly (slow) | Highest stability | 72 hours |
| Ring 1 | Clinical (MSIKA, UBOMI, PCT, OROS, etc.) | Bi-weekly | High stability | 48 hours |
| Ring 2 | Platform (Integration Hub, Notification, etc.) | Weekly | Standard | 24 hours |
| Outer | UI shells, developer portal | Continuous (with feature flags) | Standard | Instant (feature flag off) |

#### Release Process

```
1. Code merged to main (PR approved, CI green)
2. Automated build → container image tagged
3. Deploy to staging (auto)
4. Staging smoke tests (auto)
5. Release candidate tagged
6. Deploy to canary (1 site, Ring-specific schedule)
7. Canary observation period (Ring-specific duration)
8. Progressive rollout (10% → 25% → 50% → 100%)
9. Post-release verification
10. Rollback if error budget breach detected
```

#### Ring 0 Release Gate

| Gate | Check | Required By |
|------|-------|------------|
| G1 | All unit tests pass | CI |
| G2 | All integration tests pass | CI |
| G3 | Golden contract tests pass | CI |
| G4 | No new critical/high vulnerabilities | Security scan |
| G5 | Performance baseline not regressed (±10%) | Load test |
| G6 | Staging smoke tests pass | Automated |
| G7 | Change advisory board approval | CAB chair |

#### Verification Checklist

- [ ] Release cadence defined per ring
- [ ] CI/CD pipeline supports ring-based deployment
- [ ] Canary deployment mechanism operational
- [ ] Progressive rollout tooling configured
- [ ] Rollback procedure tested per ring

### 4. Change Control/CAB Processes

#### Change Advisory Board (CAB)

| Role | Responsibility |
|------|---------------|
| CAB Chair | Schedule reviews, final approval authority |
| Platform Lead | Technical risk assessment |
| Clinical Safety Officer | Clinical impact assessment |
| Security Lead | Security impact assessment |
| Operations Lead | Operational readiness assessment |
| Site Representative | Site-specific impact assessment |

#### Change Categories

| Category | Approval | Lead Time | Examples |
|----------|----------|-----------|---------|
| Standard | Pre-approved (no CAB) | Same day | Dependency updates, doc changes, config tweaks |
| Normal | CAB review required | 5 business days | New features, schema changes, new services |
| Emergency | CAB chair + 1 approval | Immediate | Security patches, critical bug fixes |
| Major | Full CAB + stakeholder review | 10 business days | Ring 0 changes, federation protocol changes |

#### Change Request Template

```markdown
# Change Request — CR-[YYYY]-[NNN]

## Summary
- **Title**: ___
- **Category**: Standard / Normal / Emergency / Major
- **Ring affected**: 0 / 1 / 2 / Outer
- **Services affected**: ___
- **Requested by**: ___
- **Requested date**: ___
- **Target deployment date**: ___

## Impact Assessment
- **Clinical impact**: None / Low / Medium / High
- **Security impact**: None / Low / Medium / High
- **Performance impact**: None / Low / Medium / High
- **Data impact**: None / Low / Medium / High

## Rollback Plan
- **Rollback procedure**: ___
- **Rollback time estimate**: ___
- **Data rollback required**: Yes / No

## Testing Evidence
- [ ] Unit tests pass
- [ ] Integration tests pass
- [ ] Contract tests pass
- [ ] Load test (if applicable)
- [ ] Security scan clean

## Approvals
- [ ] Platform Lead: _________________ Date: _______
- [ ] Clinical Safety: _________________ Date: _______
- [ ] Security Lead: _________________ Date: _______
- [ ] CAB Chair: _________________ Date: _______
```

#### Verification Checklist

- [ ] CAB charter and membership defined
- [ ] Change categories and approval paths documented
- [ ] Change request template in use
- [ ] CAB meeting cadence established (weekly for Normal, ad-hoc for Emergency)
- [ ] Change log maintained and accessible

## Deliverable: Rollout Plan + Operating Model + Support Model

```markdown
# National Rollout Plan — Impilo vNext

## Rollout Schedule
- Phase 1 (Pilot): ___ sites, ___ date range
- Phase 2 (Early Adopters): ___ sites, ___ date range
- Phase 3 (Majority): ___ sites, ___ date range
- Phase 4 (Laggards): ___ sites, ___ date range

## Operating Model
- [ ] Site readiness assessment process documented
- [ ] Site classification matrix applied
- [ ] Connectivity and power requirements published
- [ ] Hardware procurement pipeline established

## Support Model
- [ ] Training program (Impilo Fundo) curriculum finalized
- [ ] Help desk staffed and operational
- [ ] Escalation paths documented and tested
- [ ] On-call rotation established

## Release Model
- [ ] Ring-based release trains operational
- [ ] CAB processes running
- [ ] Rollback procedures tested per ring
- [ ] Canary deployment proven

## Sign-Off
- [ ] Program Director: _________________ Date: _______
- [ ] Clinical Director: _________________ Date: _______
- [ ] IT Director: _________________ Date: _______
- [ ] Security Officer: _________________ Date: _______
```

## Exit Criteria

- [ ] Site readiness assessments completed for Phase 1 sites
- [ ] Training program delivered to Phase 1 clinical champions
- [ ] Help desk operational with staffed L1/L2 support
- [ ] Ring-based release trains established and first release shipped
- [ ] CAB processes running with at least 2 change requests processed
- [ ] Rollout plan signed off by program stakeholders
