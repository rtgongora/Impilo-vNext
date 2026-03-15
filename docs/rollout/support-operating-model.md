# Support Operating Model — Impilo vNext

> Wave 24 Deliverable | Status: Draft | Date: 2026-03-15

## 1. Purpose

This document defines the multi-tier support model for Impilo vNext in production. It covers escalation paths, response SLAs, on-call structures, and the handoff between site-level support, centralized help desk, and engineering teams.

## 2. Support Tier Structure

### 2.1 Tier Overview

```
┌─────────────────────────────────────────────────────────┐
│                     End Users                           │
│            (Clinical staff, admin, patients)            │
└──────────────────────┬──────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────┐
│  L0 — Self-Service                                      │
│  In-app help, FAQ, Fundo knowledge base                 │
│  Channel: one-ui-shell help panel, portal FAQ           │
└──────────────────────┬──────────────────────────────────┘
                       │ (unresolved)
                       ▼
┌─────────────────────────────────────────────────────────┐
│  L1 — Site Support (Clinical Champions)                 │
│  Basic troubleshooting, workflow guidance, password help │
│  Channel: In-person, phone, WhatsApp group              │
└──────────────────────┬──────────────────────────────────┘
                       │ (unresolved after 30 min)
                       ▼
┌─────────────────────────────────────────────────────────┐
│  L2 — Centralized Help Desk                             │
│  Configuration, data queries, known-issue workarounds   │
│  Channel: Ticketing system, phone, email                │
└──────────────────────┬──────────────────────────────────┘
                       │ (unresolved after SLA)
                       ▼
┌─────────────────────────────────────────────────────────┐
│  L3 — Application Engineering                           │
│  Bug investigation, service-level debugging, fixes      │
│  Channel: Internal ticket, Slack, PagerDuty             │
└──────────────────────┬──────────────────────────────────┘
                       │ (infrastructure/security/critical)
                       ▼
┌─────────────────────────────────────────────────────────┐
│  L4 — Platform Engineering / Security                   │
│  Infrastructure, Ring 0, security incidents, DR         │
│  Channel: PagerDuty, war room                           │
└─────────────────────────────────────────────────────────┘
```

### 2.2 Tier Definitions

| Tier | Scope | Staffing | Hours | Tools |
|------|-------|----------|-------|-------|
| **L0** | In-app help, FAQ, status page, Fundo knowledge base | Automated + content team | 24/7 | one-ui-shell help panel, status page |
| **L1** | Password resets, login issues, basic workflow questions, "how do I" guidance, printer/scanner issues | Clinical champions (Fundo-600 graduates), ≥ 2 per site | Business hours (site local) | WhatsApp group, phone, in-person |
| **L2** | Configuration changes, user/role management (Keycloak), data corrections, report generation, known-issue workarounds, connectivity troubleshooting | Centralized help desk staff, ≥ 2 per 20 sites | Business hours + on-call after hours | Ticketing system, remote desktop, ops-console (port 3001) |
| **L3** | Bug investigation, service-level debugging, performance investigation, integration issues, hotfix development | Application engineers, ≥ 1 per service domain (Registry, Clinical, Finance, Integration) | Business hours + on-call rotation | Grafana, Loki, Jaeger/OTel traces, service logs, source code |
| **L4** | Infrastructure failures, Ring 0 incidents, security breaches, DR activation, federation issues, Kafka/DB issues | Platform engineers + security team | 24/7 on-call rotation | kubectl, Helm, Kafka CLI, PostgreSQL admin, Vault, PagerDuty |

## 3. Incident Severity and SLA

### 3.1 Severity Classification

| Severity | Definition | Clinical Impact | Examples |
|----------|-----------|----------------|---------|
| **P1 — Critical** | Platform or Ring 0 service completely down; no workaround | Patient care blocked for multiple users/sites | TSHEPO authz down (no requests processed), VITO down (no patient lookup), PostgreSQL down, Envoy gateway down |
| **P2 — Major** | Ring 1 service degraded or down; workaround exists but painful | Clinical workflow significantly impaired | PCT queue not loading (paper fallback), OROS order submission failing (phone orders), pharmacy-service down (manual dispense) |
| **P3 — Minor** | Ring 2 or Outer service degraded; limited user impact | Clinical work continues with minor inconvenience | Search slow, notifications delayed, report generation failing, UI cosmetic issues |
| **P4 — Low** | Cosmetic, documentation, or enhancement request | No clinical impact | Typo in UI label, feature request, non-urgent config change |

### 3.2 Response and Resolution SLAs

| Severity | First Response | Escalation to L3 | Escalation to L4 | Target Resolution | Update Frequency |
|----------|---------------|-------------------|-------------------|-------------------|-----------------|
| **P1** | ≤ 15 min | Immediate | ≤ 30 min if L3 cannot resolve | ≤ 4 hours | Every 30 min |
| **P2** | ≤ 1 hour | ≤ 2 hours | ≤ 4 hours if L3 cannot resolve | ≤ 8 hours | Every 2 hours |
| **P3** | ≤ 4 hours | ≤ 8 hours | As needed | ≤ 3 business days | Daily |
| **P4** | ≤ 1 business day | As needed | — | Best effort (next release train) | Weekly |

### 3.3 After-Hours Escalation

| Time | P1 | P2 | P3/P4 |
|------|----|----|-------|
| Business hours (08:00–17:00 local) | L2 → L3 → L4 (normal path) | L2 → L3 (normal path) | L2 handles |
| After hours (17:00–08:00) | PagerDuty auto-page L4 on-call | PagerDuty auto-page L3 on-call | Queue for next business day |
| Weekends/holidays | PagerDuty auto-page L4 on-call | Queue for next business day (unless clinical impact) | Queue for next business day |

## 4. Escalation Matrix

### 4.1 By Service Domain

| Domain | Services | L3 Team | L4 Team |
|--------|----------|---------|---------|
| **Trust & Identity** | tshepo-authz, tshepo-identity, tshepo-consent, tshepo-audit, tshepo-keys, tshepo-offline | Trust Engineering | Platform Engineering |
| **Registry** | vito-service, varapi-service, tuso-service, zibo-service, msika-service, ubomi-service | Registry Engineering | Platform Engineering |
| **Clinical** | pct-service, oros-service, pharmacy-service, inpatient-service, costing-engine, coverage-service | Clinical Engineering | Platform Engineering |
| **Finance** | mushex-service, costing-engine-service | Finance Engineering | Platform Engineering |
| **Integration** | integration-hub, notification-service, channels-service, fhir-gateway-service, pacs-adapter | Integration Engineering | Platform Engineering |
| **Data** | data-pipeline-service, reporting-service, surveillance-service, search-service, ndr-service | Data Engineering | Platform Engineering |
| **Offline** | offline-sync-service, tshepo-offline-service | Offline Engineering | Platform Engineering |
| **Experience** | All UI apps (one-ui-shell, ehr, portal, ops-console, etc.) | Experience Engineering | Platform Engineering |
| **Infrastructure** | PostgreSQL, Redis, Kafka, Envoy, Keycloak, MinIO, K8s | — | Platform Engineering (direct) |

### 4.2 Escalation Triggers

| Trigger | Action |
|---------|--------|
| L1 cannot resolve within 30 minutes | Escalate to L2 with ticket |
| L2 cannot resolve within response SLA | Escalate to L3 |
| L3 cannot resolve within 50% of resolution SLA | Escalate to L4 |
| Any Ring 0 service health check fails | Auto-escalate to L4 (PagerDuty) |
| Error budget for any service breached | Auto-escalate to L3 + notify CAB |
| Security incident detected (tshepo-audit chain break, unauthorized access) | Auto-escalate to L4 + Security Lead |
| Multiple sites reporting same issue | Auto-escalate to L3 + Rollout Lead |

## 5. On-Call Rotation

### 5.1 Structure

| Rotation | Coverage | Team Size | Shift Duration | Handoff |
|----------|----------|-----------|----------------|---------|
| **L3 Primary** | After-hours P2 response | ≥ 4 engineers (1 per domain cluster) | 1 week | Monday 09:00 |
| **L4 Primary** | 24/7 P1 response | ≥ 4 platform engineers | 1 week | Monday 09:00 |
| **L4 Secondary** | Backup for L4 Primary | Same pool, staggered | 1 week | Monday 09:00 |
| **Security On-Call** | Security incidents | ≥ 2 security engineers | 1 week | Monday 09:00 |

### 5.2 On-Call Expectations

- Respond to page within **5 minutes** (P1) or **15 minutes** (P2).
- Laptop and VPN available at all times during on-call shift.
- Access to kubectl, Helm, Grafana, Loki, and PagerDuty.
- Handoff includes: open incidents, pending changes, known risks.
- Compensatory time: 1 day off per week of on-call (or per local policy).

### 5.3 War Room Protocol (P1 Incidents)

1. L4 on-call opens war room (virtual call).
2. Incident commander role assigned (usually L4 on-call).
3. Scribe designated to maintain timeline.
4. Relevant L3 engineers paged based on affected domain.
5. Communications lead notifies affected sites (via channels-service or direct contact).
6. Updates posted every 30 minutes to status page and stakeholder channel.
7. Post-incident: blameless retrospective within 48 hours.

## 6. Tooling

| Category | Tool | Purpose | Access |
|----------|------|---------|--------|
| **Ticketing** | _[TBD — e.g., Jira Service Management, ServiceNow]_ | Incident and request tracking | L1–L4 |
| **Paging** | PagerDuty | On-call alerting and escalation | L3–L4 |
| **Monitoring** | Grafana + Prometheus | Dashboards, SLO tracking, alerting | L2–L4 |
| **Logging** | Loki | Centralized log search | L3–L4 |
| **Tracing** | OpenTelemetry + Jaeger | Distributed trace analysis | L3–L4 |
| **Chat** | _[TBD — e.g., Slack, Teams]_ | Real-time collaboration, war rooms | All |
| **Remote Access** | _[TBD — e.g., remote desktop tool]_ | L2 remote support to site devices | L2 |
| **Status Page** | _[TBD — e.g., Statuspage, Cachet]_ | Public incident communication | L0 (read), L2–L4 (write) |
| **Knowledge Base** | _[TBD — e.g., Confluence, wiki]_ | Known issues, runbooks, FAQs | All |
| **Admin Console** | ops-console (port 3001) | User management, service health, config | L2–L4 |

> **ASSUMPTION**: Specific tooling vendors are not defined in the repository. The above are placeholders to be confirmed during Phase 0.

## 7. Knowledge Management

### 7.1 Runbooks

Each service must have a runbook covering:

| Section | Content |
|---------|---------|
| Service overview | What it does, port, ring, dependencies |
| Health check | Endpoint URL, expected response |
| Common failure modes | Symptoms, root cause, resolution steps |
| Restart procedure | Safe restart sequence, data implications |
| Scaling procedure | How to scale up/down, limits |
| Rollback procedure | Helm rollback command, verification steps |
| Log locations | Loki query patterns for this service |
| Kafka topics | Topics produced/consumed, consumer group |
| Database | Schema name, connection details, backup schedule |
| Escalation | Who to contact, PagerDuty service |

### 7.2 Known Issue Database

| Field | Description |
|-------|-------------|
| Issue ID | KI-[YYYY]-[NNN] |
| Affected service(s) | Service name(s) and ring |
| Symptoms | What the user sees |
| Root cause | Technical explanation |
| Workaround | Steps for L1/L2 to guide users |
| Fix target | Release train / CR number |
| Status | Open / Workaround available / Fixed |

## 8. Support Staffing Model by Rollout Phase

| Phase | Sites | L1 (per site) | L2 (centralized) | L3 (engineering) | L4 (platform) |
|-------|-------|---------------|-------------------|-------------------|----------------|
| Phase 1 (Pilot) | 3 | 2 champions | 2 staff | 4 engineers (1 per domain) | 4 engineers |
| Phase 2 (Early Adopter) | 10–15 | 2 champions | 4 staff | 6 engineers | 4 engineers |
| Phase 3 (Majority) | 50–100 | 2 champions | 8 staff | 8 engineers | 6 engineers |
| Phase 4 (Full Scale) | All | 2 champions | 12+ staff | 10 engineers | 6 engineers |

### 8.1 Scaling Triggers

| Metric | Threshold | Action |
|--------|-----------|--------|
| L2 ticket queue depth | > 50 open tickets for > 24 hours | Add 1 L2 staff member |
| L2 first-response SLA miss rate | > 10% in a week | Add 1 L2 staff member |
| L3 ticket queue depth | > 20 open tickets for > 48 hours | Add 1 L3 engineer |
| P1 incidents per month | > 3 | Post-incident review; consider L4 scaling |
| L1 escalation rate to L2 | > 30% of L1 contacts | Retrain L1 champions (Fundo refresher) |

## 9. Continuous Improvement

### 9.1 Monthly Support Review

| Metric | Target | Source |
|--------|--------|--------|
| First-response SLA compliance | ≥ 95% | Ticketing system |
| Resolution SLA compliance | ≥ 90% | Ticketing system |
| L1 self-resolution rate | ≥ 70% | L1 contact log vs. L2 ticket ratio |
| Repeat incidents (same root cause) | ≤ 5% | Incident database |
| User satisfaction (post-ticket survey) | ≥ 4.0/5.0 | Survey tool |
| Mean time to resolve (P1) | ≤ 4 hours | Incident database |
| Mean time to resolve (P2) | ≤ 8 hours | Incident database |

### 9.2 Feedback Loops

| Source | Frequency | Action |
|--------|-----------|--------|
| Post-incident retrospective | After every P1/P2 | Update runbooks, add to known-issue DB, create preventive CRs |
| Monthly support review | Monthly | Identify trends, adjust staffing, update training |
| Quarterly user survey | Quarterly | Feed into Fundo training updates, UI/UX improvements |
| L1 champion feedback | Monthly | Identify knowledge gaps, update FAQ/knowledge base |
