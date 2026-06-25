-- =============================================
-- Service : oros-service
-- Flyway  : V008__order_study_linkage.sql
-- Purpose : Surface the linked PACS/DICOM study on the order (spec §4 PACS/DICOM metadata) so the
--           order-tracking and patient-file views can show study UID + viewer launch without a
--           second lookup. The authoritative study/series/instance model remains in
--           pacs-adapter-service; these are denormalized references set when a study is linked.
-- =============================================

ALTER TABLE oros_orders
    ADD COLUMN study_uid        VARCHAR(128),
    ADD COLUMN study_viewer_url VARCHAR(512);

CREATE INDEX idx_oros_orders_study_uid
    ON oros_orders (tenant_id, study_uid)
    WHERE study_uid IS NOT NULL;
