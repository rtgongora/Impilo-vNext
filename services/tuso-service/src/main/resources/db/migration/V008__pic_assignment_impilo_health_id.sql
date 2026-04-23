-- TUSO V008 — mirror Impilo Health ID on PIC assignment rows (Experience Doctrine: human edges resolve via Health ID).
ALTER TABLE tuso.practitioner_in_charge_assignment
    ADD COLUMN IF NOT EXISTS impilo_health_id UUID;

COMMENT ON COLUMN tuso.practitioner_in_charge_assignment.impilo_health_id IS
    'Canonical Impilo / Health person identity for the practitioner; complements provider_public_id.';
