-- V029: Replay media adoption + watch progress (LEARNING_RECORDING W4).
--
-- Wires the W1 recording pipeline's replay artifacts into Fundo learning truth:
-- when live-service publishes impilo.live.replay.published.v1 for a live event
-- linked to a scheduled learning session (V027 live_event_id), learning-service
-- adopts the replay as a lrn_media_asset whose storage_ref is the
-- document-service OBJECT ID (typed via the new storage_kind column — the SoR
-- for the artifact stays document-service; learning stores the reference only).
--
-- New per-enrolment watch truth (lrn_media_watch_progress) backs the V028
-- WATCH_THRESHOLD completion rule, which stops being an honest-false placeholder
-- in this wave. Chapters and bookmarks complete the recorded-playback surface.
--
-- PK convention: like every other recent lrn_* table (V028) ids have no database
-- default; the JPA entities generate the UUID in @PrePersist.

-- ── lrn_media_asset: typed storage + replay linkage + authoring queue flag ───
--
--   storage_kind   — how to interpret storage_ref:
--                      DOCUMENT_OBJECT — a document-service object id (UUID);
--                                        playback URLs are minted per-request via
--                                        the document-service signed-url endpoint.
--                      EXTERNAL_URL    — a directly usable URL (legacy authoring).
--                    NULL (legacy rows) — opaque string, semantics unspecified.
--   live_event_id  — the Impilo Live event whose replay produced this asset;
--                    unique per tenant so replay.published redeliveries are
--                    idempotent upserts.
--   course_id      — course linkage resolved from the scheduled learning session
--                    (V027) at adoption time; lesson attachment goes through
--                    source_lesson_id (V008) when it resolves deterministically.
--   unattached     — TRUE = the asset landed in the authoring queue because no
--                    lesson could be resolved deterministically; an author
--                    attaches it via POST /v11/media/{assetId}/attach.

ALTER TABLE lrn_media_asset
    ADD COLUMN IF NOT EXISTS storage_kind VARCHAR(32) NULL
        CONSTRAINT ck_lrn_media_asset_storage_kind
        CHECK (storage_kind IN ('DOCUMENT_OBJECT', 'EXTERNAL_URL'));

ALTER TABLE lrn_media_asset
    ADD COLUMN IF NOT EXISTS live_event_id UUID NULL;

ALTER TABLE lrn_media_asset
    ADD COLUMN IF NOT EXISTS course_id UUID NULL REFERENCES lrn_course(id) ON DELETE SET NULL;

ALTER TABLE lrn_media_asset
    ADD COLUMN IF NOT EXISTS unattached BOOLEAN NOT NULL DEFAULT FALSE;

CREATE UNIQUE INDEX IF NOT EXISTS uq_lrn_media_asset_live_event
    ON lrn_media_asset (tenant_id, live_event_id)
    WHERE live_event_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_lrn_media_asset_course
    ON lrn_media_asset (tenant_id, course_id)
    WHERE course_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_lrn_media_asset_lesson
    ON lrn_media_asset (tenant_id, source_lesson_id)
    WHERE source_lesson_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_lrn_media_asset_unattached
    ON lrn_media_asset (tenant_id, unattached)
    WHERE unattached = TRUE;

-- ── Watch progress (per enrolment + asset) ───────────────────────────────────
--
-- watched_seconds / furthest_position_seconds are MONOTONIC (writers take the
-- max of stored vs reported) so debounced client upserts and event redeliveries
-- are idempotent. percent is derived from watched_seconds / duration_seconds,
-- clamped 0..100. last_position_seconds is the resume point (may move backwards
-- when the learner scrubs); furthest_position_seconds never does.

CREATE TABLE IF NOT EXISTS lrn_media_watch_progress (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    enrolment_id UUID NOT NULL REFERENCES lrn_enrolment(id) ON DELETE CASCADE,
    asset_id UUID NOT NULL REFERENCES lrn_media_asset(id) ON DELETE CASCADE,
    watched_seconds INT NOT NULL DEFAULT 0,
    duration_seconds INT NULL,
    percent INT NOT NULL DEFAULT 0
        CONSTRAINT ck_lrn_media_watch_percent CHECK (percent BETWEEN 0 AND 100),
    furthest_position_seconds INT NOT NULL DEFAULT 0,
    last_position_seconds INT NOT NULL DEFAULT 0,
    completed_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_lrn_media_watch_progress UNIQUE (enrolment_id, asset_id)
);

CREATE INDEX IF NOT EXISTS idx_lrn_media_watch_tenant_asset
    ON lrn_media_watch_progress (tenant_id, asset_id);

-- ── Chapters (authored navigation markers on an asset) ──────────────────────

CREATE TABLE IF NOT EXISTS lrn_media_chapter (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    asset_id UUID NOT NULL REFERENCES lrn_media_asset(id) ON DELETE CASCADE,
    title VARCHAR(512) NOT NULL,
    start_seconds INT NOT NULL
        CONSTRAINT ck_lrn_media_chapter_start CHECK (start_seconds >= 0),
    chapter_order INT NOT NULL DEFAULT 0,
    created_by VARCHAR(255) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_lrn_media_chapter_asset
    ON lrn_media_chapter (asset_id, chapter_order, start_seconds);

-- ── Bookmarks (learner-private markers per enrolment + asset) ────────────────

CREATE TABLE IF NOT EXISTS lrn_media_bookmark (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    enrolment_id UUID NOT NULL REFERENCES lrn_enrolment(id) ON DELETE CASCADE,
    asset_id UUID NOT NULL REFERENCES lrn_media_asset(id) ON DELETE CASCADE,
    position_seconds INT NOT NULL
        CONSTRAINT ck_lrn_media_bookmark_position CHECK (position_seconds >= 0),
    note TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_lrn_media_bookmark_enrolment_asset
    ON lrn_media_bookmark (enrolment_id, asset_id, position_seconds);
