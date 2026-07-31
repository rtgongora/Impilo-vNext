-- CKP V042 — ratify SRH confidential-from-guardian age (contraception) for preview flip.
--
-- PO ruling 2026-07-31: close the ENGINEERING_SEED window on
-- SRH_CONFIDENTIAL_FROM_GUARDIAN_AGE_YEARS with value 18, approval RATIFIED, verification VERIFIED.
-- STI and TOP remain CASE_BY_CASE (null age) — documented in legal_basis only; pct uses the
-- contraception parameter for age-gated SRH stamping until treatment-specific parameters exist.
--
-- RMNP holds CKP V041-V050. V042 is free.

UPDATE clinical.national_policy_parameters
   SET effective_end = DATE '2026-07-30'
 WHERE parameter_code = 'SRH_CONFIDENTIAL_FROM_GUARDIAN_AGE_YEARS'
   AND effective_start = DATE '2026-01-01'
   AND approval_status = 'ENGINEERING_SEED'
   AND effective_end IS NULL;

INSERT INTO clinical.national_policy_parameters
    (parameter_code, domain, value_type, value_text, unit,
     effective_start, approval_status, verification_status, legal_basis, content_version)
VALUES
    ('SRH_CONFIDENTIAL_FROM_GUARDIAN_AGE_YEARS', 'REPRODUCTIVE_HEALTH', 'INTEGER', '18', 'years',
     DATE '2026-07-31', 'RATIFIED', 'VERIFIED',
     'PO ruling 2026-07-31 (preview): contraception confidential from guardian below age 18. '
     'Instruments: Constitution of Zimbabwe s.76/s.81, Children''s Act [Chapter 5:06], Marriages Act (2022), '
     'National Adolescent and Youth SRH Strategy. STI treatment: assessmentMode CASE_BY_CASE — no single '
     'national age parameter until MoHCC settles per-service rules. TOP: assessmentMode CASE_BY_CASE; '
     'TOP clinical rows use RECORD_TYPE_ALWAYS stamping independent of guardian age.',
     'po-preview-2026-07-31')
ON CONFLICT (parameter_code, effective_start) DO NOTHING;

COMMENT ON TABLE clinical.national_policy_parameters IS
    'Governed numbers that are policy, not engineering. V042 ratifies SRH_CONFIDENTIAL_FROM_GUARDIAN_AGE_YEARS=18 '
    'for contraception-age stamping (PO 2026-07-31 preview flip); STI/TOP CASE_BY_CASE documented in legal_basis.';
