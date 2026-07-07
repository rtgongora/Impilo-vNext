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
