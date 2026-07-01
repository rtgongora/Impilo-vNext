-- Provenance: each clinical resource DERIVED from a submitted form response.
-- The extraction (answers -> Observation/Condition/CarePlan/ServiceRequest) is recorded here so every
-- downstream resource is traceable to (response_id, form_schema_version_id, source field linkIds).
-- External sinks (BUTANO/OROS) are eventually consistent: rows land PENDING/FAILED and are retried;
-- extraction never rolls back submit.

CREATE TABLE pct_form_extracted_resource (
    extracted_id            UUID            PRIMARY KEY,
    tenant_id               UUID            NOT NULL,
    response_id             UUID            NOT NULL REFERENCES pct_form_response(response_id) ON DELETE CASCADE,
    form_schema_version_id  VARCHAR(36)     NOT NULL,

    resource_type           VARCHAR(32)     NOT NULL,   -- OBSERVATION|CONDITION|CARE_PLAN|SERVICE_REQUEST|PROCEDURE
    route_target            VARCHAR(24)     NOT NULL,   -- BUTANO|OROS|PCT_PROBLEM|PCT_CARE_PLAN
    source_link_ids         JSONB           NOT NULL DEFAULT '[]',
    resource_payload        JSONB,

    external_ref            VARCHAR(256),               -- OROS orderId / BUTANO resource id
    local_ref               VARCHAR(64),                -- pct_problems.problem_id / pct_care_plans.plan_id
    status                  VARCHAR(24)     NOT NULL DEFAULT 'PENDING', -- PENDING|ROUTED|CONFIRMED|FAILED
    attempts                INTEGER         NOT NULL DEFAULT 0,
    failure_reason          TEXT,

    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_pct_form_extracted_response ON pct_form_extracted_resource (response_id);
CREATE INDEX idx_pct_form_extracted_type     ON pct_form_extracted_resource (tenant_id, resource_type, status);
CREATE INDEX idx_pct_form_extracted_pending  ON pct_form_extracted_resource (status) WHERE status IN ('PENDING', 'FAILED');

COMMENT ON TABLE pct_form_extracted_resource IS
    'Provenance chain: clinical resources extracted from a form response back to (response, definition version, source fields).';
