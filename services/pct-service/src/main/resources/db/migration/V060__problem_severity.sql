-- Problem severity (PCT).
--
-- The experience layer has always collected a severity for a condition — it is a field on the
-- BFF's CreateConditionRequest and on the UI's ConditionResource — but pct_problems had no column
-- to put it in. While the write path was broken (the BFF called /v1/conditions, which pct-service
-- has never served) nothing was lost, because nothing was ever stored. Repairing that path without
-- this column would have turned a visible outage into a silent drop of a clinical attribute, which
-- is the worse of the two failures.
--
-- Deliberately nullable and uncoded at this stage: severity is a clinician's judgement that is
-- often genuinely not recorded, and NULL must keep meaning "not stated" rather than defaulting to
-- anything that reads as an assessment nobody made. The closed vocabulary lands with the wider
-- problem model.

ALTER TABLE pct_problems
    ADD COLUMN severity VARCHAR(32);

COMMENT ON COLUMN pct_problems.severity IS
    'Clinician-assessed severity of the problem. NULL means not stated — never assume mild.';
