-- Zimbabwe administrative reference (districts / wards) and Tuso locality gazetteer.
-- Rows are populated only via COD-AB / ZIMSTAT-aligned bulk import — no invented geometry.

CREATE TABLE tuso.zw_admin_district (
    id              BIGSERIAL       PRIMARY KEY,
    province_code   VARCHAR(16)     NOT NULL,
    district_code   VARCHAR(64)     NOT NULL UNIQUE,
    name            VARCHAR(512)    NOT NULL,
    source_ref      VARCHAR(512),
    imported_at     TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_zw_admin_district_province ON tuso.zw_admin_district (province_code);

CREATE TABLE tuso.zw_admin_ward (
    id              BIGSERIAL       PRIMARY KEY,
    district_code   VARCHAR(64)     NOT NULL REFERENCES tuso.zw_admin_district (district_code) ON DELETE CASCADE,
    ward_code       VARCHAR(64)     NOT NULL,
    name            VARCHAR(512)    NOT NULL,
    source_ref      VARCHAR(512),
    imported_at     TIMESTAMPTZ     NOT NULL DEFAULT now(),
    UNIQUE (district_code, ward_code)
);

CREATE INDEX idx_zw_admin_ward_district ON tuso.zw_admin_ward (district_code);

CREATE TABLE tuso.locality (
    id                  BIGSERIAL       PRIMARY KEY,
    district_code       VARCHAR(64)     NOT NULL REFERENCES tuso.zw_admin_district (district_code),
    ward_code           VARCHAR(64),
    name                VARCHAR(512)    NOT NULL,
    name_normalized     VARCHAR(512)    NOT NULL,
    status              VARCHAR(32)     NOT NULL DEFAULT 'APPROVED',
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_locality_district ON tuso.locality (district_code);
CREATE INDEX idx_locality_name_norm ON tuso.locality (name_normalized);

CREATE TABLE tuso.locality_proposal (
    id              BIGSERIAL       PRIMARY KEY,
    district_code   VARCHAR(64)     NOT NULL,
    ward_code       VARCHAR(64),
    proposed_name   VARCHAR(512)    NOT NULL,
    status          VARCHAR(32)     NOT NULL DEFAULT 'PENDING',
    reviewer_note   VARCHAR(1024),
    locality_id     BIGINT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    decided_at      TIMESTAMPTZ
);

CREATE INDEX idx_locality_proposal_status ON tuso.locality_proposal (status);
CREATE INDEX idx_locality_proposal_district ON tuso.locality_proposal (district_code);
