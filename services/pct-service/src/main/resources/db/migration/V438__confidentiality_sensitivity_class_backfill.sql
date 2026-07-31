-- pct V438 — backfill sensitivity_class when stamp-class flip enables protection claims.
--
-- PO preview flip 2026-07-31 (W14-D step 6): rows that already carry a confidentiality_category
-- and a basis indicating protection intent get sensitivity_class = SPECIALLY_PROTECTED.
-- Adult SRH rows (category present, over-age) are not distinguished here — preview estate
-- had zero stamped rows at V437; production may need an age-aware re-stamp job later.
--
-- RMNP band V430-V459. V438 is free (gap before V500).

-- TOP and mature-minor capacity rows — unambiguous protection intent.
UPDATE pct.pct_top_authorisations
   SET sensitivity_class = 'SPECIALLY_PROTECTED'
 WHERE confidentiality_category IS NOT NULL
   AND confidentiality_basis IN ('RECORD_TYPE_ALWAYS', 'MATURE_MINOR_CAPACITY');

UPDATE pct.pct_top_procedures
   SET sensitivity_class = 'SPECIALLY_PROTECTED'
 WHERE confidentiality_category IS NOT NULL
   AND confidentiality_basis IN ('RECORD_TYPE_ALWAYS', 'MATURE_MINOR_CAPACITY');

UPDATE pct.pct_pregnancy_loss_records
   SET sensitivity_class = 'SPECIALLY_PROTECTED'
 WHERE confidentiality_category IS NOT NULL
   AND confidentiality_basis IN ('RECORD_TYPE_ALWAYS', 'MATURE_MINOR_CAPACITY');

UPDATE pct.pct_postnatal_contacts
   SET sensitivity_class = 'SPECIALLY_PROTECTED'
 WHERE confidentiality_category IS NOT NULL
   AND confidentiality_basis IN ('RECORD_TYPE_ALWAYS', 'MATURE_MINOR_CAPACITY');

UPDATE pct.pct_contraceptive_episodes
   SET sensitivity_class = 'SPECIALLY_PROTECTED'
 WHERE confidentiality_category IS NOT NULL
   AND confidentiality_basis IN ('RECORD_TYPE_ALWAYS', 'MATURE_MINOR_CAPACITY');

UPDATE pct.pct_pregnancy_episodes
   SET sensitivity_class = 'SPECIALLY_PROTECTED'
 WHERE confidentiality_category IS NOT NULL
   AND confidentiality_basis IN ('RECORD_TYPE_ALWAYS', 'MATURE_MINOR_CAPACITY');

-- Under-age adolescent SRH rows stamped with SUBJECT_UNDER_POLICY_AGE while class stamping was gated.
UPDATE pct.pct_pregnancy_loss_records
   SET sensitivity_class = 'SPECIALLY_PROTECTED'
 WHERE confidentiality_category IS NOT NULL
   AND confidentiality_basis = 'SUBJECT_UNDER_POLICY_AGE'
   AND sensitivity_class = 'FULL_CLINICAL';

UPDATE pct.pct_postnatal_contacts
   SET sensitivity_class = 'SPECIALLY_PROTECTED'
 WHERE confidentiality_category IS NOT NULL
   AND confidentiality_basis = 'SUBJECT_UNDER_POLICY_AGE'
   AND sensitivity_class = 'FULL_CLINICAL';

UPDATE pct.pct_contraceptive_episodes
   SET sensitivity_class = 'SPECIALLY_PROTECTED'
 WHERE confidentiality_category IS NOT NULL
   AND confidentiality_basis = 'SUBJECT_UNDER_POLICY_AGE'
   AND sensitivity_class = 'FULL_CLINICAL';

UPDATE pct.pct_pregnancy_episodes
   SET sensitivity_class = 'SPECIALLY_PROTECTED'
 WHERE confidentiality_category IS NOT NULL
   AND confidentiality_basis = 'SUBJECT_UNDER_POLICY_AGE'
   AND sensitivity_class = 'FULL_CLINICAL';
