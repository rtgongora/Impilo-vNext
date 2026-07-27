-- Programme enrolment → pregnancy episode soft link (PCT) — the PMTCT seam.
--
-- W3 completion, cross-lane seam agreed with the RMNP lane. A mother's PMTCT care is her existing
-- HIV_CARE enrolment; this adds a nullable pointer from that enrolment to the RMNP pregnancy episode
-- it relates to, so HIV care and antenatal care follow the same woman without either duplicating the
-- other's record.
--
-- WHY THE FK IS SAFE HERE (and why it lives in THIS band, not RMNP's). The cross-lane rule is that a
-- FK lives in the band of the lane owning the REFERENCED table, because a lower band applies first
-- and cannot depend on a higher one. Here the referenced table pct.pct_pregnancy_episodes is created
-- at V059 — LOWER than this pack's V111 — so on a clean boot the target already exists when this
-- applies. There is no forward dependency, so a real FK is correct and it belongs in this pack's
-- band (it references RMNP's table but the ordering runs the right way). Added NOT VALID then
-- VALIDATE per the brownfield-safe pattern; the column is new and nullable so no existing row can
-- violate it, but the idiom is kept for consistency and at RMNP's request.
--
-- DIRECTION AND OWNERSHIP (RMNP ruling, 2026-07-27):
--   * The pointer lives HERE, on the enrolment, and points AT the pregnancy episode. There is no
--     back-reference column on the pregnancy episode — HIV-programme concerns stay off the maternal
--     spine (PMTCT is one of several programmes; the episode must not sprout a column per programme).
--   * READ-ONLY. This pack may reference the episode; it must never redate, close, restatus or write
--     back to it. The episode's lifecycle is RMNP's. Enforced in application code (this pack holds no
--     client that mutates the episode; it stores a UUID only).
--   * CONFIDENTIALITY IS ONE-DIRECTIONAL. HIV status is SPECIALLY_PROTECTED and travels with the
--     HIV_CARE enrolment, never with the pregnancy episode. Because the link exists only on this side
--     (no back-reference), the maternity summary cannot infer a woman is HIV-positive from the
--     existence of a PMTCT link — the pregnancy episode is not a back door to HIV status. Build
--     assuming the SPECIALLY_PROTECTED enforcement (RMNP W8) governs the enrolment.
--
-- The infant side is this pack's, per the same ruling: an HIV_CARE enrolment on the infant CPID,
-- triggered by RMNP's birth-time pct_newborn_birth_records.hiv_exposure flag, owns prophylaxis + EID.
-- No schema for that is needed here — it reuses pct_programme_enrolments.

ALTER TABLE pct.pct_programme_enrolments
    ADD COLUMN IF NOT EXISTS pregnancy_episode_id UUID;

ALTER TABLE pct.pct_programme_enrolments
    ADD CONSTRAINT pct_programme_enrolments_pregnancy_episode_fk
    FOREIGN KEY (pregnancy_episode_id)
    REFERENCES pct.pct_pregnancy_episodes (pregnancy_episode_id)
    NOT VALID;

ALTER TABLE pct.pct_programme_enrolments
    VALIDATE CONSTRAINT pct_programme_enrolments_pregnancy_episode_fk;

CREATE INDEX IF NOT EXISTS idx_pct_programme_enrolments_pregnancy
    ON pct.pct_programme_enrolments (tenant_id, pregnancy_episode_id)
    WHERE pregnancy_episode_id IS NOT NULL;

COMMENT ON COLUMN pct.pct_programme_enrolments.pregnancy_episode_id IS
    'PMTCT seam: nullable, read-only soft link to the RMNP pregnancy episode this HIV_CARE enrolment '
    'relates to. Never written back to; HIV sensitivity travels with the enrolment, not the episode.';
