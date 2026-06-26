-- M2: one charge -> one emergency-deferred row -> one reconciliation.
--
-- The flag() path did not dedup on charge_id, so the same charge could be flagged
-- N times, yielding N deferred rows and N reconciliations (and N value events). A
-- partial unique index enforces at most one deferred row per concrete charge per
-- tenant. It is PARTIAL (WHERE charge_id IS NOT NULL) because a bedside emergency
-- flag may legitimately have no concrete charge yet; those NULL rows are unaffected.

CREATE UNIQUE INDEX uq_costa_deferred_charge_per_tenant
    ON costa_emergency_deferred_charges (tenant_id, charge_id)
    WHERE charge_id IS NOT NULL;
