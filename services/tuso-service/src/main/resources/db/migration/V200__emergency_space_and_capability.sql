-- Emergency command view + capacity (W10). Two additive changes, neither duplicating an existing
-- SoR: tuso already owns clinical-space capacity (V024); this pack only widens what a space CAN be
-- declared as, and adds a facility-level capability profile that genuinely does not exist anywhere.
--
-- HONESTY NOTE (matches R9's standing discipline elsewhere in this pack): this migration does
-- deliberately NOT introduce a "trauma centre Level I-IV" classification. No Zimbabwe national
-- trauma-system accreditation scheme was found during this pack's research (R9's own standards-
-- baseline records the same gap for national trauma-team activation criteria) — inventing a Level
-- I-IV label would imply a formal designation system that may not exist. Instead this declares plain,
-- individually-verifiable CAPABILITIES ("does this facility have a 24h theatre right now"), which a
-- command view can use for routing decisions without asserting a national classification on the
-- facility's behalf.

-- 1. An ED resus bay is a real, unambiguous space type — safe to add without inventing anything.
ALTER TABLE tuso.clinical_space DROP CONSTRAINT chk_clinical_space_type;
ALTER TABLE tuso.clinical_space
    ADD CONSTRAINT chk_clinical_space_type
    CHECK (space_type IN ('OPERATING_THEATRE','PACU','ICU','HDU','WARD','ED_RESUS_BAY'));

COMMENT ON CONSTRAINT chk_clinical_space_type ON tuso.clinical_space IS
    'ED_RESUS_BAY added W10 — an emergency resuscitation bay is its own space type, not a capability '
    'tag on a WARD/ICU row; a command view counting "how many resus bays are free" needs a real type '
    'to filter on, not a JSONB tag scan.';

-- 2. Facility-level emergency capability profile — genuinely absent before this migration. One row
-- per facility, hand-declared (not derived), each field independently verifiable and independently
-- absent-able: NULL means "not yet declared", never "no".
CREATE TABLE tuso.facility_emergency_capability (
    facility_id             BIGINT PRIMARY KEY REFERENCES tuso.facility(id),
    tenant_id               UUID NOT NULL,

    has_24h_theatre         BOOLEAN,
    has_blood_bank          BOOLEAN,
    has_ct_scan             BOOLEAN,
    has_icu                 BOOLEAN,
    has_massive_transfusion_protocol BOOLEAN,

    -- Free text, deliberately not a closed vocabulary — see this migration's own header on why a
    -- Level I-IV classification is not asserted here.
    notes                   TEXT,

    declared_by             VARCHAR(128) NOT NULL,
    declared_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_facility_emergency_capability_tenant
    ON tuso.facility_emergency_capability (tenant_id);

COMMENT ON TABLE tuso.facility_emergency_capability IS
    'Hand-declared emergency capability per facility (W10) — NOT a national trauma-centre-level '
    'classification (none was found to exist for Zimbabwe; see this migration''s header). Each field '
    'is independently NULL-able: absence means "not yet declared", never "no".';
