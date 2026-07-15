# Simba Wellness-Social Layer — Product Truth

The wellness-**sovereign** social layer lives inside `simba-service` under the DISTINCT namespace
`/internal/v1/wellness/social/**` (separate from the generic `community-service` `/internal/v1/social/**`,
which is actor-string-keyed with no person_cpid / consent / care_linkage). Every read path goes through
`SocialVisibilityService` (consent + sensitive-category gating); every meaningful write emits an outbox
event and audits via `WellnessAuditService`. Media (reels) is object-ref only via `document-service`
MinIO — Simba never stores bytes.

## Capability status (this completion wave — S2/S3 closeout)

| Capability | Persistence | Endpoints | Enforcement | Tests | Status |
|---|---|---|---|---|---|
| **Groups CRUD** | `simba_group`, `simba_group_membership` (V009) | `SocialGroupController` `/wellness/social/groups/**` — create/list/detail/members/join/approve/role/leave/pin/announcements | Role lattice OWNER/ADMIN/MODERATOR/VERIFIED_PROVIDER/PROGRAMME_OFFICER/MEMBER/CAREGIVER/VIEWER; posting/comment permission (VIEWER + per-membership override) enforced at `SocialPostController` for group posts | `SocialGroupServiceTest` (15) + IT `groupJourney_createJoinPostAnnounceModerate` | **COMPLETE** |
| **Communities CRUD** | `simba_community`, `simba_community_membership` (V009) | `SocialCommunityController` `/wellness/social/communities/**` — create/list/detail/members/join/leave/role/announcements | Admin role lattice; official announcements only | `SocialCommunityServiceTest` (3) | **COMPLETE** |
| **Official announcements** | pinned + `official` post (`simba_social_post.official`, V013) | `POST …/groups/{id}/announcements`, `…/communities/{id}/announcements` | Official-role only; body milestone-sanitised (no clinical values); emits `SIMBA_GROUP_ANNOUNCEMENT_PUBLISHED` | covered by group/community service tests | **COMPLETE** |
| **Challenge share-to-feed** | `challenges` + `challenge_participants` ALTERs (V012: group_id/community_id/programme_id/campaign_flag/visibility/moderation_status; shared_to_feed/share_post_id/rank) | `SocialChallengeController` `/wellness/social/challenges/{id}/share`, `/leaderboard`, `/campaign-aggregate` | Participant-only share; sanitised post; ranked leaderboard; aggregate has no per-person rows | `SocialChallengeServiceTest` (4) incl. no-per-person-leak | **COMPLETE** |
| **Social notifications** | `simba_social_notification_event` (V011) | `SocialNotificationController` `/wellness/social/notifications/**` — enqueue/inbox/mark-read | Recipient-scoped inbox (X-Actor-ID); emits `SIMBA_SOCIAL_NOTIFICATION_REQUESTED` / `SIMBA_GROUP_ANNOUNCEMENT_PUBLISHED` / `SIMBA_CHALLENGE_REMINDER_REQUESTED` / `SIMBA_MODERATION_ACTION_TAKEN` | `SocialNotificationServiceTest` (4) | **COMPLETE (delivery seam: Khuluma)** |
| **Programme-engagement dashboard** | JdbcTemplate aggregate over social tables | `ProgrammeEngagementController` `/wellness/social/programme-engagement/dashboard` | Gated PROGRAMME_ADMIN via `WellnessAccessGuard.requireAggregate`; **aggregate-only, no cpid in output** | `ProgrammeEngagementServiceTest` (1) asserts no per-person leak | **COMPLETE** |
| **Moderation + safety** | `simba_content_report`, `simba_moderation_action` (V010) | `SocialModerationController` `/wellness/social/moderation/**` | SELF_HARM/ABUSE → `CareLinkageService.route(...)` (persisted PCT escalation); action notifies affected party | `SocialModerationServiceTest` (4) + IT `selfHarmReport_escalatesToCareLinkage` | **COMPLETE (built prior wave; notify wired now)** |

## Surfaces

- **Web** (`ui/one-ui-shell/src/app/wellness/social/**`): `groups` (list/new/[id]/members), `communities`
  (list/[id]), `challenges` (list/[id] with share + leaderboard), `moderation` (inbox), `programme-dashboard`,
  `notifications`. Legacy `/wellness/clubs` → `/wellness/social/groups` and `/wellness/challenges` →
  `/wellness/social/challenges` (redirects, no dead nav). `/wellness/community` is the **clinical**
  community-health surface (distinct SoR) and was intentionally left untouched.
- **Provider mobile** (`apps/mobile/provider-app`): `wellnessSocialWorkbenchService.ts` +
  `WellnessSocialWorkbenchScreen` (OfficialPostComposer / AnnouncementComposer / ModerationInbox /
  EngagementSummary / FlagToPct), a `wellnessSocial` tab wired in `ProviderTabs`, launchable from
  `ProviderSocialScreen`. **Documented-partial**: apps/mobile uses pnpm `workspace:*`; mobile
  type-check/vitest cannot run in this environment — written + grep-verified against the real
  `@impilo/mobile-api-client` and `mobile-design-system` export surfaces.

## Honest deferred seams

- **Notification / announcement / reminder delivery** — owned by **Khuluma** (8200). Simba writes the
  event row + emits the outbox event; the outbox event IS the functional seam. There is no direct
  Khuluma client in Simba and no fake in-app delivery; in-app read-state (UNREAD/READ) is Simba's own record.
- **Reel PENDING→READY async processing** — marked READY on register (prior wave); async media
  processing pipeline not built.
- **V013 `official` column** — Postgres migration (H2 tests exercise it via `ADD COLUMN IF NOT EXISTS`).

## Yypyl integration + UI-completion wave (2026-07-15)

Integrated onto `claude/staging-ux-orchestration-remediation-Yypyl` (Flyway block renumbered so the
wellness migrations seat after Yypyl's `V006__retire_crowdfunding_stub.sql`: assessment→V007,
social_core→V008 … official_posts→V014; sequence V001→V014, no duplicate version). The following
UI/vertical gaps were then closed.

| Capability | Persistence | Endpoints | Surfaces | Tests | Status |
|---|---|---|---|---|---|
| **Ephemeral statuses ("Right now")** | `simba_social_status` (V008 social_core — reused, no new migration) | `SocialStatusController` `POST/GET/DELETE /wellness/social/statuses` | Web `WellnessStatusStrip` in the feed (composer: mood/visibility/expiry + own-status delete); citizen mobile `WellnessSocialFeedScreen` strip+composer | `SocialStatusServiceTest` (7: create/emit/audit, visibility filter, owner-delete, expiry sweep) + web `WellnessStatusStrip.test` (2) | **COMPLETE** |
| **Post detail + discussion** | existing `simba_social_post` / `simba_social_comment` | existing `GET /posts/{id}`, `GET/POST /posts/{id}/comments`, `/react` | Web `wellness/social/posts/[id]` (thread + 6-type reaction picker + save) | `post-detail.test` (2) | **COMPLETE** |
| **Saved / My activity** | existing `simba_social_bookmark` / feed `MY_ACTIVITY` | existing `GET /saved`, `GET /feed?filter=MY_ACTIVITY` | Web `wellness/social/saved`, `wellness/social/my-activity` | `saved.test` (2) | **COMPLETE** |
| **Provider social workbench (web)** | n/a (composition) | existing moderation / announcement / engagement / report endpoints | Web `provider-workspace/wellness/social` (official post / announcement / moderation inbox / engagement / flag-to-PCT) | covered by hook + endpoint tests | **COMPLETE** |
| **Status expiry sweep** | `simba_social_status.moderation_status` | — | `SocialStatusExpiryScheduler` (`@Scheduled @Profile("!test")`, flips expired→HIDDEN) | `SocialStatusServiceTest.expireOverdue_*` | **COMPLETE** |

### Additional surfaces registered
- **Web routes** (all previously shipped-but-unregistered now in the route registry, guarded + nav-resolved):
  `wellness/assessment`, `wellness/timeline`, `wellness/reminders`, `wellness/follow-ups`,
  `wellness/insights` (aggregate dashboard, role-gated), `wellness/settings/consent`,
  `wellness/social/feed`, `wellness/social/reels`, `wellness/social/posts/[id]`, `wellness/social/saved`,
  `wellness/social/my-activity`, `provider-workspace/wellness(/social)`. `EXPECTED_ROUTE_COUNT` 712→735;
  route-parity 735/735.
- **Citizen mobile**: `AssessmentsSection` rebuilt from a dead `onAction={()=>{}}` empty-state button into
  a real multi-step wizard driving `wellnessAssessmentService` (start/resume → save step → complete →
  risk band + care-linkage result); new sovereign `WellnessSocialFeedScreen` (status strip + feed +
  post composer) registered as a Personal-tab section. Mobile status methods added to
  `wellnessSocialService` (`fetchActiveStatuses`/`createStatus`/`deleteStatus`).

### Gate results (this wave)
- Backend: `mvn -pl services/simba-service test` → **85 tests, 0 failures** (15 classes).
- Web: `one-ui-shell` `tsc --noEmit` clean; route-parity **735/735**; no-stub guard OK; new vitest green.
- Mobile: `tsc --noEmit` — provider-app **0 errors** (fixed a merge regression: `useAppStore` selector +
  missing `<Select label>`); citizen-app **0 new errors** (7 pre-existing baseline in
  CoursePlayerScreen/SmartCardSection/smartCardKey native-dep resolution). Mobile vitest is not runnable
  in this workspace (pnpm `workspace:*`) — documented honest partial.

### Honest deferred (this wave)
- **Status FOLLOWER visibility** degrades to owner-only until a follow-graph repository exists (no
  `simba_social_follow` repo is wired yet); PRIVATE and PUBLIC_WITHIN_IMPILO are fully enforced via the
  reused `SocialVisibilityService`.
- Citizen mobile reels/group-detail/moderation dedicated screens and the core timeline/reminders/consent
  mobile screens remain follow-ups (service methods exist; screens not built this wave).
