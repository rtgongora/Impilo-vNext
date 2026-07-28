-- FCV-W4 — what a claimant says their relationship to the facility IS, and what that demands of them.
--
-- The claim rail had five flat role scopes and no notion of regulated authority, so it asked the
-- same thing of a records officer as of a practitioner-in-charge. The distinction matters in both
-- directions: claiming to be the responsible pharmacist is a claim about regulated professional
-- standing and must resolve to a real Provider ID, while requiring one from an ICT focal person or
-- a district data officer would lock out exactly the administrative staff who run a facility's
-- record — people who are not clinicians and never will be.
--
-- Kept deliberately small: a code, a label, and whether regulated standing is required. Approver
-- levels, delegation and section scoping are real requirements but have no users yet; they belong
-- to the wave that surfaces them, not to a table nobody reads.

CREATE TABLE IF NOT EXISTS tuso.facility_relationship_type (
    code                VARCHAR(64) PRIMARY KEY,
    label               VARCHAR(128) NOT NULL,
    description         TEXT,
    -- Regulated clinical or diagnostic leadership: the claim is about professional standing, so a
    -- Provider ID must resolve before it can be approved.
    requires_provider_id BOOLEAN NOT NULL DEFAULT FALSE,
    -- "Other" needs the claimant to say what they mean in their own words.
    requires_justification BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order          INT NOT NULL DEFAULT 100,
    active              BOOLEAN NOT NULL DEFAULT TRUE
);

INSERT INTO tuso.facility_relationship_type
    (code, label, description, requires_provider_id, requires_justification, sort_order) VALUES

    -- ── regulated: a claim about professional standing ────────────────────────────────────────
    ('PRACTITIONER_IN_CHARGE', 'Practitioner in charge',
     'The registered practitioner accountable for clinical services at this facility.', TRUE, FALSE, 10),
    ('MEDICAL_SUPERINTENDENT', 'Medical superintendent',
     'The medical officer superintending the facility.', TRUE, FALSE, 11),
    ('RESPONSIBLE_MEDICAL_OFFICER', 'Responsible medical officer',
     'The medical officer responsible for a service or unit.', TRUE, FALSE, 12),
    ('NURSING_LEAD', 'Nursing lead',
     'The registered nurse leading nursing services.', TRUE, FALSE, 13),
    ('PHARMACY_LEAD', 'Responsible pharmacist',
     'The pharmacist responsible for the facility pharmacy.', TRUE, FALSE, 14),
    ('LABORATORY_LEAD', 'Responsible laboratory professional',
     'The laboratory professional responsible for diagnostic services.', TRUE, FALSE, 15),

    -- ── administrative: authority comes from employment or appointment, not a licence ──────────
    ('FACILITY_ADMINISTRATOR', 'Facility administrator',
     'Administers the facility record and its users.', FALSE, FALSE, 30),
    ('FACILITY_MANAGER', 'Facility manager',
     'Manages facility operations.', FALSE, FALSE, 31),
    ('RECORDS_OFFICER', 'Records / health-information officer',
     'Responsible for health information and records.', FALSE, FALSE, 32),
    ('ICT_FOCAL_PERSON', 'ICT or systems focal person',
     'Responsible for systems and connectivity at the facility.', FALSE, FALSE, 33),
    ('DATA_STEWARD', 'Authorised data steward',
     'Authorised to maintain the facility profile and resolve data gaps.', FALSE, FALSE, 34),
    ('IMPLEMENTATION_OFFICER', 'Implementation or onboarding officer',
     'Supports the facility through onboarding and rollout.', FALSE, FALSE, 35),
    ('DISTRICT_HEALTH_EXECUTIVE', 'District health executive representative',
     'Acts for the district health executive over this facility.', FALSE, FALSE, 40),
    ('PROVINCIAL_HEALTH_EXECUTIVE', 'Provincial health executive representative',
     'Acts for the provincial health executive over this facility.', FALSE, FALSE, 41),
    ('MOHCC_NATIONAL_ADMINISTRATOR', 'MoHCC national administrator',
     'National Ministry administrator acting on the facility record.', FALSE, FALSE, 42),

    ('OTHER', 'Other (state your relationship)',
     'Any other authorised relationship; the claimant must state it and a steward must accept it.',
     FALSE, TRUE, 90)

ON CONFLICT (code) DO NOTHING;

-- The claim now carries WHAT the claimant says they are, and — for a regulated claim — the Provider
-- ID that was resolved for them. The existing `role` column stays: relationship is what you are to
-- the facility, role is what you may do to the record, and they are not the same question.
ALTER TABLE tuso.facility_admin_appointment
    ADD COLUMN IF NOT EXISTS relationship_type VARCHAR(64) REFERENCES tuso.facility_relationship_type (code);

ALTER TABLE tuso.facility_admin_appointment
    ADD COLUMN IF NOT EXISTS provider_public_id VARCHAR(64);

ALTER TABLE tuso.facility_admin_appointment
    ADD COLUMN IF NOT EXISTS justification TEXT;

CREATE INDEX IF NOT EXISTS idx_facility_admin_appointment_relationship
    ON tuso.facility_admin_appointment (relationship_type);
