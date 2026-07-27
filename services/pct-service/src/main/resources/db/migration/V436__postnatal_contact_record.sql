-- The postnatal maternal contact record: the mother's care AFTER the birth.
--
-- WHY THIS TABLE EXISTS. The birth is recorded from the baby's side (pct_newborn_birth_records, V055)
-- and the mother's side (pct_delivery_records, V433). What happened NEXT to the mother — the WHO PNC
-- contacts within the first 24 hours, around day 3, day 7-14 and week 6; her involution, bleeding,
-- mood, and breastfeeding — was unrecordable. The PNC danger-sign and newborn packs already run off
-- clinical facts, but nothing persisted the CONTACT: that a postnatal visit happened, where, whether
-- screening was completed, and what was found. This table is that record. It is mother-anchored: the
-- newborn's postnatal care is the paediatric pack's, reached through the shared pregnancy episode.
--
-- RMNP band V430-V459 (V430 contraception · V431 preconception/fertility · V432 fragment-null fix ·
-- V433 delivery · V434 loss · V435 termination → this is V436). Anchors on pct_pregnancy_episodes
-- (V059, a lower number: applies first on a fresh database, no cross-band forward dependency) and,
-- optionally, on a contraceptive episode (V430, also lower) so postpartum family planning REUSES the
-- antenatal/existing plan rather than re-counselling from scratch.
--
-- TWO SAFETY INVARIANTS ARE MADE STRUCTURAL, not left to the read path:
--
-- 1. "NO DANGER SIGNS" IS ONLY RENDERABLE FROM A COMPLETED SCREEN. danger_signs_present may be
--    non-null ONLY when screening_complete is true (chk_pnc_screening_gate). An unscreened or
--    partially-recorded contact leaves danger_signs_present NULL, which a client renders as "not
--    screened", never as the reassuring "no danger signs". Silence is not reassurance — the same
--    law the danger-sign engine carries, here pushed down into the schema so a write that bypasses
--    the service cannot manufacture a false all-clear.
--
-- 2. A REFERRAL CARRIES ITS REASON. referral_made true requires referral_reason (chk_pnc_referral).
--    A postnatal referral with no recorded reason is a woman sent onward with nothing said about why.
--
-- Offline store-and-forward: client_offline_id is unique per tenant (the V050 precedent), so a
-- replayed packet returns the existing contact rather than minting a duplicate visit.

CREATE TABLE IF NOT EXISTS pct.pct_postnatal_contacts (
    postnatal_contact_id    UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    -- The SUBJECT is the mother. The newborn's postnatal care is the paediatric pack's, reached via
    -- the shared pregnancy episode, never duplicated here.
    mother_cpid             VARCHAR(64) NOT NULL,
    pregnancy_episode_id    UUID
        REFERENCES pct.pct_pregnancy_episodes (pregnancy_episode_id),

    status                  VARCHAR(24) NOT NULL DEFAULT 'RECORDED',

    -- WHO PNC contact schedule (PNC.S.01). ADDITIONAL covers an unscheduled or extra contact.
    contact_timing          VARCHAR(24) NOT NULL,
    contact_number          SMALLINT,
    contacted_at            TIMESTAMPTZ NOT NULL,
    -- PNC happens at a facility, at home (CHW/domiciliary), in the community, or virtually. The pack
    -- delivers community/home/virtual PNC, so the setting is first-class, not assumed to be a clinic.
    contact_setting         VARCHAR(24) NOT NULL,
    facility_id             UUID,
    attended_by             VARCHAR(128),
    attendant_cadre         VARCHAR(48),

    -- Maternal assessment. Involution, lochia, and her recovery.
    days_since_birth        SMALLINT,
    maternal_condition      VARCHAR(48),
    bleeding_assessment     VARCHAR(48),
    mood_screen             VARCHAR(48),

    -- The danger-sign SCREEN. Whether it was completed, and — only then — what it found. See
    -- chk_pnc_screening_gate: a finding cannot be recorded without a completed screen.
    screening_complete      BOOLEAN NOT NULL DEFAULT FALSE,
    danger_signs_present    BOOLEAN,

    -- Lactation / breastfeeding. NOT_ASSESSED is distinct from NONE: "we did not look" is not "she is
    -- not breastfeeding", and neither is the null default.
    breastfeeding_status    VARCHAR(24),
    breastfeeding_difficulty VARCHAR(64),
    lactation_support_given BOOLEAN NOT NULL DEFAULT FALSE,

    -- Postpartum family planning REUSES the existing plan: a pointer to the contraceptive episode
    -- (V430), not a fresh counselling record. NULL means not yet addressed, never "declined".
    postpartum_contraception_episode_id UUID
        REFERENCES pct.pct_contraceptive_episodes (contraceptive_episode_id),
    contraception_discussed BOOLEAN NOT NULL DEFAULT FALSE,

    referral_made           BOOLEAN NOT NULL DEFAULT FALSE,
    referral_reason         TEXT,
    referral_facility_id    UUID,

    confidentiality_category VARCHAR(48) NOT NULL DEFAULT 'FULL_CLINICAL',

    client_offline_id       VARCHAR(128),
    recorded_by             VARCHAR(128) NOT NULL,
    recorded_at             TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_pnc_status CHECK (status IN ('RECORDED', 'AMENDED', 'VOIDED')),

    CONSTRAINT chk_pnc_timing CHECK (
        contact_timing IN ('WITHIN_24H', 'DAY_3', 'DAY_7_14', 'WEEK_6', 'ADDITIONAL')),

    CONSTRAINT chk_pnc_setting CHECK (
        contact_setting IN ('FACILITY', 'HOME', 'COMMUNITY', 'VIRTUAL')),

    CONSTRAINT chk_pnc_breastfeeding CHECK (
        breastfeeding_status IS NULL OR breastfeeding_status IN (
            'EXCLUSIVE', 'PREDOMINANT', 'PARTIAL', 'NONE', 'NOT_ASSESSED')),

    -- Invariant 1: a danger-sign finding — present OR absent — requires a completed screen. Unscreened
    -- leaves it NULL, which never reads as "no danger signs".
    CONSTRAINT chk_pnc_screening_gate CHECK (
        danger_signs_present IS NULL OR screening_complete = TRUE),

    -- Invariant 2: a referral carries its reason.
    CONSTRAINT chk_pnc_referral CHECK (
        referral_made = FALSE OR referral_reason IS NOT NULL)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_pct_pnc_offline
    ON pct.pct_postnatal_contacts (tenant_id, client_offline_id)
    WHERE client_offline_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_pct_pnc_mother
    ON pct.pct_postnatal_contacts (tenant_id, mother_cpid);
CREATE INDEX IF NOT EXISTS ix_pct_pnc_episode
    ON pct.pct_postnatal_contacts (pregnancy_episode_id);

COMMENT ON TABLE pct.pct_postnatal_contacts IS
    'Postnatal maternal contact (PNC.S.01). Mother-anchored; the newborn''s PNC is the paediatric pack''s. "No danger signs" is only recordable behind a completed screen (chk_pnc_screening_gate); a referral carries its reason. Postpartum FP reuses the contraceptive episode rather than re-counselling.';
COMMENT ON COLUMN pct.pct_postnatal_contacts.danger_signs_present IS
    'NULL = not screened (renders as "not screened", never "no danger signs"). Only settable when screening_complete is true.';
COMMENT ON COLUMN pct.pct_postnatal_contacts.breastfeeding_status IS
    'NOT_ASSESSED is distinct from NONE and from NULL: "did not look" is not "not breastfeeding".';
