-- ============================================================================
-- workforce-governance-service V012 — normalize iso_country_code to VARCHAR(2).
--
-- V005 created wgv_country_operation.iso_country_code as CHAR(2), which
-- Postgres stores as bpchar. The JPA entity CountryOperationEntity maps it as
-- @Column(length = 2) → varchar(2), so Hibernate ddl-auto=validate rejected the
-- column ("found [bpchar], but expecting [varchar(2)]") and the service
-- crash-looped. Forward-fix the column type to match the entity. V005 is left
-- untouched to preserve its applied checksum.
-- ============================================================================

ALTER TABLE wgv_country_operation
    ALTER COLUMN iso_country_code TYPE VARCHAR(2);
