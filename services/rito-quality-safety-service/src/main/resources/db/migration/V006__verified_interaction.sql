-- =====================================================================================
-- Rito — Experience & Reputation :: V006 (RW2)
-- Verified-interaction intake: each completed PCT encounter is recorded as an
-- eligible-for-feedback interaction. A later rating that references the encounter_ref
-- is stamped verified_interaction=true (RW3). This is the PCT verification gate.
-- Design: docs/architecture/rito-experience-reputation-design.md §4
-- =====================================================================================

CREATE TABLE rito.rit_verified_interaction (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             UUID NOT NULL,
    encounter_ref         VARCHAR(128) NOT NULL,   -- PCT encounterRef
    attending_provider_id VARCHAR(64),             -- Varapi Provider ID (from the enriched PCT payload)
    patient_cpid          VARCHAR(64),
    facility_id           UUID,
    encounter_type        VARCHAR(64),
    modality              VARCHAR(24),
    ended_at              TIMESTAMPTZ,
    source_system         VARCHAR(24) NOT NULL DEFAULT 'PCT',
    feedback_requested    BOOLEAN NOT NULL DEFAULT FALSE,  -- set when Khuluma sends the post-visit request (RW3)
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- Idempotent per encounter: re-consuming the same ENCOUNTER_COMPLETED event is a no-op.
    CONSTRAINT uq_rit_verified_interaction UNIQUE (tenant_id, encounter_ref)
);
CREATE INDEX idx_rit_verified_interaction_provider ON rito.rit_verified_interaction (tenant_id, attending_provider_id);
CREATE INDEX idx_rit_verified_interaction_encounter ON rito.rit_verified_interaction (tenant_id, encounter_ref);
