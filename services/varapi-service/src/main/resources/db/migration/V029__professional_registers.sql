-- =====================================================================================
-- varapi :: V029 — First-class professional registers, restrictions, good standing (ROM-W3, R4)
-- The register is made first-class WITHOUT duplicating the existing entry table: the register
-- CATALOGUE is new (professional_registers), and the existing provider_council_registration_records
-- (V009) becomes the register ENTRY by gaining a register_id link. Restrictions and good standing —
-- today carried only as strings (restriction_summary / status) — become first-class rows.
-- =====================================================================================

-- The register catalogue: one row per statutory register within a council (e.g. NCZ_GENERAL).
CREATE TABLE IF NOT EXISTS varapi.professional_registers (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       UUID        NOT NULL,
    council_id      BIGINT      NOT NULL REFERENCES varapi.councils(id),
    register_code   VARCHAR(64) NOT NULL,
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_professional_register UNIQUE (council_id, register_code)
);
CREATE INDEX IF NOT EXISTS idx_professional_registers_council
    ON varapi.professional_registers(tenant_id, council_id);

-- The existing council-registration record becomes the register ENTRY (nullable link; new
-- registrations set it, legacy rows are assigned as they are next touched).
ALTER TABLE varapi.provider_council_registration_records
    ADD COLUMN IF NOT EXISTS register_id BIGINT REFERENCES varapi.professional_registers(id);
CREATE INDEX IF NOT EXISTS idx_pcouncil_reg_register
    ON varapi.provider_council_registration_records(register_id);

-- First-class restrictions on a register entry (replaces the free-text restriction_summary).
CREATE TABLE IF NOT EXISTS varapi.register_entry_restrictions (
    id                      BIGSERIAL PRIMARY KEY,
    tenant_id               UUID        NOT NULL,
    registration_record_id  BIGINT      NOT NULL REFERENCES varapi.provider_council_registration_records(id),
    restriction_type        VARCHAR(32) NOT NULL,
    description             TEXT        NOT NULL,
    imposed_by              VARCHAR(128),
    decision_ref            VARCHAR(255),
    valid_from              DATE,
    valid_to                DATE,
    status                  VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    source                  VARCHAR(24) NOT NULL DEFAULT 'NATIVE',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_restriction_type CHECK (restriction_type IN
        ('SCOPE_LIMIT', 'SUPERVISION', 'CONDITION', 'INTERDICTION')),
    CONSTRAINT chk_restriction_source CHECK (source IN ('NATIVE', 'IMPORT'))
);
CREATE INDEX IF NOT EXISTS idx_register_restrictions_record
    ON varapi.register_entry_restrictions(registration_record_id) WHERE status = 'ACTIVE';

-- First-class good-standing status per (provider, council) — disclosure-classed truth used by
-- renewal (W5) and the public verify (register-based good standing).
CREATE TABLE IF NOT EXISTS varapi.provider_good_standing (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       UUID        NOT NULL,
    provider_id     BIGINT      NOT NULL REFERENCES varapi.provider(id),
    council_id      BIGINT      NOT NULL REFERENCES varapi.councils(id),
    standing        VARCHAR(32) NOT NULL DEFAULT 'GOOD',
    as_of           TIMESTAMPTZ NOT NULL DEFAULT now(),
    derivation      TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_provider_good_standing UNIQUE (tenant_id, provider_id, council_id),
    CONSTRAINT chk_good_standing CHECK (standing IN
        ('GOOD', 'NOT_GOOD', 'UNDER_INVESTIGATION', 'SUSPENDED'))
);
CREATE INDEX IF NOT EXISTS idx_good_standing_provider
    ON varapi.provider_good_standing(tenant_id, provider_id);
