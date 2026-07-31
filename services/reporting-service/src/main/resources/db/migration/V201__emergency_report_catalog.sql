-- W17 — Emergency report catalog + DSEC citation.
-- Every query_template reads ONLY rpt_emergency_episode_metric (never pct.*).
-- Standard citation: EMS.DSEC.MINIMUM_DATASET (WHO Data Set for Emergency Care).

INSERT INTO rpt_report_definitions
    (tenant_id, report_key, name, description, query_template, parameters, output_format, status, created_by)
VALUES
    ('00000000-0000-4000-8000-000000000001'::uuid, 'emergency-episode-summary',
     'Emergency Episode Summary',
     'Episode counts by state/outcome, mortality, LWBS, handover expiry and overdue alerts — projected from pct.emergency.* events into rpt_emergency_episode_metric. Cites EMS.DSEC.MINIMUM_DATASET.',
     $$SELECT
         count(*) AS total_episodes,
         count(*) FILTER (WHERE closed) AS closed_episodes,
         count(*) FILTER (WHERE NOT closed) AS open_episodes,
         count(*) FILTER (WHERE died) AS deaths,
         count(*) FILTER (WHERE left_without_being_seen) AS left_without_being_seen,
         count(*) FILTER (WHERE handover_expired_count > 0) AS episodes_with_expired_handover,
         coalesce(sum(alert_overdue_count), 0) AS overdue_alerts,
         count(*) FILTER (WHERE rito_case_linked) AS episodes_with_rito_case,
         coalesce(round(avg(length_of_stay_minutes) FILTER (WHERE length_of_stay_minutes IS NOT NULL), 1), NULL) AS avg_los_minutes,
         coalesce(round(avg(time_to_triage_minutes) FILTER (WHERE time_to_triage_minutes IS NOT NULL), 1), NULL) AS avg_time_to_triage_minutes
       FROM rpt_emergency_episode_metric
       WHERE tenant_id = :tenant_id$$,
     '{}', 'JSON', 'ACTIVE', 'system-seed-w17'),

    ('00000000-0000-4000-8000-000000000001'::uuid, 'emergency-disposition-mix',
     'Emergency Disposition Mix',
     'Disposition / closed-state mix across emergency episodes (DSEC disposition element). Rates use NULL when the denominator is empty — never a fabricated 0%.',
     $$SELECT
         coalesce(disposition_type, outcome, state) AS disposition_bucket,
         count(*) AS episode_count,
         round(100.0 * count(*) / NULLIF(sum(count(*)) OVER (), 0), 1) AS pct_of_closed
       FROM rpt_emergency_episode_metric
       WHERE tenant_id = :tenant_id
         AND closed = TRUE
       GROUP BY 1
       ORDER BY episode_count DESC$$,
     '{}', 'JSON', 'ACTIVE', 'system-seed-w17'),

    ('00000000-0000-4000-8000-000000000001'::uuid, 'emergency-acuity-distribution',
     'Emergency Triage Acuity Distribution',
     'IITT acuity of record distribution. Episodes with no projected acuity are counted as NOT_YET_TRIAGED — never coerced into a colour. Cites EMS.DSEC.MINIMUM_DATASET / triage category.',
     $$SELECT
         coalesce(triage_acuity, 'NOT_YET_TRIAGED') AS acuity,
         coalesce(triage_system, 'UNKNOWN') AS triage_system,
         count(*) AS episode_count
       FROM rpt_emergency_episode_metric
       WHERE tenant_id = :tenant_id
       GROUP BY 1, 2
       ORDER BY episode_count DESC$$,
     '{}', 'JSON', 'ACTIVE', 'system-seed-w17'),

    ('00000000-0000-4000-8000-000000000001'::uuid, 'emergency-episode-register',
     'Emergency Episode Register',
     'Per-episode register derived from the Kafka projection (not a live join to pct).',
     $$SELECT episode_id, episode_reference, facility_id, state, entry_route, episode_class,
              triage_acuity, disposition_type, outcome, closed, died, left_without_being_seen,
              handover_count, handover_expired_count, alert_count, alert_overdue_count,
              rito_case_linked, time_to_triage_minutes, length_of_stay_minutes,
              opened_at, closed_at, updated_at
       FROM rpt_emergency_episode_metric
       WHERE tenant_id = :tenant_id
       ORDER BY updated_at DESC$$,
     '{}', 'JSON', 'ACTIVE', 'system-seed-w17')
ON CONFLICT (tenant_id, report_key) DO NOTHING;
