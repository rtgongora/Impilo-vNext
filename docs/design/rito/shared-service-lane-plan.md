# Rito — Shared-Service Lane Plan (non-collision with patient-safety-service)

> **Status:** DESIGN-FIRST. Defines how Rito's *future build* adds ONLY its own files /
> form-packs / consumers / controllers / routes inside shared services, without editing the same
> files `patient-safety-service` touches. Flyway version coordination for DB-bearing shared
> services. Source discipline from memory `rito-patientsafety-coordination`.

## Principle

Both new services **consume** the same shared rails. The rule: **add new files; never edit a file
the other service also edits.** On DB-bearing shared services, **append a new Flyway migration with
a coordinated version number** — never modify an existing migration. Use **owner-prefixed**
identifiers (`rito.*` / `patientsafety.*`) for topics, `form_key`s, `campaign_type`s, template keys.

## Per-shared-service lane

### channels-service (Khuluma comms hub) — DB-bearing
- **Rito adds:** a new `RitoConversationLinker` / controller in `channels.api` **only if** a Rito-
  specific linkage helper is needed; otherwise consume existing `SessionController` + `OutboxService`
  via API and store the `ch_sessions.subject_ref` → Rito case id mapping **on the Rito side**.
- **Do NOT edit:** existing `MessageController`, `SessionController`, `ch_sessions`/`ch_messages` schema.
- **3-way coordination:** the Khuluma session (`task_7bda0e52`) is the primary owner of this hub.
  Rito and patient-safety both only *link* via `subject_ref`. **No new migration here from Rito**
  (preferred). If unavoidable, take a version **after** Khuluma's and patient-safety's.

### forms-service — DB-bearing
- **Rito adds:** its **own form packs** as **separate seed files** (Rito `form_key`s, e.g.
  `rito.complaint.v1`, `rito.supervision-checklist.v1`, `rito.csat.v1`, `rito.audit.*`). Packs are
  registered via the public API (`POST /internal/v1/forms`) at runtime/seed — **preferred path
  needs no migration**.
- **Do NOT edit:** `FormSchemaController`, `fs_form_schemas`/`fs_form_schema_versions` schema, or
  patient-safety's PV form-pack files.
- **Flyway:** none from Rito if packs are API-seeded. If packs must be DB-seeded, add a **new**
  `V0xx__rito_form_packs.sql` with a version **above** patient-safety's PV-pack migration.

### campaigns-service — DB-bearing
- **Rito adds:** survey/feedback solicitation campaigns via API with `campaign_type` values
  prefixed `RITO_` (e.g. `RITO_CSAT`, `RITO_NPS`). JSONB `target_group`/`message_template` carry
  everything — **no schema change, no migration**.
- **Do NOT edit:** `CampaignController`, `camp.*` schema.

### notification-service
- **Rito adds:** notification requests via `POST /internal/v1/notify` with template keys prefixed
  `rito.*`; or emits Kafka events Rito consumers handle. **No file edits** (template keys are data).
- **Do NOT edit:** `NotifyController`, providers, or Kafka bootstrap configs. If a Rito-specific
  Kafka bootstrap consumer is genuinely required, add a **new** `RitoKafkaBootstrapConfig` class
  (sibling to `MvumoKafkaBootstrapConfig`/`OrosKafkaBootstrapConfig`) — new file only.

### surveillance-service — DB-bearing
- **Rito's relationship is consume-only** (Rito ingests surveillance signals on the Rito side).
  Rito should **NOT** add a consumer inside surveillance. If a surveillance→Rito bridge is needed,
  it lives in **Rito's** consumer, not surveillance's.
- **Do NOT edit:** `SurveillanceEventConsumer`, `surv.*` schema.

### experience-bff — (no datasource; stateless proxy)
- **Rito adds:** a new `RitoBffController` + `RitoServiceClient` (**new files**) in
  `experience.controller` / `experience.client`.
- **The one shared-file edit that is unavoidable:** `ServiceClientConfig` /`ServiceEndpoints`
  (the `baseUrls` bean) must gain a Rito entry — **and patient-safety must gain its own entry in
  the same file.** ⚠️ **COLLISION POINT.** Mitigation:
  - Coordinate so each service adds **one line** at a **non-adjacent** location (Rito appends at
    end of the bean; patient-safety appends before it), minimizing merge conflict; resolve by
    union if it conflicts.
  - This is the documented "ServiceEndpoints gotcha" — adding the controller without registering
    the base URL silently fails. Both builds must register their endpoint.
- **Do NOT edit:** other domain controllers.

### one-ui-shell / ui/self-service / mobile
- **Rito adds:** its own route folders (`app/rito/*`, self-service `app/voice/*`, mobile screens) —
  **new files only**. Any shared nav/registry that must list Rito routes: add a Rito entry,
  coordinate with patient-safety's entry (union on conflict).
- **Do NOT edit:** patient-safety route files.

### document-service
- Consume via API for attachments; **no edits**.

### Tshepo (authz/audit) — LOCKED
- **No edits.** Rito produces a **policy SPEC** (`tshepo-policy-spec.md`) and queues it for the
  single-writer CZO cluster. Rito emits audit events via the existing audit API.

## Flyway version coordination table (DB-bearing shared services)

| Shared service | Rito needs a migration? | Rule |
|---|---|---|
| channels-service | **No (preferred)** | Link via API; if forced, version after Khuluma + patient-safety |
| forms-service | **No (preferred)** | API-seed packs; if forced, version above patient-safety PV packs |
| campaigns-service | **No** | API/JSONB only |
| surveillance-service | **No** | consume-only; bridge lives in Rito |
| notification-service | **No** | template keys are data |
| **rito-service (own)** | **Yes — V001** | Rito owns its schema entirely |

**Net:** Rito's *only* unavoidable shared-file touch is the experience-bff `ServiceEndpoints`
bean (one line). Everything else is new-file or API-data. This keeps Rito and patient-safety in
fully separate lanes.

## Build-time coordination checklist (for the later Rito build round)
1. Confirm port (Rito 8390 vs patient-safety) in `docs/runbooks/port-allocation.md` — single PR per service.
2. Register Rito in `docs/registry/services-registry.yaml` + `system-of-record-map.md` (own rows).
3. Add the single `ServiceEndpoints` line; rebase on patient-safety's BFF change if it landed first.
4. Use `rito.*` prefixes for every shared-namespace identifier.
5. Never modify an existing Flyway migration in any shared service.
