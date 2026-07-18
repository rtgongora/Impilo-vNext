-- Identity Contract §7.2 / §17: clinical services key subjects by CPID, never by
-- Health ID. These columns always held the clinical subject key; after the CPID
-- decoupling the value is a mapped CPID, so the health_id names were lies.
ALTER TABLE pct.emergency_cases RENAME COLUMN known_health_id TO known_subject_cpid;
ALTER TABLE pct.emergency_cases RENAME COLUMN reconciled_health_id TO reconciled_cpid;

COMMENT ON COLUMN pct.emergency_cases.known_subject_cpid IS
    'Clinical subject CPID when the patient is known at intake (mapped by tshepo-identity; never a Health ID)';
COMMENT ON COLUMN pct.emergency_cases.reconciled_cpid IS
    'Canonical CPID this emergency case was reconciled to (never a Health ID)';
