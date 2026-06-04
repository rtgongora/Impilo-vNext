CREATE TABLE pacs.imaging_edit (
    id                BIGSERIAL PRIMARY KEY,
    study_id          BIGINT NOT NULL REFERENCES pacs.imaging_study (id) ON DELETE CASCADE,
    series_id         BIGINT REFERENCES pacs.imaging_series (id) ON DELETE SET NULL,
    instance_id       BIGINT REFERENCES pacs.imaging_instance (id) ON DELETE SET NULL,
    editor_actor_id   TEXT,
    edit_type         VARCHAR(64) NOT NULL DEFAULT 'ANNOTATION',
    annotation_data   TEXT NOT NULL,
    viewport_data     TEXT,
    triage_record_id  BIGINT,
    journey_id        VARCHAR(26),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_imaging_edit_study_instance
    ON pacs.imaging_edit (study_id, instance_id, created_at DESC);
