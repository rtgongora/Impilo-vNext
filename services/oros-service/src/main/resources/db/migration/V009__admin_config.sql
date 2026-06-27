-- =============================================
-- Service : oros-service
-- Flyway  : V009__admin_config.sql
-- Purpose : Tenant/facility-scoped admin configuration for the diagnostics journey (spec §W8):
--           routing rules and critical-result escalation rules. Stored as JSON so the rule shape
--           can evolve; retrievable + editable via the admin API. One row per
--           (tenant, facility, config_type); facility_id NULL = tenant-wide default.
-- =============================================

CREATE TABLE oros_admin_config (
    id           UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id    UUID         NOT NULL,
    facility_id  UUID,
    config_type  VARCHAR(48)  NOT NULL,  -- ROUTING_RULES, CRITICAL_ESCALATION_RULES
    config_json  JSONB        NOT NULL DEFAULT '{}'::jsonb,
    updated_by   VARCHAR(128),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uq_oros_admin_config
    ON oros_admin_config (tenant_id, COALESCE(facility_id, '00000000-0000-0000-0000-000000000000'::uuid), config_type);
