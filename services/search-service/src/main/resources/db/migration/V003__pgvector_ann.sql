-- pgvector ANN column (fixed width must match impilo.search.pgvector.dimensions, default 1536).
-- Requires CREATE privilege on the database for CREATE EXTENSION (often superuser first deploy).

CREATE EXTENSION IF NOT EXISTS vector;

ALTER TABLE ss_search_index ADD COLUMN embedding_vec vector(1536);

COMMENT ON COLUMN ss_search_index.embedding_vec IS
    'pgvector column for ANN recall; must match application pgvector.dimensions (Flyway fixed at 1536).';

-- Cosine-distance ANN index (HNSW). Safe on empty/small tables; tune m / ef_construction for scale.
CREATE INDEX idx_ss_si_embedding_vec_hnsw
    ON ss_search_index
    USING hnsw (embedding_vec vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);
