-- V025: Library hardening — richer governance metadata + access control.
-- All additive/nullable. storage_ref stays a document-service objectId (consume
-- document-service for real uploads; see docs/policy/fundo-library-uploads.md).

ALTER TABLE lrn_library_resource ADD COLUMN IF NOT EXISTS review_date DATE NULL;
ALTER TABLE lrn_library_resource ADD COLUMN IF NOT EXISTS expiry_date DATE NULL;
ALTER TABLE lrn_library_resource ADD COLUMN IF NOT EXISTS target_audience VARCHAR(255) NULL;
ALTER TABLE lrn_library_resource ADD COLUMN IF NOT EXISTS cadre VARCHAR(255) NULL;
ALTER TABLE lrn_library_resource ADD COLUMN IF NOT EXISTS programme VARCHAR(255) NULL;
ALTER TABLE lrn_library_resource ADD COLUMN IF NOT EXISTS facility_relevance VARCHAR(255) NULL;
ALTER TABLE lrn_library_resource ADD COLUMN IF NOT EXISTS access_level VARCHAR(32) NOT NULL DEFAULT 'INTERNAL';
ALTER TABLE lrn_library_resource ADD COLUMN IF NOT EXISTS ai_usage_permission BOOLEAN NOT NULL DEFAULT FALSE;
