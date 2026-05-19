# Social Timeline — Runbook

Health-aware social timeline, communities, groups, and pages for the
Impilo / vNext Health Operating System. Spans web (`one-ui-shell`),
mobile (`citizen-app`, `provider-app`), Experience BFF, and the
`community-service` bounded context.

## Architecture summary

```
 web /social,/communities,/groups,/pages   citizen-app SocialFeedScreen  provider-app ProviderSocialScreen
                  │                                  │                                 │
                  │  /internal/v1/social/**          │  /internal/v1/mobile/citizen/   │  /internal/v1/mobile/
                  │  (+ /composer/assist)            │  social/**  & /feed             │  provider/social/**
                  ▼                                  ▼                                 ▼
                       ┌─────────────────────────────────────────────────────────┐
                       │  experience-bff                                         │
                       │   - SocialController            (web + shared)          │
                       │   - SocialComposerController    (Nompilo assist)        │
                       │   - CitizenSocialController     (citizen mobile)        │
                       │   - CitizenFeedController       (legacy mobile feed)    │
                       │   - ProviderSocialController    (provider mobile)       │
                       │   - CommunityController         (legacy proxy)          │
                       └─────────────────────────────────────────────────────────┘
                                                 │
                                                 ▼
                       ┌─────────────────────────────────────────────────────────┐
                       │ community-service                                       │
                       │  package zw.gov.mohcc.impilo.community.social           │
                       │   - SocialPostController       /internal/v1/community/  │
                       │   - SocialCommunityController    social/**              │
                       │   - SocialService (business logic, visibility)          │
                       │   - SocialEventEmitter → community.event_outbox →       │
                       │     CommunityOutboxPublisher → Kafka                    │
                       │   - JPA entities (community.social_*) + Flyway          │
                       │     migration V002__social.sql                          │
                       └─────────────────────────────────────────────────────────┘
                                                 │
                                                 ▼
                              PostgreSQL schema `community` (social_* tables)
                              Kafka topics for post.created/published/updated, etc.
```

## Contracts

- `contracts/openapi/social.openapi.yaml` — full social surface (timeline,
  communities, groups, pages, moderation).
- Internal community-service paths under `/internal/v1/community/social/**`.
- Shared BFF surface at `/internal/v1/social/**`.
- Mobile-tailored BFF surfaces at `/internal/v1/mobile/citizen/social/**` and
  `/internal/v1/mobile/provider/social/**`.
- Legacy `/internal/v1/mobile/citizen/feed` is preserved and now wraps the
  social timeline.

## How to run

### 1. Start backing services

```bash
# from the repository root
docker compose up -d postgres redis kafka
mvn -pl services/community-service -am spring-boot:run
mvn -pl services/experience-bff   -am spring-boot:run
```

The first `community-service` boot applies Flyway migration
`V002__social.sql` and seeds two communities, one group, and one facility
page.

Optionally start `llm-orchestration-service` to enable real Nompilo
composer assistance. Without it, the BFF returns deterministic offline
fallbacks (no failure).

### 2. Start the web shell

```bash
cd ui/one-ui-shell
npm run dev
```

Open <http://localhost:3000/social>. The three-column timeline appears
on desktop and collapses to a feed-first layout on mobile.

### 3. Run the citizen mobile app

```bash
cd apps/mobile/citizen-app
npm install
npm run start            # Expo / Metro
```

Navigate to the Social tab → Feed. The composer is at the top.

### 4. Run the provider mobile app

```bash
cd apps/mobile/provider-app
npm install
npm run start
```

Navigate to the new **Network** tab (between Messages and Tools) → Feed/
Communities/Groups/Compose.

## How to seed demo data

Demo communities, groups, and pages are seeded by
`services/community-service/src/main/resources/db/migration/V002__social.sql`.
To reset:

```bash
docker compose exec postgres psql -U postgres -d impilo \
  -c "TRUNCATE community.social_posts, community.social_comments, \
            community.social_reactions, community.social_bookmarks, \
            community.social_moderation_cases CASCADE;"
mvn -pl services/community-service -am flyway:migrate
```

To add additional content, post via the UI or the API:

```bash
curl -X POST http://localhost:8160/internal/v1/social/posts \
  -H "X-Tenant-ID: 00000000-0000-4000-8000-000000000001" \
  -H "X-Actor-ID: demo-actor" \
  -H "X-Purpose-Of-Use: TREATMENT" \
  -H "Content-Type: application/json" \
  -d '{
    "kind": "health_promotion",
    "body": "Drink water and stay hydrated!",
    "visibility": "public",
    "topics": ["wellness"]
  }'
```

## How to test web

```bash
cd ui/one-ui-shell
npm run test -- src/components/social
```

The focused tests live under
`ui/one-ui-shell/src/components/social/__tests__/`.

## How to test mobile

```bash
cd apps/mobile/citizen-app
npm run test -- src/__tests__/social
```

The Spring service tests for the social helper class:

```bash
mvn -pl services/community-service -am test
```

## How to build Android APK

The citizen-app uses Expo with EAS. To build a local APK:

```bash
cd apps/mobile/citizen-app
npx expo prebuild --platform android
cd android
./gradlew assembleRelease
```

The APK is emitted at `apps/mobile/citizen-app/android/app/build/outputs/apk/release/app-release.apk`.

For the provider-app the same flow applies in
`apps/mobile/provider-app/`.

## Current limitations

- Feed ranking is `published_at DESC + pinned DESC`. Personalised ranking
  is not yet wired (the BFF is ready for a `relevance` scope when a
  ranking service lands).
- Polls, scheduled publishing, share/repost, and rich attachments are
  modelled in the contract but UI flows are minimal — the schema accepts
  them and the API exposes them, so adding richer composer surfaces is
  a frontend-only task.
- Mentions and notifications are emitted as Kafka events but the
  notification fan-out into Comms Hub is best-effort while the
  subscription manifest is finalised.
- iOS distribution still requires TestFlight, enterprise distribution,
  or App Store publication; sideloading is not supported by Apple.

## Next steps

- Wire post drafts into a personal drafts inbox in `home/notifications`.
- Add federation between Public Health Operations announcements and the
  social timeline announcement feed.
- Add semantic search (Redis Query Engine + Vector index) over post
  content for `/search?type=social`.
- Replace the deterministic Nompilo composer fallback with a sovereign
  Gemini adapter once the orchestration service exposes per-purpose
  model selection.
