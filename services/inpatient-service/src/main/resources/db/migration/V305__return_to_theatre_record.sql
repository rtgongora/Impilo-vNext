-- =====================================================================================
-- Pipeline demonstration 10 — a return to theatre is a recorded clinical event.
--
-- The sibling half of surgery-service V010, and deliberately a DIFFERENT shape, because the two
-- schemas start from different places. `surgery.surgical_episode` could not represent a reopen at
-- all. `inpatient.procedure_episode` already could: ProcedureEpisodeService.returnToTheatre() has
-- kept a returning case on the SAME episode since Wave 4. Four waves of the traceability docs
-- recorded that as the identical gap; it never was, and this wave corrects the record.
--
-- What is actually missing here is everything ABOUT the return:
--
--   1. WHY. `procedure_postop_record.return_to_theatre` (V031) is one boolean. It cannot say
--      haemorrhage or sepsis or leak, cannot say who decided, cannot say when.
--   2. HOW MANY. A boolean cannot count. A patient who returns twice looks exactly like a patient
--      who returned once — the second washout simply vanishes from the record.
--   3. PLANNED OR NOT. The DAK safety indicator is UNPLANNED return to theatre. A staged
--      second-look laparotomy, booked at the first operation, is a planned return and is not the
--      same event at all. Today they are one flag, so the indicator counts good practice as harm.
--      `planned` defaults to FALSE: an unclassified return counts as unplanned, which over-reports
--      rather than hides. For a safety indicator, that is the correct direction to be wrong in.
--   4. PREDECESSOR. When the return is severe enough that a new episode is opened — a different
--      team, a different operation — nothing links the new episode to the one it followed.
--
-- The boolean stays and is still maintained. Existing readers (the theatre analytics rollup, the
-- alt-journeys rig) keep working unchanged; this table is the detail behind the flag, not its
-- replacement.
--
-- Migration band: inpatient V300-V304 are this programme's; V305 is next.
-- =====================================================================================

CREATE TABLE IF NOT EXISTS inpatient.procedure_return_to_theatre (
    return_id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    episode_id            UUID NOT NULL REFERENCES inpatient.procedure_episode(episode_id) ON DELETE CASCADE,

    -- 1 for the first return, 2 for the second, and so on. What the boolean could never hold.
    seq                   INT NOT NULL,

    returned_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    returned_by           VARCHAR(128) NOT NULL,
    reason                TEXT NOT NULL,

    -- Closed vocabulary. Free text cannot be counted, and "unplanned return to theatre, by cause"
    -- is precisely the question a morbidity meeting asks.
    complication_category VARCHAR(32) NOT NULL,

    planned               BOOLEAN NOT NULL DEFAULT FALSE,

    -- The operative note whose recorded complications drove the return, when the two are linked.
    -- Nullable: a deterioration noticed on the ward has no originating note.
    originating_note_id   UUID REFERENCES inpatient.procedure_note(note_id) ON DELETE SET NULL,

    CONSTRAINT uq_return_to_theatre_seq UNIQUE (episode_id, seq),
    CONSTRAINT chk_return_to_theatre_seq_positive CHECK (seq > 0),
    CONSTRAINT chk_return_to_theatre_reason_present CHECK (length(btrim(reason)) > 0),
    CONSTRAINT chk_return_to_theatre_category CHECK (complication_category IN (
        'HAEMORRHAGE', 'SEPSIS', 'ANASTOMOTIC_LEAK', 'WOUND_DEHISCENCE', 'ISCHAEMIA',
        'OBSTRUCTION', 'RETAINED_ITEM', 'DEVICE_OR_IMPLANT_FAILURE', 'ORGAN_INJURY',
        'PLANNED_SECOND_LOOK', 'OTHER'))
);

CREATE INDEX IF NOT EXISTS idx_return_to_theatre_episode
    ON inpatient.procedure_return_to_theatre (episode_id, seq);

-- The safety-indicator query: unplanned returns, by cause, most recent first.
CREATE INDEX IF NOT EXISTS idx_return_to_theatre_unplanned
    ON inpatient.procedure_return_to_theatre (complication_category, returned_at DESC)
    WHERE planned = FALSE;

-- -------------------------------------------------------------------------------------
-- Predecessor linkage, for the return that warranted a NEW episode rather than a reopen.
-- Mirrors surgery.surgical_episode.reoperation_of_episode_id (V010) — same clinical idea,
-- each service in its own database, so each keeps its own real self-referencing key.
-- -------------------------------------------------------------------------------------
ALTER TABLE inpatient.procedure_episode
    ADD COLUMN IF NOT EXISTS reoperation_of_episode_id UUID;

ALTER TABLE inpatient.procedure_episode
    ADD CONSTRAINT fk_procedure_episode_reoperation_of
        FOREIGN KEY (reoperation_of_episode_id) REFERENCES inpatient.procedure_episode(episode_id);

-- A self-loop satisfies the foreign key and reads as a predecessor chain that never terminates.
ALTER TABLE inpatient.procedure_episode
    ADD CONSTRAINT chk_procedure_episode_reoperation_not_self CHECK (
        reoperation_of_episode_id IS NULL OR reoperation_of_episode_id <> episode_id);

CREATE INDEX IF NOT EXISTS idx_procedure_episode_reoperation_of
    ON inpatient.procedure_episode (reoperation_of_episode_id)
    WHERE reoperation_of_episode_id IS NOT NULL;

COMMENT ON TABLE inpatient.procedure_return_to_theatre IS
    'One row per return to theatre on an episode. The detail behind procedure_postop_record.return_to_theatre, which remains as the flag existing readers use.';
