# Session Suite — LEARNING_RECORDING (Runbook)

Recorded-media learning: a governed classroom recording becomes a replay,
Fundo adopts it as a media asset, and **watch truth** (monotonic watch
progress against a `WATCH_THRESHOLD` rule) drives completion. Proven
end-to-end on the live preview estate (2026-07-05/06). Design detail:
[`docs/product/learning-recording-w4-course-player.md`](../product/learning-recording-w4-course-player.md).

## What the mode is (doctrine)

Template: [`contracts/schemas/session-templates/learning-recording.json`](../../contracts/schemas/session-templates/learning-recording.json)
(`sessionMode: LEARNING_RECORDING`, `owningService: FUNDO`, no live modes —
this is a **player** mode, not a room mode).

- **Layout** `player`, join flow `DIRECT`, `maxParticipants: 0` (no room), all
  role grants false — there is no media room to join; the mode governs
  playback of owned artifacts.
- Room prefix `impilo-learn-replay`, audit depth `FULL`, post-session
  `ATTENDANCE_MAP / CERT_ELIGIBILITY`.
- Completion criteria ref: Fundo `lrn_completion_rule` (`WATCH_THRESHOLD`,
  percent, default 90).
- Notification keys: `learning.recording.published`,
  `learning.completion.achieved`, `learning.certificate.issued`.

## The journey

**Actors**: facilitator records; learner watches.

**Surfaces**: web `CoursePlayer` module
(`ui/one-ui-shell/src/components/learning/player/` — signed-URL playback,
resume from furthest position, debounced watch upserts, chapters, bookmarks,
transcript pane, completion banner); citizen-app `CoursePlayerScreen`
(expo-video, same watch contract); live replay page
`/live/event/[eventId]/replay`.

**Services & events** (the full pipeline):

1. **Record** (W1): facilitator starts a template-gated recording →
   rtc-gateway drives LiveKit RoomCompositeEgress → artifact lands in MinIO →
   egress webhook completes `rtc.recordings` → document-service
   `register-external` adopts the object (document-service is the artifact
   SoR).
2. **Publish** (W1): event ends → live-service replay pipeline →
   `PUBLISHED_REPLAY` → `impilo.live.replay.published.v1` with the artifact
   payload (`documentObjectId`, `sessionId`, `durationSeconds`, `egressId`).
3. **Adopt** (W4): learning-service `LiveReplayConsumer` joins the event to
   the scheduled session and upserts `lrn_media_asset`
   (`storage_kind = DOCUMENT_OBJECT`, idempotent on
   `(tenant_id, live_event_id)`); deterministic lesson attachment or the
   unattached authoring queue.
4. **Watch** (W4): BFF `GET /internal/v1/learning/v11/media/{assetId}/playback`
   exchanges the enrolment-gated ref for a time-limited document-service
   signed URL; the player upserts monotonic `lrn_media_watch_progress`.
5. **Complete**: `WATCH_THRESHOLD` evaluates real watch percent over every
   course-bound VIDEO asset → enrolment `COMPLETED` → certificate issued
   idempotently on the rules path (`c6e55aa04`).

## Proving it on the preview estate

```bash
bash scripts/e2e/learning-recording-proof.sh     # 15 checks
```

The proof runs the whole chain live: course + `WATCH_THRESHOLD(90)` rule →
live classroom with real browser media → facilitator records via governed
egress → replay `PUBLISHED_REPLAY` → `lrn_media_asset` adoption → signed
playback URL serves real mp4 bytes → watch progress ≥ 90% → policy completes
the enrolment → certificate.

An interactive player variant is `e2e/course-player.spec.ts`
(orchestrated-only, env-gated).

## Key contracts

- Learning never mints URLs: `storage_ref` holds the document-service object
  id; signed URLs are exchanged at playback time.
- Watch progress is monotonic (`watched_seconds`,
  `furthest_position_seconds`) — rewinding cannot un-watch.
- A course with **no** course-bound video assets evaluates `WATCH_THRESHOLD`
  to FALSE (honest, mirrors `ATTENDANCE_THRESHOLD`).
- Replay-page watch minutes (live attendance ledger) and course watch
  progress (completion ledger) are different ledgers by design.

## Failure modes seen and their fixes

| Failure | Fix |
|---|---|
| Recording artifact arriving **before** room finish → replay never published | run the replay pipeline on session end when an artifact is registered — both arrival orders converge on `PUBLISHED_REPLAY` (`5e0558663`) |
| `replay.published` emitted a bare payload — consumers had nothing to adopt | publish with the artifact payload (`db91cf870`) |
| Rule-governed completions transitioned the enrolment but never issued a certificate | idempotent issue on the rules path (`c6e55aa04`) |

## Known limits (honest seams)

- **ASR transcript generation is NOT built** — `lrn_media_asset.transcript`
  renders in `TranscriptPane` when present (offline-produced); there is no
  speech service in the estate and no "generating…" theatre.
- **Provider-app course player deferred** — the provider app joins live
  classrooms but has no recorded-course player; the citizen app and web do.
- **Recording retention/lifecycle is undefined** — document-service catalogs
  artifacts and can delete via the Landela lifecycle call, but no retention
  policy exists for recordings.
