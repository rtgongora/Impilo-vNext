-- WS#6 Inpatient Suite: bed/ward safety constraints for safe bed assignment.
-- Ward gains a gender designation + clinical-capability flags; bed inherits/overrides them.
-- These back the BedSafetyEvaluator gate (gender / age / isolation / oxygen / monitoring / ICU).

ALTER TABLE inpatient.ward
    ADD COLUMN IF NOT EXISTS gender_designation VARCHAR(20) NOT NULL DEFAULT 'ANY',  -- MALE | FEMALE | ANY
    ADD COLUMN IF NOT EXISTS age_group          VARCHAR(20) NOT NULL DEFAULT 'ADULT', -- ADULT | PAEDIATRIC | NEONATAL | ANY
    ADD COLUMN IF NOT EXISTS isolation_capable   BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS oxygen_available    BOOLEAN     NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS monitoring_capable  BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS icu_capable         BOOLEAN     NOT NULL DEFAULT FALSE;

ALTER TABLE inpatient.bed
    ADD COLUMN IF NOT EXISTS gender_designation VARCHAR(20),  -- NULL => inherit ward
    ADD COLUMN IF NOT EXISTS isolation_capable   BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS oxygen_available    BOOLEAN     NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS monitoring_capable  BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS icu_capable         BOOLEAN     NOT NULL DEFAULT FALSE,
    -- requirements stamped at assignment time, for audit of WHY a bed was deemed safe
    ADD COLUMN IF NOT EXISTS requires_isolation  BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS requires_oxygen     BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS requires_monitoring BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS requires_icu        BOOLEAN     NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN inpatient.bed.gender_designation IS 'MALE | FEMALE | ANY; NULL inherits ward gender_designation';
COMMENT ON COLUMN inpatient.ward.gender_designation IS 'MALE | FEMALE | ANY — same-sex bay safety';
