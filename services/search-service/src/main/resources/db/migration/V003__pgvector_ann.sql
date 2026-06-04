-- pgvector ANN column (fixed width must match impilo.search.pgvector.dimensions, default 1536).
-- Skipped when the Postgres image has no vector extension (e.g. slim preview Postgres).

DO $mig$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_available_extensions WHERE name = 'vector') THEN
    RAISE NOTICE 'Skipping pgvector ANN migration: vector extension not available on this Postgres build';
    RETURN;
  END IF;

  CREATE EXTENSION IF NOT EXISTS vector;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'ss_search_index' AND column_name = 'embedding_vec'
  ) THEN
    EXECUTE 'ALTER TABLE ss_search_index ADD COLUMN embedding_vec vector(1536)';
    EXECUTE $comment$
      COMMENT ON COLUMN ss_search_index.embedding_vec IS
        'pgvector column for ANN recall; must match application pgvector.dimensions (Flyway fixed at 1536).'
    $comment$;
    EXECUTE $index$
      CREATE INDEX idx_ss_si_embedding_vec_hnsw
        ON ss_search_index
        USING hnsw (embedding_vec vector_cosine_ops)
        WITH (m = 16, ef_construction = 64)
    $index$;
  END IF;
END $mig$;
