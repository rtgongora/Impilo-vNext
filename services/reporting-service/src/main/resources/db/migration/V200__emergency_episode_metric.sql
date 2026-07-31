-- W17 — Emergency episode metric projection (Theatre pattern).
-- reporting-service cannot see pct.*; indicators that name pct.emergency_* would register
-- ACTIVE and never execute (see docs/clinical/adult-medicine-domain-pack/analytics-coverage.md).
-- Kafka-fed projection from pct.emergency.* topics is the only honest path.

CREATE TABLE rpt_emergency_episode_metric (
    id                          BIGSERIAL     PRIMARY KEY,
    tenant_id                   UUID          NOT NULL,
    episode_id                  UUID          NOT NULL,
    facility_id                 UUID,
    episode_reference           VARCHAR(32),
    state                       VARCHAR(48),
    previous_state              VARCHAR(48),
    entry_route                 VARCHAR(48),
    episode_class               VARCHAR(48),
    identity_mode               VARCHAR(32),
    subject_cpid                VARCHAR(128),
    journey_id                  VARCHAR(128),
    encounter_id                VARCHAR(128),
    outcome                     VARCHAR(64),
    -- DSEC-aligned timing fields (minutes). Null means "not yet projected", never zero-as-missing.
    time_to_triage_minutes      INT,
    length_of_stay_minutes      INT,
    -- Triage acuity of record (IITT). Null until a triage event is projected — never invent.
    triage_acuity               VARCHAR(32),
    triage_system               VARCHAR(32),
    disposition_type            VARCHAR(64),
    closed                      BOOLEAN       NOT NULL DEFAULT FALSE,
    died                        BOOLEAN       NOT NULL DEFAULT FALSE,
    left_without_being_seen     BOOLEAN       NOT NULL DEFAULT FALSE,
    handover_count              INT           NOT NULL DEFAULT 0,
    handover_expired_count      INT           NOT NULL DEFAULT 0,
    alert_count                 INT           NOT NULL DEFAULT 0,
    alert_overdue_count         INT           NOT NULL DEFAULT 0,
    rito_case_linked            BOOLEAN       NOT NULL DEFAULT FALSE,
    opened_at                   TIMESTAMPTZ,
    closed_at                   TIMESTAMPTZ,
    updated_at                  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_rpt_emergency_episode_metric UNIQUE (tenant_id, episode_id)
);

CREATE INDEX idx_rpt_emergency_metric_tenant ON rpt_emergency_episode_metric (tenant_id);
CREATE INDEX idx_rpt_emergency_metric_facility ON rpt_emergency_episode_metric (tenant_id, facility_id);
CREATE INDEX idx_rpt_emergency_metric_state ON rpt_emergency_episode_metric (tenant_id, state);
