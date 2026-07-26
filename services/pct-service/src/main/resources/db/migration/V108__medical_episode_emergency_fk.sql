-- Promote pct_medical_episodes.emergency_episode_id to a real foreign key.
--
-- The column landed at V101 as a SOFT reference, deliberately: `pct.emergency_episode` did not
-- exist yet, and a hard constraint would have coupled two lanes' deploy order — neither of us could
-- have shipped independently. That justification expired the moment the emergency lane pushed V200
-- (`dd540d56d`), which is the trigger this migration was waiting on.
--
-- Why it matters that this is not left soft: a nullable cross-table reference with no constraint is
-- precisely the orphan CC-5 exists to reject. This pack's whole premise is that a clinical record
-- with no resolvable anchor is refused, and carrying an unconstrained reference into it indefinitely
-- would be exempting the medical episode from the rule it enforces on everything else.
--
-- ON DELETE RESTRICT, never CASCADE. Confirmed against the committed V200 rather than assumed:
-- `emergency_episode` hard-deletes nothing — a merge sets `state = 'MERGED'` and `merged_into_id`,
-- with its own CHECK requiring a merged episode to name its target. So the referenced id always
-- stays valid and RESTRICT is a BACKSTOP, not a workflow. CASCADE would be actively wrong: deleting
-- an emergency presentation must never silently delete the longitudinal course of medical care that
-- followed from it.
--
-- Added NOT VALID, then validated as a separate statement. A plain ADD CONSTRAINT takes an ACCESS
-- EXCLUSIVE lock while it scans, and aborts the entire migration on the first dangling value — the
-- same failure class as this pack's own V100 CHECK, which assumed a column's contents rather than
-- checking them. NOT VALID takes a weaker lock and defers the scan; VALIDATE CONSTRAINT then checks
-- existing rows without blocking writes. On a table this size the difference is academic today and
-- will not be later.

ALTER TABLE pct_medical_episodes
    ADD CONSTRAINT fk_pct_medical_episodes_emergency_episode
    FOREIGN KEY (emergency_episode_id)
    REFERENCES emergency_episode(episode_id)
    ON DELETE RESTRICT
    NOT VALID;

ALTER TABLE pct_medical_episodes
    VALIDATE CONSTRAINT fk_pct_medical_episodes_emergency_episode;

COMMENT ON COLUMN pct_medical_episodes.emergency_episode_id IS
    'The emergency presentation this course of care began with, where it began with one. A real '
    'foreign key since V108. RESTRICT rather than CASCADE because deleting a presentation must '
    'never delete the longitudinal medical care that followed it — and because emergency_episode '
    'hard-deletes nothing, a merge sets state=MERGED and merged_into_id instead.';
