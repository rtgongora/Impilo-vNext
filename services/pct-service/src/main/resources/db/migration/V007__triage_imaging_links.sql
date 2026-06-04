CREATE TABLE pct_triage_imaging_links (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    journey_id          VARCHAR(26),
    triage_record_id    BIGINT,
    study_id            BIGINT NOT NULL,
    series_id           BIGINT,
    instance_id         BIGINT,
    imaging_edit_id     BIGINT,
    created_by          VARCHAR(128),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_pct_triage_imaging_links_journey
    ON pct_triage_imaging_links (tenant_id, journey_id, created_at DESC);

CREATE INDEX idx_pct_triage_imaging_links_study
    ON pct_triage_imaging_links (tenant_id, study_id, created_at DESC);
