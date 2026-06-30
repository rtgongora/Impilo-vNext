-- WS#8 — Death & Post-Death Pathway: extend the pct_death_cases spine (PCT owns the DeathCase).
-- Adds confirmation/place/context, medico-legal + public-health screening flags, WHO cause-of-death
-- certification draft, certifier eligibility, body/mortuary custody (the genuine gap — minimal real
-- module IN pct-service, no new sovereign service), CRVS package linkage, source-seam provenance
-- (WS#6 inpatient discharge-disposition=death; WS#7 Daidzai emergency_incident), and an append-only
-- amendment + status audit trail. No fake external systems — coroner/RG/post-mortem are owner-routed
-- hooks recorded as status/flags only. Payment NEVER gates death documentation or certification.

-- ── 1. Extend pct_death_cases with the death-spine columns ───────────────────────────────────
ALTER TABLE pct_death_cases
    ADD COLUMN death_datetime          TIMESTAMPTZ,
    ADD COLUMN place_of_death_context  VARCHAR(40),   -- INPATIENT|ED|THEATRE|COMMUNITY|BROUGHT_IN_DEAD|IN_TRANSIT|OTHER
    ADD COLUMN place_of_death_location VARCHAR(128),
    ADD COLUMN resuscitation_attempted BOOLEAN,
    ADD COLUMN present_at_death        VARCHAR(256),
    ADD COLUMN confirmed_by            VARCHAR(128),
    ADD COLUMN confirmed_by_role       VARCHAR(64),
    ADD COLUMN confirmed_at            TIMESTAMPTZ,
    ADD COLUMN deceased_identity_status VARCHAR(24)  NOT NULL DEFAULT 'KNOWN', -- KNOWN|UNKNOWN|TEMPORARY
    ADD COLUMN temporary_identity_ref  VARCHAR(64),
    -- medico-legal screening
    ADD COLUMN coroner_referral_required BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN medico_legal_triggers    TEXT,         -- comma list of evaluated trigger codes
    ADD COLUMN coroner_referral_status  VARCHAR(24),  -- NULL|PENDING_REFERRAL|REFERRED|CLEARED (owner-routed hook)
    -- public-health screening
    ADD COLUMN public_health_flags      TEXT,         -- comma list (MATERNAL,PERINATAL,NEONATAL,STILLBIRTH,UNDER_FIVE,NOTIFIABLE,OUTBREAK)
    ADD COLUMN death_review_required    BOOLEAN       NOT NULL DEFAULT FALSE,
    ADD COLUMN rito_review_ref          VARCHAR(64),  -- link to Rito M&M / MDSR review (owner-routed)
    -- cause-of-death certification draft (WHO sequence)
    ADD COLUMN cod_immediate            VARCHAR(256),
    ADD COLUMN cod_antecedent           VARCHAR(256),
    ADD COLUMN cod_underlying           VARCHAR(256),
    ADD COLUMN cod_contributory         VARCHAR(256),
    ADD COLUMN cod_manner               VARCHAR(24),  -- NATURAL|ACCIDENT|HOMICIDE|SUICIDE|UNDETERMINED|PENDING
    ADD COLUMN cod_external_cause       VARCHAR(256),
    ADD COLUMN certifier_id             VARCHAR(128),
    ADD COLUMN certifier_role           VARCHAR(64),
    ADD COLUMN certifier_licence        VARCHAR(64),
    ADD COLUMN certification_status     VARCHAR(24)   NOT NULL DEFAULT 'NOT_STARTED', -- NOT_STARTED|DRAFT|SIGNED|AMENDED
    ADD COLUMN certified_at             TIMESTAMPTZ,
    ADD COLUMN cert_warnings            TEXT,         -- soft garbage-cause warnings surfaced at sign time
    ADD COLUMN cert_composition_ref     VARCHAR(128), -- Butano Composition/DocumentReference (owner-routed)
    -- body / mortuary release gating (custody detail lives in pct_body_custody)
    ADD COLUMN body_release_blocked     BOOLEAN       NOT NULL DEFAULT FALSE,
    -- CRVS handoff
    ADD COLUMN civil_registration_status VARCHAR(24)  NOT NULL DEFAULT 'NOT_STARTED', -- NOT_STARTED|PACKAGE_DRAFT|PACKAGE_READY|SUBMITTED|REGISTERED|REJECTED
    -- source seam provenance
    ADD COLUMN source_context           VARCHAR(40),  -- INPATIENT_DISCHARGE|EMERGENCY_INCIDENT|FACILITY|COMMUNITY
    ADD COLUMN source_ref               VARCHAR(64);

-- Backfill confirmed_* from existing pronounced_* so legacy rows remain consistent.
UPDATE pct_death_cases
   SET death_datetime = COALESCE(death_datetime, pronounced_at),
       confirmed_by    = COALESCE(confirmed_by, pronounced_by),
       confirmed_at    = COALESCE(confirmed_at, pronounced_at)
 WHERE death_datetime IS NULL;

CREATE INDEX idx_pct_death_cases_certstatus ON pct_death_cases (facility_id, certification_status);
CREATE INDEX idx_pct_death_cases_crvs       ON pct_death_cases (facility_id, civil_registration_status);
CREATE INDEX idx_pct_death_cases_coroner    ON pct_death_cases (facility_id, coroner_referral_required);

-- ── 2. Body / mortuary custody (the genuine gap — minimal real module, NOT a new service) ─────
CREATE TABLE pct_body_custody (
    custody_id          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID            NOT NULL,
    case_id             UUID            NOT NULL REFERENCES pct_death_cases(case_id),
    facility_id         UUID,
    body_tag            VARCHAR(64)     NOT NULL,
    storage_location    VARCHAR(128),
    status              VARCHAR(24)     NOT NULL DEFAULT 'RECEIVED', -- RECEIVED|STORED|RELEASE_REQUESTED|RELEASED
    received_by         VARCHAR(128),
    received_at         TIMESTAMPTZ,
    legal_hold          BOOLEAN         NOT NULL DEFAULT FALSE,
    legal_hold_reason   VARCHAR(256),
    postmortem_hold     BOOLEAN         NOT NULL DEFAULT FALSE,
    release_authorised_by VARCHAR(128),
    released_to_name    VARCHAR(128),
    released_to_relationship VARCHAR(64),
    released_to_id_ref  VARCHAR(64),
    released_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_pct_body_custody_case ON pct_body_custody (case_id);
CREATE INDEX idx_pct_body_custody_tenant_status ON pct_body_custody (tenant_id, facility_id, status);

-- ── 3. Append-only amendment + status audit trail (no silent amendment of time/cause/identity) ─
CREATE TABLE pct_death_audit (
    audit_id        UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID            NOT NULL,
    case_id         UUID            NOT NULL REFERENCES pct_death_cases(case_id),
    event_type      VARCHAR(48)     NOT NULL, -- CONFIRMED|MEDICO_LEGAL_SCREENED|PUBLIC_HEALTH_SCREENED|CERT_DRAFTED|CERT_SIGNED|CERT_AMENDED|BODY_RECEIVED|BODY_RELEASE_BLOCKED|BODY_RELEASED|CRVS_PACKAGED|CRVS_SUBMITTED|STATUS_CHANGE
    field_changed   VARCHAR(64),
    old_value       TEXT,
    new_value       TEXT,
    reason          VARCHAR(512),
    actor_id        VARCHAR(128)    NOT NULL,
    actor_role      VARCHAR(64),
    facility_id     UUID,
    occurred_at     TIMESTAMPTZ     NOT NULL DEFAULT now()
);
CREATE INDEX idx_pct_death_audit_case ON pct_death_audit (case_id, occurred_at);
