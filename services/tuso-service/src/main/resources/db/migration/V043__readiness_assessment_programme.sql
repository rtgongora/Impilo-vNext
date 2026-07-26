-- =====================================================================================
-- tuso :: V043 — the readiness assessment programme: rounds, assignments, and a cycle
--                driven by decay rather than by convenience
--
-- V042 gave readiness a shape. It did not give it a source: 904 maternity-capable facilities,
-- 0 ever assessed. This is the machinery that gets observations recorded — who assesses, on what
-- cycle, with what authority — and it is governed rather than ad hoc, because an assessment is a
-- claim about a facility's clinical readiness that feeds referral decisions.
--
-- THE CONSTRAINT THAT DRIVES THE WHOLE DESIGN.
--
-- Signal functions expire. The WHO recall period is three months: a facility "provides" a function
-- if it performed it at least once in the last three months. That is not a policy we chose and it
-- is not negotiable by scheduling convenience. It has a consequence most assessment programmes get
-- wrong:
--
--     an ANNUAL assessment cycle leaves every facility STALE for nine months of every year.
--
-- A yearly survey therefore does not produce a current readiness register; it produces a register
-- that is correct for one quarter and misleading for three. So a round declares its own cadence,
-- facilities become DUE *before* their data expires rather than after, and the programme's headline
-- metric is CURRENCY (how much of the network is current right now), not COVERAGE (how much has
-- ever been looked at). Coverage flatters; currency is the number a referral network actually
-- depends on.
--
-- WHAT AN ASSIGNMENT IS NOT.
--
-- An assignment is a request for someone to go and look. It is not an observation, it does not
-- change readiness, and closing a round does not resolve the facilities nobody reached. An
-- unvisited facility stays UNKNOWN — the same rule as V042, applied to the programme that feeds it:
-- absence of evidence is not evidence of absence, and a completed round with 40% coverage means
-- 60% of the network is still unknown, not 60% unequipped.
-- =====================================================================================

-- ---- A governed round of assessment ----------------------------------------------------

CREATE TABLE IF NOT EXISTS tuso.readiness_assessment_round (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            UUID NOT NULL,
    code                 VARCHAR(64) NOT NULL,
    name                 VARCHAR(255) NOT NULL,

    -- Who is doing the looking, and therefore how much the finding is worth. A self-report and a
    -- supervisory visit are both legitimate and are NOT interchangeable; the method is declared by
    -- the round and travels with every assessment it produces, all the way to the reader.
    method               VARCHAR(32) NOT NULL,
    owning_authority     VARCHAR(160) NOT NULL,

    -- Scope. NULL province means national. NULL facility_type means all maternity-capable.
    scope_province       VARCHAR(128),
    scope_facility_type  VARCHAR(100),

    opens_on             DATE NOT NULL,
    closes_on            DATE NOT NULL,

    -- The recall window this round asks about, stamped onto every assessment it produces so a round
    -- run to a different standard stays interpretable years later.
    recall_window_months INT NOT NULL DEFAULT 3,

    status               VARCHAR(24) NOT NULL DEFAULT 'PLANNED',
    created_by           VARCHAR(255) NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    closed_at            TIMESTAMPTZ,
    closure_note         TEXT,

    CONSTRAINT uq_round_code UNIQUE (tenant_id, code),
    CONSTRAINT chk_round_status CHECK (status IN ('PLANNED', 'OPEN', 'CLOSED', 'ABANDONED')),
    CONSTRAINT chk_round_method CHECK (method IN
        ('SELF_REPORTED', 'SUPERVISORY_VISIT', 'INSPECTION', 'FACILITY_SURVEY', 'REGULATOR_RETURN')),
    CONSTRAINT chk_round_window CHECK (closes_on >= opens_on),
    CONSTRAINT chk_round_recall CHECK (recall_window_months BETWEEN 1 AND 24),

    -- A round longer than the thing it measures cannot produce a current register: the facilities
    -- assessed in month one have expired before the facilities assessed in month six are visited.
    -- Refused at the schema, because it is the failure that makes an assessment programme look
    -- productive while producing nothing usable.
    CONSTRAINT chk_round_not_longer_than_recall
        CHECK (closes_on - opens_on <= recall_window_months * 31)
);

CREATE INDEX IF NOT EXISTS idx_round_status ON tuso.readiness_assessment_round (tenant_id, status);

-- ---- Who is asked to look at what -------------------------------------------------------

CREATE TABLE IF NOT EXISTS tuso.readiness_assessment_assignment (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID NOT NULL,
    round_id          UUID NOT NULL REFERENCES tuso.readiness_assessment_round(id),
    facility_id       BIGINT NOT NULL REFERENCES tuso.facility(id),

    assigned_to       VARCHAR(255),
    assigned_role     VARCHAR(64),
    due_on            DATE NOT NULL,

    -- PENDING → IN_PROGRESS → COMPLETED, or UNREACHABLE / DECLINED.
    -- UNREACHABLE is a real and important outcome: a facility nobody could reach is a fact about
    -- the referral network worth recording, and it must not be silently indistinguishable from a
    -- facility nobody got round to.
    status            VARCHAR(24) NOT NULL DEFAULT 'PENDING',

    -- Set only when an actual V042 assessment was recorded. The link is what makes "completed"
    -- verifiable rather than self-declared.
    assessment_id     UUID REFERENCES tuso.facility_readiness_assessment(id),
    outcome_note      TEXT,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_assignment_round_facility UNIQUE (round_id, facility_id),
    CONSTRAINT chk_assignment_status CHECK (status IN
        ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'UNREACHABLE', 'DECLINED')),

    -- COMPLETED means an observation exists. Without this, a round could report itself finished
    -- while having recorded nothing — the assignment status would become a claim about readiness
    -- that no assessment backs.
    CONSTRAINT chk_completed_requires_assessment
        CHECK (status <> 'COMPLETED' OR assessment_id IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_assignment_round_status
    ON tuso.readiness_assessment_assignment (round_id, status);
CREATE INDEX IF NOT EXISTS idx_assignment_assignee
    ON tuso.readiness_assessment_assignment (tenant_id, assigned_to, status);
CREATE INDEX IF NOT EXISTS idx_assignment_facility
    ON tuso.readiness_assessment_assignment (tenant_id, facility_id);

-- ---- Supporting index for the currency query -------------------------------------------
-- "Which facilities have CURRENT readiness data" is the programme's headline number, so the
-- latest-assessment-per-facility lookup needs to be cheap.

CREATE INDEX IF NOT EXISTS idx_readiness_assessment_recent
    ON tuso.facility_readiness_assessment (tenant_id, facility_id, assessed_at DESC);

-- NOTE deliberately absent:
--   * no auto-close. A round that reaches its end date stays OPEN until a person closes it with a
--     note, because "the window elapsed" and "the work finished" are different facts and only a
--     person can say which happened.
--   * no status on the facility. Readiness lives in V042 and is derived from observations; a
--     programme-level flag on tuso.facility would become a second, staler answer to the same
--     question.
