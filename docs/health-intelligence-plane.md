# Health Intelligence plane (Experience BFF)

## Purpose

The Health Intelligence plane is a **cross-service orchestration layer** in `experience-bff`. It composes governed reads from search, guidance, PCT, Vito, and Varapi into structured responses for:

- **Retrieval** (fused search)
- **Summarisation** (queues, provider lifecycle, client identity resolution)
- **Decision support** (helpdesk triage packs with knowledge + learning routes)
- **Data-quality hints** and **operational anomaly foundations** (heuristics, not automated adjudication)

It is **not** a bypass for Tshepo, DAGS, or downstream ABAC. Downstream services remain authoritative for data visibility.

## API

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/internal/v1/intelligence-plane/query` | Execute `queryType` with `context` and `input` JSON maps. |
| `GET` | `/internal/v1/intelligence-plane/shell-suggest?q=&facility_id=&rank_mode=` | Lightweight fusion for shell search (index + guidance). |

Mandatory companion headers follow the same pattern as other BFF routes: `X-Tenant-ID`, `X-Pod-ID`, `X-Request-ID`, `X-Correlation-ID` (injected by the UI `api-client`).

### Visibility (Tshepo / gateway)

Before orchestration, the BFF merges **`effectiveVisibility`** into `context` from `VisibilityHeaderParser.resolve(request, mapper)` (same obligations + flat header merge as `/internal/v1/profile/visibility`). Responses echo `effectiveVisibility` and set `policyApplied` to `VISIBILITY_HEADERS+JWT` when present.

- **Aggregate-only** profiles suppress row-level index hits for sensitive entity families (`PATIENT`, `CLIENT`, `ENCOUNTER`, …) and withhold Varapi/Vito drill-down payloads for `SUMMARY_CLIENT` / `SUMMARY_PROVIDER`.

### Search ranking

- `SEARCH_FUSED` accepts `input.rankMode` or `context.rankMode`: `recency` (default), `lexical`, `semantic`, or `hybrid`.
- BFF forwards `rankMode` to **search-service** `GET /internal/v1/search?rankMode=…`, which returns `rankModeApplied` metadata (`semantic-cosine`, `semantic-proxy-lexical`, `hybrid-cosine-lexical`, etc., depending on embedding configuration).
- **search-service** stores optional `embedding_json` on index rows. Configure `impilo.search.embeddings.provider` as `http` (OpenAI-compatible `/v1/embeddings`) for production vectors, or `hash` for deterministic local vectors (tests / dev without an API). When embeddings are off or missing on hits, semantic/hybrid fall back to lexical (`*-proxy-lexical`).
- **Optional pgvector ANN** (PostgreSQL only, Flyway `V003__pgvector_ann.sql`): set `impilo.search.pgvector.enabled=true` after `CREATE EXTENSION vector` and migrations. For `semantic` / `hybrid`, the service can then recall up to `ann-candidate-limit` rows by **cosine ANN** (`embedding_vec`), optionally **union** top lexical SQL hits (`merge-lexical-candidates`), re-rank in memory, and paginate — instead of only re-ordering the current `LIKE` page. `rankModeApplied` includes `semantic-pgvector-ann` / `hybrid-pgvector-ann`. Column width is **1536** in V003; keep `impilo.search.pgvector.dimensions` aligned with your embedding model.

### Helpdesk + learning + DAGS

- `HELP_DESK_ASSIST` uses **guidance-service** as before, and when the learning plane is configured also calls **`LearningServiceClient`** (`helpdesk/{issueType}`, `search-hits`).
- Optional `input.attachDagsGovernanceSummary: true` adds a small **DAGS** snapshot (`pendingAccessRequestRows` from `listAccessRequests(PENDING, …)`), intended for operator consoles — not for citizen self-service by default.

## Query types

- `SEARCH_FUSED` — platform index + guidance search
- `SUMMARY_QUEUE` — requires `context.facilityId` (UUID)
- `SUMMARY_PROVIDER` — requires `context.subjectId` (provider key / numeric id per Varapi)
- `SUMMARY_CLIENT` — requires `context.subjectId` (Health ID) for Vito resolution
- `HELP_DESK_ASSIST` — `input.text` issue description
- `DATA_QUALITY_HINTS` — optional `context.subjectType` (`CLIENT` \| `PROVIDER`)
- `ANOMALY_SCAN_OPERATIONS` — requires `context.facilityId`
- `WORKFLOW_ASSIST` — optional `context.workflowCode`
- `LEARNING_NUDGE` — uses `context.domain` (default `all`)

## Tshepo integration

Synthetic PDP check: `POST /internal/v1/intelligence-plane` → resource type `intelligence-plane` (see `tshepo-authz-service` migration `V006__intelligence_plane_policy_rules.sql`).

Configuration (`application.yml`):

- `impilo.intelligence.require-tshepo-authorize` — when `true`, BFF calls Tshepo `/v1/authorize` before executing plane endpoints. Default `false` for gradual rollout.
- `impilo.intelligence.tshepo-pdp-fallback-allow` — when `true` (default), a Tshepo **deny** logs a warning and the request still proceeds. Set to `false` in production once policies are verified.

**Note:** Shell `GET` suggestions use the same synthetic **POST** path for PDP, meaning “plane access” rather than literal HTTP verb matching.

## Events (Kafka)

When `KafkaTemplate` is available, `IntelligenceEventPublisher` emits to `impilo.intelligence.events` (override with `impilo.intelligence.events-topic`):

- `intelligence.query.executed`
- `intelligence.summary.generated`
- `intelligence.recommendation.generated`
- `intelligence.anomaly.detected`
- `intelligence.helpdesk.assist.used`
- `intelligence.learning.suggestion.generated`
- `intelligence.workflow.assist.generated`

## Audit

Sensitive usage is logged at **INFO** with prefix `INTELLIGENCE_AUDIT` (tenant, request, correlation, actor, query type, query id, status). Extend to Tshepo audit export when the platform wires a dedicated audit sink.

## UI integration

- `/intelligence` — operator hub for query presets
- Shell search palette — “Fused hints” section (parallel to platform index)
- Support tickets — helpdesk triage panel
- Provider council self-service — registry intelligence briefing

## Known gaps / next wave

- Richer ranking (semantic / embedding) behind search-service
- Per-role response shaping from profile visibility service
- Helpdesk ticket entity binding when ticket APIs land on BFF
- Explicit `GET` policy rules for shell-suggest if Tshepo must distinguish verbs
