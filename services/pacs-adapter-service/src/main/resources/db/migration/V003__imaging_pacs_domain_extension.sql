-- PACS domain extension: study context, DICOM hierarchy, archive linkage, viewer sessions, audit, report/order links.
-- Replaces coding-system-only migration (merged here with explicit pacs schema).

ALTER TABLE pacs.imaging_study
    ADD COLUMN IF NOT EXISTS encounter_ref VARCHAR(128),
    ADD COLUMN IF NOT EXISTS facility_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS archive_status VARCHAR(32) DEFAULT 'UNKNOWN',
    ADD COLUMN IF NOT EXISTS report_status VARCHAR(64),
    ADD COLUMN IF NOT EXISTS source_type VARCHAR(32) DEFAULT 'DICOM',
    ADD COLUMN IF NOT EXISTS body_part_examined VARCHAR(500),
    ADD COLUMN IF NOT EXISTS modality_system VARCHAR(255) DEFAULT 'http://dicom.nema.org/resources/ontology/DCM',
    ADD COLUMN IF NOT EXISTS body_part_system VARCHAR(255),
    ADD COLUMN IF NOT EXISTS body_part_code VARCHAR(100),
    ADD COLUMN IF NOT EXISTS body_part_display VARCHAR(500),
    ADD COLUMN IF NOT EXISTS procedure_system VARCHAR(255),
    ADD COLUMN IF NOT EXISTS procedure_code VARCHAR(100),
    ADD COLUMN IF NOT EXISTS procedure_display VARCHAR(500),
    ADD COLUMN IF NOT EXISTS reason_system VARCHAR(255),
    ADD COLUMN IF NOT EXISTS reason_code VARCHAR(100),
    ADD COLUMN IF NOT EXISTS reason_display VARCHAR(500);

CREATE INDEX IF NOT EXISTS idx_imaging_study_accession ON pacs.imaging_study (tenant_id, accession_number)
    WHERE accession_number IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_imaging_study_facility ON pacs.imaging_study (tenant_id, facility_id)
    WHERE facility_id IS NOT NULL;

CREATE TABLE pacs.imaging_series (
    id                   BIGSERIAL       PRIMARY KEY,
    study_id             BIGINT          NOT NULL REFERENCES pacs.imaging_study (id) ON DELETE CASCADE,
    series_instance_uid  VARCHAR(255)    NOT NULL,
    modality             VARCHAR(20),
    series_number        INT,
    series_description   VARCHAR(500),
    body_part_examined   VARCHAR(500),
    instance_count       INT             NOT NULL DEFAULT 0,
    orthanc_series_id    VARCHAR(255),
    metadata             TEXT,
    created_at           TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT uq_imaging_series_study_uid UNIQUE (study_id, series_instance_uid)
);

CREATE TABLE pacs.imaging_instance (
    id                   BIGSERIAL       PRIMARY KEY,
    series_id            BIGINT          NOT NULL REFERENCES pacs.imaging_series (id) ON DELETE CASCADE,
    sop_instance_uid     VARCHAR(255)    NOT NULL,
    instance_number      INT,
    storage_ref          TEXT            NOT NULL,
    transfer_syntax_uid  VARCHAR(100),
    frame_count          INT             NOT NULL DEFAULT 1,
    preview_ref          TEXT,
    orthanc_instance_id  VARCHAR(255),
    metadata             TEXT,
    created_at           TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT uq_imaging_instance_series_sop UNIQUE (series_id, sop_instance_uid)
);

CREATE TABLE pacs.imaging_archive_object (
    id              BIGSERIAL       PRIMARY KEY,
    study_id        BIGINT          REFERENCES pacs.imaging_study (id) ON DELETE CASCADE,
    series_id       BIGINT          REFERENCES pacs.imaging_series (id) ON DELETE CASCADE,
    instance_id     BIGINT          REFERENCES pacs.imaging_instance (id) ON DELETE CASCADE,
    storage_type    VARCHAR(64)     NOT NULL,
    storage_ref     TEXT            NOT NULL,
    checksum        VARCHAR(128),
    size_bytes      BIGINT,
    status          VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE',
    metadata        TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT ck_imaging_archive_target CHECK (
        study_id IS NOT NULL OR series_id IS NOT NULL OR instance_id IS NOT NULL
    )
);

CREATE TABLE pacs.imaging_viewer_session (
    id               BIGSERIAL       PRIMARY KEY,
    study_id         BIGINT          NOT NULL REFERENCES pacs.imaging_study (id) ON DELETE CASCADE,
    launched_by      TEXT,
    launched_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    session_status   VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE',
    viewer_type      VARCHAR(64)     NOT NULL DEFAULT 'DICOMWEB_STACK',
    context_ref      TEXT,
    scope            TEXT,
    correlation_id   TEXT,
    metadata         TEXT
);

CREATE TABLE pacs.imaging_access_audit (
    id                 BIGSERIAL       PRIMARY KEY,
    study_id           BIGINT          REFERENCES pacs.imaging_study (id) ON DELETE SET NULL,
    series_id          BIGINT          REFERENCES pacs.imaging_series (id) ON DELETE SET NULL,
    instance_id        BIGINT          REFERENCES pacs.imaging_instance (id) ON DELETE SET NULL,
    actor_id           TEXT,
    actor_type         TEXT,
    access_type        VARCHAR(64)     NOT NULL,
    correlation_id     TEXT,
    workflow_context   TEXT,
    metadata           TEXT,
    created_at         TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE TABLE pacs.imaging_report_link (
    id                        BIGSERIAL       PRIMARY KEY,
    study_id                  BIGINT          NOT NULL REFERENCES pacs.imaging_study (id) ON DELETE CASCADE,
    report_ref                TEXT            NOT NULL,
    reporting_provider_ref    TEXT,
    report_status             VARCHAR(64)     NOT NULL DEFAULT 'UNKNOWN',
    signed_at                 TIMESTAMPTZ,
    metadata                  TEXT,
    created_at                TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE TABLE pacs.imaging_order_link (
    id                        BIGSERIAL       PRIMARY KEY,
    study_id                  BIGINT          NOT NULL REFERENCES pacs.imaging_study (id) ON DELETE CASCADE,
    order_ref                 TEXT            NOT NULL,
    ordering_provider_ref     TEXT,
    requested_at              TIMESTAMPTZ,
    status                    VARCHAR(32)     NOT NULL DEFAULT 'LINKED',
    metadata                  TEXT,
    created_at                TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT uq_imaging_order_study_ref UNIQUE (study_id, order_ref)
);

CREATE INDEX idx_imaging_series_study ON pacs.imaging_series (study_id);
CREATE INDEX idx_imaging_instance_series ON pacs.imaging_instance (series_id);
CREATE INDEX idx_archive_study ON pacs.imaging_archive_object (study_id);
CREATE INDEX idx_viewer_study ON pacs.imaging_viewer_session (study_id, launched_at DESC);
CREATE INDEX idx_audit_study_time ON pacs.imaging_access_audit (study_id, created_at DESC);
CREATE INDEX idx_report_study ON pacs.imaging_report_link (study_id);
CREATE INDEX idx_order_study ON pacs.imaging_order_link (study_id);
