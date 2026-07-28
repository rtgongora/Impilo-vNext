-- Links a workforce assignment to a support-service support team, for
-- technical/operational support postings ("Facility ICT Support — Facility X",
-- "District Support — District Y", etc, A3).
--
-- Vashandi stays authoritative for the employment/posting fact (this row);
-- support-service stays authoritative for the support authority itself
-- (team scope, tier, permitted actions, elevated access) via
-- sup_support_team / sup_support_assignment. No FK across the service
-- boundary — same cross-service-reference convention already used by this
-- table's organisation_id/facility_id columns (owned by other services).
--
-- assignment_type='support' identifies a support posting for the work-context
-- resolver's SupportContextSource; support_team_id is the sup_support_team.
-- team_id it belongs to.

ALTER TABLE vashandi.vsh_workforce_assignment
    ADD COLUMN IF NOT EXISTS support_team_id UUID,
    ADD COLUMN IF NOT EXISTS support_assignment_id UUID;

CREATE INDEX IF NOT EXISTS idx_vsh_assignment_support_team
    ON vashandi.vsh_workforce_assignment (support_team_id) WHERE support_team_id IS NOT NULL;

COMMENT ON COLUMN vashandi.vsh_workforce_assignment.support_team_id IS
    'Cross-service reference to support-service sup_support_team.team_id. Set '
    'only when assignment_type=''support''; the support authority itself '
    '(tier, scope, permitted actions) lives in support-service, not here.';
COMMENT ON COLUMN vashandi.vsh_workforce_assignment.support_assignment_id IS
    'Cross-service reference to support-service sup_support_assignment.assignment_id '
    '— the specific scoped support posting this workforce assignment backs.';
