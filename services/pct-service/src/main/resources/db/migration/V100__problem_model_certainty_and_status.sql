-- Canonical problem model (PCT) — the certainty axis, and a status vocabulary that can say
-- "this came back".
--
-- The problem list is the record every medical surface composes over, and it could not express the
-- distinction the Adult Medicine brief is most explicit about: symptom, syndrome, guideline
-- classification, working diagnosis and confirmed diagnosis must not collapse into one another.
--
-- They were collapsing along two different seams, and only one of them was the fault of `category`.
--
--  1. WHAT KIND of clinical statement this is (`category`). The axis existed but its vocabulary was
--     four values wide — DIAGNOSIS, SYMPTOM, RISK, SOCIAL — with no room for a syndrome or for a
--     guideline classification. A guideline classification in particular is neither a symptom nor a
--     diagnosis: "severe pneumonia" on the IMNCI chart, "Child-Pugh B", "stage 3a CKD" are the
--     output of applying a rule to findings, and recording one as a diagnosis claims more than the
--     chart said.
--
--  2. HOW CERTAIN anyone is (`diagnostic_certainty`). This axis did not exist at all, and that is
--     the one that matters clinically. It is genuinely ORTHOGONAL to kind: a symptom can be
--     confirmed (the patient definitely has chest pain) while a diagnosis is only suspected. Folding
--     certainty into kind — a "SUSPECTED_DIAGNOSIS" category — would have made those two facts
--     inexpressible together, which is how a differential ends up on a record looking like a
--     conclusion.
--
-- Certainty is deliberately NULLABLE and is NOT backfilled. Every existing row was written before
-- anyone was asked how sure they were, so any value chosen here would be invented: CONFIRMED
-- over-claims and SUSPECTED under-claims a diagnosis that may well have been certain. NULL means
-- "certainty was never stated" and readers must not resolve it to confirmed.
--
-- `clinical_status` gains the states a chronic disease actually moves through. ACTIVE/INACTIVE/
-- RESOLVED could not say that a condition came back, so a relapse was recorded either by editing
-- the resolved row — losing the fact that it had ever resolved — or by creating a second problem,
-- which is how one disease becomes two entries in the list. The added values are a superset, so no
-- existing row can violate the new CHECK.

-- 1. Kind ---------------------------------------------------------------------
-- Widen the existing axis rather than introduce a second one. Two columns meaning "what sort of
-- problem is this" would be the same duplication this migration exists to remove.
UPDATE pct_problems SET category = 'RISK_FACTOR'        WHERE category = 'RISK';
UPDATE pct_problems SET category = 'SOCIAL_CIRCUMSTANCE' WHERE category = 'SOCIAL';

ALTER TABLE pct_problems
    ADD CONSTRAINT pct_problems_category_check CHECK (category IN (
        'DIAGNOSIS',
        'SYMPTOM',
        'SYNDROME',
        'GUIDELINE_CLASSIFICATION',
        'RISK_FACTOR',
        'FUNCTIONAL_CONSEQUENCE',
        'SOCIAL_CIRCUMSTANCE'
    ));

COMMENT ON COLUMN pct_problems.category IS
    'What kind of clinical statement this is. Orthogonal to diagnostic_certainty: a symptom can be '
    'confirmed while a diagnosis is only suspected. A GUIDELINE_CLASSIFICATION is the output of a '
    'rule applied to findings and claims less than a DIAGNOSIS.';

-- 2. Certainty ----------------------------------------------------------------
ALTER TABLE pct_problems
    ADD COLUMN diagnostic_certainty VARCHAR(32);

ALTER TABLE pct_problems
    ADD CONSTRAINT pct_problems_certainty_check CHECK (diagnostic_certainty IS NULL OR diagnostic_certainty IN (
        'SUSPECTED',     -- raised as possible, not yet being worked up as the answer
        'DIFFERENTIAL',  -- one of several being actively distinguished between
        'WORKING',       -- being treated as the answer while confirmation is pending
        'CONFIRMED',     -- established on evidence
        'REFUTED'        -- actively excluded; kept because knowing it was ruled out is clinical fact
    ));

COMMENT ON COLUMN pct_problems.diagnostic_certainty IS
    'How certain the recorder was. NULL means never stated and must not be read as CONFIRMED. '
    'REFUTED rows are retained deliberately — that a diagnosis was excluded is itself a finding, '
    'and re-investigating an excluded condition is a real cost.';

-- 3. Status -------------------------------------------------------------------
ALTER TABLE pct_problems
    ADD CONSTRAINT pct_problems_clinical_status_check CHECK (clinical_status IN (
        'ACTIVE',
        'RECURRENCE',   -- returned after resolution
        'RELAPSE',      -- returned after remission
        'REMISSION',    -- controlled but not cured, and expected back
        'INACTIVE',
        'RESOLVED'
    ));

ALTER TABLE pct_problems
    ADD COLUMN last_recurrence_at TIMESTAMPTZ;

COMMENT ON COLUMN pct_problems.last_recurrence_at IS
    'When this problem last returned. Set alongside a move to RECURRENCE or RELAPSE so that one '
    'disease stays one row rather than becoming several entries in the list.';

-- 4. The rest of the longitudinal record --------------------------------------
ALTER TABLE pct_problems
    ADD COLUMN evidence            TEXT,
    ADD COLUMN responsible_service VARCHAR(64),
    ADD COLUMN review_date         DATE;

COMMENT ON COLUMN pct_problems.evidence IS
    'What this problem rests on, in the recorder''s words. Structured evidence — the observation, '
    'the report — is linked in pct_problem_links rather than copied here.';
COMMENT ON COLUMN pct_problems.responsible_service IS
    'The service accountable for this problem. NULL means nobody has taken it, which on a '
    'multimorbidity view is a finding rather than a blank.';
COMMENT ON COLUMN pct_problems.review_date IS
    'When this problem is next due to be looked at. Drives the overdue-review signal; NULL means no '
    'review was scheduled, not that none is needed.';

-- Reading the list is always scoped by subject and usually by what is still live.
CREATE INDEX idx_pct_problems_review ON pct_problems (tenant_id, review_date)
    WHERE review_date IS NOT NULL;
