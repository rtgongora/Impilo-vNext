-- Simba social REELS. Reels REUSE document-service (8093) for the MinIO pipeline — Simba stores only
-- object references (document_object_id / thumbnail_object_id), NEVER bytes. Upload flow: BFF takes
-- the multipart → DocumentServiceClient.uploadObject (MinIO write) → POST objectId to Simba here.

CREATE TABLE IF NOT EXISTS simba.simba_social_media_asset (
    id              BIGSERIAL PRIMARY KEY,
    media_asset_id  UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    owner_cpid      VARCHAR(128) NOT NULL,
    document_object_id VARCHAR(128) NOT NULL,     -- MinIO object ref (document-service)
    thumbnail_object_id VARCHAR(128),
    content_type    VARCHAR(64),
    media_kind      VARCHAR(16) NOT NULL DEFAULT 'VIDEO',  -- VIDEO | IMAGE | AUDIO
    duration_seconds INT,
    processing_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',  -- PENDING | READY | FAILED
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_simba_media_owner
    ON simba.simba_social_media_asset (tenant_id, owner_cpid, id DESC);

CREATE TABLE IF NOT EXISTS simba.simba_social_reel (
    id              BIGSERIAL PRIMARY KEY,
    reel_id         UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    creator_cpid    VARCHAR(128) NOT NULL,
    creator_type    VARCHAR(24) NOT NULL DEFAULT 'CLIENT',
    provider_id     VARCHAR(128),
    programme_id    UUID,
    media_asset_id  UUID NOT NULL REFERENCES simba.simba_social_media_asset(media_asset_id),
    caption         VARCHAR(500),
    fundo_content_ref VARCHAR(128),           -- optional Fundo learning link (Simba never duplicates content)
    verified_badge  VARCHAR(24),              -- PROVIDER | PROGRAMME | NONE
    visibility      VARCHAR(32) NOT NULL DEFAULT 'PUBLIC_WITHIN_IMPILO',
    approval_status VARCHAR(16) NOT NULL DEFAULT 'APPROVED',  -- PENDING | APPROVED | REJECTED
    moderation_status VARCHAR(16) NOT NULL DEFAULT 'VISIBLE',
    view_count      BIGINT NOT NULL DEFAULT 0,
    reaction_count  INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_simba_reel_creator
    ON simba.simba_social_reel (tenant_id, creator_cpid, id DESC);
CREATE INDEX IF NOT EXISTS idx_simba_reel_feed
    ON simba.simba_social_reel (tenant_id, moderation_status, approval_status, id DESC);
