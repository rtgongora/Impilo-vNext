-- =====================================================================================
-- org-registry :: V010 — the invitation rail's plaintext token, and its free-text role
--
-- V005 shipped the org invitation with two defects that the provider-claim rail on the same
-- estate does not have:
--
--   * the token is stored in PLAINTEXT (`token VARCHAR(128)`, uniquely indexed). A read of the
--     table — a backup, a log, a leaked dump — is a set of live invitation links. varapi's
--     provider_claim_token stores only a SHA-256 hash; this rail should too.
--   * the role is FREE TEXT (`role VARCHAR(128)`). Nothing constrains it to the appointment-role
--     vocabulary, so an invitation can name a role the estate does not recognise, and the
--     invitation → regulatory-appointment path (RB-4) has no closed code to switch on.
--
-- This migration closes both. It is safe to hash-or-drop the token because there is nothing to
-- preserve — the table is empty — but it is written to survive a row that appears between the
-- verification and the deploy: such a row is expired and stamped with an unusable sentinel, never
-- silently left redeemable.
-- =====================================================================================

-- ---- Token: hash at rest, never plaintext -----------------------------------------------

ALTER TABLE org_registry.org_registry_invitation
    ADD COLUMN IF NOT EXISTS token_hash VARCHAR(64);

-- Any pre-existing invitation cannot be migrated: we never stored a hash, and we will not keep the
-- plaintext. Expire it and give it a sentinel that can never equal a SHA-256 hex (64 hex chars), so
-- the NOT NULL and the unique index hold and the row is unredeemable rather than lost silently.
UPDATE org_registry.org_registry_invitation
   SET status = 'EXPIRED',
       token_hash = 'MIGRATED_NO_HASH_' || id
 WHERE token_hash IS NULL;

ALTER TABLE org_registry.org_registry_invitation
    ALTER COLUMN token_hash SET NOT NULL;

-- Drop the plaintext column and its unique constraint — no plaintext token survives at rest.
ALTER TABLE org_registry.org_registry_invitation
    DROP CONSTRAINT IF EXISTS uq_org_registry_invitation_token;
ALTER TABLE org_registry.org_registry_invitation
    DROP COLUMN IF EXISTS token;

CREATE UNIQUE INDEX IF NOT EXISTS uq_org_registry_invitation_token_hash
    ON org_registry.org_registry_invitation (token_hash);

COMMENT ON COLUMN org_registry.org_registry_invitation.token_hash IS
    'SHA-256 hex of the single-use invitation token. The raw token is returned to the issuer once, '
    'at creation, and never stored — a leaked table is not a set of live invitation links.';

-- ---- Role: a closed vocabulary, and the seam to a regulatory appointment -----------------
-- Nullable and additive: the free-text `role` stays for the existing affiliation path, and
-- `role_code` is set for invitations that name a governed appointment role. When it is set to a
-- regulatory role, acceptance creates a PENDING_VERIFICATION regulatory appointment
-- (source=INVITATION) instead of an affiliation — the value the appointment model already allows
-- and that nothing has been writing.

ALTER TABLE org_registry.org_registry_invitation
    ADD COLUMN IF NOT EXISTS role_code VARCHAR(48);

ALTER TABLE org_registry.org_registry_invitation
    DROP CONSTRAINT IF EXISTS fk_org_registry_invitation_role_code;
ALTER TABLE org_registry.org_registry_invitation
    ADD CONSTRAINT fk_org_registry_invitation_role_code
    FOREIGN KEY (role_code) REFERENCES org_registry.org_registry_appointment_role (role_code);

COMMENT ON COLUMN org_registry.org_registry_invitation.role_code IS
    'Governed appointment role (FK to org_registry_appointment_role). When set to a regulatory '
    'role, acceptance mints a PENDING_VERIFICATION regulatory appointment rather than an '
    'affiliation — the source=INVITATION path. NULL keeps the legacy free-text affiliation flow.';
