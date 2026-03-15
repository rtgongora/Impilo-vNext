# Quarterly Platform Roadmap — Template — Impilo vNext

> Wave 25 Deliverable | Status: Draft | Date: 2026-03-15

## 1. Purpose

This template is used to produce the quarterly platform roadmap for Impilo vNext. It is completed at the start of each quarter by the Platform Lead, reviewed by stakeholders, and serves as the north-star for engineering prioritization for the coming 13 weeks.

The roadmap is an input to sprint planning and change control. Items on the roadmap that require code changes follow the CAB process defined in `docs/rollout/change-control-and-cab.md`.

## 2. Ownership

| Role | Responsibility |
|------|---------------|
| **Platform Lead** | Authors the roadmap; prioritizes items; presents to stakeholders |
| **Clinical Director** | Validates clinical priorities; approves clinical feature sequencing |
| **Security Lead** | Inputs security priorities; validates security roadmap items |
| **Observability Lead** | Inputs observability-driven backlog items (from `docs/governance/observability-driven-backlog-process.md`) |
| **Rollout Lead** | Inputs rollout-phase requirements; site-driven feature requests |
| **Finance / Budget Owner** | Validates cost implications; approves infrastructure roadmap items |
| **Domain Owners** | Propose domain-specific items; commit to delivery estimates |

## 3. Roadmap Inputs

The quarterly roadmap draws from these sources, each with a designated owner:

| Input Source | Owner | Document/System |
|-------------|-------|-----------------|
| Observability-driven backlog | Observability Lead | Weekly observability review output (`docs/governance/observability-driven-backlog-process.md`) |
| Post-mortem action items | Incident Commanders | Incident register / post-mortem documents (`docs/governance/security-patch-and-incident-learning.md`) |
| Security vulnerability register | Security Lead | Vulnerability register (VR-{YYYY}-{NNN}) |
| Schema governance decisions | Schema Steward | Schema governance decision log (`docs/governance/schema-governance-cycle.md`) |
| Capacity plan recommendations | Platform Lead + SRE | Quarterly capacity plan (`docs/governance/cost-and-capacity-planning.md`) |
| Rollout feedback | Rollout Lead | Site go-live retrospectives, training feedback, support ticket trends |
| Clinical feature requests | Clinical Director | Clinical advisory board output |
| Compliance gaps | Data Governance Lead | Compliance matrix (`docs/compliance/full-platform-compliance-matrix.md`) |
| Technical debt register | Domain Owners | Engineering backlog |
| Federation/offline feedback | Respective domain owners | Pod operations feedback, offline sync metrics |

## 4. Prioritization Framework

### 4.1 Priority Levels

| Priority | Definition | Scheduling |
|----------|-----------|------------|
| **P0 — Mandatory** | Regulatory requirement, security critical, or SLO-threatening; cannot be deferred | Must be completed this quarter; trumps all other work |
| **P1 — High** | Directly enables rollout phase progression, resolves recurring incidents, or unblocks federation/offline scale | Scheduled in first 6 weeks of quarter |
| **P2 — Medium** | Improves platform reliability, developer experience, or clinical workflow efficiency | Scheduled for the quarter; may slip to next if capacity constrained |
| **P3 — Low** | Nice-to-have improvements, technical debt reduction, UX polish | Scheduled if capacity allows; otherwise deferred |

### 4.2 Scoring Criteria

Each roadmap candidate is scored on 4 dimensions (1–5 scale):

| Dimension | Weight | 5 (highest) | 1 (lowest) |
|-----------|--------|-------------|-------------|
| **Impact** | 40% | Affects all sites / all users / Ring 0 | Affects single service / few users |
| **Urgency** | 30% | Regulatory deadline or active SLO breach | No deadline; quality-of-life improvement |
| **Effort** | 20% | ≤ 1 sprint (favor small wins) | > 4 sprints (penalize large uncertain work) |
| **Risk of Deferral** | 10% | Deferring causes incident, compliance gap, or rollout block | Deferring has no measurable consequence |

**Priority Assignment**: Score ≥ 4.0 → P0/P1; Score 3.0–3.9 → P2; Score < 3.0 → P3.

## 5. Roadmap Template

```markdown
# Quarterly Platform Roadmap — Q{N} {YYYY}

> Author: {Platform Lead name}
> Date: {YYYY-MM-DD}
> Review Date: {stakeholder review date}
> Approved: {approval date}

## Executive Summary

{2-3 sentences describing the quarter's focus areas. Example:
"Q3 focuses on Phase 2 rollout support (10-15 sites), Ring 0 performance
optimization driven by Phase 1 observability data, and completion of the
remaining compliance matrix gaps for federation authority enforcement."}

## Quarter Themes

1. **{Theme 1}** — {1-sentence description}
2. **{Theme 2}** — {1-sentence description}
3. **{Theme 3}** — {1-sentence description}

## Roadmap Items

### P0 — Mandatory

| # | Item | Ring | Services Affected | Source | Owner | Target Sprint | Dependencies | Success Metric |
|---|------|------|-------------------|--------|-------|--------------|-------------|----------------|
| 1 | {item} | {0/1/2/Outer} | {services} | {input source} | {domain owner} | {sprint #} | {deps} | {measurable outcome} |

### P1 — High

| # | Item | Ring | Services Affected | Source | Owner | Target Sprint | Dependencies | Success Metric |
|---|------|------|-------------------|--------|-------|--------------|-------------|----------------|
| 1 | {item} | {ring} | {services} | {source} | {owner} | {sprint} | {deps} | {metric} |

### P2 — Medium

| # | Item | Ring | Services Affected | Source | Owner | Target Sprint | Dependencies | Success Metric |
|---|------|------|-------------------|--------|-------|--------------|-------------|----------------|
| 1 | {item} | {ring} | {services} | {source} | {owner} | {sprint} | {deps} | {metric} |

### P3 — Low (Stretch)

| # | Item | Ring | Services Affected | Source | Owner | Target Sprint |
|---|------|------|-------------------|--------|-------|--------------|
| 1 | {item} | {ring} | {services} | {source} | {owner} | {sprint or "if capacity"} |

### Deferred (Explicitly Not This Quarter)

| # | Item | Reason for Deferral | Earliest Reconsideration |
|---|------|-------------------|-------------------------|
| 1 | {item} | {reason} | Q{N+1} |

## Capacity Allocation

| Category | % of Engineering Capacity | Rationale |
|----------|--------------------------|-----------|
| P0 Mandatory | {%} | {reason} |
| P1 High | {%} | {reason} |
| P2 Medium | {%} | {reason} |
| P3 Low / Tech Debt | {%} | {reason} |
| Operational (on-call, support, toil) | {%} | Based on current support load |
| Buffer (unplanned work) | 10–15% | Always reserve for incidents and emergencies |

## Key Risks

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| {risk} | High/Medium/Low | High/Medium/Low | {mitigation} |

## Dependencies on External Teams/Systems

| Dependency | External Party | Needed By | Status |
|-----------|---------------|-----------|--------|
| {dep} | {party} | {date} | Confirmed / At Risk / Blocked |

## Review and Approval

| Role | Name | Date | Decision |
|------|------|------|----------|
| Platform Lead | {name} | {date} | Author |
| Clinical Director | {name} | {date} | Approve / Reject |
| Security Lead | {name} | {date} | Approve / Reject |
| Finance / Budget | {name} | {date} | Approve / Reject |
| Rollout Lead | {name} | {date} | Approve / Reject |
```

## 6. Mid-Quarter Check-In

At the midpoint of each quarter (week 7), the Platform Lead conducts a brief review:

| Agenda Item | Duration | Detail |
|-------------|----------|--------|
| P0/P1 item status | 15 min | On track / at risk / blocked |
| Capacity re-assessment | 10 min | Has unplanned work consumed the buffer? |
| Re-prioritization (if needed) | 10 min | Move items between priorities or defer |
| New items emerged | 10 min | Any new P0/P1 items from incidents, security, or rollout? |
| Updated roadmap published | 5 min | Annotated version with status updates |

**Schedule**: Wednesday of week 7, 50 minutes. Same participants as quarterly review.

## 7. End-of-Quarter Review

At quarter-end, the Platform Lead presents outcomes:

| Metric | Target |
|--------|--------|
| P0 item completion rate | 100% |
| P1 item completion rate | ≥ 80% |
| P2 item completion rate | ≥ 60% |
| Unplanned work ratio | ≤ 15% of total capacity |
| Items deferred to next quarter | Track count and reasons |
| Roadmap accuracy (planned vs delivered) | ≥ 70% of items completed as planned |

Results feed into the quarterly platform review (see `docs/governance/platform-governance-cadence.md`).

## 8. Roadmap Item Lifecycle

```
┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐
│ PROPOSED │───▶│ SCORED   │───▶│ APPROVED │───▶│ IN       │───▶│ COMPLETED│
│          │    │          │    │ (on      │    │ PROGRESS │    │          │
│          │    │          │    │ roadmap) │    │          │    │          │
└──────────┘    └──────────┘    └──────────┘    └──────────┘    └──────────┘
                      │                               │
                      ▼                               ▼
                ┌──────────┐                    ┌──────────┐
                │ DEFERRED │                    │ BLOCKED  │
                │ (next Q) │                    │ (tracked)│
                └──────────┘                    └──────────┘
```

- **PROPOSED**: Item submitted from any input source.
- **SCORED**: Evaluated using the 4-dimension scoring criteria.
- **APPROVED**: Placed on the quarterly roadmap after stakeholder review.
- **IN PROGRESS**: Active development; tracked in sprint board.
- **COMPLETED**: Delivered and verified (success metric met).
- **DEFERRED**: Explicitly postponed with reason and earliest reconsideration date.
- **BLOCKED**: Cannot proceed due to external dependency; tracked until unblocked.
