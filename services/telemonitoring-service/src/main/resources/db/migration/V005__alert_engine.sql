-- =====================================================================================
-- Telemonitoring — Monitoring alert + escalation engine :: V005 (OF-B26)
--
-- NOTE ON NUMBERING: V001 plans, V002 governance, V003 device assignments, V004
-- readings; V005 is the next free number (verified by ls at authoring time).
--
-- DESIGN DECISION (rule source): the metric bounds/bands that drive threshold
-- evaluation are NOT duplicated here. The rule source is the plan's CURRENT immutable
-- threshold-profile version (tm_threshold_profiles.parameters — §14.3 field 10:
-- {"metric":{"unit","greenMin","greenMax","amberMin","amberMax","redMin","redMax"}}),
-- already versioned, attributed and approval-gated. tm_alert_rules carries ONLY the
-- override/rate-limit configuration that vocabulary cannot express: repeat-abnormal
-- windows (trigger 5), per-severity response windows (§14.6 acknowledgement law),
-- offline heartbeat tolerance (trigger 8, journey #65) and the device-alert switch.
-- Resolution: plan override -> programme default (plan_id NULL) -> engine defaults.
--
-- tm_alert_episodes — the §14.6 AlertEpisode aggregate:
--  * live_guard: the storm/race guard (OF-B24 device_active_guard idiom). Present
--    while live, NULLed on RESOLVED, UNIQUE — one clinical situation = one episode:
--      clinical  -> 'PLAN:{plan_id}:CLINICAL'   (grouping rule: related breaches on
--                                                the same plan attach, never sibling)
--      device    -> 'DEV:{device_ref}:{DEVICE_OFFLINE|DEVICE_QUALITY}'
--                                               (rate limit: ONE device-quality
--                                                episode per device; storms attach)
--  * accountable closure (§14.6 invariant): resolved_by + assessment + action_taken +
--    outcome — all four or the service refuses closure; EMERGENCY episodes further
--    require assumption_of_care_* before RESOLVED (journey #61 rung-15 law).
--  * ladder_history: §14.7 rung entries {rung, at, actor, action, outcome};
--    ESCALATED status is unreachable without a recorded rung.
--  * severity_ceiling_applied: §14.6 low-trust ceiling honesty stamp — DEGRADED/
--    low-trust readings may raise at most AMBER alone, never RED/EMERGENCY.
-- =====================================================================================

CREATE TABLE telemonitoring.tm_alert_rules (
    id                             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                      UUID NOT NULL,
    plan_id                        UUID,               -- per-plan override; NULL = programme default row
    programme_code                 VARCHAR(64),        -- programme-default scope; NULL = per-plan row
    repeat_abnormal_count          INT,                -- trigger 5: N breaches inside window escalate
    repeat_abnormal_window_minutes INT,
    amber_response_minutes         INT,                -- §14.6 acknowledgement windows per severity
    red_response_minutes           INT,
    emergency_response_minutes     INT,
    offline_tolerance_hours        INT,                -- trigger 8 heartbeat tolerance (default 72h, journey #65)
    device_quality_min_count       INT,                -- trigger 9: degraded/quarantined stamps in 24h before episode
    device_alerts_enabled          BOOLEAN NOT NULL DEFAULT TRUE,
    created_by                     VARCHAR(128),
    created_at                     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_tm_alert_rules_plan UNIQUE (plan_id),
    CONSTRAINT ck_tm_alert_rules_scope CHECK (plan_id IS NOT NULL OR programme_code IS NOT NULL)
);

-- One programme-default row per (tenant, programme).
CREATE UNIQUE INDEX uq_tm_alert_rules_programme
    ON telemonitoring.tm_alert_rules (tenant_id, programme_code)
    WHERE plan_id IS NULL AND programme_code IS NOT NULL;

CREATE TABLE telemonitoring.tm_alert_episodes (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                 UUID NOT NULL,
    plan_id                   UUID,                    -- NULL for device episodes from unattributed readings
    patient_cpid              VARCHAR(64),             -- CPID-only (no PII), NULL when unattributed
    device_ref                VARCHAR(64),
    trigger_class             VARCHAR(32) NOT NULL,    -- THRESHOLD_BREACH / REPEAT_ABNORMAL / DEVICE_OFFLINE / DEVICE_QUALITY
    metric_code               VARCHAR(64),
    initial_severity          VARCHAR(16) NOT NULL,    -- AMBER / RED / EMERGENCY
    severity                  VARCHAR(16) NOT NULL,    -- current severity (escalation history in ladder_history)
    status                    VARCHAR(24) NOT NULL,    -- OPEN / ACKNOWLEDGED / IN_PROGRESS / ESCALATED / RESOLVED (guarded)
    severity_ceiling_applied  BOOLEAN NOT NULL DEFAULT FALSE,
    live_guard                VARCHAR(128),            -- race/storm guard, NULL once resolved
    contributing_readings     JSONB NOT NULL DEFAULT '[]'::jsonb,
    ladder_history            JSONB NOT NULL DEFAULT '[]'::jsonb,
    current_rung              INT,
    breach_count              INT NOT NULL DEFAULT 0,
    last_breach_at            TIMESTAMPTZ,
    response_deadline_at      TIMESTAMPTZ,             -- §14.6 acknowledgement deadline for current severity
    acknowledged_by           VARCHAR(128),
    acknowledged_at           TIMESTAMPTZ,
    auto_escalated_at         TIMESTAMPTZ,             -- escalation-on-silence audit (§14.6)
    chw_task_dispatched       BOOLEAN,                 -- rung 4 best-effort outcome (outbox event = durable seam)
    review_referral_ref       VARCHAR(64),             -- rung 6/7 Vol I teleconsult case ref (origin=MONITORING_ALERT)
    ems_incident_ref          VARCHAR(64),             -- rung 11+ Daidzai incident ref (distinct namespace, §5.3)
    ems_dispatched            BOOLEAN,
    assumption_of_care_note   VARCHAR(1024),           -- journey #61: EMERGENCY cannot RESOLVE without this
    assumption_of_care_by     VARCHAR(128),
    assumption_of_care_at     TIMESTAMPTZ,
    resolved_by               VARCHAR(128),            -- §14.6 accountable closure: all four or no closure
    assessment                VARCHAR(2048),
    action_taken              VARCHAR(1024),
    outcome                   VARCHAR(128),
    resolved_at               TIMESTAMPTZ,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_tm_alert_live_guard UNIQUE (live_guard)
);

CREATE INDEX idx_tm_alert_tenant_status ON telemonitoring.tm_alert_episodes (tenant_id, status);
CREATE INDEX idx_tm_alert_plan          ON telemonitoring.tm_alert_episodes (tenant_id, plan_id, created_at DESC);
CREATE INDEX idx_tm_alert_device        ON telemonitoring.tm_alert_episodes (device_ref) WHERE device_ref IS NOT NULL;
-- Overdue-acknowledgement sweep population (§14.6): only live unacked episodes.
CREATE INDEX idx_tm_alert_overdue       ON telemonitoring.tm_alert_episodes (response_deadline_at)
    WHERE status = 'OPEN';
