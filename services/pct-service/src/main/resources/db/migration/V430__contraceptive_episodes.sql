-- Contraceptive method use: episodes, and the acts that start, sustain and end them.
--
-- WHY V430 AND NOT V062. This lane holds a lease on pct V058-V069 and V062 was free. It is not used,
-- because the highest version applied on the preview estate is 401 and pct sets neither
-- `out-of-order: true` nor an equivalent override, while `validate-on-migrate: false` means a
-- migration that is skipped for being out of order neither applies nor fails: the service starts,
-- reports healthy, and the tables are simply absent. That is not a hypothetical — it happened to
-- V058/V059 of this same pack on 2026-07-26 and needed a cross-lane repair.
--
-- The estate does show a version-106 migration applying after 401, so out-of-order application is
-- evidently happening somewhere; the mechanism is not in the chart, the pod environment or the
-- service configuration, and an unexplained behaviour is not a foundation. The asymmetry decides it:
-- numbering above the current maximum costs nothing if the concern was unfounded, and using a low
-- number costs a silently absent schema if it was not. RMNP claims V430-V459 for everything after
-- this point; V062-V069 of the original lease is left as dead space deliberately.
--
-- Two tables, because a method is used over time and the acts that punctuate that use are separate
-- events. Collapsing them would make an injection given late indistinguishable from one given on
-- time, and there would be nowhere to record a removal that did not get everything out.

CREATE TABLE IF NOT EXISTS pct.pct_contraceptive_episodes (
    contraceptive_episode_id    UUID PRIMARY KEY,
    tenant_id                   UUID NOT NULL,
    subject_cpid                VARCHAR(64) NOT NULL,

    method_code                 VARCHAR(64) NOT NULL,
    method_class                VARCHAR(48),

    status                      VARCHAR(24) NOT NULL,
    started_on                  DATE NOT NULL,
    -- When the device or supply stops being effective. NULL for methods that do not expire.
    expected_end_on             DATE,
    ended_on                    DATE,

    discontinuation_reason      VARCHAR(48),
    discontinuation_detail      TEXT,

    -- What the eligibility assessment said at the time, and against which content version. Stored
    -- rather than recomputed: the criteria will change, and the question a reviewer asks later is
    -- what was known when the decision was made, not what the current matrix would now say.
    eligibility_category        SMALLINT,
    eligibility_recommendation  VARCHAR(32),
    eligibility_content_version VARCHAR(32),
    eligibility_assessed_at     TIMESTAMPTZ,
    eligibility_overridden      BOOLEAN NOT NULL DEFAULT FALSE,
    eligibility_override_reason TEXT,

    -- What was known about pregnancy at initiation, and what backup was advised and actually given.
    -- backup_supplied is separate from backup_days on purpose: advice that was given and stock that
    -- was not is the gap the initiation guidance exists to close, and it is invisible if the record
    -- only holds the advice.
    pregnancy_certainty         VARCHAR(24) NOT NULL,
    backup_advice_code          VARCHAR(64),
    backup_days                 SMALLINT,
    backup_supplied             VARCHAR(32),
    deferred_insertion_owed     BOOLEAN NOT NULL DEFAULT FALSE,

    -- CC-5: every clinical record resolves to a PCT anchor.
    journey_id                  UUID,
    encounter_ref               VARCHAR(128),
    facility_id                 UUID,

    recorded_by                 VARCHAR(128) NOT NULL,
    recorded_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by                  VARCHAR(128),
    updated_at                  TIMESTAMPTZ,
    client_offline_id           VARCHAR(128),

    CONSTRAINT chk_pct_contra_status CHECK (
        status IN ('PLANNED', 'ACTIVE', 'DISCONTINUED', 'COMPLETED', 'ENTERED_IN_ERROR')),

    -- A method that stopped must say when and why. "She is not on it any more" with no reason is the
    -- single most useful fact in family planning thrown away: whether she stopped because it did not
    -- suit her decides what to offer next.
    CONSTRAINT chk_pct_contra_discontinued_reason CHECK (
        status <> 'DISCONTINUED'
        OR (discontinuation_reason IS NOT NULL AND ended_on IS NOT NULL)),

    CONSTRAINT chk_pct_contra_ended_not_before_start CHECK (
        ended_on IS NULL OR ended_on >= started_on),

    CONSTRAINT chk_pct_contra_category_range CHECK (
        eligibility_category IS NULL OR eligibility_category BETWEEN 1 AND 4),

    -- Category 4 is an unacceptable health risk. Recording one without recording that a clinician
    -- knowingly overrode it, and why, would let the most consequential decision in the table happen
    -- with no accountable author.
    CONSTRAINT chk_pct_contra_category4_override CHECK (
        eligibility_category IS DISTINCT FROM 4
        OR (eligibility_overridden IS TRUE
            AND eligibility_override_reason IS NOT NULL
            AND length(btrim(eligibility_override_reason)) > 0)),

    CONSTRAINT chk_pct_contra_override_reason CHECK (
        eligibility_overridden IS FALSE OR eligibility_override_reason IS NOT NULL),

    CONSTRAINT chk_pct_contra_pregnancy_certainty CHECK (
        pregnancy_certainty IN ('ESTABLISHED', 'NOT_ESTABLISHED', 'CANNOT_DETERMINE')),

    CONSTRAINT chk_pct_contra_backup_days CHECK (
        backup_days IS NULL OR backup_days BETWEEN 0 AND 30)
);

-- One active episode per method, not one per woman. Dual method use is normal and often correct —
-- condoms alongside an implant is the recommended answer for a woman who needs pregnancy prevention
-- and STI protection, and a constraint forbidding it would force the second method to go unrecorded.
CREATE UNIQUE INDEX IF NOT EXISTS uq_pct_contra_active_method
    ON pct.pct_contraceptive_episodes (tenant_id, subject_cpid, method_code)
    WHERE status IN ('PLANNED', 'ACTIVE');

-- Store-and-forward idempotency, following the V050 precedent: a replayed offline packet must return
-- the existing episode rather than creating a second one.
CREATE UNIQUE INDEX IF NOT EXISTS uq_pct_contra_offline
    ON pct.pct_contraceptive_episodes (tenant_id, client_offline_id)
    WHERE client_offline_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_pct_contra_subject
    ON pct.pct_contraceptive_episodes (tenant_id, subject_cpid, status);

CREATE INDEX IF NOT EXISTS ix_pct_contra_expected_end
    ON pct.pct_contraceptive_episodes (tenant_id, expected_end_on)
    WHERE status = 'ACTIVE' AND expected_end_on IS NOT NULL;


CREATE TABLE IF NOT EXISTS pct.pct_contraceptive_administrations (
    administration_id           UUID PRIMARY KEY,
    contraceptive_episode_id    UUID NOT NULL
        REFERENCES pct.pct_contraceptive_episodes (contraceptive_episode_id),
    tenant_id                   UUID NOT NULL,
    subject_cpid                VARCHAR(64) NOT NULL,

    administration_type         VARCHAR(24) NOT NULL,
    administered_on             DATE NOT NULL,
    -- When the next act is due. The whole point of recording an injection is knowing when the next
    -- one is owed; a dose with no next-due date cannot produce a defaulter list.
    next_due_on                 DATE,

    -- Counts, for the methods where a device can be left behind. units_expected is what should have
    -- come out, units_removed is what did.
    units_placed                SMALLINT,
    units_expected              SMALLINT,
    units_removed               SMALLINT,
    removal_completeness        VARCHAR(16),
    retained_fragment           BOOLEAN NOT NULL DEFAULT FALSE,
    retained_fragment_detail    TEXT,

    site                        VARCHAR(64),
    lot_number                  VARCHAR(64),
    notes                       TEXT,

    journey_id                  UUID,
    encounter_ref               VARCHAR(128),
    facility_id                 UUID,

    recorded_by                 VARCHAR(128) NOT NULL,
    recorded_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    client_offline_id           VARCHAR(128),

    CONSTRAINT chk_pct_contra_admin_type CHECK (
        administration_type IN ('INSERTION', 'INJECTION', 'RESUPPLY', 'REMOVAL', 'REPLACEMENT')),

    CONSTRAINT chk_pct_contra_admin_completeness_values CHECK (
        removal_completeness IS NULL
        OR removal_completeness IN ('COMPLETE', 'PARTIAL', 'FAILED')),

    -- A removal has to say whether it worked.
    CONSTRAINT chk_pct_contra_admin_removal_states_outcome CHECK (
        administration_type NOT IN ('REMOVAL', 'REPLACEMENT')
        OR removal_completeness IS NOT NULL),

    -- THE RETAINED-ROD CASE. A two-rod implant where one rod came out and one did not must not be
    -- recordable as COMPLETE. Left as a free-text nuance it would be lost, and the woman would carry
    -- a rod nobody knows about while her record says the implant was removed — she would be counted
    -- as needing contraception and offered a method on top of one still in her arm.
    CONSTRAINT chk_pct_contra_admin_complete_means_all_out CHECK (
        removal_completeness IS DISTINCT FROM 'COMPLETE'
        OR (retained_fragment IS FALSE
            AND units_expected IS NOT NULL
            AND units_removed IS NOT NULL
            AND units_removed = units_expected)),

    -- And the converse: something left behind is never a complete removal, and has to be described.
    CONSTRAINT chk_pct_contra_admin_retained_described CHECK (
        retained_fragment IS FALSE
        OR (removal_completeness IN ('PARTIAL', 'FAILED')
            AND retained_fragment_detail IS NOT NULL)),

    CONSTRAINT chk_pct_contra_admin_counts_non_negative CHECK (
        (units_placed IS NULL OR units_placed >= 0)
        AND (units_expected IS NULL OR units_expected >= 0)
        AND (units_removed IS NULL OR units_removed >= 0)),

    CONSTRAINT chk_pct_contra_admin_not_over_removed CHECK (
        units_removed IS NULL OR units_expected IS NULL OR units_removed <= units_expected)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_pct_contra_admin_offline
    ON pct.pct_contraceptive_administrations (tenant_id, client_offline_id)
    WHERE client_offline_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_pct_contra_admin_episode
    ON pct.pct_contraceptive_administrations (contraceptive_episode_id, administered_on);

-- The defaulter list: everything owed and not yet given.
CREATE INDEX IF NOT EXISTS ix_pct_contra_admin_next_due
    ON pct.pct_contraceptive_administrations (tenant_id, next_due_on)
    WHERE next_due_on IS NOT NULL;

COMMENT ON TABLE pct.pct_contraceptive_episodes IS
    'A woman''s use of one contraceptive method over time. Dual method use is permitted by design.';
COMMENT ON TABLE pct.pct_contraceptive_administrations IS
    'Insertions, injections, resupplies and removals. A removal must state whether everything came out.';
