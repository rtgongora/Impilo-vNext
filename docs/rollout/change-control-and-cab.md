# Change Control and CAB Operating Model — Impilo vNext

> Wave 24 Deliverable | Status: Draft | Date: 2026-03-15

## 1. Purpose

This document defines the Change Advisory Board (CAB) structure, change categorization, approval workflows, and operational cadence for Impilo vNext production environments. It ensures that changes to the platform are assessed for clinical safety, security, and operational risk before deployment.

## 2. Change Advisory Board (CAB)

### 2.1 Membership

| Role | Responsibility | Quorum Required |
|------|---------------|-----------------|
| **CAB Chair** | Schedules reviews, holds final approval authority, breaks ties | Always |
| **Platform Lead** | Technical risk assessment, architecture impact, dependency analysis | Always |
| **Clinical Safety Officer** | Clinical workflow impact, patient safety assessment | For Ring 0/1 changes |
| **Security Lead** | Security impact, compliance check, vulnerability assessment | Always |
| **Operations Lead** | Operational readiness, monitoring, rollback readiness | Always |
| **Site Representative(s)** | Site-specific impact, local readiness, staff availability | For Major changes |
| **Data Governance Lead** | PII handling, consent implications, data migration impact | For data-touching changes |

### 2.2 Quorum Rules

| Change Category | Minimum Quorum |
|----------------|----------------|
| Standard | No meeting required (pre-approved) |
| Normal | CAB Chair + Platform Lead + Security Lead (3 of 7) |
| Emergency | CAB Chair + 1 relevant lead (2 of 7) |
| Major | Full CAB (all 7 roles represented) |

### 2.3 Meeting Cadence

| Meeting | Schedule | Duration | Purpose |
|---------|----------|----------|---------|
| Weekly CAB | Every Tuesday 14:00 (before deployment windows) | 60 min | Review Normal change requests for the week |
| Emergency CAB | Ad-hoc (within 2 hours of request) | 30 min | Approve emergency changes |
| Major Change Review | Scheduled ≥ 10 business days before target deployment | 90 min | Deep-dive on Major changes |
| Monthly CAB Retrospective | First Tuesday of month | 30 min | Review change success rates, incidents, process improvement |

## 3. Change Categories

### 3.1 Category Definitions

| Category | Description | Approval Path | Lead Time | Examples |
|----------|-------------|--------------|-----------|---------|
| **Standard** | Low-risk, repeatable, pre-approved changes | No CAB review; automated or self-service | Same day | Dependency version bumps (non-breaking), documentation updates, config value changes within defined ranges, log level adjustments |
| **Normal** | Moderate-risk changes requiring review | Weekly CAB approval | ≥ 5 business days before target deployment | New features, API changes (backward-compatible), schema migrations (additive), new Ring 2 service deployment, UI feature flag activation |
| **Emergency** | Urgent fixes for active incidents or critical vulnerabilities | CAB Chair + 1 lead approval (phone/chat acceptable) | Immediate | Security patches (Critical CVE), P1 bug fixes (service down), data corruption fixes |
| **Major** | High-risk changes affecting Ring 0, federation protocol, or cross-cutting concerns | Full CAB + stakeholder review | ≥ 10 business days | Ring 0 service changes, trust header contract changes, federation protocol changes, Kafka topic schema breaking changes, database engine upgrades, Envoy routing changes |

### 3.2 Ring-to-Category Mapping

| Ring | Default Category | Escalation Trigger |
|------|-----------------|-------------------|
| Ring 0 | Major | Always Major unless pure config change within pre-approved range |
| Ring 1 | Normal | Escalate to Major if: schema migration is destructive, or clinical workflow changes |
| Ring 2 | Standard (most), Normal (new services) | Escalate to Normal if: new integration endpoint, or data pipeline schema change |
| Outer | Standard (feature-flagged) | Escalate to Normal if: unflagged deployment, or auth flow change |

## 4. Change Request Workflow

### 4.1 Lifecycle

```
┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐
│ DRAFT    │───▶│ SUBMITTED│───▶│ REVIEWED │───▶│ APPROVED │───▶│ DEPLOYED │
└──────────┘    └──────────┘    └──────────┘    └──────────┘    └──────────┘
                      │               │               │               │
                      │               ▼               │               ▼
                      │         ┌──────────┐          │         ┌──────────┐
                      │         │ RETURNED │          │         │ ROLLED   │
                      │         │ (rework) │          │         │ BACK     │
                      │         └──────────┘          │         └──────────┘
                      │                               │
                      ▼                               ▼
                ┌──────────┐                    ┌──────────┐
                │ WITHDRAWN│                    │ REJECTED │
                └──────────┘                    └──────────┘
```

### 4.2 Standard Change (Pre-Approved)

1. Engineer creates change request (CR) with category = Standard.
2. CI pipeline validates change against pre-approved criteria.
3. If criteria met: auto-approved, deploy in next window.
4. If criteria not met: auto-escalated to Normal.

### 4.3 Normal Change

1. Engineer creates CR with category = Normal, ≥ 5 business days before target.
2. CR submitted to weekly CAB agenda.
3. CAB reviews: technical risk, clinical impact, security impact, rollback plan.
4. Decision: Approved / Returned (rework) / Rejected.
5. If Approved: deploy in next permitted window per ring schedule.

### 4.4 Emergency Change

1. Incident commander or on-call engineer creates CR with category = Emergency.
2. CAB Chair notified immediately (phone/chat).
3. CAB Chair + 1 relevant lead review within 2 hours.
4. If Approved: deploy immediately (any window).
5. Post-deployment: full retrospective at next weekly CAB.
6. Emergency CR must be back-filled with complete documentation within 48 hours.

### 4.5 Major Change

1. Engineer creates CR with category = Major, ≥ 10 business days before target.
2. Dedicated Major Change Review meeting scheduled.
3. Full CAB + stakeholder review.
4. Requires impact assessment across all affected rings and sites.
5. Decision: Approved / Returned / Rejected.
6. If Approved: deploy per ring schedule with extended canary observation.

## 5. Change Request Template

```markdown
# Change Request — CR-[YYYY]-[NNN]

## 1. Summary
- **Title**: ___
- **Category**: Standard / Normal / Emergency / Major
- **Requestor**: ___
- **Date submitted**: ___
- **Target deployment date**: ___

## 2. Scope
- **Ring affected**: 0 / 1 / 2 / Outer
- **Services affected**: [list service names and ports]
- **Sites affected**: All / Specific sites: ___
- **Helm charts modified**: [list chart names]
- **Database migrations**: Yes (additive / destructive) / No
- **Kafka schema changes**: Yes (compatible / breaking) / No
- **Trust header changes**: Yes / No
- **Envoy route changes**: Yes / No

## 3. Impact Assessment
| Dimension | Impact Level | Justification |
|-----------|-------------|---------------|
| Clinical workflow | None / Low / Medium / High | ___ |
| Patient safety | None / Low / Medium / High | ___ |
| Security posture | None / Low / Medium / High | ___ |
| Performance | None / Low / Medium / High | ___ |
| Data integrity | None / Low / Medium / High | ___ |
| Availability | None / Low / Medium / High | ___ |

## 4. Testing Evidence
- [ ] Unit tests pass (attach CI link)
- [ ] Integration tests pass (attach CI link)
- [ ] Golden contract tests pass (Ring 0/1 only)
- [ ] Performance baseline not regressed (Ring 0/1 only)
- [ ] Security scan clean (attach report)
- [ ] Staging smoke tests pass (attach log)

## 5. Rollback Plan
- **Rollback method**: Helm rollback / Feature flag off / ___
- **Rollback time estimate**: ___ minutes
- **Data rollback required**: Yes (describe) / No
- **Rollback tested**: Yes (attach evidence) / No (justify)

## 6. Communication Plan
- **Pre-deployment notification**: ___ (who, when, channel)
- **Post-deployment verification**: ___ (who, what checks)
- **Incident escalation**: ___ (who to contact if issues)

## 7. Approvals
| Role | Name | Decision | Date |
|------|------|----------|------|
| Platform Lead | | Approve / Reject | |
| Clinical Safety Officer | | Approve / Reject / N/A | |
| Security Lead | | Approve / Reject | |
| Operations Lead | | Approve / Reject | |
| Data Governance Lead | | Approve / Reject / N/A | |
| CAB Chair | | Approve / Reject | |
```

## 6. Pre-Approved Standard Changes

The following change types are pre-approved and do not require CAB review, provided they pass automated validation:

| ID | Change Type | Automated Check | Ring |
|----|------------|-----------------|------|
| SC-01 | Dependency version bump (patch/minor, no breaking API) | CI passes, security scan clean | All |
| SC-02 | Documentation-only changes | No code changes detected | All |
| SC-03 | Log level adjustment | Config change within defined range | All |
| SC-04 | Feature flag toggle (existing flag) | Flag exists in registry | Outer |
| SC-05 | Terminology pack update (ZIBO) | Pack validation passes, no schema change | Ring 0 |
| SC-06 | Notification template update | Template renders correctly | Ring 2 |
| SC-07 | Report definition update | Report schema validates | Ring 2 |
| SC-08 | UI copy/label changes | No logic changes detected | Outer |
| SC-09 | Scaling adjustment (replica count) | Within defined min/max range | All |
| SC-10 | Certificate renewal | Certificate validates, matches existing CN/SAN | All |

## 7. Change Metrics and Reporting

### 7.1 Tracked Metrics

| Metric | Target | Review Cadence |
|--------|--------|---------------|
| Change success rate | ≥ 95% (no rollback needed) | Monthly |
| Mean time from CR submission to deployment | ≤ 7 days (Normal), ≤ 4 hours (Emergency) | Monthly |
| Emergency change ratio | ≤ 10% of total changes | Monthly |
| Changes causing incidents | ≤ 2% of total changes | Monthly |
| CAB meeting duration | ≤ 60 min (weekly), ≤ 30 min (emergency) | Quarterly |
| Rollback execution time | Within ring-defined window (24h/48h/72h) | Per incident |

### 7.2 Monthly CAB Report Template

```markdown
# CAB Monthly Report — [YYYY]-[MM]

## Summary
- Total changes processed: ___
- Standard: ___ | Normal: ___ | Emergency: ___ | Major: ___
- Approved: ___ | Rejected: ___ | Returned: ___ | Withdrawn: ___
- Deployed successfully: ___ | Rolled back: ___

## Incidents Linked to Changes
| CR # | Incident | Root Cause | Resolution |
|------|----------|------------|------------|

## Process Improvements
- [list any improvements identified]

## Next Month Focus
- [list upcoming Major changes or known risks]
```

## 8. Escalation Path for Disputed Changes

| Step | Action | Timeframe |
|------|--------|-----------|
| 1 | Requestor addresses CAB feedback; resubmit at next meeting | Within 5 business days |
| 2 | If still disputed: CAB Chair mediates with requestor + objecting lead | Within 2 business days |
| 3 | If unresolved: escalate to Program Director for final decision | Within 3 business days |
| 4 | Program Director decision is final and documented in CR | — |

## 9. Compliance Integration

| Compliance Area | CAB Touchpoint |
|-----------------|---------------|
| **POPIA / data protection** | Data Governance Lead reviews any change touching PII (VITO) or consent (TSHEPO) |
| **Clinical safety** | Clinical Safety Officer reviews any change to Ring 0/1 clinical workflows |
| **Audit trail** | All CR approvals are recorded in the change log; linked to tshepo-audit-service events |
| **Security baseline** | Security Lead verifies no regression against security-hardening-service policies |
| **SLO compliance** | Operations Lead confirms change does not breach existing SLO/error budgets |
