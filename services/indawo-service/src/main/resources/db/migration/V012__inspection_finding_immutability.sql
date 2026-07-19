-- =============================================================================
-- Indawo V012 — Inspection finding immutability (SJ4, D-L doctrine §5; W8)
--
-- "The operator may respond to findings but must never be able to alter the
-- original inspection record." Enforced at the DATABASE, not just the service:
-- a signed inspection finding is append-only — corrections are new compliance
-- actions / re-inspections, never edits of the original. Mirrors the
-- workforce-governance wgv_adjudication_decision append-only trigger.
--
-- A finding is mutable only while the inspection that owns it is still being
-- authored (status not yet SIGNED). Once signed, UPDATE/DELETE is rejected.
-- The signed flag is carried on the finding row so the trigger is self-
-- contained (no cross-table lookup in the hot path).
-- =============================================================================

ALTER TABLE ind_site_inspection_findings
    ADD COLUMN IF NOT EXISTS signed_flag BOOLEAN NOT NULL DEFAULT false;

COMMENT ON COLUMN ind_site_inspection_findings.signed_flag IS
    'Set true when the owning inspection is signed. A signed finding is append-only (SJ4) — corrections are new compliance actions, never edits.';

CREATE OR REPLACE FUNCTION ind_finding_block_signed_mutation()
RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF OLD.signed_flag THEN
            RAISE EXCEPTION 'ind_site_inspection_findings: finding % is signed and immutable (SJ4) — respond with a compliance action, do not delete', OLD.finding_id;
        END IF;
        RETURN OLD;
    END IF;
    -- UPDATE: block any change to a signed finding EXCEPT the one-way sign
    -- transition (false -> true) that seals it.
    IF OLD.signed_flag THEN
        RAISE EXCEPTION 'ind_site_inspection_findings: finding % is signed and immutable (SJ4) — respond with a compliance action, do not edit', OLD.finding_id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_ind_finding_immutable ON ind_site_inspection_findings;
CREATE TRIGGER trg_ind_finding_immutable
    BEFORE UPDATE OR DELETE ON ind_site_inspection_findings
    FOR EACH ROW
    EXECUTE FUNCTION ind_finding_block_signed_mutation();
