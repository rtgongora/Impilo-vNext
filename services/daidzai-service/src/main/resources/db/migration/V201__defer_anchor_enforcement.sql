-- Withdraw a premature CHECK that broke the live trauma spine.
--
-- V200 added chk_dai_anchored_once_in_facility, which requires pct_journey_id to be non-null once an
-- episode's current_phase reaches a facility phase (ED / TRIAGE / RESUS / DIAGNOSTICS / BLOOD /
-- DEFINITIVE / DISPOSITION). That was wrong, and it was verified wrong against a real database only
-- after V200 had shipped:
--
--   INSERT ... current_phase='INCIDENT'  -> OK
--   UPDATE ... SET current_phase='ED'    -> violates check constraint "chk_dai_anchored_once_in_facility"
--
-- The existing, in-production TraumaEpisodeService.recordPhaseInternal advances current_phase to a
-- facility phase whenever a phase owner (pct/inpatient/madi) registers one — line 148,
-- `ep.setCurrentPhase(phase); episodeRepo.save(ep)`. Nothing populates pct_journey_id, because the
-- link-on-facility-arrival flow that V-3's closing change calls for was NOT built in the same change
-- as this migration. So the constraint enforced an invariant no code could satisfy, and every
-- facility-phase advance on the live trauma spine would have failed at the first full boot.
--
-- The mistake was applying greenfield confidence to a brownfield change: the constraint is correct
-- for a table only new code writes, and premature for a table with existing live writers. A CHECK
-- must not be added ahead of the code that keeps it true.
--
-- So the enforcement is deferred. The BACK-LINK COLUMNS stay (pct_journey_id,
-- pct_emergency_episode_id, continuum_linked_at) — those are the structural half of closing V-3 and
-- they break nothing. chk_dai_continuum_link_complete stays too: it only says a link is a fact with
-- a time or neither, which every existing row (both null) satisfies. What comes out is the single
-- constraint that required the not-yet-built flow.
--
-- The enforcing CHECK returns in the migration that lands the link-on-arrival flow, once there is
-- code that can satisfy it — the honest ordering V200 skipped.

ALTER TABLE daidzai.dai_trauma_episode
    DROP CONSTRAINT IF EXISTS chk_dai_anchored_once_in_facility;

COMMENT ON COLUMN daidzai.dai_trauma_episode.pct_journey_id IS
    'The PCT anchor for closing CC-5 V-3. Nullable, and NOT YET ENFORCED: the link-on-facility-arrival '
    'flow that populates it is still to be built, and V200''s enforcing CHECK was withdrawn in V201 '
    'because it broke the live trauma phase-advance before that flow existed. Enforcement returns with '
    'the flow.';
