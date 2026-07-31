# W17 — Emergency indicators (decision + landing)

## Decision: Theatre pattern

`reporting-service` has its **own database** and cannot see `pct.*`. Seeding SQL that names
`pct.emergency_*` would show as ACTIVE and never execute — the adult-medicine failure mode
documented in `docs/clinical/adult-medicine-domain-pack/analytics-coverage.md`.

**Chosen shape:** Kafka-fed projection `rpt_emergency_episode_metric` (mirrors
`rpt_theatre_case_metric`), with report definitions that query **only** the projection.

**Rejected:** PCT-native analytics endpoints for national indicators (would keep national
measures out of the national reporting store).

## Landed

| Artefact | Path |
|----------|------|
| Projection DDL | `services/reporting-service/.../V200__emergency_episode_metric.sql` |
| Report catalog | `.../V201__emergency_report_catalog.sql` |
| Consumer | `EmergencyReportingConsumer` on `pct.emergency.*` topics |
| PCT payload | `event_type` (+ snake_case id aliases) on episode / handover / alert emits |
| Rito after-action | `rito .../V200__after_action_linkage.sql` + `EMERGENCY_EPISODE` / `AFTER_ACTION` / `DISASTER_INCIDENT` link types; auto-link from `metadata.episodeId` on case create |
| DSEC 47+31 mapping | `docs/clinical/emergency-domain-pack/dsec-element-mapping.json` |

## Report keys

- `emergency-episode-summary`
- `emergency-disposition-mix`
- `emergency-acuity-distribution` (**PARTIAL** — acuity stays NULL until triage events carry it; buckets as `NOT_YET_TRIAGED`)
- `emergency-episode-register`

## Honest absence

Resuscitation interval adherence, observation conversion, and critical-result ack time remain
**NOT_COMPUTABLE** until those events are projected. The analytics UI names them rather than
showing zeros.
