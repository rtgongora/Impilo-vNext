-- =====================================================================================
-- workforce-governance :: V013 — the regulator bootstrap request
--
-- THE PROBLEM THIS SOLVES: the cold start.
--
-- A regulator's first administrator cannot be appointed by an administrator — there is nobody
-- there yet to do it. That is exactly the situation an attacker would exploit, so the founding
-- appointment is the one that must be hardest to forge: nobody self-claims it, it is checked
-- against authoritative regulator records, and it is activated only under four-eyes.
--
-- WHY THIS RIDES THE EXISTING RAIL RATHER THAN INVENTING ONE.
--
-- PlatformOriginService already runs ACTION_APPOINT_NATIONAL_ADMIN through
-- TwoPersonApprovalService, with initiator-cannot-approve and a per-approver unique constraint
-- enforced in wgv_platform_action_approval (V006). A founding-administrator appointment is the same
-- species of act — a privileged grant that no single person may make — so it becomes a new action
-- on that rail, not a second four-eyes mechanism to keep in step with the first.
--
-- This table is the DOMAIN record of the request: who is asking to found which regulator, in what
-- role, on what evidence, and what the authoritative check said. The four-eyes vehicle is the
-- access request it points at; the two are kept distinct because the vehicle is generic and the
-- request is not.
--
-- WHAT THIS TABLE DOES NOT DO.
--
-- It does not create the appointment. Appointments are org-registry's (ownership ruling R2); on
-- activation this emits an event org-registry consumes to create and verify the founding
-- appointment. And it does not gate on person assurance — that gate lives at the BFF entry, where
-- the identity-assurance service is reachable, exactly as the facility- and provider-claim gates
-- do. A sovereign service trusting a body-supplied assurance claim would be trusting the caller to
-- rate themselves.
-- =====================================================================================

CREATE TABLE IF NOT EXISTS wgv_regulator_bootstrap_request (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                 UUID NOT NULL,

    -- Opaque applicant-facing handle (RBR-XXXXXXXX). The internal id never travels to a client, so
    -- a status poll cannot become a way to enumerate requests.
    public_id                 VARCHAR(32) NOT NULL,

    -- The regulator being founded (an org-registry organisation UUID) and who is asking to found it.
    organization_id           UUID NOT NULL,
    claimant_health_id        VARCHAR(128) NOT NULL,

    -- Bootstrap-eligible role only. Enforced in the service against the org-registry role
    -- vocabulary's is_bootstrap_only flag; the CHECK here is the coarse backstop.
    claimed_role_code         VARCHAR(48) NOT NULL,

    -- Evidence the claim is genuine (a gazette notice, council minute, appointment letter) as a
    -- document-service object id, plus the structured result of checking it against authoritative
    -- regulator records. A request activates on the strength of this, never on the claimant's say-so.
    evidence_ref              VARCHAR(512),
    authoritative_check_result JSONB,

    -- The four-eyes vehicle: the PLATFORM_ACTION access request whose two approvals gate activation.
    -- Nullable only in the instant between writing this row and initiating the action.
    access_request_id         UUID,

    status                    VARCHAR(32) NOT NULL DEFAULT 'SUBMITTED',
    rejection_reason          TEXT,

    created_by                VARCHAR(128),
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    activated_at              TIMESTAMPTZ,

    CONSTRAINT uq_regulator_bootstrap_public_id UNIQUE (tenant_id, public_id),
    CONSTRAINT chk_regulator_bootstrap_status CHECK (status IN
        ('SUBMITTED', 'PENDING_SECOND_APPROVAL', 'ACTIVATED', 'REJECTED', 'WITHDRAWN', 'EXPIRED')),

    -- Only the founding role may be bootstrapped. The ordinary administrative roles are granted by
    -- an existing administrator once one exists; routing them through the cold-start rail would let
    -- the bootstrap path become a general-purpose way to mint privileged appointments.
    CONSTRAINT chk_regulator_bootstrap_role
        CHECK (claimed_role_code = 'FOUNDING_REGULATOR_ADMINISTRATOR'),

    -- An activated request must record when, and must point at the vehicle that approved it: an
    -- ACTIVATED row with no access request is an activation nobody can audit.
    CONSTRAINT chk_regulator_bootstrap_activation
        CHECK (status <> 'ACTIVATED' OR (activated_at IS NOT NULL AND access_request_id IS NOT NULL))
);

-- At most ONE live bootstrap request per organisation. Two people racing to found the same
-- regulator, each approved by a different pair, would both activate and produce two founding
-- administrators — defeating the one-founder cap. The first to reach a live state holds the lane;
-- the other is refused with an explanation.
CREATE UNIQUE INDEX IF NOT EXISTS uq_regulator_bootstrap_one_live_per_org
    ON wgv_regulator_bootstrap_request (tenant_id, organization_id)
    WHERE status IN ('SUBMITTED', 'PENDING_SECOND_APPROVAL');

CREATE INDEX IF NOT EXISTS idx_regulator_bootstrap_status
    ON wgv_regulator_bootstrap_request (tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_regulator_bootstrap_access_request
    ON wgv_regulator_bootstrap_request (access_request_id);
