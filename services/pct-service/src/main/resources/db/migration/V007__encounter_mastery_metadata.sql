-- =============================================
-- Service : pct-service
-- Flyway  : V007__encounter_mastery_metadata.sql
-- Purpose : Add encounter mastery metadata fields
--           for context, entry point, care setting,
--           urgency, and pathway/protocol linkage.
-- =============================================

ALTER TABLE pct_encounters
    ADD COLUMN encounter_context VARCHAR(32),
    ADD COLUMN entry_point       VARCHAR(64),
    ADD COLUMN care_setting      VARCHAR(32),
    ADD COLUMN priority          VARCHAR(32),
    ADD COLUMN triage_category   VARCHAR(32),
    ADD COLUMN pathway_ref       VARCHAR(128),
    ADD COLUMN protocol_ref      VARCHAR(128);

CREATE INDEX idx_pct_encounters_context ON pct_encounters (encounter_context);
CREATE INDEX idx_pct_encounters_entry_point ON pct_encounters (entry_point);
CREATE INDEX idx_pct_encounters_priority ON pct_encounters (priority);
