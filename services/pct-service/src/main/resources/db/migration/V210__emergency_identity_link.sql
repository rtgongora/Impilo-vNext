-- W12: the audit trail for resolving an unknown or provisional emergency patient onto a real
-- identity — the "unknown -> VITO merge with nothing lost" record this pack's own doctrine promises
-- but, until this migration, never wrote down anywhere.
--
-- WHAT THIS TABLE IS NOT
--
-- It is not a second copy of VITO's own merge record. tshepo-identity-service's
-- id_mapping.merged_into_cpid is the system-of-record redirect, and IdentityRepointHook (shared-
-- kernel) is the mechanical fan-out that bulk-repoints subject_cpid across every wired table when
-- that event arrives (see PctEmergencyRepointHook, unchanged by this migration). This table is
-- narrower: it is EMERGENCY's own clinical audit of WHY a given episode's identity was resolved the
-- way it was — the biometric check, the document shown, the relative who confirmed a name, the smart
-- card tapped, or a clinician's own recognition of a returning patient — written at the moment of
-- resolution, not reconstructed from a bulk UPDATE after the fact.
--
-- Rows are never updated or deleted. A correction supersedes the row it corrects and both remain
-- readable, because MCI misidentification is common and is often discovered only after the first
-- resolution was already acted on.
CREATE TABLE emergency_identity_link (
    link_id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                UUID NOT NULL,
    episode_id               UUID NOT NULL REFERENCES emergency_episode(episode_id),

    -- What the episode's identity attribute looked like immediately before this resolution.
    previous_identity_mode   VARCHAR(16) NOT NULL,
    previous_subject_cpid    VARCHAR(64),

    -- What it is now. Always a real cpid — this table records resolution TO a known identity, never
    -- a downgrade to unknown.
    resolved_subject_cpid    VARCHAR(64) NOT NULL,

    -- Closed vocabulary, deliberately clinical-identification-only. A VITO merge event that arrives
    -- with no prior clinical resolution recorded here (e.g. VITO's own matching, outside any
    -- workflow this pack drives) is handled by PctEmergencyRepointHook alone and writes no row here
    -- — that mechanism already updates the attribute correctly, and this table is not a second copy
    -- of every repoint, only of the ones a clinician or the resolution workflow performed.
    resolution_method        VARCHAR(24) NOT NULL,
    evidence_ref             VARCHAR(128),

    -- Set only when this clinical resolution corresponds to a VITO merge — the caller's own
    -- correlation id for that merge, if one is known. Nullable: a smart-card tap that confirms an
    -- identity VITO already carries as a single record triggers no merge at all.
    vito_merge_id            VARCHAR(128),

    resolved_by              VARCHAR(128) NOT NULL,
    resolved_at              TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- Correct, never delete. NULL means this is the currently-active resolution for the episode.
    --
    -- DEFERRABLE, deliberately. A correction supersedes the OLD row before inserting the NEW one
    -- (so uq_eil_active_per_episode below, a partial index and therefore never itself deferrable,
    -- sees at most one active row at the end of each statement) — but that update must reference the
    -- new row's id before the new row exists. A deferred FK checks at COMMIT instead of immediately,
    -- which is exactly the window this two-step write needs. The application does both writes in one
    -- transaction; see EmergencyIdentityLinkService.
    superseded_by            UUID REFERENCES emergency_identity_link(link_id) DEFERRABLE INITIALLY DEFERRED,

    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_eil_resolution_method CHECK (resolution_method IN (
        'BIOMETRIC', 'DOCUMENT', 'RELATIVE', 'SMART_CARD', 'CLINICAL_RECOGNITION')),

    CONSTRAINT chk_eil_previous_identity_mode CHECK (previous_identity_mode IN (
        'UNKNOWN', 'PROVISIONAL', 'KNOWN'))
);

-- At most one ACTIVE (non-superseded) link per episode — the current understanding of who this
-- patient is. Full history stays queryable through the superseded_by chain.
CREATE UNIQUE INDEX uq_eil_active_per_episode
    ON emergency_identity_link (tenant_id, episode_id)
    WHERE superseded_by IS NULL;

CREATE INDEX idx_eil_episode_history
    ON emergency_identity_link (tenant_id, episode_id, resolved_at DESC);

COMMENT ON TABLE emergency_identity_link IS
    'Append-only audit of clinical identity resolution for emergency episodes. Not the VITO merge '
    'record itself (see tshepo-identity-service.id_mapping) and not a second repoint mechanism (see '
    'PctEmergencyRepointHook) — this is the WHY behind a resolution, retained forever, corrected '
    'never overwritten.';
