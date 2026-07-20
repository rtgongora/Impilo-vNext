-- ABIS — Adjudication + Duplicate-Investigation platform (Identity Contract §11, W3c).
-- 1:N identification and enrol-time dedup NEVER auto-merge: candidate matches open a
-- human-adjudicated case. A confirmed-duplicate merge is a governed, dual-control action
-- (a second reviewer distinct from the decider) — never automatic, always auditable.

-- ============================================================================
-- Adjudication case — one reviewable case per probe subject that had candidate
-- matches (from enrol-time dedup or a 1:N identify). Holds the workflow state,
-- the human decision, and the dual-control merge record.
-- ============================================================================
CREATE TABLE abis.adjudication_case (
    id                        BIGSERIAL    PRIMARY KEY,
    tenant_id                 UUID         NOT NULL,
    -- Opaque probe subject under investigation — NEVER a Health ID.
    subject_ref               VARCHAR(128) NOT NULL,
    modality                  VARCHAR(32)  NOT NULL,
    -- Why the 1:N ran: ENROLMENT (enrol-time dedup) / RECOVERY / DEDUPLICATION.
    reason                    VARCHAR(32)  NOT NULL,
    -- OPEN → IN_REVIEW (assigned) → RESOLVED (decision recorded).
    status                    VARCHAR(16)  NOT NULL DEFAULT 'OPEN',
    -- Human decision — null until RESOLVED. DISTINCT (not a duplicate) or CONFIRMED_DUPLICATE.
    decision                  VARCHAR(32),
    candidate_count           INTEGER      NOT NULL DEFAULT 0,
    top_score                 DOUBLE PRECISION,
    -- Reviewer assigned to work the case (X-Actor-ID at assign time).
    assigned_reviewer         VARCHAR(128),
    -- Reviewer who recorded the decision (X-Actor-ID at decide time).
    decided_by                VARCHAR(128),
    decided_at                TIMESTAMPTZ,
    decision_reason           TEXT,
    -- Governed merge (dual control): only on CONFIRMED_DUPLICATE, only via the explicit
    -- merge action, only when the second reviewer differs from decided_by. Never automatic.
    merge_target_subject_ref  VARCHAR(128),
    dual_sign_off_by          VARCHAR(128),
    merged_at                 TIMESTAMPTZ,
    created_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_adj_status   CHECK (status IN ('OPEN', 'IN_REVIEW', 'RESOLVED')),
    CONSTRAINT chk_adj_decision CHECK (decision IS NULL OR decision IN ('DISTINCT', 'CONFIRMED_DUPLICATE'))
);

CREATE INDEX idx_adj_case_tenant_status ON abis.adjudication_case (tenant_id, status, created_at);
CREATE INDEX idx_adj_case_subject       ON abis.adjudication_case (tenant_id, subject_ref);

-- ============================================================================
-- Duplicate investigation — the candidate list + scores for a case (one row per
-- suspected-duplicate subject the matcher-engine returned above the dedup threshold).
-- ============================================================================
CREATE TABLE abis.duplicate_investigation (
    id                     BIGSERIAL    PRIMARY KEY,
    case_id                BIGINT       NOT NULL REFERENCES abis.adjudication_case (id) ON DELETE CASCADE,
    -- Opaque candidate subject — NEVER a Health ID.
    candidate_subject_ref  VARCHAR(128) NOT NULL,
    score                  DOUBLE PRECISION NOT NULL,
    summary                VARCHAR(256),
    rank                   INTEGER      NOT NULL DEFAULT 0,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_dup_inv_case ON abis.duplicate_investigation (case_id, rank);
