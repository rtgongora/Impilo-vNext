# Security Patch Pipeline & Incident Learning Reviews — Impilo vNext

> Wave 25 Deliverable | Status: Draft | Date: 2026-03-15

## 1. Purpose

This document defines two tightly coupled processes:
1. **Security Patch Pipeline** — how vulnerabilities are detected, triaged, patched, and deployed.
2. **Incident Learning Reviews** — how incidents (security and operational) produce lasting improvements.

Both processes feed into the continuous improvement loop and integrate with the CAB, release train, and observability processes defined in companion Wave 24/25 documents.

## 2. Ownership Model

| Role | Responsibility |
|------|---------------|
| **Security Lead** | Owns the vulnerability triage process; chairs incident learning reviews for security incidents; maintains the vulnerability register |
| **Platform Lead** | Co-chairs operational incident learning reviews; approves infrastructure-level patches |
| **Domain Owners** (per service cluster) | Apply patches to their services; assess domain-specific CVE impact; implement post-mortem action items |
| **SRE / Platform Engineering** | Apply infrastructure patches (K8s, Kafka, PostgreSQL, Redis, Envoy); manage dependency scanning tooling |
| **Incident Commander** (per incident) | Leads incident response during active incidents; initiates post-mortem process; tracks action item completion |

## 3. Security Patch Pipeline

### 3.1 Vulnerability Detection

| Source | Detection Method | Scan Frequency | Coverage |
|--------|-----------------|---------------|----------|
| **Dependabot** (GitHub) | Automated dependency vulnerability scanning | Continuous (on push + daily) | Java (Maven), Node.js (npm), Docker base images |
| **Snyk / Trivy** | Container image scanning | On every container build | Docker images for all 70+ services |
| **OWASP Dependency-Check** | Maven plugin in CI pipeline | On every build | Java transitive dependencies |
| **Manual CVE Review** | Security Lead monitors NVD, vendor advisories | Weekly (Mondays) | Spring Boot, Keycloak, PostgreSQL, Kafka, Envoy, HAPI FHIR |
| **Penetration Testing** | External/internal pen test | Semi-annually | Full platform surface |
| **Bug Bounty / Responsible Disclosure** | Inbound reports | Continuous | Any component |
| **tshepo-audit-service** | Audit chain anomaly detection | Continuous | Trust-plane integrity |

### 3.2 Vulnerability Triage

Every detected vulnerability is assessed using CVSS score contextualized to Impilo's deployment:

| CVSS Score | Impilo Severity | Contextual Factors |
|-----------|----------------|-------------------|
| 9.0–10.0 | **Critical** | Remotely exploitable + affects Ring 0 (TSHEPO, VITO) or PII-handling component |
| 7.0–8.9 | **High** | Remotely exploitable but requires authentication, or affects Ring 1 clinical services |
| 4.0–6.9 | **Medium** | Requires local access, or affects Ring 2/Outer services with no PII exposure |
| 0.1–3.9 | **Low** | Theoretical vulnerability, no realistic attack vector in Impilo's deployment |

**Context adjustments**:
- Vulnerability in a service that handles PII (vito-service): severity escalated by 1 level.
- Vulnerability in trust-plane service (TSHEPO cluster): severity escalated by 1 level.
- Vulnerability in a service not exposed to external traffic: severity reduced by 1 level.
- Vulnerability mitigated by Envoy gateway controls (rate limiting, ext_authz): note mitigation but do not reduce severity.

### 3.3 Patch SLAs

| Severity | Patch Developed | Patch Deployed (Canary) | Patch Deployed (All Sites) | Change Category |
|----------|----------------|------------------------|---------------------------|----------------|
| **Critical** | ≤ 24 hours | ≤ 48 hours | ≤ 72 hours | Emergency CR (CAB Chair + Security Lead) |
| **High** | ≤ 3 business days | ≤ 5 business days | ≤ 7 business days | Emergency CR |
| **Medium** | ≤ 15 business days | Next release train cycle | Per ring schedule | Normal CR |
| **Low** | Next scheduled maintenance | Next release train cycle | Per ring schedule | Standard CR (pre-approved) |

### 3.4 Patch Development Process

```
Step 1: Vulnerability registered in Vulnerability Register (VR-{YYYY}-{NNN})
  ├── CVSS score recorded
  ├── Impilo severity assessed (with context adjustment)
  ├── Affected services/components identified
  └── Patch owner assigned (domain owner or SRE)

Step 2: Patch development
  ├── Fix branch created from latest release tag
  ├── Dependency updated or code fix applied
  ├── Unit tests pass
  ├── Integration tests pass
  ├── Security scan confirms vulnerability resolved
  └── Golden contract tests pass (Ring 0/1 services)

Step 3: Change request
  ├── CR created per docs/rollout/change-control-and-cab.md
  ├── Emergency CR for Critical/High (CAB Chair + Security Lead)
  ├── Normal CR for Medium
  └── Standard CR for Low

Step 4: Deployment per release train
  ├── Critical: Ring 0 first, all rings within 72 hours
  ├── High: Follow ring schedule but compress canary period to 24 hours
  ├── Medium/Low: Normal ring schedule per docs/rollout/release-train-model.md
  └── Post-deployment: verify vulnerability no longer present

Step 5: Closure
  ├── Vulnerability Register entry updated (status: RESOLVED)
  ├── Resolution date recorded
  └── If SLA was missed: root cause note added
```

### 3.5 Dependency Update Cadence

| Dependency | Update Cadence | Process | Ring Impact |
|-----------|---------------|---------|-------------|
| Java security patches (21.0.x) | Monthly (Patch Tuesday + 3 days) | Automated PR + full regression suite | All rings |
| Spring Boot patch (3.3.x) | Monthly | Automated PR + integration tests + performance baseline | All rings |
| Spring Boot minor (3.x.0) | Per release; after 30-day community soak | Manual + full regression + CAB Normal | All rings |
| PostgreSQL patch (16.x) | Quarterly | Manual + backup-restore test + CAB Normal | Infrastructure |
| Kafka patch (3.7.x) | Quarterly | Manual + consumer lag verification + CAB Normal | Infrastructure |
| Keycloak patch (25.x) | Monthly | Manual + auth flow regression + CAB Normal | Ring 0 |
| Envoy patch (1.31.x) | Monthly | Manual + routing regression + ext_authz test + CAB Normal | Gateway |
| HAPI FHIR patch (7.4.x) | Quarterly | Manual + FHIR profile validation + CAB Normal | Ring 0 (BUTANO) |
| Node.js / Next.js (UI apps) | Monthly | Automated PR + E2E tests | Outer ring |
| Docker base images | Monthly | Automated rebuild + Trivy scan | All |
| npm transitive dependencies | Weekly (Dependabot) | Auto-merge for patch; manual for minor/major | Outer ring |

## 4. Vulnerability Register

All vulnerabilities are tracked in a central register:

| Field | Description |
|-------|-------------|
| ID | VR-{YYYY}-{NNN} |
| CVE ID | CVE identifier (if applicable) |
| CVSS Score | Base CVSS v3.1 score |
| Impilo Severity | Critical / High / Medium / Low (after context adjustment) |
| Affected Component | Service name, library, infrastructure component |
| Affected Ring | 0 / 1 / 2 / Outer / Infrastructure |
| Detection Source | Dependabot / Snyk / Manual / Pen Test / Disclosure |
| Detection Date | Date vulnerability was detected |
| Patch Owner | Name of responsible engineer |
| Patch SLA Deadline | Per severity SLA |
| CR Reference | CR-{YYYY}-{NNN} |
| Status | OPEN / IN_PROGRESS / RESOLVED / ACCEPTED_RISK / MITIGATED |
| Resolution Date | Date patch was deployed to all sites |
| SLA Met | Yes / No (if No, root cause note required) |

### Register Review

| Activity | Frequency | Participants |
|----------|-----------|-------------|
| Open vulnerability review | Weekly (Monday, Security Lead) | Security Lead + SRE |
| SLA compliance check | Monthly (first Monday) | Security Lead + Platform Lead |
| Accepted-risk re-assessment | Quarterly | Security Lead + Platform Lead + Domain Owners |

## 5. Incident Learning Reviews (Post-Mortems)

### 5.1 Severity Classification

| Severity | Definition | Clinical Impact | Examples |
|----------|-----------|----------------|---------|
| **SEV-1** | Platform or Ring 0 service completely down; no workaround available | Patient care blocked for multiple users or sites | TSHEPO authz failure (all requests denied), VITO unreachable, PostgreSQL cluster down, Envoy gateway crash |
| **SEV-2** | Ring 1 service degraded or down; workaround exists | Clinical workflow significantly impaired but care continues | PCT queue loading failure, OROS order submission intermittent, pharmacy-service timeout, Kafka consumer lag >10k |
| **SEV-3** | Ring 2 or Outer service degraded; limited user impact | Convenience features impaired; clinical care unaffected | Search slow, notifications delayed, report generation timeout, UI rendering issue |
| **SEV-4** | Cosmetic or minor operational issue | No clinical impact | Log formatting error, dashboard widget misalignment, non-critical alert noise |

### 5.2 Post-Mortem Requirements

| Severity | Review Type | Timeline | Participants | Output |
|----------|------------|----------|-------------|--------|
| **SEV-1** | Mandatory blameless post-mortem meeting | Within 48 hours of resolution | Incident commander, all responders, Platform Lead, Security Lead (if security-related), affected domain owners | Written post-mortem document + action items with owners and deadlines |
| **SEV-2** | Written review document | Within 5 business days of resolution | Incident commander, primary responders, affected domain owners | Written post-mortem document + action items |
| **SEV-3** | Brief write-up | Within 10 business days | Service owner | 1-page summary + relevant action items |
| **SEV-4** | Optional | — | — | Ticket update if applicable |

### 5.3 Blameless Post-Mortem Process

```
Step 1: Incident Resolved
  └── Incident Commander schedules post-mortem within 48 hours (SEV-1) or 5 days (SEV-2)

Step 2: Pre-Meeting Preparation (assigned to Incident Commander)
  ├── Compile incident timeline from:
  │   ├── PagerDuty alert log
  │   ├── Grafana/Loki/Jaeger data
  │   ├── Chat/Slack logs from war room
  │   └── tshepo-audit-service records (if trust-related)
  ├── Identify all affected services, sites, and users
  └── Distribute timeline to participants 24 hours before meeting

Step 3: Post-Mortem Meeting (60–90 minutes for SEV-1)
  ├── Review timeline (facts only, no blame)
  ├── Identify root cause(s)
  ├── Identify contributing factors
  ├── Discuss what went well (detection, response, communication)
  ├── Discuss what did not go well
  ├── Generate action items with:
  │   ├── Specific action description
  │   ├── Owner (named individual)
  │   ├── Deadline
  │   └── Category: Prevent / Detect / Respond / Recover
  └── Assign follow-up review date

Step 4: Post-Mortem Document Published
  ├── Document shared with all engineering (transparency)
  ├── Action items entered into backlog with post-mortem reference
  └── Decision log entry created

Step 5: Action Item Tracking
  ├── Incident Commander tracks completion
  ├── Monthly review: all open post-mortem action items reviewed
  └── Overdue items escalated to Platform Lead
```

### 5.4 Post-Mortem Document Template

```markdown
# Incident Post-Mortem — INC-{YYYY}-{NNN}

## Summary
- **Date**: {YYYY-MM-DD}
- **Duration**: {start time} to {end time} ({total duration})
- **Severity**: SEV-1 / SEV-2 / SEV-3
- **Incident Commander**: {name}
- **Services Affected**: {service names with ports and rings}
- **Sites Affected**: {site names or "all"}
- **Users Affected**: {approximate count}
- **Clinical Impact**: {description of impact on patient care}

## Detection
- **How detected**: {alert / user report / monitoring / audit anomaly}
- **Time to detect**: {minutes from incident start to first alert/report}
- **Detection gap**: {was there a gap? should an alert have fired sooner?}

## Timeline
| Time (UTC) | Event | Actor |
|------------|-------|-------|
| {HH:MM} | {event description} | {person/system} |

## Root Cause
{Clear, technical description of the root cause. Not "human error" — describe
the system conditions that allowed the failure.}

## Contributing Factors
1. {factor — e.g., "No alert for outbox lag exceeding 500 events"}
2. {factor — e.g., "Runbook for Kafka rebalance was outdated"}
3. {factor — e.g., "Recent schema change increased event payload size by 3x"}

## What Went Well
1. {e.g., "PagerDuty alerted L4 on-call within 2 minutes"}
2. {e.g., "War room established within 10 minutes"}
3. {e.g., "Helm rollback procedure worked as documented"}

## What Did Not Go Well
1. {e.g., "Runbook did not cover this failure mode"}
2. {e.g., "Initial responder spent 20 minutes on wrong service"}
3. {e.g., "Communication to affected sites was delayed by 45 minutes"}

## Action Items
| # | Category | Action | Owner | Deadline | Status |
|---|----------|--------|-------|----------|--------|
| 1 | Prevent | {e.g., "Add circuit breaker to VITO→TSHEPO call"} | {name} | {date} | Open |
| 2 | Detect | {e.g., "Add alert for outbox lag >500 sustained 5min"} | {name} | {date} | Open |
| 3 | Respond | {e.g., "Update Kafka rebalance runbook with new steps"} | {name} | {date} | Open |
| 4 | Recover | {e.g., "Add automated canary rollback on error budget breach"} | {name} | {date} | Open |

## Lessons Learned
{Narrative summary: what did this incident teach us about our system,
our processes, or our assumptions?}

## References
- PagerDuty incident: {link}
- Grafana dashboard snapshot: {link}
- Related CRs: {CR-YYYY-NNN}
- Related VRs: {VR-YYYY-NNN} (if security-related)
```

## 6. Incident Metrics

| Metric | Target | Measurement Source | Review Cadence |
|--------|--------|-------------------|---------------|
| Mean time to detect (MTTD) | ≤ 5 min (SEV-1), ≤ 15 min (SEV-2) | PagerDuty / alert timestamps | Monthly |
| Mean time to respond (MTTR) | ≤ 15 min (SEV-1), ≤ 60 min (SEV-2) | Incident timeline | Monthly |
| Mean time to resolve | ≤ 4 hours (SEV-1), ≤ 8 hours (SEV-2) | Incident open/close timestamps | Monthly |
| Post-mortem completion rate | 100% (SEV-1/2 within timeline) | Post-mortem register | Monthly |
| Post-mortem action item completion rate | ≥ 90% by deadline | Action item tracker | Monthly |
| Repeat incidents (same root cause) | 0 | Incident database cross-reference | Quarterly |
| Security patch SLA compliance | ≥ 95% | Vulnerability register | Monthly |
| Incidents per site per month | ≤ 1 (SEV-1/2) | Incident database | Monthly |

## 7. Incident Register

All SEV-1, SEV-2, and SEV-3 incidents are recorded:

| Field | Description |
|-------|-------------|
| ID | INC-{YYYY}-{NNN} |
| Date | Incident start date/time |
| Severity | SEV-1 / SEV-2 / SEV-3 |
| Duration | Start to resolution |
| Services affected | Service names, ports, rings |
| Sites affected | Site names or "all" |
| Root cause category | Code defect / Configuration / Infrastructure / Dependency / Security / Capacity / External |
| Post-mortem reference | Link to post-mortem document |
| Action items count | Total / Completed / Overdue |
| Related VR | VR-{YYYY}-{NNN} (if security-related) |
| Related CR | CR-{YYYY}-{NNN} (change that caused or fixed the incident) |

## 8. Governance Integration

| Process | Integration |
|---------|------------|
| **CAB** | Emergency CRs for Critical/High security patches reference VR IDs; post-mortem action items that require code changes follow the CR process |
| **Release Train** | Security patches follow ring-based deployment per `docs/rollout/release-train-model.md`; Critical patches may compress canary period |
| **Observability** | Post-mortem "Detect" action items feed into alert tuning queue in weekly observability review (`docs/governance/observability-driven-backlog-process.md`) |
| **Schema Governance** | Incidents caused by schema issues are flagged in post-mortem; feed into schema governance review (`docs/governance/schema-governance-cycle.md`) |
| **Capacity Planning** | Incidents caused by capacity exhaustion feed into quarterly capacity review (`docs/governance/cost-and-capacity-planning.md`) |
| **Quarterly Review** | Incident metrics and security posture are required inputs to the quarterly platform review (`docs/governance/platform-governance-cadence.md`) |
| **Compliance** | Security incidents that affect PII or consent are escalated per `docs/compliance/full-platform-compliance-matrix.md` obligations |
