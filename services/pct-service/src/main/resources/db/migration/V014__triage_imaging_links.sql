-- Clinical workflow links between encounters/journeys and governed imaging studies.

CREATE TABLE pct.pct_imaging_links (
    id                  BIGSERIAL       PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    journey_id          VARCHAR(26)     NOT NULL REFERENCES pct.pct_journeys (journey_id) ON DELETE CASCADE,
    encounter_id        BIGINT          REFERENCES pct.pct_encounters (id) ON DELETE SET NULL,
    encounter_ref       UUID,
    governed_study_id   VARCHAR(64)     NOT NULL,
    oros_order_id       VARCHAR(128),
    link_type           VARCHAR(64)     NOT NULL DEFAULT 'TRIAGE_IMAGING',
    status              VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE',
    created_by          TEXT,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT uq_pct_imaging_link UNIQUE (tenant_id, encounter_id, governed_study_id)
);

CREATE INDEX idx_pct_imaging_links_journey ON pct.pct_imaging_links (tenant_id, journey_id, created_at DESC);
CREATE INDEX idx_pct_imaging_links_encounter ON pct.pct_imaging_links (tenant_id, encounter_id, created_at DESC);
