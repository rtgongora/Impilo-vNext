-- ============================================================================
-- ndr-service V004 — NDR released / analytics zone (de-identified)
--
-- The released zone is materialised only from data-governance de-identification
-- engine output (tokenised + stripped + generalised + k-anon + policy-gated).
-- Bronze (V002 ndr_bronze_event) and gold (V002 ndr_gold_encounter) stay as the
-- restricted operational zone and are NOT modified here.
-- ============================================================================

-- Release build record — one row per release run, linked to the de-id manifest.
CREATE TABLE ndr_release_run (
    release_id          UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    dataset_ref         VARCHAR(128)    NOT NULL,
    deid_run_id         UUID,
    manifest_id         UUID,
    policy_version      VARCHAR(64),
    status              VARCHAR(32)     NOT NULL,
    rejection_reason    VARCHAR(512),
    rows_in             INT             NOT NULL DEFAULT 0,
    rows_out            INT             NOT NULL DEFAULT 0,
    rows_suppressed     INT             NOT NULL DEFAULT 0,
    correlation_id      VARCHAR(255),
    built_at            TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ndr_release_run_tenant ON ndr_release_run (tenant_id);
CREATE INDEX idx_ndr_release_run_dataset ON ndr_release_run (tenant_id, dataset_ref);
CREATE INDEX idx_ndr_release_run_manifest ON ndr_release_run (manifest_id);

-- Released / analytics zone rows — genuinely de-identified counterparts of gold.
-- subject_token is a per-dataset HMAC (never a raw patient_ref); released_json holds
-- the stripped + generalised columns as described by the release manifest.
CREATE TABLE ndr_released_encounter (
    released_row_id     UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    dataset_ref         VARCHAR(128)    NOT NULL,
    release_id          UUID            NOT NULL,
    manifest_id         UUID,
    subject_token       VARCHAR(128)    NOT NULL,
    released_json       JSONB           NOT NULL,
    released_at         TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ndr_released_encounter_dataset ON ndr_released_encounter (tenant_id, dataset_ref);
CREATE INDEX idx_ndr_released_encounter_release ON ndr_released_encounter (release_id);
CREATE INDEX idx_ndr_released_encounter_token ON ndr_released_encounter (subject_token);
