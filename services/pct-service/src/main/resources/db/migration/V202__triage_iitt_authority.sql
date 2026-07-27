-- WHO IITT becomes the triage authority of record; ESI/MTS become advisory.
--
-- Product-owner ruling: the Interagency Integrated Triage Tool is the sole triage authority for
-- this estate. ESI and MTS may still be computed, but as advisory secondary scores that can never
-- set the acuity of record, and IMPILO_5 (free-hand acuity wearing a system's name, no engine) is
-- retired as a selectable system. This migration adds the columns that make that explicit; the
-- write-path change lands with it (EdVisitService), because a column nothing writes is the
-- structural-without-functional half-measure this pack has learned to refuse.
--
-- BROWNFIELD-SAFE, deliberately. ed_triage_assessment has live writers (the current ESI/MTS/IMPILO_5
-- path), so every column here is additive and nullable, and the one CHECK only bites a row whose
-- triage_tool is 'WHO_IITT' — a value nothing sets until the new path writes it. Existing rows and
-- the legacy path are untouched. This is the lesson from daidzai V200 applied up front: a constraint
-- on a live-writer table must not be able to reject the rows the existing writer already produces.
--
-- acuity stays INT 1-5 NOT NULL and remains the operational sort key every board and queue reads.
-- The IITT three tiers map to it by a DOCUMENTED derivation (RED->1, YELLOW->3, GREEN->5); the tier
-- itself is preserved first-class in iitt_priority so nothing is lost to the mapping. IITT's fourth
-- outcome, NOT_TRIAGEABLE, is deliberately NOT representable here: an unassessed patient is not a
-- triage, so the write path refuses it (422) rather than inventing an acuity — which is the whole
-- point of retiring the old `acuity = 3` default.

ALTER TABLE pct.ed_triage_assessment
    -- The tool that produced the acuity of record. Distinct from the legacy triage_system column,
    -- which the old Math.min reconciliation used to overwrite with whichever of ESI/MTS scored
    -- lower — stamping a row with a system the clinician did not select. triage_tool is the honest
    -- successor; triage_system is left in place for back-compat and no longer overwritten.
    ADD COLUMN triage_tool VARCHAR(32),
    ADD COLUMN iitt_priority VARCHAR(20),
    ADD COLUMN emergency_signs_json JSONB,
    -- IITT's own escape hatch: "high-risk vital signs or clinical concern need up-triage or
    -- immediate review by supervising clinician." A cross-system discrepancy (IITT says GREEN, the
    -- advisory ESI says 1-2) is an instance of clinical concern, raised for a human here rather than
    -- silently up-triaged by taking the lower number.
    ADD COLUMN requires_clinician_review BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN review_reason TEXT,
    -- ESI/MTS scores kept as advisory, never as the acuity of record.
    ADD COLUMN advisory_scores_json JSONB,

    ADD CONSTRAINT chk_ed_triage_tool CHECK (
        triage_tool IS NULL OR triage_tool IN ('WHO_IITT', 'ESI', 'MTS', 'IMPILO_5')),

    ADD CONSTRAINT chk_ed_triage_iitt_priority CHECK (
        iitt_priority IS NULL OR iitt_priority IN ('RED', 'YELLOW', 'GREEN')),

    -- The authority contract: a row produced by the IITT tool must carry its priority. This is what
    -- makes acuity a derived mapping of a real IITT decision rather than a bare number. It cannot
    -- bite an existing row, because no existing row has triage_tool = 'WHO_IITT'.
    ADD CONSTRAINT chk_ed_triage_iitt_requires_priority CHECK (
        triage_tool <> 'WHO_IITT' OR iitt_priority IS NOT NULL);

COMMENT ON COLUMN pct.ed_triage_assessment.triage_tool IS
    'The tool that set the acuity of record. WHO_IITT is authoritative (PO ruling); ESI/MTS are '
    'advisory and recorded in advisory_scores_json, never here. Legacy triage_system is retained '
    'for back-compat and no longer overwritten by a Math.min reconciliation.';
COMMENT ON COLUMN pct.ed_triage_assessment.iitt_priority IS
    'RED/YELLOW/GREEN — the IITT tier, kept first-class so acuity (1-5) is a documented derivation '
    'of it rather than the primary record. NOT_TRIAGEABLE is never stored: it is a refusal (422), '
    'because an unassessed patient is not a triage.';
COMMENT ON COLUMN pct.ed_triage_assessment.requires_clinician_review IS
    'IITT up-triage / clinical-concern flag. A cross-system discrepancy raises this for a human '
    'rather than silently up-triaging by taking the lower acuity.';
