-- Paediatric clinical rules framework.
--
-- Extends rule_definitions with the governance and applicability metadata a paediatric rule
-- needs, and adds a structured logic column so a rule's condition is inspectable data rather
-- than an opaque expression. The point is that a change to a clinical threshold becomes a
-- reviewable content release rather than a code deployment, and that any rule can be shown
-- to a clinician with its source, its version and the evidence it fired on.
--
-- Every column is additive and nullable, so existing rules and the Java rules engine are
-- untouched.

ALTER TABLE clinical.rule_definitions
    -- Which of the decision-support layers this rule belongs to. Separating them matters
    -- because they behave differently: a danger sign interrupts, a validation rule blocks,
    -- a monitoring rule schedules.
    ADD COLUMN layer                 VARCHAR(32),
    -- DATA_VALIDATION | DANGER_SIGN | CLASSIFICATION | THERAPY | MONITORING | FOLLOW_UP

    -- Applicability as queryable data rather than prose. Age is in days because paediatric
    -- rules change within days of birth and months cannot express that.
    ADD COLUMN age_min_days          INTEGER,
    ADD COLUMN age_max_days          INTEGER,
    ADD COLUMN sex_applicability     VARCHAR(16),
    ADD COLUMN facility_levels_json  JSONB,
    ADD COLUMN cadres_json           JSONB,

    -- Governance. A recommendation without a traceable authority and version is not
    -- something a clinician can be asked to act on.
    ADD COLUMN adaptation_authority  VARCHAR(255),
    ADD COLUMN approval_status       VARCHAR(32) NOT NULL DEFAULT 'ENGINEERING_SEED',
    -- ENGINEERING_SEED | UNDER_REVIEW | APPROVED | RETIRED
    ADD COLUMN approval_history_json JSONB,
    ADD COLUMN review_date           DATE,

    -- Structured logic: a predicate tree over named inputs, validated on load. Deliberately
    -- not a scripting language — content must be inspectable and must not be able to do
    -- anything the evaluator does not explicitly allow.
    ADD COLUMN logic_json            JSONB,
    ADD COLUMN required_inputs_json  JSONB,
    ADD COLUMN exclusions_json       JSONB,

    -- What the clinician must do, separate from the message explaining why. An alert that
    -- states a finding without stating the action is a notification, not decision support.
    ADD COLUMN required_action       TEXT,
    ADD COLUMN referral_required     BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN monitoring_instruction TEXT,

    -- Test cases travel with the rule. A rule whose own fixtures do not pass cannot ship.
    ADD COLUMN test_cases_json       JSONB;

CREATE INDEX idx_rd_layer_age ON clinical.rule_definitions (layer, age_min_days, age_max_days);
CREATE INDEX idx_rd_approval ON clinical.rule_definitions (approval_status);

COMMENT ON COLUMN clinical.rule_definitions.approval_status IS
    'ENGINEERING_SEED content is implemented and testable but NOT ratified; it must be reviewed by the named adaptation authority before it drives care. See docs/clinical-governance/edliz-engineering-seed-policy.md.';
COMMENT ON COLUMN clinical.rule_definitions.logic_json IS
    'Structured predicate tree evaluated by the tabular rule evaluator. Data, not script: the evaluator supports a closed set of operators and rejects anything else.';
