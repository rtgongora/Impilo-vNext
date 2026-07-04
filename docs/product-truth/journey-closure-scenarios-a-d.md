# Product Truth — Journey Closure: Scenarios A–D (2026-07-04)

> **Status:** Proven on the live preview estate (commit `2bb73c1c`), repeatable.
> **This document is honest about what is proven vs still open.**
> Legend: ✅ proven live · 🟡 partial · ⬜ open.

## What is now TRUE (runtime-proven, not file-existence)

| Capability | Status | Evidence |
|---|---|---|
| Identity anchor chain: login → health_id → linked IDs → provider → ACTIVE assignment | ✅ | Scenario A phases 1–2 |
| Shift context on encounters (`pct_encounters.shift_id`, `X-Shift-ID` flow) | ✅ | Scenario A phase 3+6 |
| Queue lifecycle WAITING→CALLED→IN_CONSULTATION | ✅ | Scenario A phase 5 |
| OROS lab order → RESULT_AVAILABLE → patient timeline | ✅ | Scenario A phase 6 |
| Imaging: order → DICOM → Orthanc → report-link → OROS result | ✅ | Scenario A phase 7 |
| Teleconsult: VITO-guarded intake → pool routing → consent → accept → LiveKit token | ✅ | Scenario A phases 8–9 |
| Browser media join (TrackSubscribed) | ⬜ | blocked on firewall ports (PO action) |
| PCT encounter → auto DRAFT bill (event-driven) | ✅ | Scenario B step 2 |
| Coverage eligibility → payer/patient split (90/10 proven) | ✅ | Scenario B step 3 |
| Claim filed on finalize → adjudicated | ✅ | Scenario B step 4 |
| COSTA→MusheX intent linkage → SANDBOX card capture → PAID settlement | ✅ | Scenario B step 5 |
| No-cover failure path (`INELIGIBLE:NO_COVER`, 100% patient) | ✅ | Scenario B negative path |
| Pharmacy dispense → Dura stock decrement | ✅ | backlog ③, ledger row asserted |
| Fundo enrol → lessons → certificate (verificationDigest) | ✅ | Scenario C steps 1–5 |
| Governed CPD: certificate event → varapi candidate → council accept → points | ✅ | Scenario C step 6 |
| Learning → comms-hub notification (template-rendered, SENT) | ✅ | Scenario C steps 0+7 |
| Coverage split surfaced in web bill detail + apply-coverage + shortfall prefill | ✅ | `/finance/billing/[id]` (estate `2bb73c1c`) |
| VARAPI bootstrap policy enforcement (preload actor-types, self-claim binding) | ✅ | 11 unit tests |

## What preview proves vs what production still needs

- **Tariffs**: AHFOZ-*indicative* seed only (V020). Production = governed import
  of the real AHFOZ schedule (`POST /costa/v1/tariffs/import`).
- **Card rail**: SANDBOX adapter proven; real acquirer rails unproven.
- **Adjudication**: scriptable endpoint; payer EDI/portal integration open.
- **Kafka listeners**: preview opts in 9 services explicitly; production needs a
  deliberate enablement decision per service (see `values-full-preview.yaml`).
- **Pre-service coverage enforcement**: PARKED (DEC-0001, recommendation B).
- **Notification templates**: `learning.certificate.issued` self-provisioned by
  the proof script; production template governance is a national-admin workflow.
- **Preview pipeline is not production-safe** (existing truth record stands).

## Proof automation (repeatable)

- `scripts/e2e/run-all-scenarios.sh` — A+B+C with evidence transcripts.
- `scripts/test/run-scenario-{a,b}-smoke.sh` — post-deploy gate wrappers.
- Seeds: `scripts/operator/seed-scenario-a-estate.sh`,
  `scripts/operator/reconcile-keycloak-realm-users.sh` (both idempotent).
- Runbooks: `docs/journeys/scenario-{a,b,c,d}-*.md`.

## Session defect ledger (all fixed, all deployed)

Dead-by-construction loops: learning event-topic dual-emit; varapi jsonb bind;
no intent writer on certificate issuance; comms provider body/header contract;
openLesson emit-before-save NPE; costa PCT-consumer enum strictness + missing
TrustContext (poison redelivery); coverage idempotency keys; adjudicate outbox
key collision; mushex localhost credential URL; pacs↔OROS topic mismatches;
OROS coarse-status transitions; LiveKit service-link env crash; BFF fan-out
idempotency key reuse.
