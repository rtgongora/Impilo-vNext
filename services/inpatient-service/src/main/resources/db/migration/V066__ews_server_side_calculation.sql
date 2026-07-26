-- Server-side early warning score calculation, with paediatric age bands.
--
-- Until now the total score arrived from the client and the server only banded it into a
-- risk level. The number that triggers a deterioration escalation was therefore never
-- calculated by the platform, and no paediatric score existed at all: children were
-- measured against adult NEWS2 thresholds, where a heart rate of 150 is unremarkable in a
-- three-month-old and an emergency in a ten-year-old.
--
-- These columns record how a stored score was produced, so a score can be audited and
-- re-derived: which score type and age band applied, which version of the threshold
-- content was in force, whether the server calculated it, and whether some parameters were
-- never observed. The last one matters most — a low total built from half the observations
-- reads as reassurance, so an incomplete score must be visibly incomplete.

ALTER TABLE inpatient.early_warning_score
    ADD COLUMN age_band            VARCHAR(32),
    ADD COLUMN computed_server_side BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN calculator_version   VARCHAR(64),
    ADD COLUMN incomplete           BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN missing_parameters   TEXT,
    -- When a client also supplied a total and it disagreed with the calculation, the
    -- server value stands and the client's is retained here rather than discarded: a
    -- disagreement is a signal about the device or the observation set, not noise.
    ADD COLUMN client_reported_score INTEGER;

COMMENT ON COLUMN inpatient.early_warning_score.incomplete IS
    'True when one or more scored parameters were never observed. Such a score must not be read as reassurance.';
