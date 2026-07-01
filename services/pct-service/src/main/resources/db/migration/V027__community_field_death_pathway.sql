-- Community / Field Death Pathway — contract refinement (docs/clinical/community-field-death-pathway.md).
-- The prior WS#8 spine (V022) collapsed community/field death, brought-in-dead, and facility
-- confirmation into a single crude source_context, and mixed verbal-autopsy-inferred causes with
-- medically certified ones. This migration splits those realities:
--
--   * source_context is widened to the full origin taxonomy (FACILITY | BROUGHT_IN_DEAD |
--     COMMUNITY_HOME | COMMUNITY_FIELD | OUTREACH_FIELD_OPS | DISASTER_SITE | ISOLATION_FIELD_SITE |
--     CUSTODY | UNKNOWN_LOCATION, plus the existing INPATIENT_DISCHARGE / EMERGENCY_INCIDENT seams).
--   * body_disposition_context records where the body actually goes — a death case NO LONGER
--     requires facility mortuary custody (safe burial / family release / police handover / unrecovered
--     are first-class). Mortuary custody (pct_body_custody) stays optional, created only on receipt.
--   * cause_of_death_basis records the certainty CLASS of the cause so verbal-autopsy /
--     field-investigation probable causes are never counted as medically certified in mortality
--     intelligence (WHO VA standard vs medical certification of cause of death).
--   * a community death may be REPORTED (unverified, by CHW / family / surveillance) distinct from
--     provider CONFIRMED — reporter provenance is captured.
--
-- Two new child tables carry the verbal-autopsy interview and the non-facility (field) body
-- management branch (WHO safe-and-dignified-burial / IFRC safe handling of bodies). No new sovereign
-- service — this remains inside pct-service, which owns the DeathCase. Payment NEVER gates any of it.

-- ── 1. New spine columns on pct_death_cases ──────────────────────────────────────────────────
ALTER TABLE pct_death_cases
    -- Where the body goes (custody is no longer mandatory-to-facility).
    ADD COLUMN body_disposition_context VARCHAR(40),
        -- BROUGHT_TO_FACILITY | NOT_BROUGHT_TO_FACILITY | FIELD_SAFE_BURIAL | COMMUNITY_RELEASE
        -- | POLICE_CUSTODY | FUNERAL_DIRECTOR_DIRECT | TRANSFERRED_TO_MORTUARY | TRANSFERRED_TO_POST_MORTEM_SITE
    -- Certainty class of the assigned cause of death (keeps VA-probable distinct from certified).
    ADD COLUMN cause_of_death_basis     VARCHAR(32),
        -- MEDICALLY_CERTIFIED | POST_MORTEM_CERTIFIED | VERBAL_AUTOPSY_PROBABLE
        -- | FIELD_INVESTIGATION_PROBABLE | MEDICO_LEGAL_PENDING | UNKNOWN_UNCERTIFIED
    -- Unverified community-report provenance (distinct from a provider confirmation).
    ADD COLUMN reporter_name            VARCHAR(128),
    ADD COLUMN reporter_role            VARCHAR(64),   -- CHW|FAMILY|SURVEILLANCE|COMMUNITY_LEADER|POLICE|OTHER
    ADD COLUMN reporter_contact         VARCHAR(128);

-- The source_context / place_of_death_context comments in V022 are now superseded by the widened
-- taxonomy above; the columns are already VARCHAR(40) so no width change is needed.
COMMENT ON COLUMN pct_death_cases.source_context IS
    'Origin taxonomy: FACILITY|BROUGHT_IN_DEAD|COMMUNITY_HOME|COMMUNITY_FIELD|OUTREACH_FIELD_OPS|DISASTER_SITE|ISOLATION_FIELD_SITE|CUSTODY|UNKNOWN_LOCATION|INPATIENT_DISCHARGE|EMERGENCY_INCIDENT';
COMMENT ON COLUMN pct_death_cases.place_of_death_context IS
    'INPATIENT|ED|THEATRE|COMMUNITY|COMMUNITY_HOME|COMMUNITY_FIELD|OUTREACH|DISASTER_SITE|ISOLATION_SITE|BROUGHT_IN_DEAD|IN_TRANSIT|CUSTODY|OTHER';

-- Backfill: legacy rows carried the crude source_context 'COMMUNITY'. Promote to COMMUNITY_FIELD
-- (the provider-confirmed field default) unless the place makes it a brought-in-dead case.
UPDATE pct_death_cases
   SET source_context = CASE
           WHEN place_of_death_context = 'BROUGHT_IN_DEAD' THEN 'BROUGHT_IN_DEAD'
           ELSE 'COMMUNITY_FIELD'
       END
 WHERE source_context = 'COMMUNITY';

-- Backfill body disposition for existing rows so the column is meaningful from day one.
UPDATE pct_death_cases
   SET body_disposition_context = CASE
           WHEN place_of_death_context IN ('INPATIENT','ED','THEATRE','BROUGHT_IN_DEAD','IN_TRANSIT') THEN 'BROUGHT_TO_FACILITY'
           ELSE 'NOT_BROUGHT_TO_FACILITY'
       END
 WHERE body_disposition_context IS NULL;

-- Backfill basis: a signed medical certificate is MEDICALLY_CERTIFIED; an open coroner referral is
-- MEDICO_LEGAL_PENDING; everything else is UNKNOWN_UNCERTIFIED until a cause is assigned.
UPDATE pct_death_cases
   SET cause_of_death_basis = CASE
           WHEN certification_status IN ('SIGNED','AMENDED') THEN 'MEDICALLY_CERTIFIED'
           WHEN coroner_referral_required THEN 'MEDICO_LEGAL_PENDING'
           ELSE 'UNKNOWN_UNCERTIFIED'
       END
 WHERE cause_of_death_basis IS NULL;

CREATE INDEX idx_pct_death_cases_source   ON pct_death_cases (facility_id, source_context);
CREATE INDEX idx_pct_death_cases_codbasis ON pct_death_cases (facility_id, cause_of_death_basis);

-- ── 2. Verbal-autopsy interview (WHO VA standard for deaths outside health facilities) ────────
-- A structured interview that yields a PROBABLE cause. It is NOT a medical certification: it never
-- sets certification_status=SIGNED and only ever writes cause_of_death_basis=VERBAL_AUTOPSY_PROBABLE.
-- Red flags surfaced during the interview route the case to the medico-legal pathway instead.
CREATE TABLE pct_verbal_autopsy (
    va_id                     UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                 UUID          NOT NULL,
    case_id                   UUID          NOT NULL REFERENCES pct_death_cases(case_id),
    facility_id               UUID,
    instrument                VARCHAR(32)   NOT NULL DEFAULT 'WHO_VA_2022', -- WHO_VA_2022|WHO_VA_2016|OTHER
    interviewee_relationship  VARCHAR(64),                  -- SPOUSE|CHILD|PARENT|SIBLING|CAREGIVER|NEIGHBOUR|OTHER
    interviewed_at            TIMESTAMPTZ,
    interviewer_id            VARCHAR(128),
    interviewer_role          VARCHAR(64),
    narrative                 TEXT,
    probable_immediate_cause  VARCHAR(256),
    probable_underlying_cause VARCHAR(256),
    probable_cause_code       VARCHAR(64),                  -- ICD-11 / VA cause list code
    cause_assignment_method   VARCHAR(32),                  -- PHYSICIAN_REVIEW|COMPUTER_CODED_INTERVA|COMPUTER_CODED_INSILICOVA|EXPERT_PANEL
    red_flags                 TEXT,                         -- comma list of medico-legal red flags (routes to coroner)
    status                    VARCHAR(24)   NOT NULL DEFAULT 'DRAFT', -- DRAFT|COMPLETED
    recorded_by               VARCHAR(128),
    recorded_by_role          VARCHAR(64),
    created_at                TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_pct_va_case   ON pct_verbal_autopsy (case_id);
CREATE INDEX idx_pct_va_tenant ON pct_verbal_autopsy (tenant_id, facility_id, status);

-- ── 3. Field body management (non-facility custody: safe & dignified burial / release) ────────
-- The body may never come to a facility. This records the field disposition — WHO safe-and-dignified
-- burial (infection control + family/religious involvement) and IFRC safe handling of bodies during
-- outbreaks. This is NOT mortuary custody (pct_body_custody); a case may have this and no custody row.
CREATE TABLE pct_field_body_management (
    fbm_id                     UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                  UUID         NOT NULL,
    case_id                    UUID         NOT NULL REFERENCES pct_death_cases(case_id),
    facility_id                UUID,
    disposition_type           VARCHAR(40)  NOT NULL, -- SAFE_AND_DIGNIFIED_BURIAL|FAMILY_RELEASE|FUNERAL_DIRECTOR|POLICE_HANDOVER|UNRECOVERED
    infectious_risk            BOOLEAN      NOT NULL DEFAULT FALSE,
    infectious_hazard          VARCHAR(64),           -- EVD|MARBURG|CHOLERA|COVID19|LASSA|UNKNOWN|OTHER
    safe_burial_protocol_applied BOOLEAN    NOT NULL DEFAULT FALSE,
    ppe_used                   BOOLEAN,
    handling_team              VARCHAR(128),
    family_present             BOOLEAN,
    religious_rites_observed   BOOLEAN,
    released_to_name           VARCHAR(128),
    released_to_relationship   VARCHAR(64),
    location                   VARCHAR(128),
    occurred_at                TIMESTAMPTZ,
    notes                      TEXT,
    managed_by                 VARCHAR(128),
    managed_by_role            VARCHAR(64),
    created_at                 TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                 TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_pct_fbm_case   ON pct_field_body_management (case_id);
CREATE INDEX idx_pct_fbm_tenant ON pct_field_body_management (tenant_id, facility_id, disposition_type);
