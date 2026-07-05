-- IATG Wave-1 / WS-D — Channel B (Public Workforce) EC-number matching.
--
-- Adds the Health Service Commission EC number to wgv_hsc_employment so a
-- provider onboarding via the public-workforce channel can be matched against
-- the employment system of record by EC number.
--
-- The index is deliberately NON-unique: duplicate EC numbers in the imported
-- HSC data are an honest CONFLICT signal that the match API must surface —
-- they must never be silently collapsed or rejected at the schema level.
--
-- NOTE: V005/V006 are allocated to WS-A in a sibling worktree; this file is
-- pre-assigned V007 — do not renumber.

ALTER TABLE wgv_hsc_employment ADD COLUMN ec_number VARCHAR(32);

CREATE INDEX idx_wgv_hsc_employment_ec ON wgv_hsc_employment(ec_number);
