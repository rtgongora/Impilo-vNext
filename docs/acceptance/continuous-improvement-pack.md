# Wave 25 — Continuous Improvement Loop Acceptance Pack

> Status: Draft | Date: 2026-03-15

## 1. Wave Summary

**Wave 25** establishes the recurring governance and operational processes that keep Impilo vNext evolving safely after national rollout. Unlike prior waves, Wave 25 has no terminal exit — it defines ongoing cadences. This acceptance pack validates that all governance artifacts are concrete, actionable, and integrated with the platform's actual services, tooling, and prior wave outputs.

### Prerequisites

| Wave | Dependency | Status |
|------|-----------|--------|
| 19 | Production readiness (SLOs, alerting, security posture) | Required |
| 20 | Disaster recovery (backup/restore, failover) | Required |
| 21 | Federation pilot (pod registration, authority enforcement) | Required |
| 22 | Offline pilot (edge workflow, reconciliation) | Required |
| 23 | Dual-mode ecosystem (developer portal, partner onboarding) | Required |
| 24 | National rollout (release trains, CAB, support model) | Required |

## 2. Deliverable Inventory

| # | Deliverable | Path | Status |
|---|-------------|------|--------|
| 1 | Schema Governance Cycle | `docs/governance/schema-governance-cycle.md` | [ ] Reviewed |
| 2 | Observability-Driven Backlog Process | `docs/governance/observability-driven-backlog-process.md` | [ ] Reviewed |
| 3 | Security Patch Pipeline & Incident Learning | `docs/governance/security-patch-and-incident-learning.md` | [ ] Reviewed |
| 4 | Cost Controls & Capacity Planning | `docs/governance/cost-and-capacity-planning.md` | [ ] Reviewed |
| 5 | Quarterly Platform Roadmap Template | `docs/governance/quarterly-platform-roadmap-template.md` | [ ] Reviewed |
| 6 | Platform Governance Cadence | `docs/governance/platform-governance-cadence.md` | [ ] Reviewed |
| 7 | Continuous Improvement Acceptance Pack | `docs/acceptance/continuous-improvement-pack.md` | (this document) |

## 3. Acceptance Criteria

### A) Schema Governance Cycle

- [ ] Schema types covered: Kafka events, database (Flyway), REST API (OpenAPI), trust headers, FHIR profiles
- [ ] Ownership model: Schema Steward, Domain Owners (7 domain clusters mapped to services), Data Governance Lead, Security Lead, Consumer Representatives
- [ ] Change classification: 5 categories (Additive, Default-Safe, Behavioral, Breaking, Trust-Critical) with distinct approval paths and lead times
- [ ] Governance cadence: bi-weekly schema change review, monthly compatibility audit, monthly event contract review, ad-hoc breaking change assessment, quarterly trust header review
- [ ] Schema change process: standard flow (PR → CI validation → 48h review → approval) and breaking change flow (SCP document → board review → CAB Major CR → phased migration)
- [ ] Deprecation policy: minimum 6-month deprecation window for event schemas and APIs; 12-month for trust headers
- [ ] Schema health metrics: 8 metrics defined with targets and measurement sources
- [ ] Decision log: format defined with ID scheme (SGD-{YYYY}-{NNN}), all required fields specified
- [ ] Tooling integration: references schema-registry-service, libs/tech-companion, libs/contract-tests, Flyway, CI pipeline, compliance matrix
- [ ] Ties to actual repo: EventEnvelope record in `libs/shared-kernel-java`, outbox table schema from `docs/plan/EVENTING_AND_TOPICS.md`, trust headers from `libs/tshepo-contracts`, golden contract tests from `libs/tech-companion-harness`

### B) Observability-Driven Backlog Process

- [ ] Signal sources: Prometheus metrics (9 specific metric names), Loki log queries (5 patterns), OpenTelemetry traces (3 detection methods), Alertmanager alerts
- [ ] Weekly observability review: 50-min structured agenda (8 items with time allocation and owner), held every Monday 09:30
- [ ] Signal-to-backlog conversion: 12 signals with specific thresholds, auto-generated priorities (P1–P4), and defined response expectations
- [ ] Backlog item template: OBS-{YYYY}-{WW}-{NN} format with all required fields
- [ ] Quarterly observability health report: structured template covering SLO compliance per Ring 0/1 service, error budget status, alert health, backlog impact
- [ ] Integration points: sprint planning, CAB (P1=Emergency CR, P2=Normal CR), incident post-mortems, capacity planning, schema governance, quarterly platform review
- [ ] Rollout feedback integration: 5 rollout-specific signals with thresholds and actions
- [ ] All metric names reference actual metric conventions from `docs/ops/observability-conventions.md` and `docs/ops/OBSERVABILITY_BASELINE.md`

### C) Security Patch Pipeline & Incident Learning

- [ ] Vulnerability detection: 7 detection sources (Dependabot, Snyk/Trivy, OWASP Dep-Check, manual CVE review, pen testing, bug bounty, tshepo-audit-service anomaly detection)
- [ ] Vulnerability triage: CVSS-based severity with Impilo context adjustments (PII handler escalation, trust-plane escalation, internal-only reduction)
- [ ] Patch SLAs: 4 severity levels with specific timelines (Critical: 24h develop/72h deploy-all; High: 3d/7d; Medium: 15d/next train; Low: next maintenance)
- [ ] Patch development process: 5-step flow (register → develop → CR → deploy per ring → close)
- [ ] Dependency update cadence: 11 dependency types with specific update frequencies and processes (Java, Spring Boot, PostgreSQL, Kafka, Keycloak, Envoy, HAPI FHIR, Node.js, npm, Docker base images)
- [ ] Vulnerability register: VR-{YYYY}-{NNN} format with all required fields; weekly, monthly, and quarterly review cadence
- [ ] Incident severity classification: SEV-1 through SEV-4 with clinical impact definitions and concrete examples referencing actual services
- [ ] Post-mortem requirements: per-severity review type, timeline, and participant requirements
- [ ] Blameless post-mortem process: 5-step flow (resolve → prepare → meet → publish → track)
- [ ] Post-mortem template: comprehensive template with 10 sections including detection analysis, timeline, root cause, action items (categorized: Prevent/Detect/Respond/Recover)
- [ ] Incident metrics: 8 metrics with targets (MTTD, MTTR, post-mortem completion rate, repeat incidents, patch SLA compliance)
- [ ] Incident register: INC-{YYYY}-{NNN} format with all fields
- [ ] Integration: CAB (emergency CRs), release train (ring deployment), observability (detect action items), schema governance (schema-caused incidents), capacity planning, compliance

### D) Cost Controls & Capacity Planning

- [ ] Cost categories: 9 categories (Compute, DB, Object Storage, Kafka, Redis, Network, Keycloak, Observability Stack, CI/CD) with specific metrics and collection methods
- [ ] Per-service resource baselines: table with CPU/memory/storage/replicas for representative services across all rings
- [ ] Capacity thresholds: 10 resource types with warning and critical thresholds, auto-actions, and manual actions
- [ ] Alert definitions: Prometheus alert rules (YAML) for capacity warnings and critical thresholds
- [ ] Monthly cost review: 60-min structured agenda (6 items), held first Tuesday each month, chaired by Platform Lead
- [ ] Monthly cost report template: structured template with cost summary, top-5 drivers, right-sizing actions, idle cleanup, budget status
- [ ] Quarterly capacity planning: 90-min structured process (6 steps from utilization snapshot to budget request)
- [ ] Quarterly capacity plan template: structured template with current utilization, growth rates, projections, bottleneck analysis, scaling recommendations, budget request
- [ ] Cost optimization strategies: 8 strategies with methods, review frequency, and owners
- [ ] Rollout-driven demand forecasting: per-phase projections (sites, users, compute, DB, Kafka) for Phase 1–4
- [ ] Per-phase scaling actions: specific scaling actions at each phase transition (replicas, DB expansion, Kafka partitions, infrastructure additions)
- [ ] Decision log integration: CPD-{YYYY}-{NNN} format

### E) Quarterly Platform Roadmap Template

- [ ] Ownership: 7 roles defined with specific responsibilities
- [ ] Roadmap inputs: 10 input sources, each with designated owner and source document reference
- [ ] Prioritization framework: P0–P3 priority levels with definitions and scheduling expectations
- [ ] Scoring criteria: 4 dimensions (Impact 40%, Urgency 30%, Effort 20%, Risk of Deferral 10%) with 1–5 scale descriptions
- [ ] Roadmap template: comprehensive markdown template with executive summary, themes, items by priority (with Ring, services, source, owner, target sprint, dependencies, success metric), capacity allocation, risks, dependencies, approval matrix
- [ ] Deferred items tracked explicitly with reason and earliest reconsideration
- [ ] Mid-quarter check-in: 50-min review at week 7 with structured agenda
- [ ] End-of-quarter review metrics: 6 metrics (P0/P1/P2 completion rates, unplanned work ratio, deferred items, roadmap accuracy)
- [ ] Item lifecycle: 7 states (Proposed → Scored → Approved → In Progress → Completed / Deferred / Blocked)
- [ ] Integration: roadmap items that require code changes follow CAB process; capacity items come from cost & capacity planning; backlog items come from observability review

### F) Platform Governance Cadence

- [ ] Governance bodies: 6 bodies defined (CAB, Schema Board, Observability Review, Cost & Capacity, Security Review, Quarterly Platform Review) with chairs, quorum rules, mandates
- [ ] Weekly cadence: 4 meetings (Observability Review Monday, Security Triage Monday, CAB Tuesday, Schema Change Review Wednesday bi-weekly)
- [ ] Monthly cadence: 6 meetings (Cost Review, Schema Compatibility Audit, CAB Retrospective, Support Metrics Review, Event Contract Review, Post-Mortem Action Item Review) — each assigned to specific weeks
- [ ] Quarterly cadence: 5 activities (Capacity Planning, Quarterly Platform Review, Roadmap Kickoff, Mid-Quarter Check-In, Trust Header Review)
- [ ] Semi-annual/annual: pen testing, DR failover exercise, reserved capacity review, training content refresh, compliance audit
- [ ] Visual calendar: monthly view showing all meetings by week
- [ ] Quarterly platform review: detailed 120-min agenda (10 items with presenters and input documents)
- [ ] Quarterly review output template: decisions, roadmap approval, escalations, action items, attendees
- [ ] Decision log: unified across all bodies with 7 prefix categories (CR, SGD, OBS, CPD, VR, INC, QPR) and retention periods
- [ ] Escalation paths: 8 escalation routes defined (from → to → trigger → timeline)
- [ ] Governance health metrics: 6 metrics with targets (meeting adherence, decision throughput, cycle time, overhead ratio, action item completion, satisfaction)
- [ ] Anti-patterns: 5 anti-patterns with signals and remedies (governance theater, decision bottleneck, alert fatigue, governance bypass, stale decisions)
- [ ] Onboarding process: 5-step onboarding for new governance participants

## 4. Cross-Cutting Verification

### Platform Alignment

- [ ] All service references use correct names from `docs/plan/SERVICE_CATALOG.md`
- [ ] Port numbers match the service port map (TSHEPO 8081/8181-8185, VITO 8082, VARAPI 8083, TUSO 8084, ZIBO 8085, PCT 8088, OROS 8089, BUTANO 8090, Kafka 9092, Envoy 10000)
- [ ] Ring assignments match architecture (Ring 0 kernel, Ring 1 clinical, Ring 2 platform, Outer UI)
- [ ] Metric names reference actual conventions from `docs/ops/observability-conventions.md` (e.g., `impilo_{service}_{domain}_{unit}`, `http_server_requests_seconds`, HikariCP metrics)
- [ ] Event schema references match `docs/plan/EVENTING_AND_TOPICS.md` (EventEnvelope fields, topic naming `{channel}.{service}.{aggregate}.{action}`, 5-channel model)
- [ ] Trust header references match `libs/tshepo-contracts` (14+ headers including X-Tenant-ID, X-Pod-ID, X-Request-ID, X-Correlation-ID)
- [ ] Compliance matrix references match `docs/compliance/full-platform-compliance-matrix.md` column definitions
- [ ] Outbox pattern references match outbox table schema from EVENTING_AND_TOPICS.md
- [ ] Security baseline references match `docs/ops/security-baseline.md` (mTLS, JWT audiences, rate limits)

### Wave 24 Integration

- [ ] CAB process references match `docs/rollout/change-control-and-cab.md` (4 change categories, CR template, approval paths)
- [ ] Release train references match `docs/rollout/release-train-model.md` (ring cadence, gates, rollback windows)
- [ ] Support model references match `docs/rollout/support-operating-model.md` (L0–L4 tiers, severity SLAs, escalation matrix)
- [ ] Site readiness references match `docs/rollout/site-readiness-checklist.md` (tier classification, assessment items)
- [ ] Training references match `docs/rollout/training-alignment-with-fundo.md` (Fundo-100 through Fundo-600)
- [ ] Rollout phase references match `docs/rollout/national-rollout-plan.md` (Phase 1–4, site counts)

### Internal Consistency

- [ ] Schema governance → CAB: breaking changes require CAB Major CR
- [ ] Observability → backlog: P1 signals create Emergency CRs; P2 create Normal CRs
- [ ] Security → CAB: Critical/High patches follow Emergency CR path
- [ ] Security → observability: post-mortem "Detect" action items feed into alert tuning queue
- [ ] Cost → quarterly review: capacity plan is required input to quarterly platform review
- [ ] Roadmap → all: roadmap draws from observability backlog, incident action items, vulnerability register, schema decisions, capacity recommendations
- [ ] Cadence → all: master calendar includes all meetings from all governance documents without conflicts
- [ ] Decision logs: all 7 prefix categories (CR, SGD, OBS, CPD, VR, INC, QPR) are used consistently across documents

## 5. Exit Criteria

Wave 25 defines ongoing cadences, not a one-time deliverable. The following criteria indicate the loop is operational:

| # | Criterion | Evidence | Status |
|---|----------|----------|--------|
| 1 | All 6 governance documents created, internally consistent, and cross-referenced | Document review + cross-reference map (Section 4) | [ ] |
| 2 | Schema governance has a named Schema Steward and defined meeting cadence | Schema governance doc Section 2 + cadence doc master calendar | [ ] |
| 3 | Observability review has a defined weekly meeting with structured agenda and signal-to-backlog conversion rules | Observability doc Sections 4-5 | [ ] |
| 4 | Security patch pipeline has SLAs per severity with specific timelines | Security doc Section 3.3 | [ ] |
| 5 | Incident learning has a blameless post-mortem process with template and action item tracking | Security doc Sections 5.3-5.4 | [ ] |
| 6 | Cost review has monthly cadence with structured report template | Cost doc Section 5 | [ ] |
| 7 | Capacity planning has quarterly process with demand forecasting tied to rollout phases | Cost doc Sections 6 and 8 | [ ] |
| 8 | Quarterly roadmap has a prioritization framework with scoring criteria and template | Roadmap doc Sections 4-5 | [ ] |
| 9 | Master governance cadence consolidates all meetings without conflicts | Cadence doc Sections 3-4 | [ ] |
| 10 | All governance artifacts reference actual platform components (services, metrics, schemas, tools) | Cross-cutting verification (Section 4) | [ ] |
| 11 | Decision log formats defined for all governance bodies | Cadence doc Section 6 | [ ] |
| 12 | Escalation paths defined from every governance body to resolution | Cadence doc Section 7 | [ ] |

## 6. Operational Readiness Indicators

Once Wave 25 processes are running, these indicators confirm the loop is healthy:

| Indicator | Measurement | Healthy State |
|-----------|-------------|---------------|
| Schema governance reviews happening on schedule | Meeting minutes / decision log entries | ≥ 2 reviews per month |
| Observability-driven backlog producing items | OBS-{YYYY}-{WW}-{NN} entries in backlog | ≥ 3 items per month (signal that review is actively triaging) |
| Observability items being resolved | Backlog item resolution rate | ≥ 70% resolved within target sprint |
| Security patches flowing within SLA | VR register: SLA Met = Yes rate | ≥ 95% |
| Post-mortems completed for SEV-1/2 | INC register: post-mortem status | 100% within timeline |
| Post-mortem action items closing | Action item completion rate | ≥ 90% by deadline |
| Cost reviews producing optimization actions | Monthly cost report action items | ≥ 1 optimization action per month |
| Capacity thresholds not breached without prior scaling | Critical threshold alert count | ≤ 1 per quarter (proactive scaling prevents critical alerts) |
| Quarterly roadmap published and approved | Roadmap document with sign-offs | 100% (every quarter) |
| Governance overhead within bounds | Engineering time in governance meetings | ≤ 5% of total engineering capacity |

## 7. Sign-Off

| Role | Name | Date | Decision |
|------|------|------|----------|
| Platform Lead | _________________ | _______ | Accept / Reject |
| Clinical Director | _________________ | _______ | Accept / Reject |
| Security Lead | _________________ | _______ | Accept / Reject |
| Observability Lead | _________________ | _______ | Accept / Reject |
| Schema Steward | _________________ | _______ | Accept / Reject |
| Finance / Budget Owner | _________________ | _______ | Accept / Reject |
| Data Governance Lead | _________________ | _______ | Accept / Reject |

## 8. Document Cross-Reference Map

```
schema-governance-cycle.md
  ├── references: docs/plan/EVENTING_AND_TOPICS.md (EventEnvelope, topics)
  ├── references: docs/compliance/full-platform-compliance-matrix.md (compliance status)
  ├── references: libs/tshepo-contracts (trust headers)
  ├── references: libs/tech-companion, libs/contract-tests (enforcement)
  └── feeds into: change-control-and-cab.md (breaking changes → CAB Major CR)

observability-driven-backlog-process.md
  ├── references: docs/ops/observability-conventions.md (metric names)
  ├── references: docs/ops/OBSERVABILITY_BASELINE.md (health endpoints)
  ├── feeds into: change-control-and-cab.md (P1→Emergency CR, P2→Normal CR)
  ├── feeds into: security-patch-and-incident-learning.md (missed detections)
  └── feeds into: cost-and-capacity-planning.md (saturation signals)

security-patch-and-incident-learning.md
  ├── references: docs/ops/security-baseline.md (mTLS, JWT, rate limits)
  ├── feeds into: change-control-and-cab.md (patch CRs)
  ├── feeds into: observability-driven-backlog-process.md (detect action items → alert tuning)
  ├── feeds into: cost-and-capacity-planning.md (capacity-caused incidents)
  └── feeds into: schema-governance-cycle.md (schema-caused incidents)

cost-and-capacity-planning.md
  ├── references: docs/rollout/national-rollout-plan.md (phase 1-4 site counts)
  ├── references: docs/rollout/release-train-model.md (deployment resource needs)
  └── feeds into: quarterly-platform-roadmap-template.md (scaling recommendations)

quarterly-platform-roadmap-template.md
  ├── draws from: all other governance documents (inputs)
  ├── references: docs/rollout/change-control-and-cab.md (roadmap items → CRs)
  └── feeds into: platform-governance-cadence.md (quarterly review agenda)

platform-governance-cadence.md
  ├── consolidates: all governance document cadences
  ├── references: all Wave 24 rollout documents
  └── defines: master calendar, escalation paths, governance health metrics
```
