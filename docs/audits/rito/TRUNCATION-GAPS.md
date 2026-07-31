# Rito — Truncation Gap Note (what the source brief must still supply)

> **Why this exists:** the source task prompt for Rito was **TRUNCATED** — it cut off mid the
> audit-keyword list. The lifecycle states, data models, dashboards, journeys, acceptance
> criteria and wave plan were **not delivered**. This design was produced from the partial brief
> + a grounded repo audit + the FROZEN SoR boundary. **No build round should be spawned until the
> items below are supplied/confirmed.** Every ⚠️ assumption in the design docs is collected here.

## A. Assumptions made (need user confirmation)

| # | Assumption (provisional) | Where | Confirm? |
|---|---|---|---|
| A1 | **Create new `services/rito-service`** (no existing service to extend) | capability-audit §1 | ☐ |
| A2 | **Port = 8390**; patient-safety takes a different free port | capability-audit §4 | ☐ |
| A3 | Case-type set (14 types incl. COMPLAINT…SATISFACTION_SURVEY) | design §1.1 | ☐ |
| A4 | Unified base lifecycle: NEW→TRIAGED→ACKNOWLEDGED→IN_PROGRESS→…→CLOSED→LEARNING (+ ROUTED_OUT, REJECTED) | design §2 | ☐ |
| A5 | **M&M / MPDSR review** — distinct `MPDSR_REVIEW` case type in Rito (not folded into generic clinical quality incident) | boundary §6, design §1.1 | ☑ PO confirmed 2026-07-31; built W14-E |
| A6 | Grey-zone rule: drug *reaction* → patient-safety; drug *process error/near-miss* → Rito | boundary §2 | ☐ |
| A7 | Internal quality audit/supervision ≠ tuso/indawo statutory inspection (distinct case records, shared checklists) | boundary §2, audit §2.3 | ☐ |
| A8 | Single polymorphic `Case` aggregate with typed detail (vs separate aggregates per type) | design §1.1 | ☐ |
| A9 | Risk modelled as a case type / risk register table | design §1.2 | ☐ |
| A10 | Learning loops publish into Fundo (learning-service) | design §3.3 | ☐ |
| A11 | Role set + assurance thresholds in policy spec | tshepo-policy-spec | ☐ |

## B. What the missing tail MUST define before build

1. **Concrete lifecycle states & transitions** — authoritative state names, allowed transitions,
   SLA timers per case type, escalation thresholds (the design's §2 is provisional).
2. **Authoritative data tables / field-level schema** — exact columns, enums (severity, harm level,
   incident categories, complaint categories, grievance classes), mandatory vs optional, retention,
   PII/anonymity field rules. (Design §1.2 is logical, not field-final.)
3. **Dashboard specifications** — exact KPIs/metrics, rollup scopes (facility/district/national),
   trend windows, regulator export format, what each workspace dashboard must show.
4. **Acceptance criteria** — per capability, the "done + honest" definition (no fake completions);
   test expectations; offline/federated + failure-path requirements.
5. **Wave plan** — the ordered build slices (which case types/flows ship in which wave; backend →
   BFF → UI sequencing; which UI surfaces are MVP vs later).
6. **Survey/checklist instrument library** — which standard packs (CSAT/NPS scales, supervision
   checklists, accreditation standard sets, audit tools) are in scope and their sources.
7. **M&M review scope decision** (A5) and **sentinel-event taxonomy + mandatory-reporting rules**.
8. **Governance** — confidentiality/anonymity policy, severe-harm dual-control thresholds,
   regulator export legal basis, data-sharing constraints (feeds policy spec finalisation).
9. **Nompilo guidance scope** — what proactive guidance/insights Rito surfaces (auditable, never
   overriding provider judgement).

## C. Confirmed by audit (NOT assumptions — grounded)

- No existing general quality/safety/feedback/CAPA/QI domain service exists (audit §1).
- `madi` haemovigilance, `tuso`/`indawo` regulatory inspection, `support` helpdesk,
  `surveillance` environmental complaints/signals are real, live, and are SoRs/sources to consume.
- `tshepo-audit-service`/`audit-ledger-service` are security audit, not clinical quality audit.
- `forms-service` has no survey/response semantics (schema-only) → Rito owns survey responses.
- Outbox pattern, trust headers, BFF controller pattern, signal-consumer pattern all exist to copy.
- experience-bff `ServiceEndpoints` registration is the one unavoidable shared-file touch.

## D. Hand-back

Design + SoR boundary + lane plan + policy spec + this gap note are complete. **STOP — do not
build.** These docs are the spawn spec for Rito's later build round, gated on the user supplying
section B and confirming section A.
