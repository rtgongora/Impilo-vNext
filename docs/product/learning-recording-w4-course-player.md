# LEARNING_RECORDING W4 — Replay Adoption, Course Player & Watch-Based Completion (2026-07-05)

Wave 4 of the Adaptive Real-Time Session Suite. Builds on W1 (recording pipeline:
rtc RoomCompositeEgress → MinIO → document-service `register-external` →
live-service `PUBLISHED_REPLAY` emitting `impilo.live.replay.published.v1`) and
W3 (learning-live linkage V027 + completion rules V028, where `WATCH_THRESHOLD`
was an honest-false placeholder).

## What is REAL after this wave

### learning-service (V029)
- **Replay adoption.** `LiveReplayConsumer` subscribes to
  `impilo.live.replay.published.v1` — never raw `impilo.rtc.*` (layering law) —
  joins the live event to a scheduled learning session via V027
  `live_event_id`, and `FundoReplayMediaService` upserts a `lrn_media_asset`
  idempotently (V029 unique `(tenant_id, live_event_id)`). The document-service
  **object id** is stored in `storage_ref`, typed by the new
  `storage_kind = DOCUMENT_OBJECT` column — document-service stays the artifact
  SoR; learning never mints URLs.
- **Deterministic lesson attachment.** Attach only when unambiguous: explicit
  `lessonId` in the session's `metadata_json`, or a course with exactly one
  VIDEO lesson. Everything else lands in the **unattached authoring queue**
  (`unattached=true`; `GET /v11/media/replay-queue`,
  `POST /v11/media/{assetId}/attach`). Redeliveries never detach an author's
  explicit attachment.
- **Watch truth.** `lrn_media_watch_progress` (per enrolment + asset;
  monotonic `watched_seconds` / `furthest_position_seconds`, resume point
  `last_position_seconds`, derived `percent`), `lrn_media_chapter`,
  `lrn_media_bookmark`. APIs on `FundoMediaController`
  (`/internal/v1/learning/v11/media/**`, enrolment-membership enforced,
  v1.1 outbox audit events `media.replay_adopted / media.attached /
  media.watch_milestone`).
- **WATCH_THRESHOLD is now real.** `FundoCompletionPolicyService` evaluates it
  against watch progress: `threshold_value` is PERCENT (default 90); **every**
  course-bound VIDEO asset must reach the threshold; a course with no
  course-bound video assets evaluates FALSE (mirrors ATTENDANCE_THRESHOLD
  honesty). Milestone → completion wiring: lesson-attached assets drive lesson
  progress to 100 via `FundoProgressService.recordProgress` (aggregate
  reconcile + `completeIfEligible`); lesson-less assets re-run
  `completeIfEligible` only on courses WITH rules (a rule-less
  `completeIfEligible` completes unconditionally, so a watch milestone alone
  never auto-completes a rule-less course).

### experience-bff
- `GET /internal/v1/learning/v11/media/{assetId}/playback?enrolmentId=` —
  resolves the enrolment-gated playback-ref from learning-service, then
  exchanges `DOCUMENT_OBJECT` refs for time-limited document-service signed
  URLs (legacy `EXTERNAL_URL` assets pass through). Watch-progress, chapters,
  bookmarks (new `deleteV11` verb), lesson-media, replay-queue and attach are
  thin passthroughs.

### Web (ui/one-ui-shell)
- `src/components/learning/player/` — `CoursePlayer` (signed-URL playback,
  resume from furthest position, debounced watch upserts via
  `useWatchProgress`), `PlaybackControls`, `ChapterSidebar`, `BookmarkList`,
  `TranscriptPane`, `CompletionBanner`, `QuizInterrupt`,
  `SessionReplayPlayer`, `LessonMediaPlayer`.
- `FundoLessonContent` delegates VIDEO lessons to the player when the lesson
  has a governed asset AND enrolment context; the legacy contentRef
  iframe/native rendering is preserved otherwise (fallback, unchanged tests).
- The live replay page (`/live/event/[eventId]/replay`) plays http(s) signed
  playback URLs in `SessionReplayPlayer`; LiveKit room replays unchanged.
- `QuizInterrupt` mounts only when the course's completion rules include
  `QUIZ_REQUIRED` and prompts into the existing assessment flow — it is NOT an
  in-player quiz engine (that would fork assessment truth).

### Mobile (citizen-app)
- `CoursePlayerScreen` (expo-video) with the same watch contract: signed-URL
  playback, 10 s debounced monotonic upserts, resume from furthest position,
  chapters, completion feedback, transcript when present. Reached from
  FundoLearningScreen's "Continue learning" section. `expo-video` is wired in
  BOTH `app.json` and `app.config.ts` (the config replaces plugins wholesale).

## HONEST SEAMS (owner / reason / next step)

1. **ASR / transcript generation — NOT BUILT (deliberate).**
   - *Owner:* learning-service holds the `lrn_media_asset.transcript` field;
     document-service holds the artifact.
   - *Reason:* there is no speech-recognition service in the estate; wiring a
     fake or an unbudgeted external dependency would be dishonest.
   - *Today:* transcripts arrive via **offline production** — an author (or an
     external transcription workflow) writes the transcript onto the asset via
     the studio surface / register-external companion flows. `TranscriptPane`
     renders only when a transcript exists; no "generating…" theatre.
   - *Next step:* if ASR is funded, a media-intelligence worker should consume
     `impilo.learning.media.replay_adopted.v1`, fetch the artifact via a signed
     URL, and PATCH the transcript — no schema change needed.
2. **Provider-app course player — citizen-only for now.**
   - *Reason:* the screen composes citizen-app-local services and the
     design-system shell; `packages/mobile-session` is a LiveKit room package,
     not a recorded-media home. Half-sharing would fork rather than share.
   - *Next step:* lift `coursePlayerService` + the screen into a
     `packages/mobile-learning` module when provider parity is scheduled.
3. **Web lesson→asset resolution is per-lesson.** `LessonMediaPlayer` queries
   `lessons/{id}/media` per mount; a structure-level batch endpoint can come
   later if lesson lists get hot.
4. **Live-estate proof pending.** `e2e/course-player.spec.ts` is written
   orchestrated-only (env-gated like `classroom-media-hold.spec.ts`) and needs
   the coordinator's estate run: publish a replay through the W1 pipeline, then
   run with `COURSE_PLAYER_ENROLMENT_ID` / `COURSE_PLAYER_LESSON_ID`.
5. **Replay-page watch minutes vs course watch progress.** The live replay page
   keeps the existing `useLiveTrackMinutes` accounting (live-service
   attendance truth); course watch progress accrues only through the
   enrolment-scoped CoursePlayer. These are different ledgers by design
   (attendance vs completion), not a duplication.

## Test evidence (local, this wave)
- learning-service: `mvn -pl learning-service test` → **122 tests, 0 failures**
  (incl. new: LiveReplayConsumerTest 5, FundoReplayMediaServiceTest 5,
  FundoMediaWatchServiceTest 12, FundoCompletionPolicyServiceTest 19 with the
  real WATCH_THRESHOLD matrix).
- experience-bff: **747 tests, 0 failures** (8 LearningControllerTest incl.
  signed-URL exchange + legacy passthrough).
- web: `tsc --noEmit` clean; vitest **1426/1426** (18 player-module tests:
  resume, debounce, threshold-fire, chapters, bookmarks, honest-unavailable).
- mobile citizen-app: type-check clean for wave files (5 pre-existing
  emergency-screen errors on baseline); vitest **177/177**.
