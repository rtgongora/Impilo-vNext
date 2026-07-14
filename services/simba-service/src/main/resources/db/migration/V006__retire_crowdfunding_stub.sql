-- V006: Retire the simba crowdfunding stub (2026-07).
--
-- The crowdfunding CASE (verification lifecycle, attestation) moved to
-- daidzai.dai_assistance_request; the MONEY (escrow, contributions, refunds) moved to
-- mushe bill contributions. The simba API handlers now answer HTTP 410 GONE.
-- Tables are kept READ-ONLY-by-convention for one release so any residual rows can be
-- audited/drained, then dropped. Do NOT drop them here.

COMMENT ON TABLE crowdfunding_campaigns IS
    'DEPRECATED 2026-07: crowdfunding case moved to daidzai.dai_assistance_request; money to mushe bill contributions. Read-only; drop after one release.';

COMMENT ON TABLE crowdfunding_donations IS
    'DEPRECATED 2026-07: crowdfunding case moved to daidzai.dai_assistance_request; money to mushe bill contributions. Read-only; drop after one release.';
