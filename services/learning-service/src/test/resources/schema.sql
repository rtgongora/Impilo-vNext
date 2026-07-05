-- Test-profile schema for tables that exist ONLY as Flyway DDL (no JPA entity).
-- The test profile disables Flyway and lets Hibernate create entity-mapped tables
-- (ddl-auto: create-drop), so any JdbcTemplate-accessed, entity-less table must be
-- provisioned here or every code path touching it fails with BadSqlGrammar in H2.
--
-- lrn_learning_notification: written by FundoNotificationIntentWriter (certificate
-- issuance, lifecycle sweeps) and drained by FundoNotificationDispatcher.
-- Mirrors V008__learning_intelligent_studio_foundation.sql.
CREATE TABLE IF NOT EXISTS lrn_learning_notification (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    subject_type VARCHAR(64) NOT NULL,
    subject_id VARCHAR(255) NOT NULL,
    event_code VARCHAR(128) NOT NULL,
    title VARCHAR(512) NOT NULL,
    message TEXT NOT NULL,
    channel_preference VARCHAR(64) NULL,
    scheduled_at TIMESTAMP WITH TIME ZONE NULL,
    due_at TIMESTAMP WITH TIME ZONE NULL,
    reminder_at TIMESTAMP WITH TIME ZONE NULL,
    recurrence VARCHAR(64) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    metadata_json TEXT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    sent_at TIMESTAMP WITH TIME ZONE NULL
);

CREATE INDEX IF NOT EXISTS idx_lrn_learning_notification_subject
    ON lrn_learning_notification (tenant_id, subject_type, subject_id, created_at DESC);
