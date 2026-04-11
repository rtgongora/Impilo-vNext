-- =============================================================================
-- PCT V004 — Emergency Care-First Pathway (Health OS §12)
--
-- "No person may be denied urgent or emergency care because identity,
--  registration, authentication, or ordinary enrollment processes are
--  incomplete or unavailable."
--
-- Allows authorized responders to open emergency cases with unknown or
-- unregistered persons. Records a temporary emergency identifier that
-- must later be reconciled to a verified Health ID.
-- =============================================================================

CREATE TABLE pct.emergency_cases (
    id                      BIGSERIAL PRIMARY KEY,
    tenant_id               UUID            NOT NULL,
    emergency_case_id       UUID            NOT NULL DEFAULT gen_random_uuid(),
    temp_person_ref         VARCHAR(255),
    known_health_id         VARCHAR(255),
    triage_category         VARCHAR(32)     NOT NULL DEFAULT 'RED',
    presenting_complaint    TEXT            NOT NULL,
    opened_by_actor_id      VARCHAR(255)    NOT NULL,
    opened_by_provider_id   VARCHAR(255),
    facility_id             UUID,
    location_description    VARCHAR(512),
    status                  VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE',
    reconciled_health_id    VARCHAR(255),
    reconciled_at           TIMESTAMPTZ,
    reconciled_by           VARCHAR(255),
    notes                   TEXT,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_emergency_case_id ON pct.emergency_cases (emergency_case_id);
CREATE INDEX idx_emergency_tenant_status ON pct.emergency_cases (tenant_id, status);
CREATE INDEX idx_emergency_unrec ON pct.emergency_cases (tenant_id, status)
    WHERE reconciled_health_id IS NULL AND status != 'CLOSED';
