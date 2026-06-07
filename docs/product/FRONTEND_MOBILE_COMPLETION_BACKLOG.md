# Frontend & Mobile Completion Backlog

> Living backlog derived from Product Truth Recovery, Core Transaction Matrix, and Experience Coherence Report.  
> **Re-baselined:** Phase 4.0 (2026-06-07) — see [`PHASE_4_0_REBASELINE_REPORT.md`](./PHASE_4_0_REBASELINE_REPORT.md)

## Measured state (Phase 4.0)

| Classification | Count |
|----------------|------:|
| transaction-complete | 8 |
| backend-ready-but-frontend-incomplete | 15 |
| backend-partial | 20 |
| mobile-missing | 2 |
| trust-security-incomplete | 1 |

Regenerate: `node scripts/product/generate-core-transaction-maps.mjs`

## Completed (evidence-gated)

| Journey | Web | Mobile | Notes |
|---------|-----|--------|-------|
| Queue / Walk-in Registration | `/queue/walk-in` → BFF queue entries | Provider queue | transaction-complete |
| Provider Patient Encounter | Encounter orchestration rail | EncounterScreen | transaction-complete |
| Core Transaction Orchestration Shell | `/core-transaction` feed | — | transaction-complete |
| Lab Order & Result | EncounterLabOrdersPanel + orders | Lab hooks | transaction-complete |
| Blood Donation | `/madi/donor/*` | Citizen MADI | transaction-complete |
| Blood Order & Crossmatch | `/madi/orders/[orderId]` | — | transaction-complete |
| Transfusion Episode | `/madi/transfusion/[episodeId]` | Provider pre-verify | transaction-complete |
| Haemovigilance | `/madi/haemovigilance/*` | — | transaction-complete |

## High priority — Phase 4.1 batches

| Priority | Journey | Classification | Gap | Target surfaces |
|----------|---------|----------------|-----|-----------------|
| 1 | Outpatient Consultation | backend-ready-but-FE-incomplete | Discharge + imaging write lanes | `/ehr/.../encounter`, orders tab |
| 2 | Appointment Scheduling | backend-partial | Citizen booking + check-in e2e | `/queue/scheduled`, mobile booking |
| 3 | Inpatient Admission | backend-partial | Ward movement + discharge correlation | `/ehr/.../inpatient` |
| 4 | Emergency / ED Encounter | trust-security-incomplete | Break-glass UX depth | ED flow + audit path |
| 5 | Health ID Issuance & Card Ops | backend-partial | Pickup verify BFF missing | `/operations/vito`, `/id-services` |
| 6 | Wellness & Lifestyle | backend-partial | `/wellness/routes` coming-soon stub | Wellness map surface |
| 7 | Payment / Billing / Claim | backend-partial | Payer-ops + MusheX depth | `/finance/payer-ops` |
| 8 | BFF parity debt | — | New BFF controllers unsurfaced | Launcher, monitoring, telemedicine analytics |
| 9 | Patient Search → encounter | backend-ready-but-FE-incomplete | Orchestration rail not on search | `/queue/search` |
| 10 | Context activation chain | mixed | Workspace/shift before clinical | Login → facility → workspace → shift |

## Web routes — known stubs (do not treat as complete)

| Route | Issue |
|-------|-------|
| `/clinical-tools/forms` | Form builder mock-stub |
| `/finance/payer-ops` | Stub adapters |
| `/wellness/routes` | Coming-soon (P1-fixture-risk in hotspot register) |

## Mobile — documented limitations

| Area | Classification | Limitation |
|------|----------------|------------|
| Public Health / CHW Outreach | mobile-missing | Field ops mobile thinner than web |
| Civil Registration (UBOMI) | mobile-missing | No mobile CRVS parity |
| Provider encounter | — | Mobile applies core-transaction next actions via BFF |

## Regression guardrails

- `npm run test:no-stubs` in `ui/one-ui-shell`
- `npm run test:routes` for route parity
- `scripts/guard/check-core-transaction-completion-evidence.sh`
- `scripts/guard/check-backend-frontend-parity.sh`
- `scripts/guard/check-mobile-parity.sh`
