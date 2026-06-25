-- =============================================
-- Service : oros-service
-- Flyway  : V006__result_report_lifecycle.sql
-- Purpose : Versioned diagnostic reporting lifecycle (spec §10): report status
--           (preliminary/final/amended/addendum/corrected), structured impression and
--           recommendations, critical reason, validating provider, release/acknowledge
--           timestamps, and an immutable version chain (supersedes_result_id + version).
--           A final report is never overwritten — amendments create new versioned rows.
-- =============================================

ALTER TABLE oros_results
    ADD COLUMN report_status         VARCHAR(20),
    ADD COLUMN impression            TEXT,
    ADD COLUMN recommendations       TEXT,
    ADD COLUMN critical_reason       TEXT,
    ADD COLUMN validated_by          VARCHAR(128),
    ADD COLUMN released_at           TIMESTAMPTZ,
    ADD COLUMN acknowledged_at       TIMESTAMPTZ,
    ADD COLUMN acknowledged_by       VARCHAR(128),
    ADD COLUMN supersedes_result_id  UUID,
    ADD COLUMN version               INT NOT NULL DEFAULT 1;

CREATE INDEX idx_oros_results_order_status
    ON oros_results (order_id, report_status);

CREATE INDEX idx_oros_results_supersedes
    ON oros_results (supersedes_result_id)
    WHERE supersedes_result_id IS NOT NULL;
