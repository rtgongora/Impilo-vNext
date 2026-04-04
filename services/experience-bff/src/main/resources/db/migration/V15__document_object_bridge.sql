-- V15: Add document-service object ID bridge column to clinical_documents.
-- Links BFF clinical document metadata with the document-service's
-- MinIO-backed binary object storage for download URL generation.

ALTER TABLE clinical_documents ADD COLUMN IF NOT EXISTS document_object_id UUID;
CREATE INDEX IF NOT EXISTS idx_clinical_documents_object ON clinical_documents(document_object_id);
