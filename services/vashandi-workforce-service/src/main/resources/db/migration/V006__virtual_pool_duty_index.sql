-- ============================================================================
-- V006: Virtual-pool duty lookup index.
--
-- The virtual-hospital substrate asks vashandi "who is on duty for pool X
-- right now" (shifts with virtual_pool_id = routing seam target_ref whose
-- window covers now, in an on-duty status). Partial index keeps the lookup
-- cheap without taxing the (much larger) facility-shift population.
-- ============================================================================

CREATE INDEX IF NOT EXISTS idx_vsh_shift_virtual_pool_window
    ON vashandi.vsh_shift (tenant_id, virtual_pool_id, start_time, end_time)
    WHERE virtual_pool_id IS NOT NULL;
