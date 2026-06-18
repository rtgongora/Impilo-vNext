-- Imaging annotation / measurement / review note persistence (governed study scope).

CREATE TABLE pacs.imaging_annotation (
    id                BIGSERIAL       PRIMARY KEY,
    study_id          BIGINT          NOT NULL REFERENCES pacs.imaging_study (id) ON DELETE CASCADE,
    series_id         BIGINT          REFERENCES pacs.imaging_series (id) ON DELETE SET NULL,
    instance_id       BIGINT          REFERENCES pacs.imaging_instance (id) ON DELETE SET NULL,
    annotation_type   VARCHAR(64)     NOT NULL DEFAULT 'REVIEW_NOTE',
    note              TEXT,
    measurement       JSONB,
    body              JSONB,
    actor_id          TEXT,
    actor_type        TEXT,
    facility_id       VARCHAR(128),
    created_at        TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_imaging_annotation_study ON pacs.imaging_annotation (study_id, created_at DESC);
