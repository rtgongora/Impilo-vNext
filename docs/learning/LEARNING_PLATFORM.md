# Impilo Learning Platform (Fundo + vNext)

This document describes the **learning-service** foundation, how it connects to **Impilo Fundo (Moodle)** via **Varapi**, how the **Experience BFF** exposes it to the shell, and how **CPD / helpdesk / workflow** linkage is modeled.

## Goals

- Treat **Fundo** as the canonical LMS while keeping **learning metadata, links, progress, and governance** inside vNext.
- Support **deep links**, **workflow-context recommendations**, **helpdesk issue-type linkage**, **search surfacing**, and **CPD candidate** flows without assuming “completion == CPD credit”.

## Services

| Component | Role |
|-----------|------|
| **learning-service** (`8235`) | Stores `LearningResource`, paths, workflow links, helpdesk links, role requirements, progress/completions, CPD links, hints, audit + outbox events. |
| **varapi-service** | Ingests Fundo completion webhooks into provider CPD candidates; optionally notifies learning-service (`LearningPlatformSyncClient`). |
| **experience-bff** | Proxies learning APIs to the Next experience app; merges learning hits into global search. |
| **experience (Next)** | `ContextualLearningPanel`, `HelpdeskLearningSuggestions`, `/learning` hub, shell command + search routing. |
| **tshepo** | Policy vocabulary + optional **relay** to learning-service (`/internal/v1/council-regulatory/learning/*`). |

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
