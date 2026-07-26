-- The pregnancy episode: the longitudinal spine every maternal record hangs from.
--
-- Until now this platform could record a labour (V056) and a birth (V055) but had nowhere to
-- put the pregnancy those belonged to. The practical consequences were visible from several
-- directions at once: PatientFacts.pregnant was supplied by whoever called the form resolver
-- and derived from nothing, so the ANC form's requirePregnant applicability gate was
-- unbacked; a partograph could not say which pregnancy it charted; and a normal vaginal
-- delivery outside theatre had nowhere at all to be recorded.
--
-- DOCTRINE. This is a person-level longitudinal clinical registry, which care-continuum-doctrine
-- CC-1/CC-2 places in pct-service beside growth, immunisations and newborn records. It is
-- deliberately called an EPISODE and never a journey: CC-0 reserves "journey" for a single
-- facility visit, and a pregnancy spans many. It carries opening_journey_id and
-- opening_encounter_id to satisfy the CC-5 anchoring rule — the episode outlives that visit,
-- but it must resolve to the visit at which it was first recorded.
--
-- DATING IS STAMPED HERE AND CONSUMED ELSEWHERE. pct-service writes the EDD and its basis;
-- the rules engine reads the stored gestational age and cannot recompute it. This is the same
-- boundary that keeps a stored growth z-score and its interpretation from drifting apart. An
-- engine that can re-derive an EDD will eventually disagree with the chart about how pregnant
-- a woman is.
--
-- initial_pregnancy_start_date is immutable and pregnancy_start_date is not. The first is what
-- the booking-week uniqueness key is computed from, so that a later redating cannot move a
-- pregnancy out from under its own duplicate check.
--
-- THREE LAYERS OF DUPLICATE PREVENTION, because they catch different failures:
--   1. One ONGOING episode per person. A woman cannot be pregnant twice at once. Heterotopic
--      pregnancy is ONE episode with two fetus rows, not two episodes.
--   2. client_offline_id unique per tenant — the V050 store-and-forward precedent. A replayed
--      offline packet returns the existing episode rather than minting a second.
--   3. Immutable booking week. Two DIFFERENT devices, both offline, both booking the same
--      woman with different offline ids: neither (1) nor (2) catches that until sync, and at
--      sync (1) throws. The booking week makes the two packets collide deterministically on a
--      natural key instead.
--
-- The service must translate those collisions rather than let them surface as a 500: an
-- offline-id collision is an idempotent replay and returns 200 with the existing episode; the
-- other two return 409 naming the existing episode and raise a reconciliation task. A 500 here
-- loses a booking captured in a clinic with no network, which is the case this whole mechanism
-- exists to serve.

CREATE TABLE pct.pct_pregnancy_episodes (
    pregnancy_episode_id      UUID PRIMARY KEY,
    tenant_id                 UUID NOT NULL,
    subject_cpid              VARCHAR(64) NOT NULL,   -- the pregnant person
    facility_id               UUID,

    -- CC-5 anchor. Nullable only because a pregnancy may be first recorded from a community
    -- contact that has no encounter; the service supplies them whenever a visit exists.
    opening_journey_id        VARCHAR(64),
    opening_encounter_id      VARCHAR(64),

    status                    VARCHAR(32) NOT NULL DEFAULT 'ONGOING',
    -- ONGOING | COMPLETED | LOST | TERMINATED | TRANSFERRED_OUT | RESOLVED_NOT_PREGNANT
    -- | ENTERED_IN_ERROR
    confirmed_on              DATE,
    confirmation_basis        VARCHAR(32) NOT NULL DEFAULT 'NOT_CONFIRMED',
    -- URINE_HCG | SERUM_HCG | ULTRASOUND | CLINICAL | SELF_REPORTED | NOT_CONFIRMED

    -- ── dating ────────────────────────────────────────────────────────────────────────────
    -- pregnancy_start_date is the notional LMP the EDD implies, whether or not one was
    -- recorded. Every downstream gestational age is computed from it, so a chart and a
    -- schedule engine cannot disagree.
    pregnancy_start_date         DATE NOT NULL,
    initial_pregnancy_start_date DATE NOT NULL,
    estimated_delivery_date   DATE,
    dating_method             VARCHAR(48),
    dating_confidence         VARCHAR(24) NOT NULL DEFAULT 'UNKNOWN',
    -- EXACT | HIGH | MODERATE | LOW | UNKNOWN
    dating_plus_minus_days    INTEGER,
    dated_on                  DATE,
    dating_content_version    VARCHAR(64),
    dating_revision_count     INTEGER NOT NULL DEFAULT 0,

    -- Immutable natural key for offline duplicate detection. Whole weeks since the epoch,
    -- computed from the FIRST dating so a later revision cannot move it. Arithmetic only,
    -- which is what makes it a legal generated column.
    booking_week              INTEGER GENERATED ALWAYS AS
                                  ((initial_pregnancy_start_date - DATE '1970-01-01') / 7) STORED,

    -- ── obstetric history: derived AND recorded, never silently reconciled ─────────────────
    gravida_derived           INTEGER,
    para_derived              INTEGER,
    abortus_derived           INTEGER,
    term_derived              INTEGER,
    preterm_derived           INTEGER,
    living_children_derived   INTEGER,
    gp_derivation_complete    BOOLEAN NOT NULL DEFAULT FALSE,
    gp_uncountable_count      INTEGER NOT NULL DEFAULT 0,
    -- What she says. A woman is usually the only person present for all of her pregnancies,
    -- and pregnancies cared for elsewhere are the usual explanation for a discrepancy.
    gravida_recorded          INTEGER,
    para_recorded             INTEGER,
    gp_discrepancy            BOOLEAN NOT NULL DEFAULT FALSE,
    gp_content_version        VARCHAR(64),

    -- ── plurality ─────────────────────────────────────────────────────────────────────────
    plurality                 INTEGER NOT NULL DEFAULT 1,
    plurality_basis           VARCHAR(24) NOT NULL DEFAULT 'UNKNOWN',
    -- ULTRASOUND | CLINICAL | AT_DELIVERY | UNKNOWN
    chorionicity              VARCHAR(40),
    -- DICHORIONIC_DIAMNIOTIC | MONOCHORIONIC_DIAMNIOTIC | MONOCHORIONIC_MONOAMNIOTIC
    -- | UNDETERMINED | NOT_APPLICABLE
    chorionicity_determined_on DATE,

    -- ── continuity ────────────────────────────────────────────────────────────────────────
    previous_pregnancy_episode_id  UUID REFERENCES pct.pct_pregnancy_episodes(pregnancy_episode_id),
    interpregnancy_interval_months INTEGER,
    interval_adequacy         VARCHAR(24) NOT NULL DEFAULT 'UNKNOWN',
    -- SHORT | ADEQUATE | LONG | UNKNOWN

    intended_pregnancy        VARCHAR(32) NOT NULL DEFAULT 'NOT_ASSESSED',
    -- INTENDED | MISTIMED | UNWANTED | NOT_ASSESSED | DECLINED_TO_STATE
    conception_mode           VARCHAR(32) NOT NULL DEFAULT 'SPONTANEOUS',
    -- SPONTANEOUS | OVULATION_INDUCTION | IUI | IVF | ICSI | UNKNOWN

    -- ── risk and plan ─────────────────────────────────────────────────────────────────────
    risk_status               VARCHAR(24) NOT NULL DEFAULT 'NOT_ASSESSED',
    -- LOW | HIGH | NOT_ASSESSED
    risk_factors              JSONB,
    risk_content_version      VARCHAR(64),
    birth_plan                JSONB,
    planned_place_of_birth    VARCHAR(32),
    hiv_status_at_booking     VARCHAR(24) NOT NULL DEFAULT 'UNKNOWN',
    -- POSITIVE_KNOWN | POSITIVE_NEW | NEGATIVE | DECLINED | UNKNOWN
    on_art                    VARCHAR(16) NOT NULL DEFAULT 'NOT_ASSESSED',

    -- ── outcome ───────────────────────────────────────────────────────────────────────────
    outcome                   VARCHAR(32),
    -- LIVE_BIRTH | STILLBIRTH | MIXED_OUTCOME | MISCARRIAGE | ECTOPIC | MOLAR | TERMINATION
    -- | UNKNOWN
    ended_on                  DATE,
    gestational_age_days_at_end INTEGER,
    birth_maturity_band       VARCHAR(32),
    outcome_recorded_by       VARCHAR(128),

    confidentiality           VARCHAR(32) NOT NULL DEFAULT 'ROUTINE',
    -- ROUTINE | SPECIALLY_PROTECTED

    -- ── offline store-and-forward (V050 precedent) ────────────────────────────────────────
    client_offline_id         VARCHAR(128),
    package_checksum          VARCHAR(128),
    captured_at               TIMESTAMPTZ,

    recorded_by               VARCHAR(128) NOT NULL,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_pct_preg_plurality CHECK (plurality BETWEEN 1 AND 8),
    CONSTRAINT chk_pct_preg_ended CHECK (
        status IN ('ONGOING', 'ENTERED_IN_ERROR') OR ended_on IS NOT NULL),
    CONSTRAINT chk_pct_preg_outcome_when_ended CHECK (ended_on IS NULL OR outcome IS NOT NULL),
    CONSTRAINT chk_pct_preg_edd CHECK (
        estimated_delivery_date IS NULL OR estimated_delivery_date > pregnancy_start_date),

    -- Chorionicity is a multiple-pregnancy question. Asserted on a singleton it later reads as
    -- a missed monochorionic pregnancy, which is the highest-risk twin configuration there is.
    CONSTRAINT chk_pct_preg_chorionicity CHECK (
        plurality > 1 OR chorionicity IS NULL OR chorionicity = 'NOT_APPLICABLE')
);

-- 1. A woman cannot be pregnant twice at once.
CREATE UNIQUE INDEX uq_pct_pregnancy_ongoing
    ON pct.pct_pregnancy_episodes(tenant_id, subject_cpid)
    WHERE status = 'ONGOING';

-- 2. Idempotent replay of an offline packet.
CREATE UNIQUE INDEX uq_pct_pregnancy_offline
    ON pct.pct_pregnancy_episodes(tenant_id, client_offline_id)
    WHERE client_offline_id IS NOT NULL;

-- 3. Two devices, two offline ids, one woman, one pregnancy.
CREATE UNIQUE INDEX uq_pct_pregnancy_booking_week
    ON pct.pct_pregnancy_episodes(tenant_id, subject_cpid, booking_week)
    WHERE status <> 'ENTERED_IN_ERROR';

CREATE INDEX idx_pct_pregnancy_subject
    ON pct.pct_pregnancy_episodes(tenant_id, subject_cpid, pregnancy_start_date DESC);
CREATE INDEX idx_pct_pregnancy_edd
    ON pct.pct_pregnancy_episodes(tenant_id, estimated_delivery_date)
    WHERE status = 'ONGOING';

COMMENT ON TABLE pct.pct_pregnancy_episodes IS
    'Longitudinal pregnancy episode. PCT-owned person-level clinical registry (CC-1). '
    'Deliberately an episode, not a journey (CC-0): a pregnancy spans many facility visits.';

-- ──────────────────────────────────────────────────────────────────────────────────────────
-- Dating revisions.
--
-- An estimated delivery date decides what counts as preterm, when a woman is post-term, which
-- antenatal contacts are due, and whether a growth measurement is reassuring. An EDD that moves
-- without anyone deciding it should is therefore one of the more dangerous silent changes this
-- record can contain.
--
-- redating_applied = FALSE is the row that matters most. It records a basis that was CONSIDERED
-- AND REJECTED — "we saw the 26-week scan and kept the LMP dating" — which is clinical
-- information, and which nothing in this estate could express before. Without it the next
-- clinician sees a scan that appears to have been ignored.
--
-- rationale is NOT NULL for the same reason: a revision that cannot say why is not reviewable.

CREATE TABLE pct.pct_pregnancy_dating_revisions (
    revision_id             UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    pregnancy_episode_id    UUID NOT NULL
                                REFERENCES pct.pct_pregnancy_episodes(pregnancy_episode_id),
    subject_cpid            VARCHAR(64) NOT NULL,
    revision_number         INTEGER NOT NULL,       -- 1 = the original dating

    dating_method           VARCHAR(48) NOT NULL,
    basis_observed_on       DATE,
    last_menstrual_period   DATE,
    lmp_certainty           VARCHAR(24),            -- CERTAIN | UNCERTAIN | UNKNOWN
    measured_ga_days        INTEGER,
    conception_date         DATE,
    embryo_age_days_at_transfer INTEGER,
    ultrasound_ref          VARCHAR(128),

    estimated_delivery_date DATE NOT NULL,
    dating_confidence       VARCHAR(24) NOT NULL,
    plus_minus_days         INTEGER,

    superseded_method       VARCHAR(48),
    superseded_edd          DATE,
    discrepancy_days        INTEGER,
    redating_tolerance_days INTEGER,
    redating_applied        BOOLEAN NOT NULL DEFAULT FALSE,
    rationale               TEXT NOT NULL,
    content_version         VARCHAR(64) NOT NULL,

    journey_id              VARCHAR(64),
    encounter_id            VARCHAR(64),
    recorded_by             VARCHAR(128) NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_pct_dating_revision UNIQUE (pregnancy_episode_id, revision_number)
);

CREATE INDEX idx_pct_dating_episode
    ON pct.pct_pregnancy_dating_revisions(pregnancy_episode_id, revision_number DESC);

COMMENT ON COLUMN pct.pct_pregnancy_dating_revisions.redating_applied IS
    'FALSE records a basis considered and rejected. "We saw the scan and kept the LMP dating" '
    'is clinical information; without it the scan appears to have been ignored.';
