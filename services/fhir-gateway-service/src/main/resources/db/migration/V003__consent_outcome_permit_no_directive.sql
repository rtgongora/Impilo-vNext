-- ============================================================
-- FHIR Gateway Service
-- V003: PERMIT_NO_DIRECTIVE joins the consent_outcome vocabulary
--
-- tshepo-consent returns permitted=false in four different situations, and only one of them is a
-- person refusing:
--
--   consentId set,  reason CONSENT_DENIED      -> an explicit deny directive exists  (REFUSAL)
--   consentId null, reason NO_ACTIVE_CONSENT   -> the subject has no directives at all
--   consentId null, reason NO_MATCHING_CONSENT -> directives exist, none for this purpose
--   consentId null, reason NO_VALID_CONSENT    -> directives exist, all expired or out of scope
--
-- ConsentEnforcementService read only `permitted` and collapsed all four into DENY. Measured
-- 2026-08-08 against the deployed estate, that had refused 22 of 22 non-break-glass forwards in
-- this table's entire history -- 20 of them telemonitoring observations -- while the only three
-- SUCCESS rows carried BREAK_GLASS_BYPASS. The shared health record was empty because consent had
-- been asked a question it could not answer, and the answer defaulted to no.
--
-- A WRITE now proceeds when no directive speaks to the point; a READ still does not. PERMIT and
-- PERMIT_NO_DIRECTIVE are deliberately separate values rather than one: a regulator asking on
-- whose authority a record was written must be able to tell a directive that said yes from the
-- absence of one, and this column is the only place that answer survives.
--
-- No column change is needed -- consent_outcome is already VARCHAR(32) and PERMIT_NO_DIRECTIVE is
-- 19 characters. This migration exists so the column does not document a vocabulary it no longer
-- has, which is how the next person querying this table would be misled.
-- ============================================================

COMMENT ON COLUMN fhir_gateway.fhir_audit_log.consent_outcome IS
    'Consent evaluation outcome: PERMIT (an active directive permits), '
    'PERMIT_NO_DIRECTIVE (a WRITE proceeded because no directive spoke to the point -- absence, '
    'not authority), DENY (an explicit deny directive, a revocation, an unreachable consent '
    'service, or a read with no permitting directive), NOT_APPLICABLE, or BREAK_GLASS_BYPASS';
