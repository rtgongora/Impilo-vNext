-- =============================================================================================
-- V113 — the examination framework (brief.md §7)
--
-- "Implement reusable structured examinations for" twenty-two regions, supporting six states, with
-- graphics for eleven diagrams.
--
-- Nothing in this estate owned a general examination record. What existed was specific and shaped
-- to its own domain: the ED trauma survey (V039) and a `newborn_examination JSONB` blob inside the
-- birth record (V055). The second is precisely what a reusable framework replaces — an examination
-- stored as unstructured JSON cannot be asked "when was this patient's abdomen last examined", and
-- that question is the reason the framework exists.
--
-- THE SIX STATES ARE THE POINT OF THIS MIGRATION
--
-- An examination framework with only "normal" and "abnormal" forces every unperformed examination
-- into one of them, and the one it lands in is "normal", because that is the harmless-looking
-- default. The distinction this table enforces:
--
--   NORMAL            examined, and it was normal
--   ABNORMAL          examined, and it was not
--   NOT_EXAMINED      nobody looked
--   UNABLE_TO_EXAMINE looked, and could not (patient unconscious, in pain, contracture, no chaperone)
--   DEFERRED          deliberately postponed to a named later point
--   UNCERTAIN         examined, and the examiner is not sure what they found
--
-- The first and third are the pair that matters. "The chest was clear" and "nobody listened to the
-- chest" are different clinical facts and they read identically on a form that cannot tell them
-- apart. The third and fourth also differ, and the difference is actionable: NOT_EXAMINED can be
-- resolved by examining, UNABLE_TO_EXAMINE needs something to change first.
-- =============================================================================================

CREATE TABLE pct_examinations (
    examination_id   UUID            PRIMARY KEY,
    tenant_id        UUID            NOT NULL,
    subject_cpid     VARCHAR(64)     NOT NULL,

    -- The PCT anchor required by care-continuum-doctrine CC-5: a clinical record with no resolvable
    -- anchor is a record nobody can place in the patient's journey.
    journey_id       UUID,
    encounter_id     UUID,

    examined_at      TIMESTAMPTZ     NOT NULL,
    examined_by      VARCHAR(255)    NOT NULL,
    facility_id      VARCHAR(64),

    -- Why this examination was performed, in the examiner's words. Free text on purpose: a closed
    -- vocabulary here would be guessing at a clinical intent nobody has catalogued.
    context_note     TEXT,

    client_offline_id VARCHAR(128),
    created_at       TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT pct_examinations_anchor_check CHECK (
        journey_id IS NOT NULL OR encounter_id IS NOT NULL
    )
);

COMMENT ON CONSTRAINT pct_examinations_anchor_check ON pct_examinations IS
    'care-continuum-doctrine CC-5: a clinical record must carry a resolvable PCT anchor. An '
    'examination floating free of a journey or encounter cannot be placed in the patient timeline.';

CREATE INDEX idx_pct_examinations_subject ON pct_examinations (tenant_id, subject_cpid, examined_at DESC);
CREATE INDEX idx_pct_examinations_journey ON pct_examinations (tenant_id, journey_id) WHERE journey_id IS NOT NULL;
CREATE UNIQUE INDEX uq_pct_examinations_offline
    ON pct_examinations (tenant_id, client_offline_id) WHERE client_offline_id IS NOT NULL;

-- ---------------------------------------------------------------------------------------------
-- One row per region examined, or deliberately not examined.
--
-- A region absent from this table means it was never considered at all — which is different again
-- from NOT_EXAMINED, where the examiner looked at the list and decided not to. Both are honest; the
-- read side must not merge them.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE pct_examination_findings (
    finding_id       UUID            PRIMARY KEY,
    tenant_id        UUID            NOT NULL,
    examination_id   UUID            NOT NULL REFERENCES pct_examinations(examination_id) ON DELETE CASCADE,

    region           VARCHAR(48)     NOT NULL,
    state            VARCHAR(24)     NOT NULL,

    -- What was found. Required when the state is ABNORMAL or UNCERTAIN (see the CHECK below), and
    -- required when the state is UNABLE_TO_EXAMINE or DEFERRED, where it carries the reason.
    detail           TEXT,

    -- Optional coded finding from the governed examination content pack, where one applies.
    finding_code     VARCHAR(64),

    -- Which diagram this finding was placed on, and where. Nullable: most findings are not sited.
    graphic          VARCHAR(48),
    site             VARCHAR(64),
    laterality       VARCHAR(16),

    recorded_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT pct_examination_findings_region_check CHECK (region IN (
        'GENERAL_CONDITION', 'VITAL_SIGNS', 'ANTHROPOMETRY', 'HYDRATION', 'PALLOR', 'JAUNDICE',
        'CYANOSIS', 'OEDEMA', 'LYMPH_NODES', 'CARDIOVASCULAR', 'RESPIRATORY', 'ABDOMEN',
        'NEUROLOGY', 'MUSCULOSKELETAL', 'SKIN', 'ENDOCRINE', 'PERIPHERAL_VASCULAR', 'FEET',
        'EYES', 'FUNCTIONAL_STATUS', 'COGNITION', 'FRAILTY'
    )),

    CONSTRAINT pct_examination_findings_state_check CHECK (state IN (
        'NORMAL',
        'ABNORMAL',
        'NOT_EXAMINED',
        'UNABLE_TO_EXAMINE',
        'DEFERRED',
        'UNCERTAIN'
    )),

    -- An abnormality with nothing written down is indistinguishable from a normal examination by the
    -- next reader, and worse than one, because it looks as though somebody documented something.
    -- The same applies to UNCERTAIN: "I am not sure" is only useful with what it is about.
    -- UNABLE_TO_EXAMINE and DEFERRED require their reason for a different purpose: the next examiner
    -- needs to know what has to change before the examination can happen.
    CONSTRAINT pct_examination_findings_detail_check CHECK (
        state IN ('NORMAL', 'NOT_EXAMINED')
        OR (detail IS NOT NULL AND length(btrim(detail)) > 0)
    ),

    CONSTRAINT pct_examination_findings_graphic_check CHECK (graphic IS NULL OR graphic IN (
        'CHEST_AUSCULTATION', 'CARDIAC_FINDINGS', 'ABDOMINAL_REGIONS', 'NEUROLOGICAL_DEFICITS',
        'DERMATOMES', 'PERIPHERAL_PULSES', 'OEDEMA_SITES', 'JOINTS', 'SKIN_LESIONS',
        'DIABETIC_FOOT', 'PRESSURE_INJURIES'
    )),

    CONSTRAINT pct_examination_findings_laterality_check CHECK (
        laterality IS NULL OR laterality IN ('LEFT', 'RIGHT', 'BILATERAL', 'MIDLINE')
    ),

    -- A site pin belongs to a diagram. A site with no graphic cannot be drawn and cannot be read
    -- back — it is a coordinate with no map.
    CONSTRAINT pct_examination_findings_site_needs_graphic_check CHECK (
        site IS NULL OR graphic IS NOT NULL
    )
);

COMMENT ON CONSTRAINT pct_examination_findings_state_check ON pct_examination_findings IS
    'The six states of brief.md §7. NORMAL and NOT_EXAMINED are the pair this vocabulary exists for: '
    '"the chest was clear" and "nobody listened to the chest" are different clinical facts and a '
    'two-state form renders them identically, always as the reassuring one. Kept in lockstep with '
    'ExaminationVocabulary by ExaminationVocabularyTest.';

COMMENT ON CONSTRAINT pct_examination_findings_detail_check ON pct_examination_findings IS
    'ABNORMAL and UNCERTAIN must say what was found; UNABLE_TO_EXAMINE and DEFERRED must say why, so '
    'the next examiner knows what has to change first. Only NORMAL and NOT_EXAMINED stand alone.';

-- One row per region per examination: recording the abdomen twice in one examination is a data
-- error, and the second row would silently win on any read that takes the latest.
CREATE UNIQUE INDEX uq_pct_examination_findings_region
    ON pct_examination_findings (examination_id, region);

CREATE INDEX idx_pct_examination_findings_region_state
    ON pct_examination_findings (tenant_id, region, state);

COMMENT ON TABLE pct_examinations IS
    'A structured physical examination event (brief.md §7). Reusable across every care setting; the '
    'specialty packs configure which regions they ask for rather than each defining its own record.';
