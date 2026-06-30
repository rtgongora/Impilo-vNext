-- WS#8 — link Ubomi death_notification to the originating PCT death case and record the
-- civil-registration package state. Ubomi OWNS CRVS; PCT owns the clinical DeathCase. This adds a
-- back-reference plus package-readiness so a notification can be validated before submission to the
-- Registrar General. We never forge a REGISTERED state — civil_reg_number is only set by a real
-- registrar response (owner-routed hook).
ALTER TABLE ubomi.death_notification
    ADD COLUMN IF NOT EXISTS pct_case_ref     VARCHAR(64),
    ADD COLUMN IF NOT EXISTS package_ready    BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS package_validated_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_death_pct_case_ref
    ON ubomi.death_notification (tenant_id, pct_case_ref) WHERE pct_case_ref IS NOT NULL;
