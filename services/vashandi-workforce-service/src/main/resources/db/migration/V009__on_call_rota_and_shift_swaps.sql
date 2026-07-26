-- =====================================================================================
-- vashandi :: V009 — the scheduled on-call rota, and swapping a shift
--
-- WHAT WAS BROKEN.
--
-- experience-bff has been calling tuso for /v1/staffing/roster-week, /v1/staffing/on-call and
-- /v1/staffing/on-call/swaps. No service in the estate serves any of them, so the roster screen,
-- the on-call screen and the control tower have always rendered empty while every layer reported
-- success. Vashandi owns rostering (vsh_roster/vsh_shift), so the surface is completed here.
--
-- ONE ON-CALL TRUTH, SEVERAL READ SHAPES.
--
-- Scheduled on-call is NOT a new concept needing a new store. vsh_shift already carries the
-- window, the facility, the status and virtual_pool_id (the frozen TUSO routing-seam target_ref
-- that VirtualPoolDutyService reads for "who is on duty right now"). What it could not express is
-- the two things a printed on-call rota is actually made of:
--
--   * WHICH ROLE this shift plays in the rota — first call or second call. Without it, a rota can
--     list four names for a night and say nothing about who to ring first.
--   * WHICH SERVICE LINE the rota is for, in a form that does not abuse the pool key. The pool key
--     is a frozen contract with tuso's routing seams (e.g. `trauma-oncall:<facilityId>`); parsing
--     a specialty back out of it would make a display concern load-bearing on an integration
--     contract, and would break the moment a pool key changes shape.
--
-- So two nullable columns, not a second rota table. A shift with on_call_role IS NULL is an
-- ordinary rostered shift, exactly as today; the on-call read is a projection over the ones where
-- it is set. Nothing existing changes meaning.
--
-- NOT MODELLED HERE, deliberately: the on-call contact number. vsh_workforce_profile holds no name
-- and no phone — person identity is vito's and provider identity is varapi's — and a roster is not
-- the place to start a second copy of either. The read returns the profile reference and lets the
-- composition layer resolve a name; where it cannot, callers get null rather than a plausible
-- string. An invented phone number on an on-call rota is the kind of fallback someone dials at 3am.
-- =====================================================================================

-- ---- On-call as a role a rostered shift plays ------------------------------------------

ALTER TABLE vashandi.vsh_shift
    ADD COLUMN IF NOT EXISTS on_call_role VARCHAR(16),
    ADD COLUMN IF NOT EXISTS specialty    VARCHAR(64);

COMMENT ON COLUMN vashandi.vsh_shift.on_call_role IS
    'PRIMARY (first call) or BACKUP (second call) when this shift is part of an on-call rota; '
    'NULL for an ordinary rostered shift. The rota read is a projection over the non-NULL rows.';

COMMENT ON COLUMN vashandi.vsh_shift.specialty IS
    'Service line this on-call shift covers. Held here rather than parsed out of virtual_pool_id, '
    'which is a frozen TUSO routing-seam contract and must not become a display dependency.';

ALTER TABLE vashandi.vsh_shift
    DROP CONSTRAINT IF EXISTS chk_vsh_shift_on_call_role;
ALTER TABLE vashandi.vsh_shift
    ADD CONSTRAINT chk_vsh_shift_on_call_role
    CHECK (on_call_role IS NULL OR on_call_role IN ('PRIMARY', 'BACKUP'));

-- The rota read: facility + week window, on-call rows only.
CREATE INDEX IF NOT EXISTS idx_vsh_shift_on_call_window
    ON vashandi.vsh_shift (tenant_id, facility_id, start_time)
    WHERE on_call_role IS NOT NULL;

-- The roster-week read: every shift at a facility across a window, on-call or not.
CREATE INDEX IF NOT EXISTS idx_vsh_shift_facility_window
    ON vashandi.vsh_shift (tenant_id, facility_id, start_time);

-- ---- Swapping a shift ------------------------------------------------------------------
--
-- vsh_shift.status already contemplates the outcome ('swapped', 'covered') — what was missing is
-- the request that produces it. A swap is a two-sided proposal about ROSTERED SHIFTS, so it is
-- keyed on shift ids, never on typed names: two people share a name, and a clinical rota is not
-- somewhere to resolve that ambiguity by guessing. The requested shift is mandatory; the offered
-- shift is optional, because "please cover me" and "swap with mine" are both real requests.

CREATE TABLE IF NOT EXISTS vashandi.vsh_shift_swap_request (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    facility_id             UUID,

    -- The shift the requester wants covered, and who is asking.
    requesting_shift_id     UUID NOT NULL REFERENCES vashandi.vsh_shift(id),
    requesting_profile_id   UUID NOT NULL REFERENCES vashandi.vsh_workforce_profile(id),

    -- Who is being asked, and (optionally) the shift offered back in exchange.
    requested_profile_id    UUID NOT NULL REFERENCES vashandi.vsh_workforce_profile(id),
    offered_shift_id        UUID REFERENCES vashandi.vsh_shift(id),

    reason                  TEXT,
    status                  VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    decided_by              VARCHAR(128),
    decided_at              TIMESTAMPTZ,
    decision_note           TEXT,
    created_by              VARCHAR(128),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_vsh_swap_status CHECK (status IN
        ('PENDING', 'APPROVED', 'DECLINED', 'WITHDRAWN', 'EXPIRED')),

    -- Nobody swaps with themselves: it would look like an approved swap and change nothing.
    CONSTRAINT chk_vsh_swap_distinct_parties
        CHECK (requesting_profile_id <> requested_profile_id),

    -- A decision must say who made it. An APPROVED row with no decider cannot be audited, and a
    -- shift swap is a change to who is clinically responsible at a given hour.
    CONSTRAINT chk_vsh_swap_decided_by
        CHECK (status IN ('PENDING', 'WITHDRAWN') OR decided_by IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_vsh_swap_facility_status
    ON vashandi.vsh_shift_swap_request (tenant_id, facility_id, status);
CREATE INDEX IF NOT EXISTS idx_vsh_swap_requesting_shift
    ON vashandi.vsh_shift_swap_request (requesting_shift_id);

-- One live request per shift: a queue of competing PENDING swaps for the same shift resolves to
-- whichever approval happens to land last, which is not a decision anybody made.
CREATE UNIQUE INDEX IF NOT EXISTS uq_vsh_swap_one_pending_per_shift
    ON vashandi.vsh_shift_swap_request (requesting_shift_id)
    WHERE status = 'PENDING';

-- NOTE deliberately absent:
--   * no auto-approval and no auto-expiry. A swap changes who is clinically accountable for a
--     window; it is approved by a person, and a request nobody answered stays PENDING and visible
--     rather than lapsing quietly into "not covered".
--   * no denormalised names on the swap row. Names are resolved at read time from the identity
--     services that own them; copying them here would create a second, ageing copy of a person's
--     name inside a rostering table.
