-- Persist human-readable facility label for delegated pickup verify/handover surfaces.
ALTER TABLE vito.delegated_pickup
    ADD COLUMN IF NOT EXISTS facility_name VARCHAR(255);
