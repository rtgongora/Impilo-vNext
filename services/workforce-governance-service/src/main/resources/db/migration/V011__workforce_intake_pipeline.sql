-- ============================================================================
-- V011 — Workforce Intake pipeline (staged MoHCC staff bootstrap journey)
--
-- Extends the V003 bulk-import batch model with an explicit pipeline stage,
-- a persisted column mapping, per-row Health-ID match results, and per-row
-- execution outcomes (VARAPI Provider ID issuance + Vashandi profile bridge).
--
-- Stage lifecycle: UPLOADED → MAPPED → VALIDATED → MATCHED → APPROVED
--                  → EXECUTING → COMPLETED | PARTIAL (PARTIAL → EXECUTING resume).
--
-- Note: wgv_import_row.match_status already exists from V003 (VARCHAR(64))
-- and is reused for MATCHED / AMBIGUOUS / NO_MATCH / DUPLICATE_IN_BATCH.
-- ============================================================================

ALTER TABLE wgv_import_batch ADD COLUMN stage VARCHAR(40);
ALTER TABLE wgv_import_batch ADD COLUMN column_mapping JSONB;

-- Backfill stage for pre-existing batches from their legacy status axis.
UPDATE wgv_import_batch
SET stage = CASE
    WHEN status = 'approved' THEN 'APPROVED'
    WHEN status IN ('invitations_pending', 'invitations_sent') THEN 'COMPLETED'
    WHEN status = 'validated' THEN 'VALIDATED'
    ELSE 'UPLOADED'
END
WHERE stage IS NULL;

ALTER TABLE wgv_import_row ADD COLUMN matched_health_id VARCHAR(64);
ALTER TABLE wgv_import_row ADD COLUMN duplicate_of_row_id UUID;
ALTER TABLE wgv_import_row ADD COLUMN provider_public_id VARCHAR(64);
ALTER TABLE wgv_import_row ADD COLUMN vashandi_profile_id UUID;
ALTER TABLE wgv_import_row ADD COLUMN assignment_id UUID;
ALTER TABLE wgv_import_row ADD COLUMN execution_status VARCHAR(40);
ALTER TABLE wgv_import_row ADD COLUMN execution_error VARCHAR(500);

CREATE INDEX idx_wgv_import_row_execution ON wgv_import_row (import_batch_id, execution_status);

-- Canonical workforce_intake import template (headers are lower-cased by the
-- CSV parser, so canonical column keys are the lower-cased header names).
INSERT INTO wgv_import_template (id, tenant_id, import_type, template_version, required_columns, optional_columns, schema_json)
VALUES (
    'c1a7f0d2-4b3e-4a90-9a2b-7e5d1c8f3a01',
    '00000000-0000-0000-0000-000000000001',
    'workforce_intake',
    'v1',
    'nationalid,givenname,familyname,profession',
    'cadre,councilcode,councilnumber,facilitycode,email,phone,healthid',
    '{"headers":["nationalId","givenName","familyName","profession","cadre","councilCode","councilNumber","facilityCode","email","phone","healthId"],"notes":"healthId optional — rows without it are matched via the identity resolution step; rows without email are blocked from invitation dispatch (BLOCKED_MISSING_CONTACT), never silently dropped."}'
);
