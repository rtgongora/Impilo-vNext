-- National policy parameters: governed numbers that are policy, not engineering.
--
-- WHY. The RMNP confidentiality stamper needs an age: below which age is a young person's sexual and
-- reproductive health record confidential from her guardian? That number is a legal determination,
-- not an engineering one. Zimbabwean consent ages are NOT uniform across services — independent
-- consent for HIV testing, for contraception and for mental health care are governed by different
-- instruments amended at different times — which is exactly why it cannot be a Java constant, a
-- config default, or a value someone guesses once and nobody revisits.
--
-- The tshepo-authz adolescent-confidentiality pack already says where this belongs. Its own
-- `consumers` block reads: "guardianConfidentialityRules — The clinical system of record at STAMP
-- time, not the PDP. Deciding whether a record is confidential from a guardian needs the patient's
-- age, which the PDP does not have — it sees a subject identifier, never a date of birth." CKP is
-- where governed clinical knowledge lives, so the parameter lives here and pct reads it.
--
-- WHAT MAKES THIS DIFFERENT FROM A CONFIG TABLE. Two constraints:
--
-- 1. chk_npp_ratified_is_verified — a RATIFIED parameter must be VERIFIED. The seed row below is
--    ENGINEERING_SEED/UNVERIFIED, so it is STRUCTURALLY UN-RATIFIABLE until a human verifies it
--    against the instrument named in legal_basis. A number that governs whether a teenager's record
--    is hidden from her parents must not become authoritative because someone flipped a status.
--
-- 2. The effective window is explicit and open-ended-by-default, so a past decision stays
--    re-explainable: a stamp applied in 2026 can be re-read against the parameter version that was
--    in force in 2026, not against whatever the value is when someone asks.
--
-- THE SEED IS A GUESS, AND SAYS SO. 18 is the pack's placeholder, carried here with
-- verification_status UNVERIFIED and the legal instruments to check named in legal_basis. Moving it
-- from a JSON null into a governed row with an un-ratifiable status does not make 18 correct; it
-- makes the guess visible, dated, attributable and hard to promote by accident.
--
-- RMNP holds CKP V041-V050. Head was V006 (then V200/V201 from other bands); out-of-order is on for
-- CKP, so a V041 arriving after V201 still applies.

CREATE TABLE IF NOT EXISTS clinical.national_policy_parameters (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    parameter_code      VARCHAR(128) NOT NULL,
    domain              VARCHAR(64)  NOT NULL,

    value_type          VARCHAR(24)  NOT NULL,
    value_text          TEXT         NOT NULL,
    unit                VARCHAR(32),

    effective_start     DATE         NOT NULL,
    effective_end       DATE,
    version             INTEGER      NOT NULL DEFAULT 1,

    approval_status     VARCHAR(32)  NOT NULL,
    verification_status VARCHAR(24)  NOT NULL,
    -- The instrument that would settle this. Not a citation of where the number came from — a
    -- pointer to what a human must read to make it real.
    legal_basis         TEXT,
    source_refs_json    JSONB        NOT NULL DEFAULT '[]'::jsonb,
    content_version     VARCHAR(64)  NOT NULL,

    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_npp_code_effective UNIQUE (parameter_code, effective_start),

    CONSTRAINT chk_npp_value_type CHECK (
        value_type IN ('INTEGER', 'DECIMAL', 'BOOLEAN', 'STRING')),

    CONSTRAINT chk_npp_approval CHECK (
        approval_status IN ('ENGINEERING_SEED', 'RATIFIED', 'WITHDRAWN')),

    CONSTRAINT chk_npp_verification CHECK (
        verification_status IN ('UNVERIFIED', 'VERIFIED')),

    -- NULL effective_end means open-ended and MUST be accepted — it is the normal state of a
    -- parameter in force. Only a window that closes before it opens is refused.
    CONSTRAINT chk_npp_effective_window CHECK (
        effective_end IS NULL OR effective_end > effective_start),

    -- The one that carries the governance weight: ratified implies verified.
    CONSTRAINT chk_npp_ratified_is_verified CHECK (
        approval_status <> 'RATIFIED' OR verification_status = 'VERIFIED')
);

CREATE INDEX IF NOT EXISTS ix_npp_code_window
    ON clinical.national_policy_parameters (parameter_code, effective_start DESC);

-- ---------------------------------------------------------------------------------------------
-- Seeds. Both ENGINEERING_SEED / UNVERIFIED, therefore both un-ratifiable until reviewed.
-- ---------------------------------------------------------------------------------------------

INSERT INTO clinical.national_policy_parameters
    (parameter_code, domain, value_type, value_text, unit,
     effective_start, approval_status, verification_status, legal_basis, content_version)
VALUES
    ('SRH_CONFIDENTIAL_FROM_GUARDIAN_AGE_YEARS', 'REPRODUCTIVE_HEALTH', 'INTEGER', '18', 'years',
     DATE '2026-01-01', 'ENGINEERING_SEED', 'UNVERIFIED',
     'Age of independent access to contraception and sexual health services, and of confidentiality '
     'of those records from a guardian. Check the Constitution of Zimbabwe s.76 (health care) and '
     's.81 (rights of children), the Children''s Act [Chapter 5:06], the Marriages Act (2022), and '
     'the National Adolescent and Youth Sexual and Reproductive Health Strategy in force. '
     'UNRESOLVED AND BLOCKING RATIFICATION: (a) contraception, STI treatment and termination of '
     'pregnancy may each carry a DIFFERENT threshold, so this may need to be three parameters, not '
     'one; (b) how the mandatory-reporting duty for a sexual offence against a minor interacts with '
     'confidentiality from the guardian — the sharpest conflict in the pack, and it must be resolved '
     'before any threshold is set.',
     'engineering-seed-1.0.0'),

    ('SRH_NON_TERMINATION_LOSS_CONFIDENTIAL_FROM_GUARDIAN', 'REPRODUCTIVE_HEALTH', 'BOOLEAN', 'true', NULL,
     DATE '2026-01-01', 'ENGINEERING_SEED', 'UNVERIFIED',
     'Whether a miscarriage or stillbirth record for a young person below the SRH age is confidential '
     'from a guardian, as a termination record always is. Engineering seeded this true so the '
     'judgement is a governed parameter rather than a lane''s opinion buried in a decision table. '
     'Needs a clinical and legal view: bereavement support frequently involves the guardian, so the '
     'protective answer here is genuinely less obvious than for termination.',
     'engineering-seed-1.0.0')
ON CONFLICT (parameter_code, effective_start) DO NOTHING;

COMMENT ON TABLE clinical.national_policy_parameters IS
    'Governed numbers that are policy, not engineering — read by systems of record at decision time. A RATIFIED row must be VERIFIED (chk_npp_ratified_is_verified), so a seeded guess cannot become authoritative by a status flip. Effective windows keep a past decision re-explainable against the version that was in force.';
COMMENT ON COLUMN clinical.national_policy_parameters.legal_basis IS
    'The instrument a human must read to make this real — not a citation for where the number came from. Seeds carry their unresolved questions here.';
COMMENT ON COLUMN clinical.national_policy_parameters.effective_end IS
    'NULL means open-ended and in force. Accepted deliberately; only a window closing before it opens is refused.';
