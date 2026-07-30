-- =====================================================================================
-- Surgical demonstration 3 — was this decision made by one surgeon or by a board?
--
-- `surgical_decision` (V004) records the reasoning in depth and then attributes it to
-- `decided_by` / `decided_at`: one name, one moment. A cancer resection agreed by a
-- multidisciplinary team — oncology, radiology, pathology, surgery, palliative care — is recorded
-- identically to a decision one surgeon made alone in a clinic. The forum is invisible, and with
-- it goes the audit question "who agreed to this operation".
--
-- ── SURGERY REFERENCES; IT DOES NOT BECOME A THIRD MDT SYSTEM OF RECORD ─────────────
-- PCT already has TWO, and that is an inherited defect this programme names rather than repeats:
--
--   * `pct_mdt_sessions` / `pct_mdt_case_items` (pct V051) — the telemedicine board lane, with a
--     chair, a scribe, an agenda and per-participant AGREE/DISSENT positions.
--   * `pct_mdt_decisions` (pct V114) — the Adult Medicine lane, with meeting_type, participants,
--     treatment_intent and a responsible owner.
--
-- Consolidating them is a cross-lane decision, and the lease says this programme writes nothing
-- in pct. So surgery records only WHICH forum decided and a REFERENCE to the board record, and
-- reads it back best-effort. Creating a surgical MDT table here would have made three.
--
-- ── WHY THE REFERENCE NEEDS A SOURCE ────────────────────────────────────────────────
-- Because there are two systems of record, a bare UUID is ambiguous — it could be a
-- pct_mdt_case_items row or a pct_mdt_decisions row, and nothing about the value says which.
-- `mdt_decision_source` names the table. When PCT consolidates its two lanes, this column is the
-- migration key; until then it is the only thing that makes the reference resolvable at all.
--
-- ── VERIFIED IS NOT THE SAME AS PRESENT ─────────────────────────────────────────────
-- `mdt_decision_verified_at` records that surgery actually resolved the reference against PCT and
-- found it. Null means unverified, never "invalid": a PCT outage must not block recording that a
-- board decided, and must not be allowed to look like confirmation that it did. The reconciliation
-- query is the partial index at the foot of this file.
-- =====================================================================================

ALTER TABLE surgery.surgical_decision
    ADD COLUMN IF NOT EXISTS decision_forum           VARCHAR(24) NOT NULL DEFAULT 'INDIVIDUAL',
    ADD COLUMN IF NOT EXISTS mdt_decision_ref         UUID,
    ADD COLUMN IF NOT EXISTS mdt_decision_source      VARCHAR(32),
    ADD COLUMN IF NOT EXISTS mdt_decision_verified_at TIMESTAMPTZ;

COMMENT ON COLUMN surgery.surgical_decision.decision_forum IS
    'INDIVIDUAL or MDT. Defaults to INDIVIDUAL because that is what every pre-V012 row actually was — an individually attributed decision — not because it is a safe unknown.';
COMMENT ON COLUMN surgery.surgical_decision.mdt_decision_source IS
    'Which PCT system of record mdt_decision_ref points at. Required because PCT has two, an inherited duplication surgery references rather than resolves.';
COMMENT ON COLUMN surgery.surgical_decision.mdt_decision_verified_at IS
    'When surgery last resolved mdt_decision_ref against PCT and found it. NULL means unverified, never invalid — a PCT outage must not masquerade as confirmation.';

ALTER TABLE surgery.surgical_decision
    ADD CONSTRAINT chk_surgical_decision_forum CHECK (
        decision_forum IN ('INDIVIDUAL', 'MDT'));

ALTER TABLE surgery.surgical_decision
    ADD CONSTRAINT chk_surgical_decision_mdt_source CHECK (
        mdt_decision_source IS NULL OR mdt_decision_source IN ('PCT_MDT_DECISION', 'PCT_MDT_CASE_ITEM'));

-- The reference and the table it points into are meaningless apart.
ALTER TABLE surgery.surgical_decision
    ADD CONSTRAINT chk_surgical_decision_mdt_ref_pair CHECK (
        (mdt_decision_ref IS NULL AND mdt_decision_source IS NULL)
        OR (mdt_decision_ref IS NOT NULL AND mdt_decision_source IS NOT NULL));

-- An MDT decision that names no board is the defect this migration exists to remove, and an
-- INDIVIDUAL decision carrying a board reference is a contradiction rather than extra detail.
ALTER TABLE surgery.surgical_decision
    ADD CONSTRAINT chk_surgical_decision_mdt_forum_ref CHECK (
        (decision_forum = 'MDT' AND mdt_decision_ref IS NOT NULL)
        OR (decision_forum = 'INDIVIDUAL' AND mdt_decision_ref IS NULL));

-- Nothing can be verified that was never referenced.
ALTER TABLE surgery.surgical_decision
    ADD CONSTRAINT chk_surgical_decision_mdt_verified CHECK (
        mdt_decision_verified_at IS NULL OR mdt_decision_ref IS NOT NULL);

-- The reconciliation query: board decisions whose reference surgery has not yet resolved against
-- PCT. Same shape and same purpose as V002's idx_surgical_episode_uncontributed.
CREATE INDEX IF NOT EXISTS idx_surgical_decision_mdt_unverified
    ON surgery.surgical_decision (tenant_id, updated_at DESC)
    WHERE decision_forum = 'MDT' AND mdt_decision_verified_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_surgical_decision_mdt_ref
    ON surgery.surgical_decision (mdt_decision_source, mdt_decision_ref)
    WHERE mdt_decision_ref IS NOT NULL;
