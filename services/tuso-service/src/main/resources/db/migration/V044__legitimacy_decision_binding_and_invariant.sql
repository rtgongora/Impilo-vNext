-- FCV-W0a — two safety properties the legitimacy lattice needs before any governed Ministry
-- decision (or any corrected import) may change a facility's operational verdict.
--
-- Background. `FacilityController.getStatusSummary` derives:
--     operational = statusActive && operatingConfirmed && !regulatorListingOnly && legitimacyAllows
--     legitimacyAllows = !anyDenies && anyAllows      -- over tuso.facility_source_legitimacy
-- The 1,774 facilities seeded by V027 are status='ACTIVE', operational_status NULL (so
-- operating-confirmed) and regulatory_status='DRAFT' (so not regulator-listing-only). For them
-- `legitimacyAllows` is the ONLY false term, and it is false ONLY because no rows exist. They are
-- one allowing row away from going live.
--
-- ── 1. decision_id ─────────────────────────────────────────────────────────────────────────────
-- `FacilitySourceLegitimacyService.stampFromImport` upserts by (facility_id, source) and overwrites
-- unconditionally, swallowing every exception. Once a governed Ministry decision projects an allow
-- onto PLATFORM_OPERATIONAL, the next MFL or HPA import would silently re-stamp
-- PENDING_VERIFICATION/false and the facility would go dark with no error and no audit. Binding a
-- row to the decision that produced it lets the import path recognise "this row is not mine" and
-- skip it, and makes the projection a rebuildable cache rather than the only copy of the truth.
ALTER TABLE tuso.facility_source_legitimacy
    ADD COLUMN IF NOT EXISTS decision_id UUID;

COMMENT ON COLUMN tuso.facility_source_legitimacy.decision_id IS
    'Set when this row was projected from a governed Ministry legitimacy decision. Import paths must '
    'never overwrite a row where this is non-null — the decision table is the system of record and '
    'this row is its derived projection.';

CREATE INDEX IF NOT EXISTS idx_facility_source_legitimacy_decision
    ON tuso.facility_source_legitimacy (decision_id) WHERE decision_id IS NOT NULL;

-- ── 2. The denier-present invariant ────────────────────────────────────────────────────────────
-- A MINISTRY_OPERATIONAL allow says "the Ministry recognises this facility". It must never be the
-- only row, because on its own it satisfies (!anyDenies && anyAllows) and grants platform operation
-- without anyone having verified the facility. Both importers write the PLATFORM_OPERATIONAL row in
-- the same method body as the Ministry row, so correct code already satisfies this; the constraint
-- exists so that a future backfill, a partial failure inside the exception-swallowing stamp path, or
-- a hand-run SQL statement cannot quietly operationalise the estate.
--
-- Recognition is not operation: a facility may carry a Ministry allow and still be non-operational,
-- which is exactly what the PLATFORM_OPERATIONAL row expresses.
CREATE OR REPLACE FUNCTION tuso.assert_platform_verdict_present()
RETURNS TRIGGER AS $$
BEGIN
    -- Guard the two ways the hole can open: writing a Ministry allow with no platform verdict, and
    -- removing the platform verdict from under an existing Ministry allow.
    IF (TG_OP = 'DELETE') THEN
        IF OLD.source = 'PLATFORM_OPERATIONAL' AND EXISTS (
            SELECT 1 FROM tuso.facility_source_legitimacy l
            WHERE l.facility_id = OLD.facility_id
              AND l.source = 'MINISTRY_OPERATIONAL'
              AND l.allowed_on_platform
        ) THEN
            RAISE EXCEPTION
                'Refusing to delete the PLATFORM_OPERATIONAL verdict for facility % while a '
                'MINISTRY_OPERATIONAL allow stands — that would grant platform operation without '
                'any platform verification.', OLD.facility_id
                USING ERRCODE = 'integrity_constraint_violation';
        END IF;
        RETURN OLD;
    END IF;

    IF NEW.source = 'MINISTRY_OPERATIONAL' AND NEW.allowed_on_platform AND NOT EXISTS (
        SELECT 1 FROM tuso.facility_source_legitimacy l
        WHERE l.facility_id = NEW.facility_id
          AND l.source = 'PLATFORM_OPERATIONAL'
    ) THEN
        RAISE EXCEPTION
            'Refusing a MINISTRY_OPERATIONAL allow for facility % with no PLATFORM_OPERATIONAL '
            'verdict recorded — Ministry recognition is not platform operation, and on its own this '
            'row would make the facility operational without verification.', NEW.facility_id
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_facility_legitimacy_platform_verdict ON tuso.facility_source_legitimacy;
CREATE TRIGGER trg_facility_legitimacy_platform_verdict
    BEFORE INSERT OR UPDATE OR DELETE ON tuso.facility_source_legitimacy
    FOR EACH ROW EXECUTE FUNCTION tuso.assert_platform_verdict_present();
