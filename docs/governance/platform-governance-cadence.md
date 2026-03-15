# Platform Governance Cadence — Impilo vNext

> Wave 25 Deliverable | Status: Draft | Date: 2026-03-15

## 1. Purpose

This document is the master calendar and integration map for all recurring governance activities on the Impilo vNext platform. It consolidates the cadences defined in the other Wave 25 governance documents and the Wave 24 rollout operational model into a single reference that answers: "Who meets when, about what, and what decisions result?"

## 2. Governance Bodies

| Body | Chair | Quorum | Mandate |
|------|-------|--------|---------|
| **Change Advisory Board (CAB)** | CAB Chair | 3 of 7 (Normal); 2 of 7 (Emergency); all 7 (Major) | Approves changes to production (see `docs/rollout/change-control-and-cab.md`) |
| **Schema Governance Board** | Schema Steward | Schema Steward + ≥ 2 Domain Owners (standard); full board (breaking) | Governs schema evolution (see `docs/governance/schema-governance-cycle.md`) |
| **Observability Review** | Observability Lead | Observability Lead + Ring 0 On-Call + SRE | Converts telemetry signals into backlog items (see `docs/governance/observability-driven-backlog-process.md`) |
| **Cost & Capacity Review** | Platform Lead | Platform Lead + SRE + Finance | Controls spend and plans scaling (see `docs/governance/cost-and-capacity-planning.md`) |
| **Security Review** | Security Lead | Security Lead + Platform Lead | Triages vulnerabilities and reviews security posture (see `docs/governance/security-patch-and-incident-learning.md`) |
| **Quarterly Platform Review** | Platform Lead | All governance body chairs + Clinical Director + Finance | Strategic direction, roadmap approval, cross-cutting health (see Section 5) |

## 3. Master Calendar

### 3.1 Weekly Cadence

| Day | Time | Meeting | Duration | Chair | Participants | Output |
|-----|------|---------|----------|-------|-------------|--------|
| **Monday** | 09:30 | Observability Review | 50 min | Observability Lead | Ring 0 On-Call, SRE, Domain Leads (rotating 2) | Backlog items (OBS-{YYYY}-{WW}-{NN}); alert tuning queue |
| **Monday** | 11:00 | Security Vulnerability Triage | 30 min | Security Lead | SRE | Updated vulnerability register; patch assignments |
| **Tuesday** | 14:00 | CAB Weekly | 60 min | CAB Chair | CAB members (see CAB doc) | Change approvals/rejections for the week |
| **Wednesday** | 10:00 | Schema Change Review (bi-weekly, odd weeks) | 45 min | Schema Steward | Domain Owners with proposals | Schema change approvals; decision log entries |

### 3.2 Monthly Cadence

| Week | Day | Meeting | Duration | Chair | Participants | Output |
|------|-----|---------|----------|-------|-------------|--------|
| W1 | Tuesday (after CAB) | Cost Review | 60 min | Platform Lead | SRE, Domain Owners (rotating 2), Finance | Monthly cost report; right-sizing actions; budget update |
| W1 | Wednesday | Schema Compatibility Audit | 60 min | Schema Steward | Platform Architect, Data Governance Lead | Schema version inventory; deprecated schema sunset schedule |
| W1 | Monday | CAB Retrospective (monthly) | 30 min | CAB Chair | CAB members | Change success rate report; process improvements |
| W2 | — | Support Metrics Review | 30 min | Support Lead | L2 team lead, L3 domain leads | SLA compliance report; staffing adjustment recommendations |
| W3 | Wednesday | Event Contract Review | 60 min | Schema Steward | All Domain Owners | Updated topic catalog; consumer onboarding approvals |
| W4 | Monday | Post-Mortem Action Item Review | 30 min | Platform Lead | Incident Commanders with open items | Action item status; overdue escalations |

### 3.3 Quarterly Cadence

| Timing | Meeting | Duration | Chair | Participants | Output |
|--------|---------|----------|-------|-------------|--------|
| Q last week | Quarterly Capacity Planning | 90 min | Platform Lead | SRE, all Domain Owners, Finance, Rollout Lead | Quarterly Capacity Plan; budget request |
| Q last week | Quarterly Platform Review | 120 min | Platform Lead | All governance chairs, Clinical Director, Finance, Rollout Lead | Roadmap approval; cross-cutting decisions; governance health report |
| Q first week | Roadmap Kickoff | 60 min | Platform Lead | Domain Owners | Roadmap items assigned to sprints; capacity allocation confirmed |
| Q mid (W7) | Mid-Quarter Check-In | 50 min | Platform Lead | Same as Quarterly Review | Roadmap status update; re-prioritization if needed |
| Q variable | Trust Header Review | 60 min | Schema Steward | Security Lead, Trust domain owner | Trust header alignment report |
| Q variable | Accepted-Risk Re-Assessment | 45 min | Security Lead | Platform Lead, Domain Owners | Re-evaluate ACCEPTED_RISK vulnerabilities |

### 3.4 Semi-Annual / Annual

| Timing | Activity | Owner | Participants |
|--------|----------|-------|-------------|
| Semi-annually | Penetration Test (external) | Security Lead | External pen-test vendor, Platform Engineering |
| Semi-annually | DR Failover Exercise | Platform Lead | SRE, all Domain Owners (see `docs/dr/` runbooks) |
| Annually | Reserved Capacity Review | Finance + Platform Lead | Finance, SRE |
| Annually | Fundo Training Content Refresh | Training Program Lead | Training team, Domain Owners |
| Annually | Compliance Matrix Full Audit | Data Governance Lead | All Domain Owners, Security Lead |

## 4. Visual Calendar (Monthly View)

```
Week 1:
  Mon: Observability Review | Security Triage | Post-Mortem Items (W4 carryover)
  Tue: CAB Weekly | Cost Review (monthly) | CAB Retrospective (monthly)
  Wed: Schema Compatibility Audit (monthly) | Schema Change Review (bi-weekly)

Week 2:
  Mon: Observability Review | Security Triage
  Tue: CAB Weekly
  Wed: Support Metrics Review (monthly)

Week 3:
  Mon: Observability Review | Security Triage
  Tue: CAB Weekly
  Wed: Event Contract Review (monthly) | Schema Change Review (bi-weekly)

Week 4:
  Mon: Observability Review | Security Triage | Post-Mortem Action Item Review (monthly)
  Tue: CAB Weekly
  Wed: —

Quarter boundary weeks add: Capacity Planning (90 min), Platform Review (120 min)
Mid-quarter (W7) adds: Mid-Quarter Check-In (50 min)
```

## 5. Quarterly Platform Review — Detailed Agenda

This is the highest-level recurring governance meeting. It synthesizes outputs from all other governance processes into strategic decisions.

### 5.1 Agenda

| Item | Duration | Presenter | Inputs |
|------|----------|-----------|--------|
| **Platform Health Dashboard** | 15 min | Observability Lead | Quarterly Observability Health Report (SLO compliance, error budgets, alert health) |
| **Incident Summary** | 10 min | Platform Lead | Incident register summary (SEV-1/2 count, MTTR, repeat incidents, open action items) |
| **Security Posture** | 10 min | Security Lead | Vulnerability register summary (open/resolved/accepted-risk by severity), patch SLA compliance |
| **Schema Governance Health** | 10 min | Schema Steward | Schema version inventory, deprecated schemas, decision count, breaking change proposals |
| **Cost & Capacity** | 15 min | Platform Lead + SRE | Monthly cost trends, quarterly capacity plan, scaling actions taken/planned, budget status |
| **Rollout Progress** | 10 min | Rollout Lead | Sites live, sites in pipeline, site readiness assessment pass rate, training completion rate |
| **Support Health** | 10 min | Support Lead | SLA compliance, ticket volume trends, L1 self-resolution rate, staffing adequacy |
| **Previous Quarter Roadmap Scorecard** | 10 min | Platform Lead | P0/P1/P2 completion rates, deferred items, unplanned work ratio |
| **Next Quarter Roadmap** | 20 min | Platform Lead | Proposed roadmap (see `docs/governance/quarterly-platform-roadmap-template.md`); stakeholder discussion and approval |
| **Cross-Cutting Decisions** | 10 min | Platform Lead | Decisions that span multiple governance domains; escalations from sub-processes |

### 5.2 Output

```markdown
# Quarterly Platform Review — Q{N} {YYYY} — Decisions

## Decisions Made
| # | Decision | Scope | Rationale | Owner | Deadline |
|---|----------|-------|-----------|-------|----------|
| 1 | {decision} | {scope} | {rationale} | {owner} | {date} |

## Roadmap Approved
- Q{N+1} roadmap approved: Yes / No (with conditions: ___)
- Key changes from proposal: {list any modifications made during review}

## Escalations Received
| Source | Issue | Resolution |
|--------|-------|------------|
| {governance body} | {issue} | {resolution} |

## Action Items
| # | Action | Owner | Deadline | Status |
|---|--------|-------|----------|--------|
| 1 | {action} | {name} | {date} | Open |

## Attendees
| Role | Name | Present |
|------|------|---------|
| Platform Lead | {name} | Y/N |
| Clinical Director | {name} | Y/N |
| Security Lead | {name} | Y/N |
| Observability Lead | {name} | Y/N |
| Schema Steward | {name} | Y/N |
| Finance / Budget | {name} | Y/N |
| Rollout Lead | {name} | Y/N |
| Support Lead | {name} | Y/N |
```

## 6. Decision Log

All governance decisions across all bodies are recorded in a unified decision log to maintain institutional memory and audit trail.

### 6.1 Decision Categories

| Prefix | Source | Examples |
|--------|--------|---------|
| **CR-{YYYY}-{NNN}** | CAB | Change approvals, emergency patches |
| **SGD-{YYYY}-{NNN}** | Schema Governance Board | Schema change approvals, deprecation schedules |
| **OBS-{YYYY}-{WW}-{NN}** | Observability Review | Backlog items generated from telemetry |
| **CPD-{YYYY}-{NNN}** | Cost & Capacity Review | Scaling decisions, right-sizing, budget approvals |
| **VR-{YYYY}-{NNN}** | Security Review | Vulnerability assessments, patch priorities |
| **INC-{YYYY}-{NNN}** | Incident Post-Mortems | Post-mortem action items |
| **QPR-{YYYY}-Q{N}-{NN}** | Quarterly Platform Review | Strategic decisions, roadmap approvals |

### 6.2 Log Retention

| Decision Type | Retention Period | Storage |
|---------------|-----------------|---------|
| All decisions | 5 years minimum | Version-controlled markdown in `docs/governance/decisions/` |
| Incident post-mortems | 7 years | Version-controlled + backup |
| CAB change requests | 5 years | Version-controlled |
| Vulnerability register | 7 years (regulatory) | Secure storage with access control |

## 7. Escalation Paths

When a governance body cannot resolve an issue within its mandate:

| From | To | Trigger | Timeline |
|------|----|---------|----------|
| Observability Review | Platform Lead (direct) | P1 SLO breach with no clear owner | Same day |
| Schema Governance Board | CAB (Major change) | Breaking schema change requiring production deployment | 10 business days |
| Schema Governance Board | Quarterly Platform Review | Cross-domain schema dispute unresolved after 2 meetings | Next quarterly review |
| Security Review | CAB (Emergency) | Critical vulnerability requiring immediate patch | Same day |
| Security Review | Quarterly Platform Review | Accepted-risk vulnerability disputed by domain owner | Next quarterly review |
| Cost & Capacity Review | Finance / Budget Owner | Budget request exceeds quarterly allocation | 5 business days |
| CAB | Program Director | Disputed change request after CAB Chair mediation fails | 3 business days |
| Any body | Quarterly Platform Review | Strategic decision beyond the body's mandate | Next quarterly review |

## 8. Governance Health Metrics

The governance process itself is measured to prevent governance becoming overhead:

| Metric | Target | Review |
|--------|--------|--------|
| Meeting adherence (held as scheduled) | ≥ 90% of scheduled meetings held | Quarterly |
| Decision throughput (decisions per month) | Tracked, not targeted (context-dependent) | Quarterly |
| Decision cycle time (proposal to decision) | ≤ 5 days (additive schema), ≤ 30 days (breaking schema), ≤ 7 days (Normal CR) | Quarterly |
| Governance overhead ratio | ≤ 5% of total engineering time spent in governance meetings | Quarterly |
| Action item completion rate (across all bodies) | ≥ 85% completed by deadline | Monthly |
| Governance satisfaction survey (participants) | ≥ 3.5/5.0 | Semi-annually |

### 8.1 Anti-Patterns to Monitor

| Anti-Pattern | Signal | Remedy |
|-------------|--------|--------|
| Governance theater | Meetings happen but produce no decisions or action items | Review meeting outputs; cancel meetings with no agenda items |
| Decision bottleneck | Decision cycle time increasing quarter-over-quarter | Delegate more decisions to pre-approved categories; empower domain owners |
| Alert fatigue in governance | Too many low-priority items consuming review time | Raise thresholds; automate triage of P3/P4 items |
| Governance bypass | Changes deployed without CR or schema approval | Audit CI/CD pipeline for un-approved deployments; enforce automated gates |
| Stale decisions | Decisions made but never implemented | Monthly action item review; escalate overdue items |

## 9. Onboarding New Governance Participants

When a new team member joins a governance body:

| Step | Action | Owner |
|------|--------|-------|
| 1 | Share this document + the relevant governance process document | Body chair |
| 2 | Provide read access to decision log for past 6 months | Body chair |
| 3 | Shadow 2 meetings before participating actively | New member |
| 4 | Assign a mentor from existing body members | Body chair |
| 5 | After 2 shadowed meetings: full participant status | Body chair confirms |
