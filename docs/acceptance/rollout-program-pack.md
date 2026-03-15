# Wave 24 — National Rollout Program Acceptance Pack

> Status: Draft | Date: 2026-03-15

## 1. Wave Summary

**Wave 24** delivers the operational framework for adopting Impilo vNext at national scale. This wave is documentation/process-heavy — its outputs are operational processes, not code. The deliverables provide the machinery for site assessment, training, release management, change control, and support that enables Phases 1–4 of the national rollout.

### Prerequisites (Waves 19–23)

| Wave | Dependency | Status |
|------|-----------|--------|
| 19 | Production readiness gate (SLOs, alerting, security posture verified) | Required |
| 20 | Disaster recovery (backup/restore, RPO/RTO measured, failover tested) | Required |
| 21 | Federation pilot (pod registration, authority enforcement working) | Required |
| 22 | Offline pilot (edge workflow proven, reconciliation working) | Required |
| 23 | Dual-mode ecosystem (developer portal, partner onboarding working) | Required |

## 2. Deliverable Inventory

| # | Deliverable | Path | Status |
|---|-------------|------|--------|
| 1 | National Rollout Plan | `docs/rollout/national-rollout-plan.md` | [ ] Reviewed |
| 2 | Site Readiness Checklist | `docs/rollout/site-readiness-checklist.md` | [ ] Reviewed |
| 3 | Release Train Model | `docs/rollout/release-train-model.md` | [ ] Reviewed |
| 4 | Change Control and CAB | `docs/rollout/change-control-and-cab.md` | [ ] Reviewed |
| 5 | Support Operating Model | `docs/rollout/support-operating-model.md` | [ ] Reviewed |
| 6 | Training Alignment with Fundo | `docs/rollout/training-alignment-with-fundo.md` | [ ] Reviewed |
| 7 | Rollout Program Acceptance Pack | `docs/acceptance/rollout-program-pack.md` | (this document) |

## 3. Acceptance Criteria

### A) National Rollout Plan

- [ ] Phased rollout model defined with clear entry/exit criteria per phase
- [ ] Rollout sequenced by service ring (Ring 0 → 1 → 2 → Outer)
- [ ] Per-site cutover process defined (pre-cutover, cutover day, post-cutover)
- [ ] Rollback strategy defined per ring with time windows (72h/48h/24h/instant)
- [ ] Full site rollback procedure documented (last resort)
- [ ] Risk register with mitigations for top rollout risks
- [ ] Governance meetings defined (standup, gate review, CAB, retrospective)
- [ ] Success metrics defined with measurable targets
- [ ] Province sequencing template provided (marked as assumption where facts unavailable)

### B) Site Readiness Checklist

- [ ] Site classification by tier (1–4) with infrastructure and connectivity models
- [ ] Connectivity assessment with minimum requirements per tier (bandwidth, uptime, latency)
- [ ] Power assessment with UPS and generator requirements
- [ ] Hardware assessment for servers (Tier 1/2 pods) and client devices
- [ ] Network port requirements documented (inbound, internal, outbound)
- [ ] Environment assessment (server room, temperature, physical security)
- [ ] Staffing assessment (IT contact, clinical champions, training completion)
- [ ] Data readiness checks (facility, provider, terminology, formulary registration)
- [ ] Security assessment (TSHEPO trust chain, Keycloak, TLS, audit chain)
- [ ] Pass/Conditional/Fail scoring with sign-off matrix
- [ ] All checks tied to actual platform services and ports

### C) Release Train Model

- [ ] Ring-based cadence defined (Monthly/Bi-weekly/Weekly/Continuous)
- [ ] Step-by-step release process per ring (code complete → canary → progressive rollout)
- [ ] Release gates matrix (G1–G10) with required/optional per ring
- [ ] Gate failure protocol defined
- [ ] Versioning convention specified
- [ ] Rollback procedures with Helm commands per ring
- [ ] Change windows defined with blackout periods (month-end, holidays)
- [ ] Release train calendar template provided
- [ ] Hotfix process for P1/P2 (emergency bypass path)
- [ ] Federation coordination for pod-level releases
- [ ] Gates reference actual CI/CD artifacts (contract tests, security scans, smoke tests)

### D) Change Control and CAB

- [ ] CAB membership and roles defined with quorum rules
- [ ] Meeting cadence: weekly (Normal), ad-hoc (Emergency), scheduled (Major)
- [ ] Change categories: Standard (pre-approved), Normal, Emergency, Major
- [ ] Ring-to-category mapping defined
- [ ] Change request lifecycle (Draft → Submitted → Reviewed → Approved → Deployed)
- [ ] CR template with impact assessment, testing evidence, rollback plan, approvals
- [ ] Pre-approved standard changes enumerated (SC-01 through SC-10)
- [ ] Change metrics defined (success rate, lead time, emergency ratio)
- [ ] Monthly CAB report template provided
- [ ] Escalation path for disputed changes
- [ ] Compliance integration (POPIA, clinical safety, audit trail, SLO)

### E) Support Operating Model

- [ ] Multi-tier support structure defined (L0 self-service → L1 → L2 → L3 → L4)
- [ ] Incident severity classification (P1–P4) with clinical impact definitions
- [ ] Response and resolution SLAs per severity
- [ ] Escalation matrix by service domain (Trust, Registry, Clinical, Finance, etc.)
- [ ] Escalation triggers defined (queue depth, SLA miss, Ring 0 failure, error budget breach)
- [ ] On-call rotation structure (L3, L4, Security)
- [ ] War room protocol for P1 incidents
- [ ] Support staffing model scaled by rollout phase
- [ ] Scaling triggers for adding support staff
- [ ] Tooling requirements identified (ticketing, paging, monitoring, chat, knowledge base)
- [ ] Runbook template per service
- [ ] Known-issue database structure
- [ ] Continuous improvement metrics and feedback loops

### F) Training Alignment with Fundo

- [ ] Six Fundo modules defined (100–600) with audience, duration, delivery mode
- [ ] Certification criteria per module (quiz, practical exam, teach-back)
- [ ] Module-to-service mapping: each module maps to specific Impilo services and UI apps
- [ ] Training sandbox environment specified (namespace, synthetic data, accounts)
- [ ] Training timeline relative to site go-live (T-30 to T+30)
- [ ] Training capacity model scaled by rollout phase
- [ ] Champion-led cascade model for Phase 3+ scaling
- [ ] Training gates integrated with site readiness checklist
- [ ] Training content maintenance process (update triggers, versioning)
- [ ] Training metrics defined (completion rate, pass rate, post-go-live correlation)
- [ ] Responsibilities matrix (training lead, developers, trainers, champions, managers)

## 4. Cross-Cutting Verification

### Platform Alignment

- [ ] All service references use correct service names from `docs/plan/SERVICE_CATALOG.md`
- [ ] All port numbers match the service port map
- [ ] Ring assignments match the architectural ring model (Ring 0/1/2/Outer)
- [ ] Trust-first model referenced (Envoy ext_authz → TSHEPO) in security-relevant sections
- [ ] Outbox pattern and event-driven architecture referenced where applicable
- [ ] Federation model (pod-based deployment) considered in release and support models
- [ ] Offline-first patterns (CRDT, O-CPID, edge data packs) addressed in training and support

### Operational Readiness

- [ ] Site readiness checklist is actionable (each item has evidence requirements)
- [ ] Rollback procedures include actual commands (Helm, kubectl, feature flags)
- [ ] SLAs are measurable and have monitoring sources identified
- [ ] Staffing models scale with rollout phases
- [ ] Training gates are enforceable (binary pass/fail, tied to LMS records)
- [ ] Change control integrates with release train schedule
- [ ] Support model covers after-hours and weekend scenarios

### Assumptions Clearly Marked

- [ ] Province sequencing marked as assumption (requires Ministry input)
- [ ] Specific tooling vendors (ticketing, paging, chat) marked as TBD
- [ ] Training content repository location marked as TBD
- [ ] Exact site counts per phase marked as template (to be filled per deployment plan)
- [ ] No fabricated Ministry or government data

## 5. Exit Criteria

| # | Criterion | Evidence | Status |
|---|----------|----------|--------|
| 1 | All 6 rollout documents created and internally consistent | Document review | [ ] |
| 2 | Site readiness checklist is actionable for Phase 1 pilot sites | Checklist dry-run on 1 site | [ ] |
| 3 | Training program (Impilo Fundo) modules mapped to platform services | Module-service matrix in training doc | [ ] |
| 4 | Release train model defines gates that reference actual CI/CD artifacts | Gate matrix in release train doc | [ ] |
| 5 | CAB process includes templates ready for first change request | CR template in change control doc | [ ] |
| 6 | Support model defines escalation paths tied to service domains | Escalation matrix in support doc | [ ] |
| 7 | Rollback expectations defined per ring with time windows | Rollback table in rollout plan + release train model | [ ] |
| 8 | All assumptions explicitly marked (no fabricated facts) | Document review for assumption markers | [ ] |
| 9 | Documents reference actual platform services, ports, and architecture | Cross-reference with SERVICE_CATALOG.md | [ ] |

## 6. Sign-Off

| Role | Name | Date | Decision |
|------|------|------|----------|
| Program Director | _________________ | _______ | Accept / Reject |
| Clinical Director | _________________ | _______ | Accept / Reject |
| IT Director | _________________ | _______ | Accept / Reject |
| Security Officer | _________________ | _______ | Accept / Reject |
| Platform Lead | _________________ | _______ | Accept / Reject |
| Training Program Lead | _________________ | _______ | Accept / Reject |

## 7. Document Cross-Reference Map

```
national-rollout-plan.md
  ├── references: site-readiness-checklist.md (cutover prerequisites)
  ├── references: release-train-model.md (ring-based deployment)
  ├── references: change-control-and-cab.md (governance)
  └── references: support-operating-model.md (hypercare, steady-state)

site-readiness-checklist.md
  ├── references: training-alignment-with-fundo.md (staffing training gates)
  └── references: national-rollout-plan.md (site classification)

release-train-model.md
  ├── references: change-control-and-cab.md (CAB approval gates)
  └── references: support-operating-model.md (error budget triggers)

change-control-and-cab.md
  ├── references: release-train-model.md (ring schedules, change windows)
  └── references: support-operating-model.md (incident-driven emergency changes)

support-operating-model.md
  ├── references: training-alignment-with-fundo.md (L1 champions = Fundo-600 grads)
  └── references: national-rollout-plan.md (staffing scales by phase)

training-alignment-with-fundo.md
  ├── references: site-readiness-checklist.md (training gates)
  └── references: support-operating-model.md (L1 = clinical champions)
```
