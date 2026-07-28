-- Diagnostic order lifecycle hardening (W7a) — a real closed vocabulary + "critical cannot close".
--
-- ed_diagnostic_order (V040) has had a bare VARCHAR status with only a code comment ("PLACED /
-- CRITICAL / ACKED") since it was written, and no constraint at all. Reading the one place that ever
-- writes it (EdDiagnosticsService) shows the comment was already stale: the row's own status column
-- has NEVER been set to 'CRITICAL' — that value is only ever passed to daidzai's phase log, a
-- different table. So today an order that received a critical result and was never acknowledged sits
-- at 'PLACED' forever, indistinguishable from an order nobody has looked at yet, and NOTHING stops it
-- being treated as resolved. That is the exact defect this migration closes.
--
-- BROWNFIELD-SAFETY CHECK PERFORMED BEFORE WRITING THIS: grepped every service for a write to this
-- table (`grep -rln ed_diagnostic_order services/*/src/main/java`). Exactly one writer exists —
-- EdDiagnosticsService — and it has only ever set two values, 'PLACED' (the default) and 'ACKED'.
-- Unlike the emergency_activation.protocol_type situation in inpatient V200, this vocabulary can
-- therefore be a fully VALIDATED CHECK, not NOT VALID: there is no unaudited historical value it
-- could reject. Verified on real Postgres against a pre-seeded legacy PLACED/ACKED row regardless,
-- because "I checked the source" and "it is true on the database" are different claims.

ALTER TABLE ed_diagnostic_order
    -- acked_at already existed (V040); acked_by did not, so who acknowledged a critical result was
    -- unrecorded. Added here because the fix below (see acked_at's COMMENT) makes acked_at
    -- unconditional on the clinician's own act, and that act deserves an audited actor.
    ADD COLUMN acked_by    VARCHAR(128),
    ADD COLUMN acted_at    TIMESTAMPTZ,
    ADD COLUMN acted_by    VARCHAR(128),
    ADD COLUMN closed_at   TIMESTAMPTZ,
    ADD COLUMN closed_by   VARCHAR(128),
    ADD COLUMN close_reason TEXT,

    -- The 10-state lifecycle. Distinguishes "a specimen problem" (SPECIMEN_REJECTED — a real and
    -- currently entirely unrepresented diagnostics failure mode: a haemolysed sample, an unlabelled
    -- tube) from "no result yet" (PLACED/SPECIMEN_COLLECTED) from "the patient left before the
    -- result came back" (EXPIRED — the results-on-a-departed-patient problem this table could not
    -- previously express at all). ACTED_ON is deliberately distinct from ACKED: acknowledging a
    -- critical result means a clinician has SEEN it; acting on it (starting antibiotics for a
    -- positive culture) is a separate, later fact, and the plan's acted_at column exists precisely
    -- to keep that distinction on the record rather than collapsing "saw it" and "did something
    -- about it" into one timestamp.
    ADD CONSTRAINT chk_edo_status CHECK (status IN (
        'PLACED', 'SPECIMEN_COLLECTED', 'SPECIMEN_REJECTED', 'RESULTED',
        'CRITICAL', 'ACKED', 'ACTED_ON', 'CANCELLED', 'EXPIRED', 'CLOSED')),

    -- CRITICAL-NOT-CLOSED. An order carrying a critical_reason that was never acknowledged (acked_at
    -- still null) may not reach a terminal status. This is the schema property that makes "a
    -- critical result was flagged and nobody has confirmed seeing it" structurally unable to be
    -- filed away as done — the exact gap the stale status comment was hiding.
    ADD CONSTRAINT chk_edo_critical_not_closed CHECK (
        status NOT IN ('CLOSED', 'CANCELLED') OR critical_reason IS NULL OR acked_at IS NOT NULL),

    -- Value-or-reason: a terminal transition states who, when and why. A close with no reason is
    -- indistinguishable later from an order nobody ever actually reviewed.
    ADD CONSTRAINT chk_edo_terminal_pair CHECK (
        status NOT IN ('CLOSED', 'CANCELLED')
        OR (closed_at IS NOT NULL AND closed_by IS NOT NULL AND close_reason IS NOT NULL));

COMMENT ON COLUMN ed_diagnostic_order.acked_at IS
    'Found while adding chk_edo_critical_not_closed: EdDiagnosticsService only stamped this WHEN THE '
    'DOWNSTREAM OROS SYNC SUCCEEDED (`if (acked)`), not when the clinician actually acknowledged by '
    'calling this endpoint. A degraded OROS during the sync call would leave acked_at permanently '
    'null on a genuinely-acknowledged result — which this new CHECK would then refuse to ever let '
    'close. Fixed alongside this migration: acked_at is now stamped unconditionally (calling the '
    'endpoint IS the clinical acknowledgement), and the OROS-sync outcome is reported separately '
    'rather than gating the local record — the same care-first principle applied everywhere else in '
    'this pack.';
COMMENT ON COLUMN ed_diagnostic_order.acted_at IS
    'When a clinician took action on this result — distinct from acked_at (acknowledging a critical '
    'flag means seeing it; acting on it, e.g. starting antibiotics for a positive culture, is a '
    'separate later fact).';
COMMENT ON CONSTRAINT chk_edo_critical_not_closed ON ed_diagnostic_order IS
    'A critical result that was never acknowledged cannot be closed or cancelled. Before this '
    'constraint, the status column never actually recorded CRITICAL at all (only daidzai''s phase '
    'log did), so an unacknowledged critical order was indistinguishable from an untouched one.';
