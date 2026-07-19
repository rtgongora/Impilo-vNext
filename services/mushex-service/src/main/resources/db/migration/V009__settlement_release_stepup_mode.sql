-- Records how a high-value settlement release step-up was satisfied.
-- "biometric" when a MATCH from the shared biometric verification seam stepped the
-- release up; NULL when the release relied on the existing non-biometric step-up
-- factor or fell below the biometric step-up threshold.
ALTER TABLE mushex_settlements
    ADD COLUMN IF NOT EXISTS release_stepup_mode VARCHAR(32);
