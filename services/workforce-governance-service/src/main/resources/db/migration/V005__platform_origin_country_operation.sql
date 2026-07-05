-- ============================================================================
-- workforce-governance-service V005 — Platform Origin + Country Operation
-- (IATG Wave-1, WS-A).
--
-- Introduces the platform-level root of trust ("Platform Origin"), the
-- per-country operation record, and doctrine-compliant national appointments.
--
--   * wgv_platform_origin      — singleton platform root record. NOT tenant
--                                scoped: this is the origin of the platform
--                                itself, above every country operation.
--   * wgv_country_operation    — one row per country/tenant activation
--                                (tenant_id UNIQUE), carrying the ISO country
--                                code, legal framework and lifecycle status.
--   * wgv_national_appointment — national-level role appointments (e.g.
--                                NATIONAL_ADMINISTRATOR) with explicit source,
--                                appointer, dates, expiry, scope and
--                                revocation trail per doctrine.
--
-- All mutations of these records execute ONLY through APPROVED PLATFORM_ACTION
-- access requests under the two-person rule (see V006).
-- ============================================================================

CREATE TABLE wgv_platform_origin (
    id                UUID PRIMARY KEY,
    -- Guard column: always TRUE and UNIQUE, so at most one root record exists.
    singleton         BOOLEAN NOT NULL DEFAULT TRUE,
    root_account_refs JSONB,
    recovery_policy   JSONB,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_wgv_platform_origin_singleton UNIQUE (singleton),
    CONSTRAINT ck_wgv_platform_origin_singleton CHECK (singleton)
);

CREATE TABLE wgv_country_operation (
    id                         UUID PRIMARY KEY,
    tenant_id                  UUID NOT NULL,
    iso_country_code           CHAR(2) NOT NULL,
    display_name               VARCHAR(255),
    legal_framework            JSONB,
    sovereign_organisation_id  UUID,
    status                     VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
                                            -- DRAFT|ACTIVE|SUSPENDED|CLOSED
    created_by_platform_action UUID,        -- wgv_access_request id (PLATFORM_ACTION)
    created_at                 TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                 TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_wgv_country_operation_tenant UNIQUE (tenant_id)
);

CREATE INDEX idx_wgv_country_op_status ON wgv_country_operation (status);
CREATE INDEX idx_wgv_country_op_iso ON wgv_country_operation (iso_country_code);

CREATE TABLE wgv_national_appointment (
    id                   UUID PRIMARY KEY,
    country_operation_id UUID NOT NULL REFERENCES wgv_country_operation (id),
    subject_user_id      VARCHAR(255) NOT NULL,
    role                 VARCHAR(64) NOT NULL DEFAULT 'NATIONAL_ADMINISTRATOR',
    appointment_source   VARCHAR(32) NOT NULL DEFAULT 'PLATFORM_ORIGIN',
                                            -- PLATFORM_ORIGIN|DELEGATED
    appointed_by         VARCHAR(255) NOT NULL,
    appointed_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at           TIMESTAMPTZ,
    scope                JSONB,
    status               VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
                                            -- ACTIVE|REVOKED|EXPIRED
    revocation_reason    TEXT,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_wgv_natappt_country_op ON wgv_national_appointment (country_operation_id, status);
CREATE INDEX idx_wgv_natappt_subject ON wgv_national_appointment (subject_user_id, status);
