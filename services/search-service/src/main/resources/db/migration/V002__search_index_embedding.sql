-- Optional dense embedding for semantic / hybrid ranking (JSON array of floats).
ALTER TABLE ss_search_index ADD COLUMN embedding_json TEXT;

COMMENT ON COLUMN ss_search_index.embedding_json IS
    'L2-normalized embedding vector as JSON array of floats; null when not computed.';
