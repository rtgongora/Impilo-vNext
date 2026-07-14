-- ============================================================================
-- V022: Virtual service-delivery registry (virtual hospitals) — TUSO SoR.
--
-- Doctrine: virtual hospitals are SERVICE ARCHITECTURE, not duplicate building
-- architecture. They are governed pools of clinical capability organised as
-- virtual service-delivery institutions. They are explicitly NOT rows in
-- tuso.facility — a virtual service entity must never be collapsed into the
-- physical facility registry (distinct identifier class: Virtual Hospital ID).
--
-- TUSO owns the definitions (entity + service lines + routing seams + queue
-- definitions); PCT materialises the queue definitions into pct_queues for
-- care operations (same ownership doctrine as facility queue definitions).
--
-- Queue definitions carry NO status column: runtime queue status
-- (LIVE vs AWAITING_BACKEND) is COMPUTED downstream from what PCT actually
-- materialised — stored state must never overstate runtime reality.
-- ============================================================================

CREATE TABLE IF NOT EXISTS tuso.virtual_service_entity (
    id                              BIGSERIAL PRIMARY KEY,
    vs_uid                          VARCHAR(64)  NOT NULL UNIQUE,   -- e.g. 'vh-national-telemedicine'
    tenant_id                       UUID         NOT NULL,
    name                            VARCHAR(255) NOT NULL,
    level                           VARCHAR(32)  NOT NULL,          -- NATIONAL | PROVINCIAL | SPECIALIST | PROGRAMME | COMMUNITY | EMERGENCY | LEARNING
    operating_model                 VARCHAR(48)  NOT NULL,          -- HOSTED | NETWORKED | PROGRAMME_OWNED | EMERGENCY_ACTIVATED | DIASPORA_ENABLED
    operating_authority             VARCHAR(255),
    regulatory_status               VARCHAR(64),
    purpose                         TEXT,
    catchment                       VARCHAR(255),
    province_code                   VARCHAR(8),                     -- provincial entities only (ZW-HA, …)
    cross_border_participation      BOOLEAN      NOT NULL DEFAULT FALSE,
    allowed_cross_border_privileges JSONB        NOT NULL DEFAULT '[]'::jsonb,
    billing_models                  JSONB        NOT NULL DEFAULT '[]'::jsonb,
    session_mode_ids                JSONB        NOT NULL DEFAULT '[]'::jsonb,
    provider_pool_cadres            JSONB        NOT NULL DEFAULT '[]'::jsonb,
    linked_facility_roles           JSONB        NOT NULL DEFAULT '[]'::jsonb,
    -- Fail-closed lifecycle: CONFIGURED -> ACTIVATION_REQUESTED (preconditions held)
    -- -> ACTIVE (only after PCT confirms pool materialisation) -> SUSPENDED.
    lifecycle_status                VARCHAR(32)  NOT NULL DEFAULT 'CONFIGURED'
        CHECK (lifecycle_status IN ('CONFIGURED', 'ACTIVATION_REQUESTED', 'ACTIVE', 'SUSPENDED')),
    activated_by                    VARCHAR(255),
    activated_at                    TIMESTAMPTZ,
    version                         INTEGER      NOT NULL DEFAULT 1,
    created_at                      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_vse_tenant ON tuso.virtual_service_entity (tenant_id);
CREATE INDEX IF NOT EXISTS idx_vse_lifecycle ON tuso.virtual_service_entity (tenant_id, lifecycle_status);

-- Service lines (display/service-catalogue rows, ordered).
CREATE TABLE IF NOT EXISTS tuso.virtual_service_line (
    id                 BIGSERIAL PRIMARY KEY,
    virtual_service_id BIGINT       NOT NULL REFERENCES tuso.virtual_service_entity(id) ON DELETE CASCADE,
    name               VARCHAR(255) NOT NULL,
    position           INTEGER      NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_vsl_entity ON tuso.virtual_service_line (virtual_service_id);

-- Routing seams: how a virtual service is reachable through EXISTING routing
-- substrate. target_ref is the pool key (frozen contract): PCT referrals carry
-- routing_kind='POOL' + routing_pool_id=<target_ref>.
CREATE TABLE IF NOT EXISTS tuso.virtual_service_routing_seam (
    id                 BIGSERIAL PRIMARY KEY,
    virtual_service_id BIGINT       NOT NULL REFERENCES tuso.virtual_service_entity(id) ON DELETE CASCADE,
    routing_type       VARCHAR(64)  NOT NULL,           -- e.g. SPECIALTY_POOL
    target_ref         VARCHAR(128) NOT NULL,           -- pool key (routing seam target)
    note               TEXT,
    active             BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_vsrs_entity_target UNIQUE (virtual_service_id, routing_type, target_ref)
);
CREATE INDEX IF NOT EXISTS idx_vsrs_entity ON tuso.virtual_service_routing_seam (virtual_service_id);
CREATE INDEX IF NOT EXISTS idx_vsrs_target ON tuso.virtual_service_routing_seam (target_ref) WHERE active;

-- Queue definitions (owned here, materialised by PCT with
-- sourceRef '{vs_uid}:{queue_key}'). Deliberately NO status column — status is
-- computed from PCT materialisation state, never stored here.
CREATE TABLE IF NOT EXISTS tuso.virtual_service_queue_def (
    id                  BIGSERIAL PRIMARY KEY,
    virtual_service_id  BIGINT       NOT NULL REFERENCES tuso.virtual_service_entity(id) ON DELETE CASCADE,
    queue_key           VARCHAR(128) NOT NULL,          -- e.g. 'provincial-general'
    name                VARCHAR(255) NOT NULL,
    default_sla_minutes INTEGER,                        -- NULL = no SLA timer for this queue
    rules               JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_vsqd_entity_key UNIQUE (virtual_service_id, queue_key)
);
CREATE INDEX IF NOT EXISTS idx_vsqd_entity ON tuso.virtual_service_queue_def (virtual_service_id);

-- Governed-edit history (per-field, same shape as tuso.facility_history but
-- bound to the virtual service registry — virtual services are NOT facilities).
CREATE TABLE IF NOT EXISTS tuso.virtual_service_history (
    id                 BIGSERIAL PRIMARY KEY,
    virtual_service_id BIGINT       NOT NULL REFERENCES tuso.virtual_service_entity(id) ON DELETE CASCADE,
    change_type        VARCHAR(30)  NOT NULL,           -- CREATE | UPDATE | SEAM_ADD | SEAM_UPDATE | QUEUE_DEF_UPDATE | ACTIVATE | ACTIVE | SUSPEND
    field_name         VARCHAR(100),
    old_value          TEXT,
    new_value          TEXT,
    changed_by         VARCHAR(255) NOT NULL,
    changed_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    reason             TEXT
);
CREATE INDEX IF NOT EXISTS idx_vsh_entity ON tuso.virtual_service_history (virtual_service_id);
