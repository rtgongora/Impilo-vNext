-- Link encounters to the shift (attendance session) they were performed under.
-- Populated from the X-Shift-ID trust header at encounter start; nullable because
-- not every encounter context has an active shift (e.g. community, virtual-only).
ALTER TABLE pct_encounters ADD COLUMN shift_id VARCHAR(64);
