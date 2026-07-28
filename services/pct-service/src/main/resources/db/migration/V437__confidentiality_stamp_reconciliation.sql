-- SRH confidentiality: give every reproductive record the TWO values the enforcement seam needs, and
-- correct a vocabulary defect this lane shipped.
--
-- WHY. tshepo-authz's confidentiality seam went live on 2026-07-26: the PDP decides WHICH CONFIDENTIAL
-- CATEGORIES a requester may receive and says so on the VisibilityProfile obligation, and
-- SpeciallyProtectedVisibilityGuard (shared-core) consumes that answer. Its javadoc states the
-- division of labour precisely: "the PDP sees a URL, never a record, so it cannot know that row 7 of
-- a collection is a safeguarding note; the service knows that but must not invent its own access
-- rule." This migration is the system-of-record half — the part that says which of OUR records carry
-- the class, and in which category.
--
-- THE DEFECT BEING CORRECTED. V434/V435/V436 each shipped `confidentiality_category VARCHAR(48) NOT
-- NULL DEFAULT 'FULL_CLINICAL'`. But FULL_CLINICAL is a DataSensitivityClass NAME, not a member of the
-- governed CATEGORY vocabulary (six codes, seeded in zibo V008). The column name and its contents
-- disagree, so the column answers neither question the guard asks. Separately, V059 named the same
-- concept `confidentiality` with values ROUTINE|SPECIALLY_PROTECTED — a third vocabulary, in which
-- ROUTINE parses through DataSensitivityClass.fromString to NON_SENSITIVE_OPERATIONAL, i.e. LESS
-- protected than ordinary clinical data. Three spellings of one idea, none of them usable.
--
-- After this migration every stamped table carries four columns:
--   sensitivity_class              the DataSensitivityClass name           -> guard's 1st argument
--   confidentiality_category       the zibo V008 category                  -> guard's 2nd argument
--   confidentiality_basis          WHY it was stamped                      -> auditable, re-explainable
--   confidentiality_policy_version WHICH policy version decided it         -> a past stamp stays readable
--
-- THE LOAD-BEARING DECISION: STAMP THE CATEGORY NOW, GATE THE CLASS.
-- A category is a CONTENT classification ("this record is SRH content") and asserts nothing about
-- protection, so it ships immediately and truthfully. A class is a PROTECTION CLAIM. Stamping
-- SPECIALLY_PROTECTED today would make the record INVISIBLE TO THE MIDWIFE WHO JUST WROTE IT: the
-- PDP runs in SHADOW and grants no categories, and the guard fails closed by design. It would also
-- break the governing rule in docs/clinical-governance/adolescent-confidentiality-seed-policy.md that
-- no record may carry a protection label that does not protect it. So sensitivity_class stays
-- FULL_CLINICAL behind pct.confidentiality.stamp-class (default false) while the category is
-- populated for real — the same shape as zibo's ClassificationResult.protectedAs(), which already
-- reports matched categories while withholding a stampable class.
--
-- CONSEQUENCE FOR THE CONSTRAINTS BELOW: a category on a FULL_CLINICAL row is the EXPECTED shadow
-- state, not an error. Only the reverse is forbidden. There is deliberately NO constraint of the form
-- "a category implies the protected class" — that would forbid the very state this design runs in.
--
-- SAFETY OF THE RENAMES. All six tables verified 0 rows on the live estate before writing this, so no
-- row is reinterpreted. A RENAME COLUMN is invisible to a running pod, so this migration and the
-- entity change ship in ONE artefact — a stale pre-V437 image running when this applies fails every
-- write on a missing column.
--
-- RMNP band V430-V459 (V430-V436 consumed -> this is V437).

-- ---------------------------------------------------------------------------------------------
-- 1. The four tables whose "category" column actually holds a class. Rename to what it is; the
--    NOT NULL, the DEFAULT and any comments travel with the rename.
-- ---------------------------------------------------------------------------------------------

ALTER TABLE pct.pct_pregnancy_loss_records RENAME COLUMN confidentiality_category TO sensitivity_class;
ALTER TABLE pct.pct_top_authorisations     RENAME COLUMN confidentiality_category TO sensitivity_class;
ALTER TABLE pct.pct_top_procedures         RENAME COLUMN confidentiality_category TO sensitivity_class;
ALTER TABLE pct.pct_postnatal_contacts     RENAME COLUMN confidentiality_category TO sensitivity_class;

ALTER TABLE pct.pct_pregnancy_loss_records ALTER COLUMN sensitivity_class TYPE VARCHAR(32);
ALTER TABLE pct.pct_top_authorisations     ALTER COLUMN sensitivity_class TYPE VARCHAR(32);
ALTER TABLE pct.pct_top_procedures         ALTER COLUMN sensitivity_class TYPE VARCHAR(32);
ALTER TABLE pct.pct_postnatal_contacts     ALTER COLUMN sensitivity_class TYPE VARCHAR(32);

-- ---------------------------------------------------------------------------------------------
-- 2. pct_contraceptive_episodes (V430) had no confidentiality column of any name at all.
-- ---------------------------------------------------------------------------------------------

ALTER TABLE pct.pct_contraceptive_episodes
    ADD COLUMN IF NOT EXISTS sensitivity_class VARCHAR(32) NOT NULL DEFAULT 'FULL_CLINICAL';

-- ---------------------------------------------------------------------------------------------
-- 3. pct_pregnancy_episodes (V059) carried the third vocabulary. Converge onto sensitivity_class and
--    backfill. The old column is left in place, nullable and unwritten, rather than dropped: it has
--    exactly one writer (PregnancyEpisodeService) and zero readers estate-wide, and a DROP COLUMN on
--    a table that other migration bands read is not this lane's call to make.
-- ---------------------------------------------------------------------------------------------

ALTER TABLE pct.pct_pregnancy_episodes
    ADD COLUMN IF NOT EXISTS sensitivity_class VARCHAR(32) NOT NULL DEFAULT 'FULL_CLINICAL';

UPDATE pct.pct_pregnancy_episodes
   SET sensitivity_class = CASE
           WHEN confidentiality = 'SPECIALLY_PROTECTED' THEN 'SPECIALLY_PROTECTED'
           ELSE 'FULL_CLINICAL'
       END;

ALTER TABLE pct.pct_pregnancy_episodes ALTER COLUMN confidentiality DROP NOT NULL;

COMMENT ON COLUMN pct.pct_pregnancy_episodes.confidentiality IS
    'SUPERSEDED by sensitivity_class (V437). Its ROUTINE value is not a DataSensitivityClass name and '
    'parsed to NON_SENSITIVE_OPERATIONAL — i.e. LESS protected than ordinary clinical data. Left in '
    'place, nullable and unwritten, rather than dropped: dropping a column on a table other bands read '
    'is not this lane''s call.';

-- ---------------------------------------------------------------------------------------------
-- 4. The real category, the basis, and the policy version — on all six tables.
-- ---------------------------------------------------------------------------------------------

ALTER TABLE pct.pct_pregnancy_loss_records
    ADD COLUMN IF NOT EXISTS confidentiality_category       VARCHAR(48),
    ADD COLUMN IF NOT EXISTS confidentiality_basis          VARCHAR(64),
    ADD COLUMN IF NOT EXISTS confidentiality_policy_version VARCHAR(64);

ALTER TABLE pct.pct_top_authorisations
    ADD COLUMN IF NOT EXISTS confidentiality_category       VARCHAR(48),
    ADD COLUMN IF NOT EXISTS confidentiality_basis          VARCHAR(64),
    ADD COLUMN IF NOT EXISTS confidentiality_policy_version VARCHAR(64);

ALTER TABLE pct.pct_top_procedures
    ADD COLUMN IF NOT EXISTS confidentiality_category       VARCHAR(48),
    ADD COLUMN IF NOT EXISTS confidentiality_basis          VARCHAR(64),
    ADD COLUMN IF NOT EXISTS confidentiality_policy_version VARCHAR(64);

ALTER TABLE pct.pct_postnatal_contacts
    ADD COLUMN IF NOT EXISTS confidentiality_category       VARCHAR(48),
    ADD COLUMN IF NOT EXISTS confidentiality_basis          VARCHAR(64),
    ADD COLUMN IF NOT EXISTS confidentiality_policy_version VARCHAR(64);

ALTER TABLE pct.pct_contraceptive_episodes
    ADD COLUMN IF NOT EXISTS confidentiality_category       VARCHAR(48),
    ADD COLUMN IF NOT EXISTS confidentiality_basis          VARCHAR(64),
    ADD COLUMN IF NOT EXISTS confidentiality_policy_version VARCHAR(64);

ALTER TABLE pct.pct_pregnancy_episodes
    ADD COLUMN IF NOT EXISTS confidentiality_category       VARCHAR(48),
    ADD COLUMN IF NOT EXISTS confidentiality_basis          VARCHAR(64),
    ADD COLUMN IF NOT EXISTS confidentiality_policy_version VARCHAR(64);

-- ---------------------------------------------------------------------------------------------
-- 5. Constraints. Four per table:
--
--    (a) sensitivity_class vocabulary. Only two of the DataSensitivityClass members are reachable on
--        a reproductive clinical record; junk must not be storable, because
--        DataSensitivityClass.fromString falls back to NON_SENSITIVE_OPERATIONAL on junk — a typo
--        would silently make a record LESS protected, which is the worst available failure direction.
--
--    (b) confidentiality_category vocabulary — the six governed codes (zibo V008). NULL IS ACCEPTED
--        and that is deliberate: an unclassified record is ordinary clinical data, and the guard's
--        isProtected(null) is false by design. This also permanently forecloses the defect being
--        corrected here — 'FULL_CLINICAL' can never be written into this column again.
--
--    (c) confidentiality_basis vocabulary. A closed diagnostic vocabulary that is not enforced drifts
--        into free text, and the basis is what makes a past stamp re-explainable.
--
--    (d) THE ONE THAT MATTERS: a protected record must carry a category. Grants are category-scoped,
--        so a SPECIALLY_PROTECTED record with no category is withheld from EVERYONE holding a
--        category-scoped grant — only a "*" whole-set grant reaches it (SpeciallyProtectedVisibility
--        Guard L94-99). That is not a protected record; it is a record nobody can treat from. The
--        schema refuses to mint one.
-- ---------------------------------------------------------------------------------------------

ALTER TABLE pct.pct_pregnancy_loss_records
    ADD CONSTRAINT chk_pct_loss_sensitivity_class CHECK (
        sensitivity_class IN ('FULL_CLINICAL', 'SPECIALLY_PROTECTED')),
    ADD CONSTRAINT chk_pct_loss_confid_vocabulary CHECK (
        confidentiality_category IS NULL OR confidentiality_category IN (
            'SEXUAL_REPRODUCTIVE_HEALTH', 'HIV', 'MENTAL_HEALTH',
            'SAFEGUARDING', 'SUBSTANCE_USE', 'GENDER_BASED_VIOLENCE')),
    ADD CONSTRAINT chk_pct_loss_confid_basis CHECK (
        confidentiality_basis IS NULL OR confidentiality_basis IN (
            'RECORD_TYPE_ALWAYS', 'SUBJECT_UNDER_POLICY_AGE', 'MATURE_MINOR_CAPACITY',
            'AGE_UNRESOLVED', 'POLICY_UNAVAILABLE')),
    ADD CONSTRAINT chk_pct_loss_protected_has_category CHECK (
        sensitivity_class <> 'SPECIALLY_PROTECTED' OR confidentiality_category IS NOT NULL);

ALTER TABLE pct.pct_top_authorisations
    ADD CONSTRAINT chk_pct_topauth_sensitivity_class CHECK (
        sensitivity_class IN ('FULL_CLINICAL', 'SPECIALLY_PROTECTED')),
    ADD CONSTRAINT chk_pct_topauth_confid_vocabulary CHECK (
        confidentiality_category IS NULL OR confidentiality_category IN (
            'SEXUAL_REPRODUCTIVE_HEALTH', 'HIV', 'MENTAL_HEALTH',
            'SAFEGUARDING', 'SUBSTANCE_USE', 'GENDER_BASED_VIOLENCE')),
    ADD CONSTRAINT chk_pct_topauth_confid_basis CHECK (
        confidentiality_basis IS NULL OR confidentiality_basis IN (
            'RECORD_TYPE_ALWAYS', 'SUBJECT_UNDER_POLICY_AGE', 'MATURE_MINOR_CAPACITY',
            'AGE_UNRESOLVED', 'POLICY_UNAVAILABLE')),
    ADD CONSTRAINT chk_pct_topauth_protected_has_category CHECK (
        sensitivity_class <> 'SPECIALLY_PROTECTED' OR confidentiality_category IS NOT NULL);

ALTER TABLE pct.pct_top_procedures
    ADD CONSTRAINT chk_pct_topproc_sensitivity_class CHECK (
        sensitivity_class IN ('FULL_CLINICAL', 'SPECIALLY_PROTECTED')),
    ADD CONSTRAINT chk_pct_topproc_confid_vocabulary CHECK (
        confidentiality_category IS NULL OR confidentiality_category IN (
            'SEXUAL_REPRODUCTIVE_HEALTH', 'HIV', 'MENTAL_HEALTH',
            'SAFEGUARDING', 'SUBSTANCE_USE', 'GENDER_BASED_VIOLENCE')),
    ADD CONSTRAINT chk_pct_topproc_confid_basis CHECK (
        confidentiality_basis IS NULL OR confidentiality_basis IN (
            'RECORD_TYPE_ALWAYS', 'SUBJECT_UNDER_POLICY_AGE', 'MATURE_MINOR_CAPACITY',
            'AGE_UNRESOLVED', 'POLICY_UNAVAILABLE')),
    ADD CONSTRAINT chk_pct_topproc_protected_has_category CHECK (
        sensitivity_class <> 'SPECIALLY_PROTECTED' OR confidentiality_category IS NOT NULL);

ALTER TABLE pct.pct_postnatal_contacts
    ADD CONSTRAINT chk_pct_pnc_sensitivity_class CHECK (
        sensitivity_class IN ('FULL_CLINICAL', 'SPECIALLY_PROTECTED')),
    ADD CONSTRAINT chk_pct_pnc_confid_vocabulary CHECK (
        confidentiality_category IS NULL OR confidentiality_category IN (
            'SEXUAL_REPRODUCTIVE_HEALTH', 'HIV', 'MENTAL_HEALTH',
            'SAFEGUARDING', 'SUBSTANCE_USE', 'GENDER_BASED_VIOLENCE')),
    ADD CONSTRAINT chk_pct_pnc_confid_basis CHECK (
        confidentiality_basis IS NULL OR confidentiality_basis IN (
            'RECORD_TYPE_ALWAYS', 'SUBJECT_UNDER_POLICY_AGE', 'MATURE_MINOR_CAPACITY',
            'AGE_UNRESOLVED', 'POLICY_UNAVAILABLE')),
    ADD CONSTRAINT chk_pct_pnc_protected_has_category CHECK (
        sensitivity_class <> 'SPECIALLY_PROTECTED' OR confidentiality_category IS NOT NULL);

ALTER TABLE pct.pct_contraceptive_episodes
    ADD CONSTRAINT chk_pct_contra_sensitivity_class CHECK (
        sensitivity_class IN ('FULL_CLINICAL', 'SPECIALLY_PROTECTED')),
    ADD CONSTRAINT chk_pct_contra_confid_vocabulary CHECK (
        confidentiality_category IS NULL OR confidentiality_category IN (
            'SEXUAL_REPRODUCTIVE_HEALTH', 'HIV', 'MENTAL_HEALTH',
            'SAFEGUARDING', 'SUBSTANCE_USE', 'GENDER_BASED_VIOLENCE')),
    ADD CONSTRAINT chk_pct_contra_confid_basis CHECK (
        confidentiality_basis IS NULL OR confidentiality_basis IN (
            'RECORD_TYPE_ALWAYS', 'SUBJECT_UNDER_POLICY_AGE', 'MATURE_MINOR_CAPACITY',
            'AGE_UNRESOLVED', 'POLICY_UNAVAILABLE')),
    ADD CONSTRAINT chk_pct_contra_protected_has_category CHECK (
        sensitivity_class <> 'SPECIALLY_PROTECTED' OR confidentiality_category IS NOT NULL);

ALTER TABLE pct.pct_pregnancy_episodes
    ADD CONSTRAINT chk_pct_preg_sensitivity_class CHECK (
        sensitivity_class IN ('FULL_CLINICAL', 'SPECIALLY_PROTECTED')),
    ADD CONSTRAINT chk_pct_preg_confid_vocabulary CHECK (
        confidentiality_category IS NULL OR confidentiality_category IN (
            'SEXUAL_REPRODUCTIVE_HEALTH', 'HIV', 'MENTAL_HEALTH',
            'SAFEGUARDING', 'SUBSTANCE_USE', 'GENDER_BASED_VIOLENCE')),
    ADD CONSTRAINT chk_pct_preg_confid_basis CHECK (
        confidentiality_basis IS NULL OR confidentiality_basis IN (
            'RECORD_TYPE_ALWAYS', 'SUBJECT_UNDER_POLICY_AGE', 'MATURE_MINOR_CAPACITY',
            'AGE_UNRESOLVED', 'POLICY_UNAVAILABLE')),
    ADD CONSTRAINT chk_pct_preg_protected_has_category CHECK (
        sensitivity_class <> 'SPECIALLY_PROTECTED' OR confidentiality_category IS NOT NULL);

-- ---------------------------------------------------------------------------------------------
-- 6. Mature-minor capacity determinations.
--
-- The PO ruling is that adolescent confidential SRH is configurable (default 18) WITH A RECORDED
-- MATURE-MINOR CAPACITY OVERRIDE. Capacity is a fact about A PERSON AT A TIME, not about one record:
-- putting it on each record lets two records disagree about the same young woman on the same day, and
-- the disagreement would be invisible. It is also per CARE DOMAIN — capacity to consent to
-- contraception is not capacity to consent to mental-health treatment — so the category is part of
-- the key, not a detail.
--
-- Capacity ADDS eligibility for confidentiality; it never removes it. LACKS_CAPACITY does not strip
-- an under-age young person of the protection the age rule already gave her.
-- ---------------------------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS pct.pct_confidentiality_capacity_determinations (
    determination_id  UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    subject_cpid      VARCHAR(64) NOT NULL,
    category          VARCHAR(48) NOT NULL,
    determined_on     DATE NOT NULL,
    capacity          VARCHAR(24) NOT NULL,
    -- An unexplained determination is not a determination. NOT NULL rejects a null but happily
    -- accepts '', so the emptiness check is a separate constraint and is proven separately.
    rationale         TEXT NOT NULL,
    determined_by     VARCHAR(128) NOT NULL,
    recorded_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_pct_capacity_value CHECK (
        capacity IN ('HAS_CAPACITY', 'LACKS_CAPACITY', 'NOT_ASSESSED')),
    CONSTRAINT chk_pct_capacity_category CHECK (
        category IN ('SEXUAL_REPRODUCTIVE_HEALTH', 'HIV', 'MENTAL_HEALTH',
                     'SAFEGUARDING', 'SUBSTANCE_USE', 'GENDER_BASED_VIOLENCE')),
    CONSTRAINT chk_pct_capacity_rationale_present CHECK (length(btrim(rationale)) > 0)
);

CREATE INDEX IF NOT EXISTS ix_pct_capacity_subject
    ON pct.pct_confidentiality_capacity_determinations (tenant_id, subject_cpid, category, determined_on DESC);

COMMENT ON TABLE pct.pct_confidentiality_capacity_determinations IS
    'Mature-minor capacity determinations. A fact about a person at a time, per care domain — never a per-record flag, which would let two records disagree about the same young person on the same day. Capacity ADDS eligibility for confidentiality and never removes it.';

COMMENT ON COLUMN pct.pct_pregnancy_loss_records.sensitivity_class IS
    'DataSensitivityClass name. Stays FULL_CLINICAL until the governance flip: stamping SPECIALLY_PROTECTED while the PDP runs in SHADOW would hide the record from the clinician who wrote it, because the guard fails closed and no categories are granted.';
COMMENT ON COLUMN pct.pct_pregnancy_loss_records.confidentiality_category IS
    'Governed confidential category (zibo V008 vocabulary). NULL means unclassified, which reads as ordinary clinical data. Populated for real now — a category is a content classification and asserts nothing about protection.';
COMMENT ON COLUMN pct.pct_pregnancy_loss_records.confidentiality_basis IS
    'Why the stamp was applied. AGE_UNRESOLVED and POLICY_UNAVAILABLE record that stamping failed OPEN: a false-negative leaves the record readable by the care team, a false-positive makes it invisible to them.';
