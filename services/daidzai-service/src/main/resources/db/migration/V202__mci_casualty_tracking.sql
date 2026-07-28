-- MCI casualty tracking (W11). dai_emergency_incident already has incident_type='MCI' in its own
-- CHECK vocabulary and dai_affected_site already carries a rough estimated_affected COUNT — but
-- nothing tracks an individual casualty as a row. dai_emergency_incident itself models exactly ONE
-- subject (subject_cpid/subject_temp_ref/triage_category are singular columns, and incident_type
-- literally defaults to 'INDIVIDUAL') — for a mass-casualty incident that shape cannot hold more than
-- one person. This is the genuinely-absent capability the plan names.
--
-- Sieve category uses the standard international MCI triage vocabulary (IMMEDIATE/URGENT/DELAYED/
-- EXPECTANT/DECEASED) — a DIFFERENT, purpose-built scale from WHO IITT (R7's single-patient acuity
-- authority elsewhere in this pack). MCI sieve/sort is deliberately a separate, globally-recognised
-- method for the mass-casualty context, not a fourth invented vocabulary layered on IITT.

CREATE TABLE daidzai.dai_mci_casualty (
    id                      UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID          NOT NULL,
    incident_id             UUID          NOT NULL REFERENCES daidzai.dai_emergency_incident(id),
    site_id                 UUID          REFERENCES daidzai.dai_affected_site(id),

    -- Scene-assigned tag (e.g. "T-001") — the physical triage tag number, not a system-generated id.
    casualty_reference      VARCHAR(48)   NOT NULL,

    sieve_category          VARCHAR(16)   NOT NULL DEFAULT 'UNASSESSED',

    subject_identity_mode   VARCHAR(24)   NOT NULL DEFAULT 'UNKNOWN',
    subject_cpid            VARCHAR(128),
    subject_temp_ref        VARCHAR(128),

    status                  VARCHAR(24)   NOT NULL DEFAULT 'ON_SCENE',
    destination_facility_id UUID,

    -- The bulk-mint correlation (W11's other half): the pct.emergency_episode this casualty was
    -- minted onto. NULL until PCT's bulk-mint pass runs; NOT a foreign key (pct is a separate
    -- service/database), matching this pack's established cross-service correlation convention.
    emergency_episode_id    UUID,

    tagged_by               VARCHAR(128)  NOT NULL,
    tagged_at               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT chk_dmc_sieve_category CHECK (sieve_category IN (
        'UNASSESSED', 'IMMEDIATE', 'URGENT', 'DELAYED', 'EXPECTANT', 'DECEASED')),
    CONSTRAINT chk_dmc_status CHECK (status IN (
        'ON_SCENE', 'TRANSPORTED', 'AT_FACILITY', 'DECEASED_CONFIRMED')),
    CONSTRAINT chk_dmc_identity_mode CHECK (subject_identity_mode IN (
        'UNKNOWN', 'PROVISIONAL', 'KNOWN')),

    -- A retagging of the same physical tag number for one incident replaces the same casualty
    -- record, not a duplicate — the tag IS the idempotency key at the scene.
    CONSTRAINT uq_dmc_incident_reference UNIQUE (tenant_id, incident_id, casualty_reference)
);

CREATE INDEX idx_dai_mci_casualty_incident ON daidzai.dai_mci_casualty (tenant_id, incident_id, sieve_category);
-- The bulk-mint pass's own query: casualties not yet minted onto a pct episode.
CREATE INDEX idx_dai_mci_casualty_unminted ON daidzai.dai_mci_casualty (tenant_id, incident_id)
    WHERE emergency_episode_id IS NULL;

COMMENT ON TABLE daidzai.dai_mci_casualty IS
    'Individual casualty tracking within an MCI incident (W11) — genuinely absent before this '
    'migration; dai_emergency_incident models exactly one subject per row.';
COMMENT ON COLUMN daidzai.dai_mci_casualty.emergency_episode_id IS
    'The pct.emergency_episode this casualty was minted onto by PCT''s bulk-mint pass. NOT a '
    'foreign key — pct is a separate service/database.';

-- Surge signal (W11's "surge alerts"): a plain counter + timestamp on the incident itself, stamped
-- the moment a casualty count threshold is crossed while tagging — no separate polling job for
-- something already visible on every tag-write.
ALTER TABLE daidzai.dai_emergency_incident
    ADD COLUMN surge_declared_at TIMESTAMPTZ,
    ADD COLUMN surge_casualty_count INTEGER;

COMMENT ON COLUMN daidzai.dai_emergency_incident.surge_declared_at IS
    'Stamped once, the first time this incident''s casualty count crosses the surge threshold '
    '(MciCasualtyService.SURGE_THRESHOLD). Never cleared — a surge that happened is a fact about the '
    'incident''s history, not a live status that un-happens as casualties are dispositioned.';
