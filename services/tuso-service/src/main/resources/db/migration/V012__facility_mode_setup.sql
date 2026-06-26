-- Facility Mode setup-state tracking and service-point configuration (L2 / T4 build).
-- Owns the setup-wizard progress (dept->service-point->queue->workflow->workforce->
-- OROS-routing->Khuluma-channel->Fundo-readiness->go-live) and service-point config.
-- Facility config is persisted here (TUSO SoR); never in the stateless experience-bff.

-- ── Facility setup state (one row per facility, tenant-scoped) ──────────────────
CREATE TABLE IF NOT EXISTS tuso.facility_setup_state (
    id                          BIGSERIAL PRIMARY KEY,
    facility_id                 BIGINT NOT NULL REFERENCES tuso.facility(id),
    tenant_id                   UUID NOT NULL,
    departments_configured      BOOLEAN NOT NULL DEFAULT FALSE,
    service_points_configured   BOOLEAN NOT NULL DEFAULT FALSE,
    queues_configured           BOOLEAN NOT NULL DEFAULT FALSE,
    workflows_configured        BOOLEAN NOT NULL DEFAULT FALSE,
    workforce_linked            BOOLEAN NOT NULL DEFAULT FALSE,
    oros_routing_configured     BOOLEAN NOT NULL DEFAULT FALSE,
    khuluma_channels_configured BOOLEAN NOT NULL DEFAULT FALSE,
    fundo_ready                 BOOLEAN NOT NULL DEFAULT FALSE,
    go_live                     BOOLEAN NOT NULL DEFAULT FALSE,
    go_live_at                  TIMESTAMPTZ,
    metadata                    JSONB,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by                  VARCHAR(255),
    updated_by                  VARCHAR(255),
    CONSTRAINT uq_facility_setup_state_facility UNIQUE (facility_id)
);

CREATE INDEX IF NOT EXISTS idx_facility_setup_state_tenant
    ON tuso.facility_setup_state (tenant_id);

-- ── Service points (operational sub-units bound to a facility unit/department) ──
CREATE TABLE IF NOT EXISTS tuso.service_point (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    facility_id         BIGINT NOT NULL REFERENCES tuso.facility(id),
    facility_unit_id    BIGINT REFERENCES tuso.facility_unit(id),
    tenant_id           UUID NOT NULL,
    name                VARCHAR(255) NOT NULL,
    code                VARCHAR(64),
    service_point_type  VARCHAR(64) NOT NULL DEFAULT 'CONSULTATION',
    queue_id            VARCHAR(128),
    workflow_archetype  VARCHAR(64),
    status              VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    metadata            JSONB,
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(255),
    updated_by          VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_service_point_facility
    ON tuso.service_point (facility_id);
CREATE INDEX IF NOT EXISTS idx_service_point_tenant
    ON tuso.service_point (tenant_id);
CREATE INDEX IF NOT EXISTS idx_service_point_unit
    ON tuso.service_point (facility_unit_id);
