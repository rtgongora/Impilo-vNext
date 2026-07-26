-- Medication reconciliation (PCT).
--
-- Reconciliation is not a medicine list. The list is what the system believes; reconciliation is
-- the act of comparing that against what the patient is actually taking, at a moment where the two
-- are known to diverge — admission, discharge, transfer, a clinic review after another prescriber
-- has been involved. Most medication error happens at exactly those transitions, and it happens
-- because nobody wrote down that the comparison was made.
--
-- So the record here is the RECONCILIATION EVENT and its findings, not another copy of the
-- medicines. The current list stays OROS's (read over the S2S seam); what patients report stays
-- here, because nothing else in the estate has anywhere to put "she says she stopped the
-- amlodipine two months ago".
--
-- THE PROPERTY THAT MATTERS: an unfinished reconciliation must never read as a clean one.
-- A reconciliation with no discrepancies recorded is indistinguishable from one nobody completed
-- unless the record says which — so status is explicit, IN_PROGRESS is the default, and a
-- COMPLETED reconciliation must name who completed it and when. "No discrepancies found" is an
-- affirmative clinical finding and is only believable from a reconciliation that finished.

CREATE TABLE pct_medication_reconciliations (
    reconciliation_id UUID           PRIMARY KEY,
    tenant_id         UUID           NOT NULL,
    subject_cpid      VARCHAR(128)   NOT NULL,
    journey_id        VARCHAR(26),
    encounter_id      VARCHAR(64),
    episode_id        UUID           REFERENCES pct_medical_episodes(episode_id),

    -- The transition this reconciliation exists to cover.
    context           VARCHAR(32)    NOT NULL,
    status            VARCHAR(32)    NOT NULL DEFAULT 'IN_PROGRESS',

    -- Where the "what they are actually taking" side came from. A reconciliation against the
    -- patient's own account and one against a pharmacy record are different levels of evidence,
    -- and collapsing them hides which was available.
    sources           VARCHAR(512),

    -- Set only on completion, and required then.
    completed_by      VARCHAR(255),
    completed_at      TIMESTAMPTZ,
    -- Why it was abandoned. Recorded rather than left as a stalled IN_PROGRESS row, because a
    -- reconciliation nobody could finish is a clinical fact about the encounter.
    abandoned_reason  VARCHAR(255),

    started_by        VARCHAR(255)   NOT NULL,
    started_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    notes             TEXT,

    CONSTRAINT pct_medrec_context_check CHECK (context IN (
        'ADMISSION', 'DISCHARGE', 'TRANSFER', 'CLINIC_REVIEW', 'PERIODIC_REVIEW')),
    CONSTRAINT pct_medrec_status_check CHECK (status IN (
        'IN_PROGRESS', 'COMPLETED', 'ABANDONED')),
    -- A completed reconciliation names who finished it and when. Without this, "completed" can be
    -- set by anything and the clean bill of health it implies has no author.
    CONSTRAINT pct_medrec_completion_check CHECK (
        (status = 'COMPLETED' AND completed_by IS NOT NULL AND completed_at IS NOT NULL)
        OR (status <> 'COMPLETED' AND completed_by IS NULL AND completed_at IS NULL)),
    CONSTRAINT pct_medrec_abandoned_check CHECK (
        (status = 'ABANDONED' AND abandoned_reason IS NOT NULL)
        OR (status <> 'ABANDONED' AND abandoned_reason IS NULL))
);

CREATE INDEX idx_pct_medrec_subject
    ON pct_medication_reconciliations (tenant_id, subject_cpid, started_at DESC);
CREATE INDEX idx_pct_medrec_open
    ON pct_medication_reconciliations (tenant_id, subject_cpid)
    WHERE status = 'IN_PROGRESS';

COMMENT ON TABLE pct_medication_reconciliations IS
    'The act of comparing what the system believes a patient takes against what they actually '
    'take, at a transition where the two diverge. Not a medicine list — OROS owns that.';

-- What the comparison found ----------------------------------------------------
--
-- One row per medicine considered, INCLUDING the ones that matched. A reconciliation that only
-- records discrepancies cannot distinguish a medicine that was checked and agreed from one nobody
-- looked at, and that distinction is the entire clinical value of the exercise.
CREATE TABLE pct_medication_reconciliation_items (
    item_id           UUID           PRIMARY KEY,
    reconciliation_id UUID           NOT NULL
        REFERENCES pct_medication_reconciliations(reconciliation_id) ON DELETE CASCADE,
    tenant_id         UUID           NOT NULL,

    -- What the medicine is. Coded where possible; free text is the common case in this estate and
    -- refusing it would mean refusing most of what patients actually report.
    display           VARCHAR(512)   NOT NULL,
    code              VARCHAR(64),
    code_system       VARCHAR(64),
    -- The prescription in OROS this item corresponds to, when it corresponds to one. NULL means
    -- the patient is taking something the system has no prescription for — which is the finding.
    prescription_ref  VARCHAR(128),

    -- The two sides being compared, as free text because a dose the patient describes
    -- ("half a tablet when my ankles swell") is evidence and normalising it away loses it.
    system_says       VARCHAR(512),
    patient_says      VARCHAR(512),

    finding           VARCHAR(32)    NOT NULL,
    -- What was decided. NULL while the item is still being worked.
    action            VARCHAR(32),
    -- Traditional and complementary remedies, over-the-counter medicines: recorded as items with
    -- their own source so they reach interaction checking rather than living in a note.
    source            VARCHAR(32)    NOT NULL DEFAULT 'PRESCRIBED',
    notes             TEXT,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),

    CONSTRAINT pct_medrec_item_finding_check CHECK (finding IN (
        'MATCHED',            -- system and patient agree
        'DOSE_DIFFERS',
        'FREQUENCY_DIFFERS',
        'NOT_TAKING',         -- prescribed but the patient is not taking it
        'TAKING_UNPRESCRIBED',-- taking something with no prescription in the system
        'DUPLICATE',          -- two entries for the same therapy
        'UNABLE_TO_VERIFY'    -- neither side could be established; explicitly not a match
    )),
    CONSTRAINT pct_medrec_item_action_check CHECK (action IS NULL OR action IN (
        'CONTINUE', 'STOP', 'CHANGE_DOSE', 'SUBSTITUTE', 'ADD_TO_RECORD', 'REFER_TO_PRESCRIBER')),
    CONSTRAINT pct_medrec_item_source_check CHECK (source IN (
        'PRESCRIBED', 'OVER_THE_COUNTER', 'TRADITIONAL', 'COMPLEMENTARY', 'OTHER'))
);

CREATE INDEX idx_pct_medrec_items_reconciliation
    ON pct_medication_reconciliation_items (reconciliation_id);
CREATE INDEX idx_pct_medrec_items_unresolved
    ON pct_medication_reconciliation_items (tenant_id, reconciliation_id)
    WHERE action IS NULL AND finding <> 'MATCHED';

COMMENT ON TABLE pct_medication_reconciliation_items IS
    'One row per medicine considered, including matches. A reconciliation recording only '
    'discrepancies cannot tell a medicine that was checked and agreed from one nobody looked at.';
COMMENT ON COLUMN pct_medication_reconciliation_items.finding IS
    'UNABLE_TO_VERIFY is deliberately not MATCHED: a medicine neither side could establish is an '
    'open question, and recording it as agreement is how an unverified dose becomes a confirmed one.';
COMMENT ON COLUMN pct_medication_reconciliation_items.patient_says IS
    'Free text on purpose. "Half a tablet when my ankles swell" is evidence about adherence and '
    'understanding; normalising it into a structured dose discards the part that was informative.';
