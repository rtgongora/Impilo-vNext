-- =============================================
-- Service : oros-service
-- Flyway  : V014__histopathology_report.sql
-- Purpose : Histopathology report depth (spec §11) as a structured child of a result. A histopath
--           report carries the diagnostic narrative sections (gross / microscopic / diagnosis /
--           ancillary tests) that a plain result summary cannot express, plus a pathologist
--           sign-out gate. Sign-out routes a FINAL result through the versioned reporting lifecycle
--           (ReportService) so the SHR DiagnosticReport + HL7 ORU are produced for free; the
--           returned result_id + Butano ref are stored back on this row for lineage.
-- =============================================

CREATE TABLE oros_histopath_report (
    report_id               UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id                VARCHAR(26)     NOT NULL,
    result_id               UUID            REFERENCES oros_results(result_id),
    tenant_id               UUID            NOT NULL,
    specimen_ref            VARCHAR(128),
    gross_description        TEXT,
    microscopic_description  TEXT,
    diagnosis               TEXT,
    ancillary_tests         JSONB,
    reporting_pathologist   VARCHAR(128),
    sign_out_status         VARCHAR(16)     NOT NULL DEFAULT 'DRAFT',   -- DRAFT | FINAL
    signed_out_at           TIMESTAMPTZ,
    critical                BOOLEAN         NOT NULL DEFAULT FALSE,
    critical_reason         TEXT,
    acknowledged_at         TIMESTAMPTZ,
    acknowledged_by         VARCHAR(128),
    butano_report_ref       VARCHAR(128),
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_oros_histopath_order ON oros_histopath_report (order_id);
CREATE INDEX idx_oros_histopath_tenant_status ON oros_histopath_report (tenant_id, sign_out_status);
