-- =====================================================================================
-- Surgical demonstration 9 — a reoperation joins the SAME surgical episode.
--
-- Postoperative sepsis, a bleed, an anastomotic leak: the patient goes back to theatre, and
-- clinically that second operation is part of the SAME course of care as the first. Until now
-- `surgery.surgical_episode` could not say so. Its status CHECK is a hard five-value constraint
-- (ASSESSMENT / LISTED_FOR_SURGERY / OPERATED / CLOSED / ABANDONED) with no way back from
-- OPERATED or CLOSED, and there is no predecessor column anywhere in the schema. An episode that
-- reopened had to be represented as an unrelated new episode, which loses the very link the
-- demonstration exists to test.
--
-- ── THE ASYMMETRY WITH THE SIBLING SCHEMA, STATED PLAINLY ───────────────────────────
-- Four waves of the traceability docs recorded inpatient.procedure_episode as having "the
-- identical gap". It does not. ProcedureEpisodeService.returnToTheatre() has kept a returning
-- case on the same episode since Wave 4. What inpatient lacks is a predecessor link and a
-- complication-originated trigger (V305, this wave), and its status has no DB CHECK at all — its
-- state machine is Java-only. Here the CHECK is real, so the state must be widened in SQL.
--
-- ── TWO DIFFERENT THINGS, BOTH NEEDED ───────────────────────────────────────────────
-- `REOPENED` is the demonstration's primary case: one episode, reopened, reoperated. The
-- episode's own history carries the reoperation and nothing is lost.
--
-- `reoperation_of_episode_id` is for the case where a reoperation genuinely warrants a NEW
-- episode — a different specialty takes over, or a new indication supersedes the original. That
-- new episode points back at its predecessor rather than floating free. Same clinical event
-- class, two legitimate representations; recording only the first would have forced the second
-- into a shape that lies.
--
-- Unlike SB-5's cross-service template reference (V304), this IS a real foreign key: predecessor
-- and successor are both rows in this table, in this service's own database.
--
-- ── A REOPEN IS AUDITED OR IT DID NOT HAPPEN ────────────────────────────────────────
-- Returning a closed episode to an open state is a consequential act. The actor, the moment and
-- the reason are enforced by CHECK, not merely requested by the service, so that a direct SQL
-- write cannot produce an unaudited reopen either.
--
-- Migration band: this programme owns surgery-service outright; V009 is unused, V010 is next
-- after SB-6's V008.
-- =====================================================================================

ALTER TABLE surgery.surgical_episode
    -- The predecessor, when a reoperation is represented as a new episode rather than a reopen.
    ADD COLUMN IF NOT EXISTS reoperation_of_episode_id UUID,
    ADD COLUMN IF NOT EXISTS reopened_at               TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS reopened_by               VARCHAR(255),
    ADD COLUMN IF NOT EXISTS reopen_reason             TEXT;

COMMENT ON COLUMN surgery.surgical_episode.reoperation_of_episode_id IS
    'The episode this one re-operates, when the reoperation warranted a new episode. A reoperation that joins the SAME episode uses the REOPENED status instead and needs no row here.';
COMMENT ON COLUMN surgery.surgical_episode.reopen_reason IS
    'Why the episode was reopened. Required whenever status is REOPENED — an unexplained reopen is not auditable.';

-- Widen the status vocabulary. Drop and recreate: Postgres has no ALTER CONSTRAINT for a CHECK.
ALTER TABLE surgery.surgical_episode
    DROP CONSTRAINT IF EXISTS chk_surgical_episode_status;
ALTER TABLE surgery.surgical_episode
    ADD CONSTRAINT chk_surgical_episode_status CHECK (
        status IN ('ASSESSMENT', 'LISTED_FOR_SURGERY', 'OPERATED', 'CLOSED', 'ABANDONED', 'REOPENED'));

ALTER TABLE surgery.surgical_episode
    ADD CONSTRAINT fk_surgical_episode_reoperation_of
        FOREIGN KEY (reoperation_of_episode_id) REFERENCES surgery.surgical_episode (id);

-- An episode cannot be a reoperation of itself. Without this the FK is satisfied by a self-loop,
-- which reads as a valid predecessor chain and terminates nowhere.
ALTER TABLE surgery.surgical_episode
    ADD CONSTRAINT chk_surgical_episode_reoperation_not_self CHECK (
        reoperation_of_episode_id IS NULL OR reoperation_of_episode_id <> id);

-- The audit trio is all-or-nothing, and REOPENED requires it. An episode that has moved on from
-- REOPENED keeps its reopen record: this constrains the act, not the aftermath.
ALTER TABLE surgery.surgical_episode
    ADD CONSTRAINT chk_surgical_episode_reopen_audit_triple CHECK (
        (reopened_at IS NULL AND reopened_by IS NULL AND reopen_reason IS NULL)
        OR (reopened_at IS NOT NULL AND reopened_by IS NOT NULL
            AND reopen_reason IS NOT NULL AND length(btrim(reopen_reason)) > 0));

ALTER TABLE surgery.surgical_episode
    ADD CONSTRAINT chk_surgical_episode_reopened_is_audited CHECK (
        status <> 'REOPENED' OR reopened_at IS NOT NULL);

-- The predecessor lookup: "what came before this episode", and its inverse, "what reoperations
-- followed". Partial, because the overwhelming majority of episodes are not reoperations.
CREATE INDEX IF NOT EXISTS idx_surgical_episode_reoperation_of
    ON surgery.surgical_episode (reoperation_of_episode_id)
    WHERE reoperation_of_episode_id IS NOT NULL;
