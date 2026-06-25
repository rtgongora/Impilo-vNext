-- =============================================
-- Service : oros-service
-- Flyway  : V013__result_observations.sql
-- Purpose : Structured laboratory observations (spec §7) as children of a result, so lab results
--           render as a clinical table (value / unit / reference range / abnormal+critical flags)
--           and support per-analyte critical flagging and longitudinal trends. The result's
--           summary JSONB is retained as a snapshot for SHR writeback / back-compat.
-- =============================================

CREATE TABLE oros_result_observations (
    observation_id  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    result_id       UUID            NOT NULL REFERENCES oros_results(result_id),
    order_id        VARCHAR(26)     NOT NULL,
    sequence        INTEGER         NOT NULL DEFAULT 0,
    analyte_code    VARCHAR(64),                -- LOINC / local code
    analyte_system  VARCHAR(255),
    analyte_name    VARCHAR(255)    NOT NULL,
    value_text      VARCHAR(255),
    value_numeric   NUMERIC,
    value_type      VARCHAR(16),                -- NUMERIC, TEXT, CODED, RATIO
    unit            VARCHAR(48),
    ref_range_low   NUMERIC,
    ref_range_high  NUMERIC,
    ref_range_text  VARCHAR(128),
    abnormal_flag   VARCHAR(8),                 -- H, L, HH, LL, A, N
    critical_flag   BOOLEAN         NOT NULL DEFAULT false,
    comment         TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_oros_result_obs_result ON oros_result_observations (result_id);
-- Supports longitudinal trend queries (same analyte over time for a subject's orders).
CREATE INDEX idx_oros_result_obs_order_analyte ON oros_result_observations (order_id, analyte_code);
