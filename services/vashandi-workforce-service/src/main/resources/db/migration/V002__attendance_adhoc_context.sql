-- L3 Provider Experience — Wave 1 (C2 work-context + ad-hoc check-in).
-- The original check-in path required a pre-assigned shift_id. Ad-hoc check-in
-- lets a worker check in against an active assignment (or facility context)
-- without a rostered shift — the WHERE/WHAT context picker depends on this.
-- These columns capture the resolved context of an ad-hoc check-in so the
-- work-context query can report current check-in state with its WHERE/WHAT.

ALTER TABLE vashandi.vsh_attendance_event
    ADD COLUMN IF NOT EXISTS assignment_id   UUID REFERENCES vashandi.vsh_workforce_assignment(id),
    ADD COLUMN IF NOT EXISTS facility_id     UUID,
    ADD COLUMN IF NOT EXISTS department_id   VARCHAR(128),
    ADD COLUMN IF NOT EXISTS unit_id         VARCHAR(128),
    ADD COLUMN IF NOT EXISTS workspace_id    UUID,
    ADD COLUMN IF NOT EXISTS context_scope   VARCHAR(32),
    ADD COLUMN IF NOT EXISTS adhoc           BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_vsh_attendance_assignment
    ON vashandi.vsh_attendance_event (assignment_id);
CREATE INDEX IF NOT EXISTS idx_vsh_attendance_facility
    ON vashandi.vsh_attendance_event (tenant_id, facility_id);

-- Latest-event-per-profile lookups for the work-context check-in state.
CREATE INDEX IF NOT EXISTS idx_vsh_attendance_profile_time
    ON vashandi.vsh_attendance_event (tenant_id, workforce_profile_id, event_time DESC);
