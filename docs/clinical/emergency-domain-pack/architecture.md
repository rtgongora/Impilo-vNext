# Emergency pack — architecture

## Canonical truth

`pct.emergency_episode` is the facility-scoped emergency episode. It is a **sibling** of
`ed_visit` under the same journey (`journey_id`), not a parent that mints a second journey.
Acuity lives in `ed_triage_assessment` (IITT of record); disposition detail in
`emergency_disposition` / episode outcome — the episode composes them and does not duplicate
them.

## Request path

```
Browser (one-ui-shell)
  → Next rewrites /internal/*
  → Experience BFF (EmergencyEpisodeController / EdWorkflowController / MentalHealthController)
  → pct-service | mental-health-service | reporting-service | rito-quality-safety-service
  → Kafka outbox → pct.emergency.* topics → reporting EmergencyReportingConsumer
```

Trust: Envoy ext_authz → TSHEPO on the public path. Local drive rig uses
`impilo.security.disable-oauth-for-tests` / BFF `allow-anonymous` because this box has no Keycloak.

## Planes

| Concern | Plane / SoR |
|---------|-------------|
| Episode, triage, alerts, handover, disposition | clinical — pct-service |
| IITT criteria engine | libs/emergency-domain (pure Java; TeaVM GO for browser compile) |
| Pathway content | clinical-knowledge-platform |
| Mental-health acceptance | mental-health-service (undeployed — gap register) |
| Safety after-action cases | rito-quality-safety-service |
| National indicators | reporting-service projection `rpt_emergency_episode_metric` |
| Facility capability flags | tuso (`facility_emergency_capability`) |
| Blood / MTP | madi |
| Mass casualty sieve | daidzai |

## Non-negotiables

- Reporting never SQL-joins `pct.*` from its own database (Theatre pattern).
- Clinical conclusions are not derived in TypeScript (`check-no-ts-clinical-logic.sh`).
- Failed reads use the flat honesty envelope — absence and unreadability stay distinct.
