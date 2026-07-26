-- =====================================================================================
-- varapi :: V038 — Supporting indexes for the HPA council register backfill (HAR W2)
-- The backfill selects COUNCIL_IMPORT providers that carry a practice number but still have no
-- register record for the HPA council, and the public verification lane resolves by registration
-- number. Index only: the register rows are written by HpaPractitionerRegisterBackfillService so
-- the work is paced, idempotent and auditable.
--
-- NOTE the status these rows carry: LISTED_PENDING_COUNCIL_VERIFICATION. It is deliberately NOT a
-- member of any verified-registry vocabulary, so making these registrations FINDABLE cannot make
-- them VERIFIED, and cannot widen any access gate.
-- =====================================================================================

-- Candidate selection: imported practitioners awaiting a register record.
CREATE INDEX IF NOT EXISTS idx_provider_bootstrap_origin_tenant
    ON varapi.provider (tenant_id, bootstrap_origin);

-- The NOT EXISTS anti-join, and the per-provider council lookup.
CREATE INDEX IF NOT EXISTS idx_pcrr_provider_council
    ON varapi.provider_council_registration_records (provider_id, council_id);

-- The public verification lane's exact, case-insensitive registration-number lookup.
CREATE INDEX IF NOT EXISTS idx_pcrr_registration_number_lower
    ON varapi.provider_council_registration_records (tenant_id, lower(registration_number));
