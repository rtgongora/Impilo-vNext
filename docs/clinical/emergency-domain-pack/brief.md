# Emergency domain pack — brief

## One-line scope

One facility-scoped **emergency episode** per acute presentation, journey-anchored (CC-5), with
WHO IITT as the acuity of record, honest absence on every board tile, and national indicators
that execute against a Kafka projection — never against `pct.*` from reporting-service's own DB.

## In scope (this pack)

- `pct.emergency_episode` spine: open / arrive / state / identity-link / alerts / handover /
  disposition (15 types) / observation stay / command summary
- ED visit elevation so ordinary walk-in/ambulance registration mints the episode
- IITT engine (`libs/emergency-domain`) + triage authority wiring
- Mental-health acceptance side of `emergency_handover(MENTAL_HEALTH)` (service + BFF + UI)
- Offline Tier B (`NOT_TRIAGEABLE_OFFLINE`) and feature-scoped service worker
- Theatre-pattern reporting projection + DSEC element mapping
- Realtime channel resolver (W19) for episode invalidation hints

## Explicitly out of scope

| Topic | Owner / disposition |
|-------|---------------------|
| Inter-facility ambulance transport | **Scoped OUT** — Nhume `PATIENT` is intra-facility porter only; model transfer as `emergency_handover(TO_FACILITY)` + PCT referral + Daidzai EMS mission where one exists |
| ~140 syndrome content tranches (W14) | **Skipped** — sourcing blocker; engines/schema ready, content not fabricated |
| Pharmacovigilance / haemovigilance | patient-safety-service / madi |
| National trauma-centre level classification | Declined — no MoHCC scheme found to encode |
| Fabricating a Helm image digest for undeployed services | Deliberately omitted |

## Doctrine anchors

- Care Continuum: PCT owns the clinical continuum; an emergency episode is a facility visit
  component, not a second continuum.
- No PII in reporting projections; CPID only where identity is known.
- A zero on an emergency dashboard is a clinical claim — absent pipelines use named absence.
