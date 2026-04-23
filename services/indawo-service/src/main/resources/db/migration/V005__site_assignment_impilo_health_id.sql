-- Indawo V005 — optional anchor for human assignees on site assignments (Experience Doctrine).
ALTER TABLE ind_site_assignments
    ADD COLUMN IF NOT EXISTS impilo_health_id UUID;

COMMENT ON COLUMN ind_site_assignments.impilo_health_id IS
    'When the assignee is a person, their Impilo / Health ID; use with assigned_to_ref pointing at Varapi provider public id.';
