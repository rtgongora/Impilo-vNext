-- Wave 4 §20 — Theatre utilisation reporting.
-- reporting-service is the FIRST downstream consumer of theatre.* events. A new @KafkaListener
-- projects them (from inpatient.events / inpatient.safety) into this per-case metric table; the seeded
-- report catalog reads from it. Reports therefore derive from REAL transactional/event data and
-- reconcile with the cases actually run.

-- 1. Per-case theatre metric projection (upserted by ReportingEventConsumer from theatre.* events).
CREATE TABLE rpt_theatre_case_metric (
    id                    BIGSERIAL     PRIMARY KEY,
    tenant_id             UUID          NOT NULL,
    episode_id            UUID          NOT NULL,
    facility_id           UUID,
    procedure_code        VARCHAR(64),
    procedure_name        VARCHAR(255),
    status                VARCHAR(24),
    theatre_minutes       INT,
    has_anaesthesia       BOOLEAN       NOT NULL DEFAULT FALSE,
    implant_count         INT           NOT NULL DEFAULT 0,
    consumable_count      INT           NOT NULL DEFAULT 0,
    blood_units           INT           NOT NULL DEFAULT 0,
    cancelled             BOOLEAN       NOT NULL DEFAULT FALSE,
    cancellation_reason   VARCHAR(500),
    returned_to_theatre   BOOLEAN       NOT NULL DEFAULT FALSE,
    unplanned_icu         BOOLEAN       NOT NULL DEFAULT FALSE,
    pacu_destination      VARCHAR(24),
    safety_event_count    INT           NOT NULL DEFAULT 0,
    complication_flag     BOOLEAN       NOT NULL DEFAULT FALSE,
    completed_at          TIMESTAMPTZ,
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_rpt_theatre_metric UNIQUE (tenant_id, episode_id)
);
CREATE INDEX idx_rpt_theatre_metric_tenant ON rpt_theatre_case_metric (tenant_id);

-- 2. Seed the theatre report catalog (preview tenant). Reports read the projection above.
INSERT INTO rpt_report_definitions
    (tenant_id, report_key, name, description, query_template, parameters, output_format, status, created_by)
VALUES
    ('00000000-0000-4000-8000-000000000001'::uuid, 'theatre-utilisation',
     'Theatre Utilisation Summary',
     'Cases run, scheduled-vs-completed, cancellation rate, anaesthesia rate, returns-to-theatre, unplanned ICU, complications and blood/implant/consumable usage — derived from theatre.* events.',
     $$SELECT
         count(*) AS total_cases,
         count(*) FILTER (WHERE status = 'COMPLETED' AND NOT cancelled) AS completed_cases,
         count(*) FILTER (WHERE cancelled) AS cancelled_cases,
         round(100.0 * count(*) FILTER (WHERE cancelled) / NULLIF(count(*), 0), 1) AS cancellation_rate_pct,
         count(*) FILTER (WHERE has_anaesthesia) AS anaesthesia_cases,
         count(*) FILTER (WHERE returned_to_theatre) AS returns_to_theatre,
         count(*) FILTER (WHERE unplanned_icu) AS unplanned_icu_transfers,
         count(*) FILTER (WHERE complication_flag) AS complication_cases,
         coalesce(sum(blood_units), 0) AS blood_units_used,
         coalesce(sum(implant_count), 0) AS implants_used,
         coalesce(sum(consumable_count), 0) AS consumables_used,
         coalesce(round(avg(theatre_minutes), 1), 0) AS avg_theatre_minutes
       FROM rpt_theatre_case_metric
       WHERE tenant_id = :tenant_id$$,
     '{}', 'JSON', 'ACTIVE', 'system-seed'),
    ('00000000-0000-4000-8000-000000000001'::uuid, 'theatre-case-register',
     'Theatre Case Register',
     'Per-case register of theatre activity (procedure, status, theatre minutes, complications, disposition) — derived from theatre.* events.',
     $$SELECT episode_id, procedure_code, procedure_name, status, theatre_minutes, has_anaesthesia,
              implant_count, consumable_count, blood_units, cancelled, cancellation_reason,
              returned_to_theatre, unplanned_icu, pacu_destination, safety_event_count,
              complication_flag, completed_at
       FROM rpt_theatre_case_metric
       WHERE tenant_id = :tenant_id
       ORDER BY updated_at DESC$$,
     '{}', 'JSON', 'ACTIVE', 'system-seed')
ON CONFLICT (tenant_id, report_key) DO NOTHING;
