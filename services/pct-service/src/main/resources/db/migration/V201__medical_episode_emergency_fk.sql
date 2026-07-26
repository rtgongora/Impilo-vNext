-- Promote pct_medical_episodes.emergency_episode_id to a real foreign key.
--
-- WHY THIS CONSTRAINT LIVES IN THE EMERGENCY BAND AND NOT THE ADULT MEDICINE ONE
--
-- The referencing table (`pct_medical_episodes`, V101) belongs to the Adult Medicine lane and this
-- constraint was originally written there, at V108. It was withdrawn (`ac620b355`) because it could
-- not work: Flyway applies migrations in version order, V108 sorts before V200, and V200 is the
-- migration that creates `emergency_episode`. On a clean database the foreign key ran before its
-- referenced table existed:
--
--     ERROR:  relation "emergency_episode" does not exist
--
-- It dry-ran green against the preview schema because `emergency_episode` was already deployed
-- there, which is the shape of the whole class: the ordering trap is invisible in every environment
-- except a fresh one, and a fresh one is exactly what a full boot is.
--
-- THE RULE, now recorded in docs/registry/iatg-emergency-leases.md §3: with one migration band per
-- delivery lane, a cross-lane dependency must live in the band of the lane that owns the DEPENDED-ON
-- object, never the depending one. That covers more than foreign keys — an INSERT referencing a
-- higher-band table, or an ALTER of one, fails identically.
--
-- So the constraint sits here, in the emergency band, and the Adult Medicine lease carries a pointer
-- at §4a so the next reader of their block finds it rather than concluding it was forgotten. The
-- reasoning below is theirs, carried across verbatim, because it is the part worth keeping.
--
-- ── Adult Medicine lane's original rationale ─────────────────────────────────────────────────────
--
-- The column landed at V101 as a SOFT reference, deliberately: `pct.emergency_episode` did not
-- exist yet, and a hard constraint would have coupled two lanes' deploy order — neither of us could
-- have shipped independently. That justification expired the moment the emergency lane pushed V200
-- (`dd540d56d`).
--
-- Why it matters that this is not left soft: a nullable cross-table reference with no constraint is
-- precisely the orphan CC-5 exists to reject. That pack's whole premise is that a clinical record
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
-- EXCLUSIVE lock while it scans, and aborts the entire migration on the first dangling value. NOT
-- VALID takes a weaker lock and defers the scan; VALIDATE CONSTRAINT then checks existing rows
-- without blocking writes. On a table this size the difference is academic today and will not be
-- later.

ALTER TABLE pct_medical_episodes
    ADD CONSTRAINT fk_pct_medical_episodes_emergency_episode
    FOREIGN KEY (emergency_episode_id)
    REFERENCES emergency_episode(episode_id)
    ON DELETE RESTRICT
    NOT VALID;

ALTER TABLE pct_medical_episodes
    VALIDATE CONSTRAINT fk_pct_medical_episodes_emergency_episode;

COMMENT ON CONSTRAINT fk_pct_medical_episodes_emergency_episode ON pct_medical_episodes IS
    'A medical episode may descend from an emergency presentation. RESTRICT is a backstop: '
    'emergency_episode rows are merged, never deleted. Written in the emergency band because Flyway '
    'orders by version and the referenced table is created at V200 — see the header.';
