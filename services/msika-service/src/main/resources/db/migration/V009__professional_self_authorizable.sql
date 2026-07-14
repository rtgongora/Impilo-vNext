-- =============================================================================
-- MSIKA V009 — DB-governed PROFESSIONAL_SELF_AUTHORIZABLE flag on the
-- risk-class friction mapping.
--
-- When TRUE for a risk class, a buyer who is a RECOGNISED LICENSED PROVIDER
-- in good standing (VARAPI recognition, verified server-side by msika-flow)
-- may self-satisfy the prescriber-standing requirement when purchasing for
-- themselves. Identity-VERIFIED requirements are unchanged; facility
-- requirements are unchanged; audit row REGULATED_PRO_PURCHASE is recorded.
--
-- Governance: FALSE for every class by default — no behaviour change until a
-- tenant operator flips a class deliberately.
-- =============================================================================

ALTER TABLE msika_risk_friction_mapping
    ADD COLUMN IF NOT EXISTS professional_self_authorizable BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN msika_risk_friction_mapping.professional_self_authorizable IS
    'When TRUE, a recognised licensed provider in good standing may self-satisfy the prescriber-standing requirement for their own purchase of items in this risk class (identity verification unchanged; audited as REGULATED_PRO_PURCHASE). Governed: default FALSE.';
