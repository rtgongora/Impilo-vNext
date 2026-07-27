-- Growth standard selection: record which chart a measurement was read on, and why.
--
-- Until now every measurement was scored against the WHO 2006 term standard. For a baby
-- born preterm that is not a conservative approximation, it is a category error: corrected
-- age is clamped at zero before term-equivalent age, so a 28-week infant two weeks old was
-- scored against the day-0 term curve and came out several standard deviations below it —
-- a severe-underweight finding, and an IMAM referral, on a baby growing exactly as
-- expected. Preterm infants are now read on the Fenton 2013 preterm chart, whose axis is
-- postmenstrual age rather than age since birth.
--
-- postmenstrual_age_weeks is stored for the same reason age_days already is: it is the
-- position on the axis the score was actually read at, and resolving it later from a date
-- of birth that may since have been corrected would not reproduce the stored score.
--
-- gestational_age_source records where the gestational age came from, because the whole
-- standard-selection decision turns on it. A measurement scored as term because nobody
-- recorded a gestational age is a different clinical statement from one scored as term
-- because the baby was known to be born at 39 weeks, and the two must not look alike.

ALTER TABLE pct.pct_growth_measurements
    ADD COLUMN postmenstrual_age_weeks INTEGER,
    ADD COLUMN gestational_age_source  VARCHAR(32),
    ADD COLUMN scoring_gaps            JSONB;

COMMENT ON COLUMN pct.pct_growth_measurements.postmenstrual_age_weeks IS
    'Completed weeks since the last menstrual period at the time of measurement; the axis the Fenton 2013 preterm chart is read on.';

COMMENT ON COLUMN pct.pct_growth_measurements.gestational_age_source IS
    'Where the gestational age used for standard selection came from: SUPPLIED | NEWBORN_BIRTH_RECORD | NOT_RECORDED.';

COMMENT ON COLUMN pct.pct_growth_measurements.scoring_gaps IS
    'Per-indicator explanations of why something that was measured could not be scored, so an absent z-score is a stated gap rather than a silent omission.';

COMMENT ON COLUMN pct.pct_growth_measurements.growth_standard IS
    'Identifier of the reference the z-scores were read against: WHO_2006_CHILD_GROWTH_STANDARDS or FENTON_2013. Stamped at write so a later revision of either standard cannot restate a historical measurement.';
