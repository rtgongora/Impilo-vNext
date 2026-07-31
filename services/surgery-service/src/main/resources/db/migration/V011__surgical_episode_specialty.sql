-- =====================================================================================
-- Surgical demonstration 4 — shared-specialty care.
--
-- A retroperitoneal sarcoma resection is surgical oncology, vascular and urology in one
-- operation. A complex pelvic exenteration is colorectal, urology and plastics. Today
-- `surgical_episode.specialty` is a single CHECK'd column (fifteen values, V006 §6), so the
-- episode can name exactly one of the teams involved and the rest are invisible — to the
-- waiting list, to the theatre booking, to the audit that asks which teams operated.
--
-- V011 adds the join table. The existing column STAYS and keeps its meaning as the LEAD
-- specialty: the team that owns the episode and answers for it. Nothing that reads
-- `surgical_episode.specialty` today changes behaviour, and code that has not been taught about
-- shared care still gets a correct, if incomplete, answer rather than a wrong one. That is the
-- whole reason for keeping the column rather than migrating it away.
--
-- ── THE TWO PLACES THE LEAD IS NOW WRITTEN ──────────────────────────────────────────
-- One fact in two columns is a divergence waiting to happen. The service writes both together;
-- this migration backfills the join table from the column so they start in agreement, and
-- `surgery-shared-specialty-journeys.sh` asserts they still agree afterwards. The alternative —
-- dropping the column — would break every existing reader for a purity that buys nothing.
--
-- ── WHY NOT AN ARRAY COLUMN ─────────────────────────────────────────────────────────
-- Because a shared specialty is not just a name. It has a role, it was added by someone at some
-- point, and (when the lead changes hands mid-episode) that handover is a clinical event. An
-- array holds the names and loses all of that.
-- =====================================================================================

CREATE TABLE IF NOT EXISTS surgery.surgical_episode_specialty (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    episode_id          UUID NOT NULL REFERENCES surgery.surgical_episode (id) ON DELETE CASCADE,

    specialty           VARCHAR(48) NOT NULL,

    -- LEAD owns the episode and answers for it. SHARED operates alongside. Exactly one LEAD.
    role                VARCHAR(16) NOT NULL DEFAULT 'SHARED',

    -- Why this team is involved. Optional for the lead (the operative indication already says),
    -- meaningful for a shared team: "vascular control of the IVC", "ureteric reimplantation".
    contribution        TEXT,

    added_by            VARCHAR(255) NOT NULL,
    added_at            TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_episode_specialty_role CHECK (role IN ('LEAD', 'SHARED')),
    CONSTRAINT chk_episode_specialty_value CHECK (specialty IN (
        'GENERAL_SURGERY', 'ORTHOPAEDICS', 'UROLOGY', 'ENT', 'OPHTHALMOLOGY',
        'COLORECTAL', 'UPPER_GI_HPB', 'BREAST_ENDOCRINE', 'VASCULAR', 'NEUROSURGERY',
        'CARDIOTHORACIC', 'MAXILLOFACIAL', 'PLASTICS', 'PAEDIATRIC_SURGERY', 'SURGICAL_ONCOLOGY')),

    -- A team is on the case once. Adding vascular twice is a duplicate, not a second team.
    CONSTRAINT uq_episode_specialty UNIQUE (episode_id, specialty)
);

-- At most one LEAD per episode. Two teams that both believe they own the case is precisely the
-- failure shared-specialty care exists to prevent, so the database refuses it rather than
-- trusting every writer to check first.
CREATE UNIQUE INDEX IF NOT EXISTS uq_episode_specialty_single_lead
    ON surgery.surgical_episode_specialty (episode_id)
    WHERE role = 'LEAD';

CREATE INDEX IF NOT EXISTS idx_episode_specialty_episode
    ON surgery.surgical_episode_specialty (episode_id, role);

-- "Which episodes is vascular involved in", the question the single column could never answer.
CREATE INDEX IF NOT EXISTS idx_episode_specialty_by_specialty
    ON surgery.surgical_episode_specialty (tenant_id, specialty, added_at DESC);

-- Backfill: every episode that already names a specialty gets it as its LEAD, so the join table
-- is complete from the first read rather than only for episodes touched after this migration.
-- 'migration:V011' is the honest provenance — nobody added these; they were derived.
INSERT INTO surgery.surgical_episode_specialty (tenant_id, episode_id, specialty, role, added_by, added_at)
SELECT e.tenant_id, e.id, e.specialty, 'LEAD', 'migration:V011', e.created_at
FROM surgery.surgical_episode e
WHERE e.specialty IS NOT NULL
ON CONFLICT (episode_id, specialty) DO NOTHING;

COMMENT ON TABLE surgery.surgical_episode_specialty IS
    'Every specialty operating on an episode. surgical_episode.specialty remains the LEAD and is kept in step with the LEAD row here.';
COMMENT ON COLUMN surgery.surgical_episode_specialty.role IS
    'LEAD owns the episode and answers for it; SHARED operates alongside. Exactly one LEAD per episode, enforced by a partial unique index.';
