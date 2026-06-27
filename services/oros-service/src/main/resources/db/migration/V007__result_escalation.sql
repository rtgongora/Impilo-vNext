-- =============================================
-- Service : oros-service
-- Flyway  : V007__result_escalation.sql
-- Purpose : Track critical-result escalation so the escalation scheduler emits an ACK_ESCALATION
--           exactly once per unacknowledged critical result that breaches the ack threshold.
-- =============================================

ALTER TABLE oros_results
    ADD COLUMN escalated_at TIMESTAMPTZ;

CREATE INDEX idx_oros_results_critical_unacked
    ON oros_results (is_critical, acknowledged_at, escalated_at, created_at)
    WHERE is_critical = true;
