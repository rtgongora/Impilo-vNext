-- Per-facility imaging capability (deployment mode) + facility modality/machine registry.
-- Closes the "one global PACS config" gap: each facility's true imaging integration
-- state (existing PACS/VNA, gateway store-and-forward, machines-only, manual import,
-- digitisation bridge, referral-only, offline-sync, none) becomes representable.

CREATE TABLE pacs.facility_imaging_capability (
    id                              BIGSERIAL       PRIMARY KEY,
    tenant_id                       UUID            NOT NULL,
    facility_id                     VARCHAR(128)    NOT NULL,
    facility_name                   VARCHAR(255),
    deployment_mode                 VARCHAR(40)     NOT NULL DEFAULT 'NONE',
    pacs_vna_available              BOOLEAN         NOT NULL DEFAULT false,
    local_gateway_available         BOOLEAN         NOT NULL DEFAULT false,
    dicomweb_available              BOOLEAN         NOT NULL DEFAULT false,
    dimse_available                 BOOLEAN         NOT NULL DEFAULT false,
    mwl_supported                   BOOLEAN         NOT NULL DEFAULT false,
    cstore_receive_supported        BOOLEAN         NOT NULL DEFAULT false,
    mpps_supported                  BOOLEAN         NOT NULL DEFAULT false,
    store_forward_supported         BOOLEAN         NOT NULL DEFAULT false,
    local_cache_supported           BOOLEAN         NOT NULL DEFAULT false,
    manual_import_supported         BOOLEAN         NOT NULL DEFAULT false,
    digitisation_bridge_supported   BOOLEAN         NOT NULL DEFAULT false,
    viewer_available                BOOLEAN         NOT NULL DEFAULT false,
    reporting_available             BOOLEAN         NOT NULL DEFAULT false,
    internet_quality                VARCHAR(30),
    operational_status              VARCHAR(30)     NOT NULL DEFAULT 'UNKNOWN',
    default_archive_target          VARCHAR(255),
    default_worklist_target         VARCHAR(255),
    supported_modalities            JSONB,
    configuration_status            VARCHAR(30)     NOT NULL DEFAULT 'UNCONFIGURED',
    go_live_readiness               VARCHAR(30)     NOT NULL DEFAULT 'NOT_READY',
    last_heartbeat_at               TIMESTAMPTZ,
    notes                           TEXT,
    updated_by                      TEXT,
    created_at                      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at                      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT uq_facility_imaging_capability UNIQUE (tenant_id, facility_id)
);

CREATE INDEX idx_fic_facility ON pacs.facility_imaging_capability (facility_id);
CREATE INDEX idx_fic_mode ON pacs.facility_imaging_capability (deployment_mode);

CREATE TABLE pacs.imaging_modality (
    id                              BIGSERIAL       PRIMARY KEY,
    tenant_id                       UUID            NOT NULL,
    facility_id                     VARCHAR(128)    NOT NULL,
    modality_type                   VARCHAR(20)     NOT NULL,
    name                            VARCHAR(255)    NOT NULL,
    ae_title                        VARCHAR(64),
    host                            VARCHAR(255),
    port                            INT,
    called_ae_title                 VARCHAR(64),
    calling_ae_title                VARCHAR(64),
    vendor                          VARCHAR(255),
    model                           VARCHAR(255),
    serial_number                   VARCHAR(128),
    supports_dicom_storage          BOOLEAN         NOT NULL DEFAULT false,
    supports_worklist               BOOLEAN         NOT NULL DEFAULT false,
    supports_mpps                   BOOLEAN         NOT NULL DEFAULT false,
    supports_dicomweb               BOOLEAN         NOT NULL DEFAULT false,
    export_options                  JSONB,
    operational_status              VARCHAR(30)     NOT NULL DEFAULT 'UNKNOWN',
    last_seen_at                    TIMESTAMPTZ,
    default_storage_destination     VARCHAR(255),
    default_worklist_source         VARCHAR(255),
    notes                           TEXT,
    active                          BOOLEAN         NOT NULL DEFAULT true,
    updated_by                      TEXT,
    created_at                      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at                      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_imaging_modality_facility ON pacs.imaging_modality (tenant_id, facility_id, active);
CREATE UNIQUE INDEX uq_imaging_modality_aet ON pacs.imaging_modality (tenant_id, facility_id, ae_title)
    WHERE ae_title IS NOT NULL;
