-- ============================================================================
-- V045: Referral transition ledger + worklist-bucket indexes (TM-B1).
--
-- The telemedicine referral status was an ungated VARCHAR set by bare string
-- literals across ~10 mutators. TM-B1 introduces a guarded state machine
-- (ReferralStateMachine) rolled out shadow-then-enforce. Every attempted
-- transition — allowed, or (in SHADOW) a flagged violation — writes one row
-- here. This is simultaneously the §11.3 per-transition audit record, the
-- flip criterion (count allowed=false over a window), and the provenance
-- substrate for later task-driven transitions (TM-B7).
--
-- NOTE: no CHECK constraint on pct_referrals.status yet. During SHADOW the
-- updateReferralStage passthrough still writes arbitrary strings; a CHECK now
-- would turn a tolerated write into a mid-transaction failure. The status CHECK
-- (ADD CONSTRAINT ... NOT VALID -> data-fix -> VALIDATE) is a follow-up applied
-- at the shadow->enforce flip, mirroring the guard rollout.
-- ============================================================================

CREATE TABLE IF NOT EXISTS pct_referral_transitions (
    id           BIGSERIAL,
    referral_id  UUID        NOT NULL,
    tenant_id    UUID        NOT NULL,
    from_status  VARCHAR(48),
    to_status    VARCHAR(48) NOT NULL,
    action       VARCHAR(48) NOT NULL,
    actor        VARCHAR(128),
    allowed      BOOLEAN     NOT NULL,
    mode         VARCHAR(16) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_pct_referral_transitions PRIMARY KEY (id)
);

COMMENT ON TABLE pct_referral_transitions IS
    'TM-B1 append-only ledger of telemedicine referral state transitions (audit + shadow-flip criterion + task provenance).';

CREATE INDEX IF NOT EXISTS idx_pct_referral_transitions_referral
    ON pct_referral_transitions (referral_id, created_at);
-- Flip criterion: violations in a window.
CREATE INDEX IF NOT EXISTS idx_pct_referral_transitions_violations
    ON pct_referral_transitions (created_at) WHERE allowed = false;

-- Worklist-bucket + timer-sweep support on pct_referrals (previously unindexed):
-- pool worklist query (findByTenantIdAndRoutingPoolIdAndStatusIn...).
CREATE INDEX IF NOT EXISTS idx_pct_referrals_pool_status
    ON pct_referrals (tenant_id, routing_pool_id, status, created_at)
    WHERE routing_pool_id IS NOT NULL;
-- Expiry/abandon sweeps select by status + age.
CREATE INDEX IF NOT EXISTS idx_pct_referrals_status_submitted
    ON pct_referrals (status, submitted_at);
CREATE INDEX IF NOT EXISTS idx_pct_referrals_status_created
    ON pct_referrals (tenant_id, status, created_at);
