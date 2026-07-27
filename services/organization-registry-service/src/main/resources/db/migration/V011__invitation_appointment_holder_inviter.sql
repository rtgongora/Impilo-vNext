-- RB-6: an invitation may be issued by a regulatory appointment-holder, not only by an
-- authorized representative.
--
-- The invitation rail (V005) was built for the delegated-onboarding flow, where the inviter is an
-- org_registry_authorized_representative and invited_by_rep_id is that row's UUID. RB-4 then made
-- this rail the way an administrator invites a colleague into a governed regulatory role. But a
-- regulator administrator holds a regulatory_appointment, not a representative row — they have no
-- rep UUID to supply, and the NOT NULL rep column made the administrator-invites-colleague path
-- (the whole point of the RB-4 seam) impossible to drive. Relax the rep requirement and record the
-- inviter's Health ID for the appointment-holder path, keeping at least one inviter identity on
-- every row.

ALTER TABLE org_registry_invitation
    ALTER COLUMN invited_by_rep_id DROP NOT NULL;

ALTER TABLE org_registry_invitation
    ADD COLUMN IF NOT EXISTS invited_by_health_id VARCHAR(64);

-- Every invitation must still name who issued it — a representative (delegated onboarding) or a
-- Health ID (an appointment-holder administrator). Never neither.
ALTER TABLE org_registry_invitation
    ADD CONSTRAINT chk_org_invitation_has_inviter
    CHECK (invited_by_rep_id IS NOT NULL OR invited_by_health_id IS NOT NULL);
