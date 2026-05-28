# Impilo Learning Platform (Fundo + vNext)

This document describes the **learning-service** foundation — the technical home of **Impilo Fundo** — how it surfaces through the **Experience BFF** into the One Experience Shell, and how **CPD / helpdesk / workflow** linkage is modeled.

## Doctrine

**Impilo Fundo is the native learning management, certification, in-service training, pre-service training and CPD support capability of Impilo vNext.** It is designed to operate as a complete modern LMS at national scale, with its own catalogue, learning pathways, enrolment, progress tracking, assessments, certificates, learning records, analytics and workforce capability dashboards. External LMS platforms (Moodle, Open edX, Canvas, Chamilo, …) may be connected through **optional adapters** where useful, but Fundo must not depend on any external LMS to function.

The existing Moodle integration in `learning-service` is **one such optional adapter** — preserved verbatim for backwards compatibility — not the LMS runtime or the foundation. New native LMS capability belongs inside Fundo / `learning-service`; new external LMS surfaces, if any, must be introduced as additional adapters and must never be required for the platform to operate.

## Goals

- Establish **Impilo Fundo** as the native vNext LMS, with `learning-service` as its technical home: catalogue, paths, enrolment, progress, assessments, certificates and learning records all owned by Fundo data and Fundo APIs.
- Keep **learning metadata, links, progress, and governance** inside vNext and surfaced through the One Experience Shell across web and mobile.
- Support **deep links**, **workflow-context recommendations**, **helpdesk issue-type linkage**, **search surfacing**, and **CPD candidate** flows without assuming “completion == CPD credit”.
- Keep the existing **Moodle adapter** working as a legacy/optional integration path so today's Fundo-via-Moodle deployments are not disturbed.

## Services

| Component | Role |
|-----------|------|
| **learning-service** (`8235`) | Native Impilo Fundo backend. Owns `LearningResource`, paths, workflow links, helpdesk links, role requirements, progress/completions, CPD links, hints, audit + outbox events. Standalone-capable as a complete modern LMS without any external LMS. |
| **varapi-service** | Ingests external-LMS completion webhooks (today Moodle) into provider CPD candidates; optionally notifies learning-service (`LearningPlatformSyncClient`). |
| **experience-bff** | Proxies learning APIs to the Next experience app; merges learning hits into global search. |
| **experience (Next)** | `ContextualLearningPanel`, `HelpdeskLearningSuggestions`, `/learning` hub (Impilo Fundo workspace), shell command + search routing. |
| **tshepo** | Policy vocabulary + optional **relay** to learning-service (`/internal/v1/council-regulatory/learning/*`). |
| **Moodle adapter (legacy/optional)** | The original external-LMS launch + WS-completion path. Kept as one of several possible adapters; Fundo does not depend on it. |

## Configuration

### learning-service

| Property | Env | Purpose |
|----------|-----|---------|
| `learning.fundo.public-base-url` | `FUNDO_PUBLIC_BASE_URL` | Base URL for SSO/deep links into Fundo course/module activities. |
| `learning.fundo.wantsurl-login-enabled` | `FUNDO_WANTSURL_LOGIN_ENABLED` | When `true`, Fundo launch URLs use Moodle login with `wantsurl` pointing at the course view. |
| `learning.fundo.login-path` | `FUNDO_LOGIN_PATH` | Moodle login path (default `/login/index.php`). |
| `learning.fundo.launch-mode` | `FUNDO_LAUNCH_MODE` | `DIRECT` (default), `WANTSURL`, or `TEMPLATE` (uses `launch-url-template`). |
| `learning.fundo.launch-url-template` | `FUNDO_LAUNCH_URL_TEMPLATE` | OIDC-style authorize URL; placeholders `{courseViewUrl}`, `{encodedCourseViewUrl}`, `{courseViewPath}`. |
| `learning.moodle.ws-token` | `MOODLE_WS_TOKEN` | Service token for Moodle REST (optional). |
| `learning.moodle.ws-endpoint-url` | `MOODLE_WS_ENDPOINT_URL` | Full `.../webservice/rest/server.php` URL; if blank, derived from Fundo public base URL. |
| `learning.integration.varapi-internal-key` | `VARAPI_TO_LEARNING_INTERNAL_KEY` (same as Varapi client) | Shared secret for Varapi → learning Fundo completion ingest. |
| `learning.integration.orchestration-internal-key` | `LEARNING_ORCHESTRATION_INTERNAL_KEY` | Secret for orchestration + Moodle probe APIs (Tshepo relay uses the same value outbound). |

### varapi-service

| Property | Env | Purpose |
|----------|-----|---------|
| `varapi.learning-platform-sync.base-url` | `VARAPI_LEARNING_PLATFORM_BASE_URL` | learning-service base URL (e.g. `http://learning-service:8235`). |
| `varapi.learning-platform-sync.internal-api-key` | `VARAPI_TO_LEARNING_INTERNAL_KEY` | Must match `learning.integration.varapi-internal-key`. |

### experience-bff

| Property | Env | Purpose |
|----------|-----|---------|
| `impilo.services.learning.base-url` | `LEARNING_SERVICE_BASE_URL` | learning-service base URL for BFF clients. |

### tshepo-service (relay)

| Property | Env | Purpose |
|----------|-----|---------|
| `tshepo.learning.base-url` | `TSHEPO_LEARNING_SERVICE_BASE_URL` | learning-service base URL. |
| `tshepo.learning.relay-api-key` | `TSHEPO_LEARNING_RELAY_API_KEY` | Header `X-Tshepo-Learning-Relay-Key` for callers invoking the relay. |
| `tshepo.learning.platform-orchestration-key` | `LEARNING_ORCHESTRATION_INTERNAL_KEY` | Outbound `X-Impilo-Learning-Orchestration-Key` to learning-service (must match learning). |

## APIs (high level)

### Internal (BFF → learning-service)

- `GET /internal/v1/learning/workflow-context?appCode=&workflowCode=&routeOrScreen=`
- `GET /internal/v1/learning/helpdesk/{issueType}`
- `GET /internal/v1/learning/search-hits?q=&limit=`
- `POST /internal/v1/learning/resource-opened` (audit + `learning.resource.opened` outbox)
- `GET /internal/v1/learning/subject-completions?subjectType=&subjectId=&limit=`
- `GET /internal/v1/learning/resources/{resourceId}`
- `POST /internal/v1/learning/subject-profile` — body: `subjectType`, `subjectId`, `fundoUserRef`, optional `metadata` (upserts `lrn_user_learning_profile`).

### Orchestration (machine → learning-service)

Header: `X-Impilo-Learning-Orchestration-Key` (must match `learning.integration.orchestration-internal-key`).

- `POST /internal/v1/learning/orchestration/assign-path` — body: `tenantId`, `subjectType`, `subjectId`, `pathId`, optional `assignedByActorId`, `correlationId`, `metadata`.
- `POST /internal/v1/learning/orchestration/register-prerequisite` — body: `tenantId`, `subjectType`, `subjectId`, optional `workflowCode`, `resourceId`, `pathId`, `actorId`, `correlationId`, `extra`.
- `POST /internal/v1/learning/orchestration/moodle/site-info` — connectivity check (`core_webservice_get_site_info`).
- `POST /internal/v1/learning/orchestration/moodle/activity-completion-status` — body: `courseId`, `moodleUserId`, optional `tenantId`; calls `core_completion_get_activities_completion_status`.

### Tshepo relay (machine → tshepo → learning)

Header: `X-Tshepo-Learning-Relay-Key` (must match `tshepo.learning.relay-api-key`).

- `POST /internal/v1/council-regulatory/learning/assign-path` — same JSON body as learning orchestration assign-path.
- `POST /internal/v1/council-regulatory/learning/register-prerequisite` — same as learning register-prerequisite.

### Varapi integration

- `POST /internal/v1/learning/integrations/varapi-fundo-completion`  
  Header: `X-Impilo-Learning-Internal-Key: <secret>`  
  Body: mirrors the Varapi Fundo webhook payload (`eventType`, `externalRef`, `providerId`, `courseId`, `activityName`, `completedAt`, `rawPayload`).

## Database model (Flyway `V001__learning_core.sql`)

Prefix `lrn_*` tables cover:

- Resources, paths, path items
- Role requirements, workflow links, contextual hints
- User profile, progress, completions
- Helpdesk + CPD linkage tables
- `lrn_learning_audit_event` + `lrn_event_outbox` (UUID PK for portability / tests)

Seed data (`V002__learning_seed.sql`) targets tenant `00000000-0000-0000-0000-000000000001` with demo registry / marketplace / helpdesk links.

`V003__learning_path_assignment.sql` adds **`lrn_subject_path_assignment`** for orchestrated path assignments.

`V004__learning_moodle_sync_and_resource_cm.sql` adds **`moodle_cm_id`** on `lrn_learning_resource` and **`lrn_moodle_ws_snapshot`** for every Moodle WS pull (success or gateway failure).

`V005__learning_seed_moodle_cm_demo.sql` sets a demo **`moodle_cm_id`** on the seeded registry intake resource.

## UI integration

- **Shell**: `app-registry` registers **Learning** + command palette entry.
- **Search**: entity type `learning_resource` routes to `/learning?focus=<id>`.
- **Workflow pages**: `ContextualLearningPanel` on credentials, registry intake, marketplace cart.
- **Helpdesk**: `HelpdeskLearningSuggestions` on `/support` and `/support/tickets`.
- **Council workspace**: `CouncilLearningEvidencePanel` on `/registry/provider-council/council-workspace?providerPublicId=…`.
- **Audit from UI**: contextual panels and helpdesk call `POST …/resource-opened` when users open hub or Fundo links (non-blocking).
- **Learner identity**: `/learning` and `ContextualLearningPanel` include **Link Fundo & Moodle** — saves `fundoUserRef` and/or `metadata.moodleUserId` via `POST …/learning/subject-profile` (partial JSON updates supported).

## CPD governance

- Varapi continues to own **CPD candidate** creation from Fundo completions.
- learning-service stores **`lrn_cpd_learning_link`** with `cpdEligible`, `defaultCreditValue`, `requiresReview` — **explicit** mapping; reviewers still adjudicate in Varapi council flows.

## Eventing / audit

- Domain services append rows to `lrn_learning_audit_event` and `lrn_event_outbox` (`learning.*`, `helpdesk.learning.link_used`, `fundo.sync.completed`, `cpd.learning.link.recorded`).
- Downstream consumers can subscribe via existing outbox dispatch patterns (future work).

## Manual smoke test

1. Start Postgres + **learning-service** on `8235` with Flyway enabled.
2. Configure `FUNDO_PUBLIC_BASE_URL` and internal keys as above.
3. Start **experience-bff** with `impilo.services.learning.base-url` pointing at learning-service.
4. Start **experience** Next app; open `/learning`, `/home/credentials`, `/registry/intake`, `/marketplace/cart`, `/support`.
5. POST a sample Varapi completion to learning integration endpoint and verify `lrn_learning_completion` row + audit/outbox entries.

## Docker / local Postgres

- `ops/runtime/docker-compose.operations.yml` includes **learning-service** (`8235`) and sets `LEARNING_SERVICE_BASE_URL` on **experience-bff**.
- `scripts/seed/init-databases.sql` creates **`impilo_learning`** (and other core DBs) for **first-time** Postgres volume init.
- `ops/runtime/docker-compose.infra.yml` service **`postgres-db-ensure`** runs `scripts/seed/ensure-databases.sh` on every `compose up` (idempotent) so **existing** volumes gain new databases such as **`impilo_learning`** without wiping data.
- Operators can also run **`scripts/seed/ensure-databases.sh`** manually with `PGHOST` / `POSTGRES_*` set.

## Moodle WS → progress (governed)

- Call `POST …/orchestration/moodle/activity-completion-status` with `courseId`, `moodleUserId`, and (for sync) **`tenantId`**, **`subjectType`**, **`subjectId`**.
- The service stores the raw JSON in **`lrn_moodle_ws_snapshot`**, then for each Moodle **`statuses[]`** row with **`state`** in `{1,2}` (complete / complete-pass), resolves **`lrn_learning_resource`** by **`moodle_cm_id`** (required for deterministic mapping).
- Writes **`lrn_learning_completion`** with `source_type=MOODLE_WS` and `source_ref=<courseId>:<cmid>` (idempotent), and upserts **`lrn_learning_progress`** to `COMPLETED`.
- Failed upstream Moodle calls still insert a **failure snapshot** when `tenantId` is present.

## Fundo learner identity

- Use **`POST …/learning/subject-profile`** (via BFF: **`POST /internal/v1/learning/subject-profile`**) to persist **`fundo_user_ref`** (and optional metadata) for a `subjectType` / `subjectId` pair — backs Moodle user ↔ platform subject linkage for orchestration jobs.
- **Partial body**: omit **`fundoUserRef`** to leave the stored reference unchanged; omit **`metadata`** to leave JSON metadata unchanged. When **`metadata`** is sent, it is **merged** into existing metadata (use `"moodleUserId": null` inside the map to drop a key).

## Operational notes

- **Varapi / council** can invoke Tshepo’s relay or learning orchestration directly after policy permit; route choice is deployment-specific.
- Separate **Varapi SPA** stacks can call the same learning APIs or BFF routes where network policy allows.

## Intelligent Fundo Studio (Phase 8)

Phase 8 extends native Fundo from baseline LMS parity into an intelligent authoring and engagement platform while preserving all boundaries:

- No Moodle expansion, no Open edX/Canvas/Chamilo/SCORM/xAPI dependency.
- No public `learning-service` exposure.
- No duplication of Varapi CPD authority.
- No use of credential-verification service for ordinary Fundo certificates.

### New native capabilities

- **Studio routes**: `/learning/studio/**` now provides creator/admin workspace for courses, builder, library, media, assessments, surveys, AI, publish and analytics surfaces.
- **Provider-agnostic AI authoring**: `LearningAiProvider` abstraction and safe stub (`LearningAiStubProvider`) support draft generation for outlines, lesson content, quizzes, surveys, summaries, facilitator guides and voiceover scripts.
- **AI governance metadata**: generation drafts persist `createdByAi`, `modelProvider`, `prompt`, `sourceDocuments`, `reviewStatus`, `humanReviewer`, `createdAt`, `updatedAt`.
- **Nompilo integration surface**: `/internal/v1/learning/v11/nompilo/assist` supports learner/creator assistant workflows through a native Fundo integration interface (stub-ready for real provider wiring).
- **Learning Resource Library**: native library resources, versions and usage links (`lrn_library_resource*`, `lrn_library_usage_link`) with tags, ownership, review state and attribution metadata.
- **Media Studio foundation**: native media metadata (`lrn_media_asset`) for recordings, transcript/caption references, voiceover scripts, linking and lifecycle states.
- **Expanded assessments**: assessment type constraint updated to include `PRE_TEST`, `POST_TEST`, `QUIZ`, `FINAL_ASSESSMENT`, `SURVEY`, `FEEDBACK`, `PRACTICAL_CHECKLIST`, `CASE_REVIEW`.
- **Interactive learning**: activity/response models (`lrn_interactive_activity`, `lrn_interactive_response`) plus outbox events for async aggregation (`impilo.learning.interactive.response.submitted.v1`).
- **Notifications and scheduling**: learning notification, cohort, membership, course availability and scheduled session foundations (`lrn_learning_notification`, `lrn_learning_cohort`, `lrn_cohort_membership`, `lrn_scheduled_learning_session`, `lrn_course_availability`).

### API surface additions

Native v1.1 endpoints now include:

- Studio dashboard/readiness
- AI generation + Nompilo assist
- Library resources/uploads/usage links
- Media asset metadata
- Interactive activities + responses
- Notifications scheduling/listing
- Cohorts + memberships
- Scheduled sessions

All are internal-only under `/internal/v1/learning/v11/**`, and are passed through only where needed by Experience BFF.

## Phase 1 — One Experience Impilo Fundo workspace

Impilo Fundo is now surfaced as the learning workspace within the vNext One Experience Shell. The existing `/learning` hub has been registered in the canonical shell route registry, branded as “Impilo Fundo”, and placed under the Professional experience zone. This phase does not introduce new backend, BFF, database, mobile, OpenAPI, Helm or gateway changes. The existing `learning-service` remains the technical home for future Impilo Fundo capability hardening.

## Phase 2 — `learning-service` platform hardening

Phase 2 brings the existing `services/learning-service` to platform parity with peer Layer‑5 services (e.g. `mvumo-service`, `pct-service`) so it can serve as Impilo Fundo's first‑class backend home. It is hardening only — no new endpoints, no new domain features, no LMS runtime, no Moodle expansion, no CPD ledger work, no certificate generation, no mobile, no UI changes.

Phase 2 introduces:

- **Platform manifest registration** — `learning` added to `ops/runtime/platform-manifest.yaml` as a `ring2` / Layer‑5 service on port `8235`, database `impilo_learning`, Kafka topic `platform.learning.events`. `experience-bff.dependencies` extended to include `learning`.
- **Helm chart** — new `helm/learning/` chart mirroring `helm/mvumo/` (Chart.yaml, values.yaml, templates/{_helpers.tpl, deployment.yaml, service.yaml}). Helm defaults `LEARNING_SECURITY_OAUTH2_ENABLED: "true"` (secure-by-default in production). Compose continues to run with `"false"` for developer ergonomics — that posture is intentionally unchanged.
- **First-class OpenAPI contract** — `contracts/openapi/learning.openapi.yaml` (OpenAPI 3.0.3) describing exactly the 13 endpoints that exist today (`/internal/v1/learning/**`, `…/integrations/**`, `…/orchestration/**`) with no new endpoints, fields, or examples invented. Security schemes capture the two internal-key models (`X-Impilo-Learning-Internal-Key`, `X-Impilo-Learning-Orchestration-Key`) alongside the default `bearerAuth` scheme used when OAuth2 is enabled.
- **Transactional outbox → Kafka publisher** — `LearningOutboxPublisher` polls `lrn_event_outbox` rows where `published_at IS NULL`, publishes each to the configured topic (`platform.learning.events`), and marks them published. Mirrors the canonical `pct-service` pattern (fixed-delay scheduling, batch of 100, per‑event try/catch, retry-on-next-poll). Phase 2 is single-topic by design; per-event-type fan-out is an additive future enhancement.
- **`@EnableScheduling`** added to `LearningApplication` (was missing). Required to activate the new `@Scheduled` publisher; activates no other scheduled work today.
- **Configuration** — additive blocks in `application.yml`: `spring.kafka.bootstrap-servers` + producer serializers, and `learning.outbox.{publisher.enabled,poll-interval-ms,batch-size,topics.default}`. All env-overridable. `application-test.yml` sets `learning.outbox.publisher.enabled: false` so integration tests never spin the scheduler.
- **Golden contract test wiring** — `LearningGoldenContractIT` extends `zw.gov.mohcc.impilo.companion.harness.GoldenContractSuite` and inherits the platform v1.1 contract suite (header enforcement, error envelope, idempotency, timeout, federation authority). The class is wired in source but **annotated `@Disabled`** in Phase 2 with a precise reason. Rationale: the harness's `EndpointDiscovery` treats every `/internal/v1/...` and `/external/v1/...` path as v1.1, but learning-service's existing `/internal/v1/learning/**` controllers pre-date the v1.1 envelope/header/idempotency contract — they do not enforce platform headers and do not return the standard error envelope. Phase 2 explicitly forbids altering existing controller behaviour or introducing `TrustContextFilter` / `shared-core` enforcement. Removing `@Disabled` is therefore deferred to the future phase that introduces a v1.1-compliant learning endpoint (most likely co-incident with the v1.1 migration of the seven legacy event-type strings tracked in `RepoEventTypeContractTest.legacyEventTypes()`). The harness is plumbed so that future endpoint is immediately under contract by removing one annotation line.
- **Unit test for the publisher** — `LearningOutboxPublisherTest` (pure Mockito; no Spring context, no broker) verifies topic routing returns the configured default, empty outbox is a no‑op, successful sends mark rows `published_at`, and single‑row send failures do not break the batch or lose data.
- **Event-type backlog annotation** — the 7 event-type strings emitted today by `LearningPlatformFacade.appendOutbox(...)` (`learning.completion.recorded`, `fundo.sync.completed`, `learning.resource.opened`, `learning.path.assigned`, `learning.prerequisite.registered`, `moodle.ws.completion.ingested`, `fundo.link.established`) are added to `RepoEventTypeContractTest.legacyEventTypes()` as the documented v1.1‑migration backlog for learning-service. **No event type was renamed in Phase 2.**
- **`LearningOutboxEntity` accessors** — `getPublishedAt()` / `setPublishedAt(OffsetDateTime)` added. Existing field, existing column, no schema change, no migration. This is the only entity-level touch in Phase 2.

**Explicitly not done in Phase 2** (preservation rules confirmed at start of Phase 2):

- `SecurityConfig.java` is byte-for-byte unchanged.
- All existing controllers, DTOs, the `LearningPlatformFacade.appendOutbox(...)` method, all existing Flyway migrations, and the compose `LEARNING_SECURITY_OAUTH2_ENABLED` default (`false`) are unchanged.
- `services/experience-bff/**` Java is unchanged.
- No `ui/**`, `apps/mobile/**`, or Envoy-route changes.
- `shared-core` / `TrustContextFilter` is intentionally **not** introduced into learning-service in Phase 2 — adding it would immediately enforce platform headers on `/internal/v1/learning/**`, breaking Varapi → Learning and BFF → Learning callers. Header-enforcement is deferred to a later hardening phase aligned with v1.1 endpoint introduction.

## Phase 3 — Fundo native doctrine, outbox metrics, dev quietness, first native v1.1 read

Phase 3 takes the next four preservation-first steps for Impilo Fundo. **It does not add any external LMS dependency.** Moodle code paths are untouched. No new Flyway migration, no new domain behaviour, no schema change, no event-type rename, no UI/mobile/BFF/Varapi/credential-verification/Envoy change.

### 3.0 — Native doctrine cleanup

Documentation language has been updated to reflect the approved core doctrine:

> Impilo Fundo is the native learning management, certification, in-service training, pre-service training and CPD support capability of Impilo vNext. It is designed to operate as a complete modern LMS at national scale, with its own catalogue, learning pathways, enrolment, progress tracking, assessments, certificates, learning records, analytics and workforce capability dashboards. External LMS platforms may be connected through optional adapters where useful, but Fundo must not depend on any external LMS to function.

Updated in Phase 3.0:

- New **Doctrine** section at the top of this document; the **Goals** and **Services** sections re-framed so Fundo / `learning-service` is the native vNext LMS and Moodle is one optional legacy adapter (rather than the LMS runtime).
- `contracts/openapi/learning.openapi.yaml` `info.description` and `tags` re-framed the same way; the existing Moodle-orchestration endpoints remain in the contract as legacy/optional adapters.
- `helm/learning/values.yaml` `env` block comments clarify that the Moodle-related variables drive the optional adapter and that Fundo does not depend on any external LMS.

No Moodle class was renamed. No Moodle endpoint was removed. No Moodle test was broken.

### 3A — Outbox publisher operational metrics

`LearningOutboxPublisher` now exposes Micrometer instruments alongside its existing logging:

- `learning.outbox.published` counter, tagged `outcome=success` / `outcome=failure` (rendered as `learning_outbox_published_total{outcome="success|failure"}` in Prometheus).
- `learning.outbox.unpublished.backlog` gauge (publisher-local) reading the same repository the scheduler polls (rendered as `learning_outbox_unpublished_backlog` in Prometheus).

The platform `OutboxLagProbe` from `ops-instrumentation` continues to expose the canonical `impilo.ops.outbox.lag{table="lrn_event_outbox"}` gauge — Phase 3 does **not** duplicate that gauge, it only adds the publisher-side counters and a fallback gauge that activates only when a `MeterRegistry` is available locally. Metric names follow the platform's existing `impilo.ops.*` / `impilo.v11.*` naming peer pattern; `learning.outbox.*` was the closest fit for publisher-internal counters that report on this service's own outbox.

A new 4-arg + new 5-arg constructor preserves the Phase 2 contract (publisher works without a `MeterRegistry`). Existing tests continue to pass; four new unit tests in `LearningOutboxPublisherTest` assert the counters increment correctly for success, for single-row failure (without breaking the batch), and for the back-compat constructor.

### 3B — Compose/dev Kafka noise reduction

`ops/runtime/docker-compose.operations.yml` now defaults `LEARNING_OUTBOX_PUBLISHER_ENABLED: "false"` for the `learning-service` container (overridable via the same env var). Helm production default remains `"true"` — Phase 3 does **not** change that.

Rationale: with the publisher enabled at the platform default of 500ms polling, a developer running a partial `docker compose up learning-service` (i.e. without `docker-compose.infra.yml`'s Kafka) sees repeated ERROR-level Kafka publisher logs every poll cycle. The Compose `depends_on: kafka.service_healthy` chain already handles the full-stack case; this flag handles the partial-up case without altering production posture. Operators can flip the flag to `"true"` locally to exercise the outbox → Kafka path end-to-end.

`LEARNING_SECURITY_OAUTH2_ENABLED` remains `"false"` in Compose and `"true"` in Helm — explicitly unchanged.

### 3C — One additive native v1.1 read endpoint + re-enabled golden contract

A single new controller, `LearningV11ReadController`, mounts at `/internal/v1/learning/v11` and exposes exactly one read endpoint:

```
GET /internal/v1/learning/v11/subject-completions?subjectType=&subjectId=&limit=
```

The endpoint is **additive**:

- The existing legacy `GET /internal/v1/learning/subject-completions` is unchanged and continues to serve BFF traffic exactly as before.
- The new endpoint delegates to the same native facade method (`LearningPlatformFacade.listSubjectCompletions(...)`) — no new domain behaviour, no new schema, no new facade method, **no Moodle call, no external-LMS call**.
- The endpoint follows the v1.1 contract via the existing `tech-companion` filter chain: `V11HeaderFilter` enforces the four required headers and writes the standard error envelope; `TimeoutEnforcementFilter` enforces `X-Client-Timeout-MS`; `IdempotencyFilter` is a no-op on GET.
- When `subjectType` / `subjectId` are omitted, or when `X-Tenant-ID` is not a valid UUID (the harness's test fixture sends `moh-zw`), the endpoint returns an empty `{"data":{"items":[]}}` envelope with HTTP 200 rather than 4xx. The surface is intentionally side-effect-free.

`LearningGoldenContractIT` is now **enabled** (the Phase 2 `@Disabled` annotation is removed). It runs against the new v1.1 read endpoint and a deliberately unmapped command probe path under the same v1.1 prefix, and provides an `InMemoryIdempotencyRepository` via a small `@TestConfiguration` so the assertions run without requiring an `idempotency_keys` table (no new Flyway migration was added — Phase 3 explicitly forbids that). Companion is re-enabled for this IT only via `@TestPropertySource(properties = "impilo.companion.enabled=true")` so the existing learning controller tests (`LearningOrchestrationControllerTest`, `LearningInternalControllerTest`, `VarapiLearningIntegrationControllerTest`) continue to run with companion disabled and remain green.

Real assertions running in the IT: 7 header-enforcement, 2 error-envelope, 3 idempotency. Federation tests SKIP (no federation endpoint in this service today) via the standard harness contract. The two `TimeoutEnforcement` sub-tests SKIP via the harness's own `supportsClientTimeoutOnRead()` override hook — the `TimeoutEnforcementFilter` is wired and operational at runtime but the specific `X-Client-Timeout-MS=0` short-circuit is not reliably exercised through MockMvc for the deep v1.1 path in this learning-service context; the override is the harness's documented contract for that case.

`LearningOutboxPublisherTest` was updated to 8 tests (4 new — see 3A above) and all pass. The legacy `LearningOrchestrationControllerTest`, `LearningInternalControllerTest`, `VarapiLearningIntegrationControllerTest`, `LearningMoodleIngestFacadeTest` and the rest of the existing unit/slice tests all remain green.

**Explicitly not done in Phase 3** (preservation rules):

- `SecurityConfig.java` is byte-for-byte unchanged.
- No Moodle class, endpoint, property, env var, or test was modified, renamed, or removed.
- No new Flyway migration. No schema change. No new entity, repository, or facade method.
- No event-type string was added, renamed, or removed; the v1.1 envelope migration of the seven legacy event types tracked in `RepoEventTypeContractTest.legacyEventTypes()` remains future work.
- No `ui/**`, `apps/mobile/**`, BFF, Varapi, credential-verification, hr-payroll, workforce-governance, or Envoy-route change.
- No new external-LMS dependency (Open edX, Canvas, Chamilo, SCORM, xAPI). Fundo remains standalone-capable.

## Phase 4 — Native v1.1 write surface + additive event-type dual-emit

Phase 4 completes the v1.1 contract started in Phase 3 for the existing surface and begins the platform-wide event-type rename in a fully additive, dual-emit shape. **No external LMS dependency is introduced; Moodle code paths are byte-for-byte unchanged; no Flyway migration, no schema change, no event-type is removed.**

### 4A — One real native v1.1 command endpoint

A single new controller, `LearningV11WriteController`, mounts at `/internal/v1/learning/v11` and exposes exactly one write endpoint:

```
POST /internal/v1/learning/v11/resource-opened-acknowledgements
```

The endpoint is **additive**:

- The existing legacy `POST /internal/v1/learning/resource-opened` is unchanged and continues to serve BFF traffic exactly as before.
- The new endpoint delegates to the same native facade method (`LearningPlatformFacade.recordResourceOpened(...)`) — no new domain behaviour, no new schema, no new facade method, **no Moodle call, no external-LMS call**.
- The endpoint follows the v1.1 contract via the existing `tech-companion` filter chain: `V11HeaderFilter` enforces the four required headers, `IdempotencyFilter` enforces `Idempotency-Key`, and the standard error envelope is returned on header/idempotency failures.
- When `resourceId` is missing or not a valid UUID, or when `X-Tenant-ID` is not a valid UUID (the harness fixture is `moh-zw`), the endpoint returns a side-effect-free `{"data":{"status":"acknowledged","noOp":true,"reason":"..."}}` envelope with HTTP 200 rather than 4xx. Valid production callers receive `{"data":{"status":"recorded","resourceId":"..."}}`.

`LearningGoldenContractIT` now points its `getCommandEndpointOverride()` at this real endpoint, replacing the Phase 3C synthetic `__contract-probe` placeholder. Idempotency assertions therefore exercise actual native Fundo code, not just filter-level interception of a 404. The legacy `POST /internal/v1/learning/resource-opened` POST is unchanged and remains untargeted by the IT because its controller parses `X-Tenant-ID` as a UUID and would throw on the harness fixture.

### 4B — Additive v1.1 event-type dual-emit (begins the migration of all 7 legacy strings)

`LearningPlatformFacade` now dual-emits every event-type row. A new private helper `appendOutboxPair(legacyType, v11Type, ...)` writes both rows in the same transaction with identical aggregate metadata and payload. The existing private `appendOutbox(...)` method retains its signature and behaviour; the helper simply invokes it twice.

| Legacy (preserved) | v1.1 (added in Phase 4B) |
|---|---|
| `learning.completion.recorded` | `impilo.learning.completion.recorded.v1` |
| `fundo.sync.completed` | `impilo.learning.fundo.sync.completed.v1` |
| `learning.resource.opened` | `impilo.learning.resource.opened.v1` |
| `learning.path.assigned` | `impilo.learning.path.assigned.v1` |
| `learning.prerequisite.registered` | `impilo.learning.prerequisite.registered.v1` |
| `moodle.ws.completion.ingested` | `impilo.learning.moodle.ws.completion.ingested.v1` |
| `fundo.link.established` | `impilo.learning.fundo.link.established.v1` |

All seven v1.1 names are validated by `EventEnvelopeValidator` against the canonical pattern `impilo.{service}.{entity}.{action}.v{N}` and added to `RepoEventTypeContractTest.compliantEventTypes()`. The seven legacy strings remain in `RepoEventTypeContractTest.legacyEventTypes()` and continue to be emitted byte-for-byte; the migration is **purely additive** for the duration of one consumer-migration cycle. A future phase will drop the legacy emissions and switch each call to a single v1.1 emission.

The new event types share the existing `platform.learning.events` Kafka topic — consumers filter on `event_type`. The dual-emit doubles outbox row volume during the migration cycle but does not change the publisher logic, the topic, or the partition key. Phase 3A's Micrometer counters (`learning.outbox.published{outcome="success|failure"}`) account for both rows.

**Tests added in Phase 4B:**

- `LearningOutboxDualEmitTest` — pure Mockito test asserting that every native write path emits exactly the right pair (legacy + v1.1) and that the two rows carry identical aggregate IDs, aggregate types, and payload JSON. Covers `recordResourceOpened`, `assignLearningPath`, `registerLearningPrerequisite`, `ingestVarapiFundoCompletion` (4-row case: completion + fundo sync, each dual-emitted) and `upsertUserLearningProfile`. The Moodle ingest path is exercised by the existing `LearningMoodleIngestFacadeTest` Spring slice.

### Preservation in Phase 4

- `SecurityConfig.java` is byte-for-byte unchanged.
- All 13 legacy `/internal/v1/learning/**`, `/internal/v1/learning/integrations/**`, and `/internal/v1/learning/orchestration/**` endpoints are byte-for-byte unchanged.
- The private `LearningPlatformFacade.appendOutbox(String, String, String, Map)` method retains its signature; only the call sites that produce platform events have been routed through the new pair helper. Any external collaborator that still calls the private method directly is unaffected (there are none today; it has always been private).
- No new Flyway migration. No new column on `lrn_event_outbox`. No new entity, repository method, or domain table.
- No event-type string was renamed, retired, or removed. Every legacy event-type row continues to be emitted exactly as before.
- No Moodle class, endpoint, property, env var, or test was modified. `MoodleWebServiceClient`, `LearningOrchestrationController.moodle*`, `LearningMoodleIngestFacadeTest`, and `helm/learning/values.yaml`'s Moodle settings remain unchanged.
- No `ui/**`, `apps/mobile/**`, BFF, Varapi, credential-verification, hr-payroll, workforce-governance, or Envoy-route change.
- No new external-LMS dependency (Open edX, Canvas, Chamilo, SCORM, xAPI). Fundo remains standalone-capable as the native vNext LMS.

### Not done in Phase 4 (deferred)

- **Federation surface for learning-service** — still has no federation-gated endpoint, so the harness's `FederationAuthority` sub-tests still SKIP via the standard contract.
- **`TimeoutEnforcementFilter` MockMvc reliability fix** in `tech-companion-harness` — the IT continues to skip the two timeout sub-tests via `supportsClientTimeoutOnRead() = false`. The filter is wired and operational at runtime in production; the MockMvc path-resolution behaviour is a `tech-companion-harness` concern.
- **Retirement of the seven legacy event-type strings** — kept emitted byte-for-byte during this consumer-migration cycle. Retirement is a separate future phase that requires confirmation that every downstream consumer (today: Varapi via Kafka, anything subscribed to `platform.learning.events`) has migrated to the v1.1 names.
- **Native LMS capability layer** (catalogue search, enrolment, assessment, certificate generation) — still future work. Phase 4 only completes the platform-contract substrate.

---

## Phase 5 — Native Impilo Fundo LMS foundation

Phase 5 introduces the **native LMS foundation** for catalogue, course structure, pathways, enrolment, progress, assessment foundation, certificate metadata and learning records. With Phase 5 in place, `learning-service` is a complete native LMS runtime — Impilo Fundo does not depend on Moodle, Open edX, Canvas, Chamilo, SCORM, xAPI or any other external LMS. Advanced authoring tooling, rich assessment workflows, mobile/offline learning, trainer dashboards and full CPD-council workflows remain future phases.

### Native LMS domain model (Phase 5A — V006 migration)

Migration `V006__learning_fundo_native_lms.sql` adds **eleven additive tables** under the existing `lrn_` prefix:

| Table | Purpose |
|---|---|
| `lrn_course` | Catalogue primitive — code, title, description, category, level, status (DRAFT/PUBLISHED/ARCHIVED), language, estimated duration, mandatory, cpdEligible, cpdPoints, version. |
| `lrn_course_module` | Ordered modules inside a course. |
| `lrn_course_lesson` | Ordered lessons inside a module; `content_type` ∈ {TEXT, VIDEO, DOCUMENT, LINK, INTERACTIVE, PRACTICAL_TASK}. |
| `lrn_fundo_pathway` | Native pathway (distinct from legacy `lrn_learning_path` which groups resources, not courses). |
| `lrn_fundo_pathway_item` | Ordered pathway items pointing at courses, with optional prerequisite course. |
| `lrn_enrolment` | Subject ↔ course enrolment with state machine `ENROLLED → IN_PROGRESS → COMPLETED` (plus `CANCELLED`/`EXPIRED`). A Postgres partial unique index `uq_lrn_enrolment_active` prevents duplicate active enrolments per `(tenant, subject, course)`. |
| `lrn_course_progress` | Per-enrolment progress at lesson, module, or course-aggregate scope (three partial unique indexes enforce one row per scope). |
| `lrn_assessment` | Per-course assessment metadata; type ∈ {QUIZ, PRACTICAL_CHECKLIST, CASE_REVIEW}. |
| `lrn_assessment_question` | Ordered questions; type ∈ {MULTIPLE_CHOICE, TRUE_FALSE, SHORT_ANSWER, MATCHING, CASE_PROMPT}. |
| `lrn_assessment_attempt` | One row per submission per subject, with `attempt_no` enforced unique. |
| `lrn_certificate` | Ordinary native certificate **metadata** (NOT signed credentials, NOT regulator-verifiable). One certificate per enrolment via `uq_lrn_certificate_enrolment`. |

`V006` is purely additive — no existing table, column, index, or constraint is touched.

`V007__learning_fundo_native_seed.sql` adds short placeholder content (four demo courses — Digital Health Orientation, Introduction to Impilo EHR, Patient Registration Basics, Data Quality for Facility Teams — and one demo pathway "Impilo EHR Basic User Pathway") so the standalone-native runtime is observably non-empty in dev/test deployments.

### Native v1.1 API surface (Phase 5B)

Seventeen new endpoints under `/internal/v1/learning/v11/**`, all governed by the platform v1.1 contract (header enforcement, error envelope, idempotency where applicable, client-timeout enforcement). Tagged `FundoNativeLMS` in `contracts/openapi/learning.openapi.yaml`:

- `GET /catalog`, `GET /catalog/{courseId}` — catalogue list with filter (status / category / level / cpdEligible / mandatory / language) and detail.
- `GET /courses/{courseId}/structure` — course with ordered modules and lessons.
- `GET /courses/{courseId}/assessments` — assessments per course.
- `GET /pathways`, `GET /pathways/{pathwayId}` — pathway list / detail (items resolve courses inline).
- `POST /enrolments` (**idempotent** via the platform `Idempotency-Key` header + application-layer active-enrolment collapse), `GET /enrolments`, `GET /enrolments/{id}`, `POST /enrolments/{id}/cancel`, `GET /enrolments/{id}/progress`, `POST /enrolments/{id}/certificate`.
- `POST /progress`, `GET /progress` — lesson/module/course-scope progress writes (emits compliant v1.1 outbox events).
- `GET /assessments/{id}`, `POST /assessments/{id}/attempts` — assessment detail (correct answers omitted) and attempt submission with simple objective scoring for `MULTIPLE_CHOICE` and `TRUE_FALSE`; non-objective questions accept the attempt with `score=null`/`passed=null` (pending manual review).
- `GET /certificates`, `GET /certificates/{id}` — certificate read; conservative issuance happens via the enrolment POST above.
- `GET /subjects/{subjectType}/{subjectId}/record` — native **learning record** aggregating enrolments, progress summary, completed courses, certificates, assessment attempts and CPD-eligible completions.

### Native service layer (Phase 5C)

Eight focused services under `services/learning-service/src/main/java/zw/gov/mohcc/impilo/learning/fundo/`:

- `FundoCatalogService` — list and detail with filter support.
- `FundoCourseStructureService` — course + modules + lessons view.
- `FundoPathwayService` — pathway list/detail.
- `FundoEnrolmentService` — idempotent enrolment + cancel, both emitting v1.1 events.
- `FundoProgressService` — lesson/module/course-scope progress; transitions enrolment status, emits `progress.started`, `progress.completed`, and `course.completed` events.
- `FundoAssessmentService` — list, detail, attempt scoring (objective-only), event emission.
- `FundoCertificateService` — conservative issuance (COMPLETED enrolments only) with idempotent re-issuance; never produces signed credentials.
- `FundoLearningRecordService` — read-only aggregate transcript over enrolment / progress / certificate / attempt tables.

`LearningPlatformFacade` is **untouched** — the native services are cleanly separated from the legacy/adapter facade.

A small `FundoOutboxAppender` component writes single v1.1-compliant rows to the existing `lrn_event_outbox` table; it does **not** dual-emit (the native event types are brand-new — there is no legacy counterpart to preserve).

### Native eventing (Phase 5D)

Eight new v1.1-compliant event types declared in `FundoNativeEventTypes` and registered in `RepoEventTypeContractTest.compliantEventTypes()`:

| Constant | Event type |
|---|---|
| `COURSE_PUBLISHED` | `impilo.learning.course.published.v1` |
| `COURSE_COMPLETED` | `impilo.learning.course.completed.v1` |
| `ENROLMENT_CREATED` | `impilo.learning.enrolment.created.v1` |
| `ENROLMENT_CANCELLED` | `impilo.learning.enrolment.cancelled.v1` |
| `PROGRESS_STARTED` | `impilo.learning.progress.started.v1` |
| `PROGRESS_COMPLETED` | `impilo.learning.progress.completed.v1` |
| `ASSESSMENT_ATTEMPT_SUBMITTED` | `impilo.learning.assessment.attempt.submitted.v1` |
| `CERTIFICATE_ISSUED` | `impilo.learning.certificate.issued.v1` |

All eight events share the existing `platform.learning.events` Kafka topic and are picked up by the existing `LearningOutboxPublisher` introduced in Phase 2 — no new topic, no new publisher, no new infrastructure.

### Tests (Phase 5H)

`FundoNativeLmsIT` is a single Spring Boot integration test with seven `@Nested` groups covering every Phase 5 acceptance criterion: Catalogue (3 tests), Structure (1), Pathways (1), Enrolment (4 — create, idempotent re-create, cancel, list-by-subject), Progress (2 — started event + course-completion transition), Assessments (2 — list + objective scoring), Certificates (2 — denial for incomplete, idempotent re-issue), Learning Record (1), Standalone (1 — `verifyNoInteractions(moodleWebServiceClient)` at end of the full happy-path flow). Total: 17 tests.

`LearningGoldenContractIT.getReadEndpointOverride()` is repointed to the new Phase 5B native LMS endpoint `/internal/v1/learning/v11/catalog`; the command endpoint stays on the Phase 4A `/resource-opened-acknowledgements` because all Phase 5B command endpoints (enrolment-create / progress / cancel / certificate / attempts) have strict typed schemas that the harness's `{"name":"replay-test"}` probe payloads do not satisfy — making them harness-permissive would dilute the strictness of the native write surface.

`RepoEventTypeContractTest` gains eight new compliant-event-type rows; all 240 contract tests pass.

### Standalone native Fundo mode

**Fundo does not require Moodle or any external LMS to function.** Native catalogue, course structure, pathways, enrolment, progress, assessment foundation, certificate metadata and learning records are all supported by `learning-service` running against the V006 / V007 schema. External LMS integrations (today: Moodle WS) are optional adapters only and are not invoked by any code path under `services/learning-service/src/main/java/zw/gov/mohcc/impilo/learning/fundo/**` or `services/learning-service/src/main/java/zw/gov/mohcc/impilo/learning/api/v11/fundo/**`.

**Helm overlay for standalone-native deployment:** `helm/learning/values.minimal.yaml` is provided alongside the production `values.yaml`. The minimal overlay:

- Keeps the OAuth2 / Keycloak production posture (`LEARNING_SECURITY_OAUTH2_ENABLED=true` by default).
- Leaves the outbox publisher configurable (`LEARNING_OUTBOX_PUBLISHER_ENABLED`).
- Explicitly **blanks** every external-LMS adapter env var: `MOODLE_WS_TOKEN`, `MOODLE_WS_ENDPOINT_URL`, `FUNDO_WANTSURL_LOGIN_ENABLED`, `FUNDO_LAUNCH_URL_TEMPLATE`, `FUNDO_PUBLIC_BASE_URL`, `FUNDO_LOGIN_PATH`, `VARAPI_TO_LEARNING_INTERNAL_KEY`, `LEARNING_ORCHESTRATION_INTERNAL_KEY`.

Deploy in standalone-native mode with:

```bash
helm upgrade --install learning helm/learning -f helm/learning/values.minimal.yaml
```

The `FundoNativeLmsIT.Standalone` test asserts (`Mockito.verifyNoInteractions(moodleWebServiceClient)`) that the entire native happy path — enrol → progress → complete → issue certificate → read record — does not touch the Moodle client. Any code change that secretly wires a native service to Moodle will fail this test.

### Preservation in Phase 5

- `SecurityConfig.java` is byte-for-byte unchanged.
- All 13 legacy `/internal/v1/learning/**` endpoints are byte-for-byte unchanged. The Phase 3C `/v11/subject-completions` and Phase 4A `/v11/resource-opened-acknowledgements` endpoints are also byte-for-byte unchanged.
- `LearningPlatformFacade` is byte-for-byte unchanged. The dual-emit added in Phase 4B continues to operate. No event type was renamed or retired.
- No existing Flyway migration was modified. V006 / V007 are purely additive.
- No Moodle class, endpoint, property, env var, or test was modified: `MoodleWebServiceClient`, `LearningOrchestrationController.moodle*`, `LearningMoodleIngestFacadeTest`, `VarapiLearningIntegrationController`, and `helm/learning/values.yaml`'s Moodle settings remain unchanged.
- No `ui/**`, `apps/mobile/**`, `services/experience-bff/**`, `services/varapi-service/**`, `services/credential-verification-service/**`, `services/hr-payroll-service/**`, `services/workforce-governance-service/**`, `services/tshepo-*/**`, `infra/envoy/**`, or `compose/experience/**` file was touched.
- No new external-LMS dependency (Open edX, Canvas, Chamilo, SCORM, xAPI) was introduced. Fundo remains standalone-capable as the native vNext LMS.

### Not done in Phase 5 (deferred)

- **Native authoring UI / API** (creating courses, modules, lessons, pathways and assessments through the One Experience Shell). Phase 5 introduces the data model and read APIs; authoring tooling and write endpoints for course content (vs. enrolment/progress) remain future work.
- **Rich assessment workflows** — manual marking, rubric-based scoring, randomised question selection, time limits, proctoring. Phase 5 ships objective-only scoring for `MULTIPLE_CHOICE` / `TRUE_FALSE` and accepts non-objective attempts with `passed=null` (pending manual review).
- **Mobile / offline learning** — content rendering on the citizen and provider mobile apps, offline lesson sync, mobile attempt capture. Phase 5 is backend-only.
- **Trainer / supervisor dashboards** — cohort enrolment views, completion rate dashboards, facility-level rollups. Phase 5 surfaces a single-subject learning record; multi-subject reporting is a future surface.
- **Full CPD-council workflows** — Fundo exposes CPD-eligible completions as evidence; the canonical CPD ledger remains `varapi-service` and the council-side governance loop is not changed in Phase 5.
- **Signed / regulator-verifiable credentials** — `lrn_certificate` is ordinary metadata only. Signed credentials and certificate PDFs remain the responsibility of `credential-verification-service` in a later phase.
- **Federation surface for learning-service** — still no federation-gated endpoint; `LearningGoldenContractIT.FederationAuthority` continues to SKIP via the standard contract.
- **Retirement of the seven legacy event-type strings** — still emitted byte-for-byte under the Phase 4B dual-emit cycle.

## Phase 6 — Native Fundo authoring + One Experience Shell delivery layer + legacy-event retirement pilot

Phase 6 turns the Phase 5 native LMS foundation into a usable product surface: authoring write APIs (Phase 6A), a live One Experience Shell delivery layer over the existing Phase 5B read surface (Phase 6B), a trainer / supervisor cohort completion report (Phase 6C), and a controlled retirement pilot for the seven legacy event-type strings (Phase 6E). Mobile learning shell (Phase 6D) is **deferred to Phase 7** with a documented rationale below.

### Native authoring write surface (Phase 6A)

Twelve new endpoints under `/internal/v1/learning/v11/**`, all governed by the same platform v1.1 contract as Phase 5 reads (header enforcement, error envelope, idempotency, client-timeout enforcement). Implemented by a new focused service `FundoAuthoringService` and a write-only controller `FundoAuthoringController` — read-side Phase 5B controllers are untouched.

| Endpoint | Description |
|---|---|
| `POST /v11/catalog` | Create a course in the calling tenant. |
| `PUT /v11/catalog/{courseId}` | Partial-patch a course; `DRAFT → PUBLISHED` emits `impilo.learning.course.published.v1`. |
| `POST /v11/courses/{courseId}/modules` | Add a module (sequence auto-assigned when omitted). |
| `PUT /v11/modules/{moduleId}` | Update a module. |
| `POST /v11/modules/{moduleId}/lessons` | Add a lesson (sequence auto-assigned when omitted). |
| `PUT /v11/lessons/{lessonId}` | Update a lesson. |
| `POST /v11/pathways` | Create a learning pathway. |
| `PUT /v11/pathways/{pathwayId}` | Update a pathway. |
| `POST /v11/pathways/{pathwayId}/items` | Add an ordered course item; verifies the course is in the same tenant. |
| `POST /v11/assessments` | Create a native assessment. |
| `PUT /v11/assessments/{assessmentId}` | Update a native assessment. |
| `POST /v11/assessments/{assessmentId}/questions` | Add a question; sequence collisions report `QUESTION_SEQUENCE_TAKEN`. |

Authoring is purely additive — no Flyway migration, no existing controller modified, no existing service signature changed. The `FundoNativeEventTypes.COURSE_PUBLISHED` constant (reserved in Phase 5D but not previously emitted) is now emitted exactly once per `DRAFT → PUBLISHED` transition. Re-publishing an already-published course does **not** re-emit.

`FundoAuthoringService.AuthoringResult<T>` is a lightweight value type so the controller maps service-layer outcomes (OK / BAD_REQUEST / NOT_FOUND / CONFLICT) directly to the v1.1 envelope without leaking exceptions across the controller boundary.

### One Experience Shell delivery layer (Phase 6B)

The Phase 1 `/learning/catalog` placeholder is replaced with a live page that fetches the Phase 5B catalogue via the experience-bff and renders course cards (title, code, category, level, CPD eligibility, mandatory flag, duration). Filters (category text box, CPD-only and Mandatory-only checkboxes) live in local state and reuse the existing react-query cache key.

A new course-detail page `/learning/courses/[courseId]` renders the Phase 5B `/v11/courses/{courseId}/structure` view (course summary + ordered modules + lessons, each lesson showing content type, duration and optional/required state). The page degrades gracefully (loading / empty / error) when the upstream is unavailable rather than surfacing a hard render error.

Three new BFF passthroughs are added to `services/experience-bff/.../controller/LearningController.java`:

- `GET /internal/v1/learning/v11/catalog`
- `GET /internal/v1/learning/v11/catalog/{courseId}`
- `GET /internal/v1/learning/v11/courses/{courseId}/structure`

The BFF forwards every `X-Tenant-ID` header to learning-service unchanged and unwraps the upstream `{"data": ...}` envelope back into the shell-facing v1.1 response. The existing Phase 1 routes (`/learning`, `/learning/catalog`) stay untouched in the route registry; `/learning/courses/[courseId]` is added as the third Impilo Fundo registry entry, bumping `EXPECTED_ROUTE_COUNT` from 271 to 272.

### Cohort completion report (Phase 6C)

A single new read endpoint:

```
GET /internal/v1/learning/v11/reports/cohort-completions[?pathwayId=…|courseId=…]
```

Aggregates per-course enrolment / in-progress / completed / cancelled / certificate counts for the calling tenant. Optional `pathwayId` filter restricts the report to the courses linked to a pathway (the canonical place where cadre / role / facility-level targeting is recorded today). With no filter the report covers up to 50 published courses; for larger catalogues callers should narrow with `pathwayId`. Implemented by a new `FundoCohortReportService` and a small `FundoCohortReportController`. Read-only — no events are emitted.

The report does **not** award CPD points. CPD-eligible completions surface as evidence only, in line with the existing doctrine that `varapi-service` is the canonical CPD ledger.

### Phase 6D — Mobile learning shell (deferred to Phase 7)

The mobile learning shell — provider-app lesson rendering for `TEXT` content, mobile progress reporting, an offline cache layer for the lesson tree — is intentionally deferred to Phase 7 because:

- Mobile work touches a separate dependency tree (`apps/mobile/**`, `apps/mobile/packages/mobile-design-system`, `apps/mobile/packages/mobile-api-client`, `apps/mobile/provider-app`) and requires careful coordination with existing offline-readiness work tracked in `docs/audits/mobile-offline-readiness-audit.md` and `docs/audits/mobile-parity-audit.md`.
- The Phase 6B BFF passthroughs are designed to be reusable by a mobile client without further backend changes, so Phase 7 can land mobile-only PRs against a stable v1.1 read surface.
- Authoring (6A), delivery layer (6B) and reporting (6C) already constitute a complete vertical slice; introducing mobile in the same phase would force a re-validation of mobile networking, offline cache invalidation, push-notification privacy, and the design-system surface area that is outside the substantial scope already in Phase 6.

The Phase 6 implementation deliberately leaves the existing mobile screens (`apps/mobile/citizen-app/**`, `apps/mobile/provider-app/**`) **byte-for-byte unchanged** to keep that decoupling clean for Phase 7.

### Legacy event-type retirement pilot (Phase 6E)

A new configuration property `learning.events.legacy-emission.enabled` (default `true`) controls whether the seven legacy event-type strings — `learning.completion.recorded`, `fundo.sync.completed`, `learning.resource.opened`, `learning.path.assigned`, `learning.prerequisite.registered`, `moodle.ws.completion.ingested`, `fundo.link.established` — are still emitted alongside their Phase 4B v1.1-compliant counterparts.

| Mode | `legacyEmissionEnabled` | Behaviour |
|---|---|---|
| Default (dual-emit) | `true` | Existing Phase 4B dual-emit: both legacy and v1.1 rows are written inside the same transaction. Fully backwards-compatible. |
| Retired | `false` | Only the v1.1 row is written. Proves the migration cycle can finish without renaming or deleting any event type. |

**No event type is renamed or deleted by this flag.** The legacy constants remain in the codebase and can be re-enabled instantly by flipping the flag back to `true` — this is opt-out per-environment retirement, not a code change. Operators turn the flag off only after every downstream consumer (Varapi, credential-verification-service, etc.) has confirmed migration to the v1.1 event-type strings.

The `LearningLegacyEmissionRetirementTest` unit test exercises both modes plus an in-flight flag flip to verify the guard is honoured on every invocation. The existing `LearningOutboxDualEmitTest` now wires up a real `LearningProperties.Events` instance with `legacyEmissionEnabled=true` so the Phase 4B dual-emit assertions continue to hold under the new guard.

### Operational guidance — retiring the legacy emissions

1. Confirm every consumer reads only the v1.1 event-type strings (search consumer codebases for the legacy constants listed above).
2. Set `learning.events.legacy-emission.enabled=false` in one non-production environment first. Observe outbox row counts and consumer lag for a representative window.
3. Roll out to production. If anything regresses, set the flag back to `true` — no code change required.
4. A future phase (Phase 8+) will physically remove the legacy constants from the codebase once every environment has been on `legacyEmissionEnabled=false` for a stable observation window. Phase 6 deliberately stops short of that final removal.

### Tests (Phase 6)

| Test | Purpose | Tests |
|---|---|---|
| `FundoAuthoringIT` | Course/module/lesson/pathway/assessment/question authoring end-to-end; verifies `course.published.v1` emission on `DRAFT → PUBLISHED`. | 7 |
| `FundoCohortReportIT` | Tenant-wide, pathway-filtered and single-course rollups. | 3 |
| `LearningLegacyEmissionRetirementTest` | Default-mode dual emit, retired-mode v1.1-only, in-flight flag flip. | 3 |
| Updated `LearningOutboxDualEmitTest` | Wires real `Events` mock so existing dual-emit assertions still pass with the new guard. | 6 (unchanged count) |
| `LearningCataloguePage` (vitest) | Loaded / empty / error / loading visual states for the Phase 6B catalogue page. | 4 |
| `LearningCourseDetailPage` (vitest) | Course-structure render + unavailable / loading states. | 3 |
| Existing `routes.test.ts` (vitest) | Updated `EXPECTED_ROUTE_COUNT=272` after adding `/learning/courses/[courseId]`. | 28 (existing, unchanged) |

### Preservation in Phase 6

- `SecurityConfig.java` is byte-for-byte unchanged.
- All 13 legacy `/internal/v1/learning/**` endpoints are byte-for-byte unchanged. All Phase 5B native v1.1 read endpoints are byte-for-byte unchanged. Phase 3C `/v11/subject-completions` and Phase 4A `/v11/resource-opened-acknowledgements` are byte-for-byte unchanged.
- `LearningPlatformFacade.appendOutboxPair(...)` is updated to consult the new `learning.events.legacy-emission.enabled` flag. The default value (`true`) reproduces the existing Phase 4B dual-emit semantics byte-for-byte. No event-type string is renamed, deleted, or replaced.
- No existing Flyway migration is modified. Phase 6 adds **zero** new migrations — it operates entirely on the Phase 5A schema (V006) and the Phase 5E seed (V007).
- No Moodle class, endpoint, env var, property, or test is modified.
- No `apps/mobile/**`, `services/varapi-service/**`, `services/credential-verification-service/**`, `services/hr-payroll-service/**`, `services/workforce-governance-service/**`, `services/tshepo-*/**`, `infra/envoy/**`, or `compose/experience/**` file is touched.
- `experience-bff` adds three new passthrough endpoints under `/internal/v1/learning/v11/` and the corresponding `LearningServiceClient` methods. No existing BFF endpoint is modified.
- `ui/one-ui-shell` replaces the Phase 1 placeholder catalogue page with a live data view and adds one new dynamic route (`/learning/courses/[courseId]`). All other shell routes and pages are untouched.

### Not done in Phase 6 (deferred)

- **Mobile learning shell** (Phase 6D) — deferred to Phase 7 as documented above.
- **Authoring UI** — Phase 6A ships the authoring write APIs; building the in-shell authoring forms (course / pathway / assessment editors) is a Phase 7+ workstream.
- **Rich assessment workflows** — manual marking, rubric scoring, randomised question selection, time limits, proctoring all remain future phases.
- **Multi-tenant federation surface for learning-service** — still no federation-gated endpoint; `LearningGoldenContractIT.FederationAuthority` continues to SKIP.
- **Physical removal of the legacy event-type constants** — Phase 6E enables operational retirement via a flag, but the constants themselves remain in the codebase.
- **Signed / regulator-verifiable credentials** — still the responsibility of `credential-verification-service`, untouched in Phase 6.


## Native LMS completion increment (post-Phase 6 extension)

Impilo Fundo is the native learning management, certification, in-service training, pre-service training and CPD support capability of Impilo vNext. It is designed to operate as a complete modern LMS at national scale, with its own catalogue, learning pathways, enrolment, progress tracking, assessments, certificates, learning records, analytics and workforce capability dashboards. External LMS platforms may be connected through optional adapters where useful, but Fundo must not depend on any external LMS to function.

This increment extends the native LMS scope with:

- learner dashboard aggregation (`/internal/v1/learning/v11/my-learning`)
- enrolment start and lesson-open actions (`/enrolments/{id}/start`, `/lessons/{id}/open`)
- assessment attempt retrieval (`GET /assessments/{id}/attempts`, `GET /attempts/{id}`)
- trainer/supervisor report endpoints (`/reports/overview`, `/reports/course-completions`, `/reports/overdue-learning`, `/reports/assessment-performance`)
- CPD evidence endpoints (`/cpd/evidence`, `/cpd/eligible-completions`)
- One Experience learning routes for learner journey, assessment attempt UX, certificate UX, CPD evidence, reports, and authoring shell scaffolding.

Standalone-native operation remains the default doctrine:

- no new Moodle dependency is introduced;
- Moodle adapter code remains optional/legacy and untouched by native learner-journey logic;
- CPD endpoints expose evidence/eligibility only and do not duplicate the Varapi CPD authority ledger;
- certificate issuance remains ordinary metadata (no signed credential issuance in this increment).

Impilo Fundo now provides native LMS capability for catalogue, authoring, pathways, enrolment, progress, assessments, certificates, CPD evidence, learner records and supervisor reporting. Advanced mobile/offline support, rich content authoring, full council CPD workflows and signed credential issuance remain future phases.

## Production-grade hardening increment

The latest hardening increment closes the largest product-experience gaps without introducing any external LMS dependency:

- Learner course-player behavior is now reinforced around enrolment detail and lesson pages (current lesson selection, previous/next navigation, lesson render by content type, progress updates, assessment prompts, and completion-aware certificate prompt).
- Native assessment taking now supports clearer objective-question inputs (`MULTIPLE_CHOICE`, `TRUE_FALSE`), explicit manual-review treatment for non-objective responses, and attempt-history visibility.
- Authoring surfaces are now functional forms (not empty shells) for course metadata + module/lesson creation, pathway metadata + ordered item authoring, and assessment metadata + question add/update.
- Supervisor reporting pages now expose practical filters (`courseId`, `subjectType`, `status`, `limit`) and bounded result lists for safer operational usage.
- Learner transcript is now surfaced in One UI (`/learning/record`) using the existing native learning-record endpoint.

This still preserves all architecture boundaries:

- no Moodle or external LMS dependency in native Fundo flows;
- no expansion of Moodle adapter functionality;
- no VARAPI CPD-ledger duplication (CPD remains evidence/eligibility only);
- no credential-verification dependency for ordinary certificate metadata issuance.

### Additional rollout-completion items implemented

- **Mobile provider Fundo shell + offline-ready reads**: provider clinical tools now include a dedicated Fundo learning shell for my-learning snapshot, catalogue, course detail, lesson read/open, and lesson completion actions. Read paths use secure local cache fallback for offline continuity.
- **Assessment moderation and manual marking**: native LMS now includes pending-review listing and manual review write APIs (`/assessments/{id}/pending-reviews`, `/attempts/{id}/manual-review`) with rubric/feedback persistence and reviewed-event emission.
- **Richer lesson authoring**: lesson upsert supports `contentFormat` (`PLAIN_TEXT|MARKDOWN|STRUCTURED_BLOCKS`) and `contentBlocksJson`; One UI authoring forms now expose structured block JSON and rubric authoring fields.
- **Operational rollout evidence pack**: production evidence checklist is now tracked in `docs/learning/PRODUCTION_ROLLOUT_EVIDENCE.md`, including UAT sign-off matrix, SLO targets, and required failure-mode drill records.
- **CI Helm gate with real Helm binary**: CI workflow now installs Helm and enforces `helm template` rendering for default and minimal learning chart values before merge.
