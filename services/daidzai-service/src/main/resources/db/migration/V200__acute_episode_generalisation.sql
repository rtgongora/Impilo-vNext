-- Generalise the trauma-episode spine into the acute-episode spine, and give it a PCT anchor.
--
-- This migration closes registered CC-5 violation **V-3**: `dai_trauma_episode` anchored on
-- `subject_health_id` alone, with no journey or encounter back-link. The doctrine's own register
-- names the closing change as "nullable PCT back-link + link-on-facility-arrival flow", and that is
-- what this is.
--
-- WHY THE TABLE KEEPS ITS NAME
--
-- The spine is no longer trauma-only — it correlates any acute presentation across the prehospital
-- and multi-facility window. Renaming it would mean renaming `trauma_episode_id` on every phase
-- owner (pct, inpatient, madi), the live `X-Trauma-Episode-ID` header, and roughly a hundred call
-- sites, for no behavioural gain. So the name is historical and `episode_class` carries the meaning.
-- Documented here so the next reader does not conclude the model is trauma-shaped.
--
-- WHAT DAIDZAI STILL DOES NOT OWN
--
-- CC-4 makes this a DELEGATED correlation capability, not a rank above the continuum. Daidzai holds
-- identity, lifecycle and the phase timeline; it references peer truth by id and duplicates no
-- clinical record. Adding a PCT back-link does not reverse that direction — it is the spine becoming
-- resolvable to the continuum, which is what CC-4's second clause requires once the patient reaches
-- a facility.

ALTER TABLE daidzai.dai_trauma_episode
    -- The spine covers any acute presentation. TRAUMA remains the default so every existing row
    -- keeps its current meaning without a backfill that would guess.
    ADD COLUMN episode_class VARCHAR(24) NOT NULL DEFAULT 'TRAUMA',

    -- ── THE PCT BACK-LINK — this is V-3 closing ──────────────────────────────────────────────
    -- Nullable, because the window this spine exists for is precisely the one where no PCT anchor
    -- exists yet: an incident, a dispatch, a prehospital handover. The link is written on facility
    -- arrival. What must never happen is an episode staying unanchored AFTER the patient reaches a
    -- facility, which is what the phase-aware CHECK below enforces.
    ADD COLUMN pct_journey_id           VARCHAR(26),
    ADD COLUMN pct_emergency_episode_id UUID,
    ADD COLUMN continuum_linked_at      TIMESTAMPTZ,

    -- ── MERGE / ADOPT ────────────────────────────────────────────────────────────────────────
    -- uq_dai_trauma_episode_origin keys on a bare origin_key, which held while only trauma minted.
    -- It stops holding the moment two entry paths can describe one patient: someone who phones the
    -- SOS line AND walks in mints twice — daidzai on the incident, PCT on the episode. That is the
    -- common case for emergency care, not an edge case, so the spine needs a way to absorb one
    -- episode into another rather than carrying two.
    --
    -- Absorbed episodes are never deleted. A correlation already stamped with the absorbed id still
    -- has to resolve, and an audit trail written against it still has to read.
    ADD COLUMN merged_into_id UUID REFERENCES daidzai.dai_trauma_episode (id),
    ADD COLUMN merged_at      TIMESTAMPTZ,
    ADD COLUMN merge_reason   VARCHAR(255);

ALTER TABLE daidzai.dai_trauma_episode
    ADD CONSTRAINT chk_dai_episode_class CHECK (episode_class IN (
        'TRAUMA', 'MEDICAL', 'OBSTETRIC', 'PAEDIATRIC', 'NEONATAL', 'TOXICOLOGY', 'PSYCHIATRIC',
        'ENVIRONMENTAL', 'BURNS', 'MCI', 'UNDIFFERENTIATED')),

    -- A link is a fact with a time, or it is neither. Half a link — an id with no timestamp, or a
    -- timestamp with no id — is the state that makes "when did this become resolvable?" unanswerable.
    ADD CONSTRAINT chk_dai_continuum_link_complete CHECK (
        (pct_journey_id IS NULL AND continuum_linked_at IS NULL)
        OR (pct_journey_id IS NOT NULL AND continuum_linked_at IS NOT NULL)),

    -- V-3, enforced rather than reviewed. Once the episode has reached a facility phase it must
    -- resolve to a PCT journey. The prehospital phases are exempt because that is the window the
    -- spine exists to cover; everything from ED onward is inside the continuum.
    ADD CONSTRAINT chk_dai_anchored_once_in_facility CHECK (
        current_phase IN ('INCIDENT', 'DISPATCH', 'PREHOSPITAL')
        OR status = 'CLOSED'
        OR pct_journey_id IS NOT NULL),

    ADD CONSTRAINT chk_dai_merge_complete CHECK (
        (merged_into_id IS NULL AND merged_at IS NULL)
        OR (merged_into_id IS NOT NULL AND merged_at IS NOT NULL)),

    -- An episode cannot be absorbed into itself. Without this, an idempotent adopt that resolved to
    -- the same id would create a self-loop and any timeline walk over merges would not terminate.
    ADD CONSTRAINT chk_dai_no_self_merge CHECK (merged_into_id IS NULL OR merged_into_id <> id);

CREATE INDEX idx_dai_trauma_episode_pct_journey
    ON daidzai.dai_trauma_episode (tenant_id, pct_journey_id)
    WHERE pct_journey_id IS NOT NULL;

CREATE INDEX idx_dai_trauma_episode_pct_episode
    ON daidzai.dai_trauma_episode (tenant_id, pct_emergency_episode_id)
    WHERE pct_emergency_episode_id IS NOT NULL;

CREATE INDEX idx_dai_trauma_episode_merged
    ON daidzai.dai_trauma_episode (tenant_id, merged_into_id)
    WHERE merged_into_id IS NOT NULL;

-- The unanchored-in-facility sweep: episodes that reached a facility and never acquired a journey.
-- This is the query that makes V-3 regressions visible rather than theoretical.
CREATE INDEX idx_dai_trauma_episode_unanchored
    ON daidzai.dai_trauma_episode (tenant_id, current_phase)
    WHERE pct_journey_id IS NULL AND status = 'OPEN';

COMMENT ON COLUMN daidzai.dai_trauma_episode.episode_class IS
    'The spine correlates any acute presentation, not only trauma. The table name is historical: '
    'renaming it would mean renaming trauma_episode_id on every phase owner and the live '
    'X-Trauma-Episode-ID header for no behavioural gain.';

COMMENT ON COLUMN daidzai.dai_trauma_episode.pct_journey_id IS
    'The PCT anchor that closes CC-5 violation V-3. Nullable only for the prehospital window this '
    'spine exists to cover; chk_dai_anchored_once_in_facility requires it from the ED phase onward. '
    'Daidzai remains a DELEGATED correlator (CC-4) — this link makes the spine resolvable to the '
    'continuum, it does not place the spine above it.';

COMMENT ON COLUMN daidzai.dai_trauma_episode.merged_into_id IS
    'Absorbed episodes are never deleted: a correlation already stamped with the absorbed id must '
    'still resolve. uq_dai_trauma_episode_origin cannot prevent the two-entry-path duplicate — '
    'someone who phones the SOS line and also walks in mints twice — so absorption is the mechanism.';
