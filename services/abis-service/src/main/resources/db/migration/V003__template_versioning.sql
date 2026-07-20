-- ABIS — Template versioning + quality history (Identity Contract §11, W3d).
-- Re-enrolment SUPERSEDES the prior ACTIVE template (keeping history) rather than
-- overwriting it in place: prior rows are retained with status='SUPERSEDED' and the
-- new row carries version = prior.version + 1. A confirmed-duplicate merge marks the
-- duplicate subject's templates status='MERGED'.

ALTER TABLE abis.template ADD COLUMN version INTEGER NOT NULL DEFAULT 1;

-- The original constraint uq_template_subject (tenant, subject_ref, modality, position)
-- forced UPSERT/in-place overwrite. Replace it with a PARTIAL unique index that allows
-- exactly one ACTIVE template per (tenant, subject_ref, modality, position) while
-- retaining any number of SUPERSEDED / MERGED historical rows.
ALTER TABLE abis.template DROP CONSTRAINT uq_template_subject;

CREATE UNIQUE INDEX uq_template_active
    ON abis.template (tenant_id, subject_ref, modality, position)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_template_history
    ON abis.template (tenant_id, subject_ref, modality, position, version DESC);
