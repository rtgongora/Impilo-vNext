# Phase 0 F — the three "retire" services: measured, classified, corrected

**Date:** 2026-08-08 · **Branch:** `phase0/f-false-success` (from `origin/main` `00c0a7100`)
**Serves:** Phase 0 gate condition #5 of 5 — *zero known false-success paths*
**Namespace measured:** `impilo-full-preview`

---

## 1. The instruction this workstream was given, and why it could not be followed literally

[`hybrid-federated-target-architecture-v1.3.11.md:3207`](../../docs/architecture/hybrid-federated-target-architecture-v1.3.11.md)
says:

> **Retire false-success paths** — delete `offline-sync-service`, `jobs-service`, `channels-service`; …

All three are deployed and `Running` in `impilo-full-preview`. "Delete" is not a safe procedure for
any of them, but **not for the reason the brief anticipated**. The brief warned that ~97 call sites
stood behind them. That figure does not survive measurement (§3). The real reason is different and
more interesting: **two of the three are reached by nothing at all, and the one that is reached is
reached through a BFF controller that was itself the worst false-success path in the set.**

Deleting the services would have removed three honest-ish record stores and left the actual lie —
in `experience-bff` — untouched and unexamined.

---

## 2. What each service actually does, traced to its terminal behaviour

### The finding that applies to all three: every event they write reaches nobody

Outbox publishing in this estate is an **explicit per-service class** (`XOutboxPublisher extends
CompanionOutboxPublisher`), not an auto-configuration. Grep for `@Scheduled`, `KafkaTemplate`,
`@KafkaListener` and `OutboxPublisher` across all three services' `src/main`:

```
channels-service       NONE
jobs-service           NONE
offline-sync-service   NONE
```

No scheduler, no producer, no consumer. Each has an `event_outbox` table it writes to and nothing
drains. `JOB_TRIGGERED` and `JOB_DEFINITION_CREATED` have **no subscriber anywhere in the tree**.

**Runtime proof, with a positive control.** The single row in `jobs.event_outbox` — created
`2026-08-07 15:11:25` by the Phase 0 E probe — was still `published = f` a day later. On the same
postgres instance, `pct.pct_event_outbox` (a service that *has* a publisher) shows **232 of 232
rows published**. The contrast is the proof: rows drain where a publisher exists, and sit forever
where one does not.

### Per-service verdicts

| Service | Terminal behaviour of its representative write | Verdict |
|---|---|---|
| `offline-sync-service` | `replaySyncPack` flipped `PENDING → SYNCING → SYNCED`, stamped `syncedAt`, returned 200 — under the literal comment `// Simulate replay: mark as SYNCED`. Nothing applies a pack's payload to any system of record. | **False success** |
| `jobs-service` | `trigger` saved a `PENDING` execution, appended an outbox row, returned `201 Created`. No executor exists, so the row could never leave `PENDING`. | **False success** |
| `channels-service` | Genuinely persists sessions and messages. But `sendOutbound` stamped `deliveryStatus = SENT` + `deliveredAt = now()` with **no gateway adapter for any channel type**, and `escalateSession` emitted `escalation.completed.v1` in the same transaction as `escalation.requested.v1`. | **Real store, false claims** |

`NotifyOnlyService` in the same package was **already honest** (`QUEUED` vs `DISPATCHED`, gated on
`external-notify.enabled`). The defect was `MessageService` next door telling the opposite story
about the same absent infrastructure.

### Runtime state after 21 days deployed

| DB | Contents |
|---|---|
| `channels` | 0 sessions · 0 messages · 0 assisted interactions · 0 outbox |
| `offline_sync` | 0 packs · 0 conflicts · 0 outbox |
| `jobs` | 0 definitions · 0 executions · 1 outbox row (the E probe's, unpublished) |

Schemas were installed `2026-07-18`, matching the 21-day pod uptime — so these are true zeros, not
a wiped database. **Positive control:** on the same postgres, `pct` holds 232 outbox rows / 89
telemetry events / 28 referrals, `notification` 76 / 38, `vito` 33 clients.

**No production traffic has ever reached any of the three.**

---

## 3. Call-site classification — correcting the 60 / 33 / 4 figure

The brief's "60 / 33 / 4 BFF/UI references" does not reproduce. Measured by file:

| Service | Total refs (all file types) | Of which `docs/` + `reports/` | Code-bearing |
|---|---|---|---|
| `channels-service` | 347 | 140 | 15 `services/`, 2 `ui/`, 1 `apps/`, 4 `deploy/`, + contracts/config/ops/compose |
| `jobs-service` | 296 | 121 | 7 `services/`, 2 `ui/`, 5 `deploy/`, + contracts/config/ops |
| `offline-sync-service` | 321 | 134 | 7 `services/`, 2 `ui/`, 1 `apps/`, 5 `deploy/`, + contracts/config/ops |

Roughly 40 % of every count is prior reports and docs describing these services — self-referential,
not consumption. What matters is the consumer-facing API ([[grep-the-consumer-facing-api]]):

### A. Real HTTP callers

| Caller | Target | Status |
|---|---|---|
| `ChannelsServiceClient` (11 methods) | channels-service | **Real.** Used by `CommunicationController` (web) and `MobileMessagingController` (mobile) |
| — | jobs-service | **NONE** |
| — | offline-sync-service | **NONE** |

`jobsServiceBaseUrl` exists in `OrchestrationBacklogEndpoints` as a getter/setter pair and
**`getJobsServiceBaseUrl()` is never called**. offline-sync-service has no BFF client class at all.
Both services have **zero callers in the entire repository**.

### B. References that name a service but reach nothing

| Reference | Claim | Reality |
|---|---|---|
| `apps/mobile/…/mobile-messaging/src/channelClient.ts` | SSE to `/internal/v1/channels/stream/*` | channels-service has **no stream endpoint** — the route does not exist |
| `apps/mobile/…/mobile-offline/src/syncEngine.ts` | "Backend: offline-sync-service (`/internal/v1/sync/*`)" | posts to `/internal/v1/sync/edge/snapshot`; offline-sync-service serves `/internal/v1/sync-packs`. **No controller anywhere serves `/internal/v1/sync`** |
| `patient-safety-service` (2 files) | Comms Hub linkage | doc comments + a `String conversationRef` field. **No HTTP client, no call** |
| `CitizenMessagingController` | "Delegates … for channels-service operations" | uses `CommunityServiceClient` throughout. Stale comment |
| `ui/one-ui-shell/…/registry-service-module-refs.ts`, `registry-maturity.json` | — | auto-generated name lists, not call sites |

### C. Broken BFF→service contract (pre-existing, not introduced here)

`ChannelsServiceClient` calls five routes channels-service **does not implement**:
`GET/POST /internal/v1/channels`, `PUT /internal/v1/channels/{id}`,
`POST /internal/v1/channels/{id}/test`, and `GET /internal/v1/channels/messages/sessions/{id}`
(`MessageController` has **only** a `@PostMapping`). These fail closed — the BFF returns 502
`channels_unavailable` — so they are honest, but the web message list can never load. Left in
place and reported; see §6.

Note `/internal/v1/channels` is claimed by **two** services: channels-service and
notification-service (`ChannelStatusController`, which is itself an exemplary honesty surface).

---

## 4. The largest defect was in the BFF, not in the three services

`experience-bff` `MobileMessagingController` — the only path by which a real user journey reaches
channels-service — contained four `catch (Exception ignored) {}` blocks:

| Endpoint | Old behaviour when channels-service failed |
|---|---|
| `GET /conversations` | `200` with `data: []` — **UNAVAILABLE rendered as EMPTY** |
| `GET /conversations/{id}/messages` | `200` with `data: []` — same |
| `POST /conversations/{id}/messages` | **`201 Created`** with a fresh `UUID` and the caller's own content echoed back. Nothing persisted anywhere |
| `POST /conversations` | **`201 Created`** with a fabricated conversation id |
| `POST /conversations/{id}/read` | `200` with a `ConversationReadReceipt` built from its own arguments — **it never called upstream at all** |

A provider was told their message had been sent. The client then stops retrying, and the recipient
never receives it. The fabricated conversation id is worse still: the client stores it and posts
every subsequent message into a session that never existed.

This is the same defect the sibling controllers had already been fixed for —
`ChannelsController` and `CommunicationController` both carry explicit
`COMMUNICATION_UNAVAILABLE` / `channels_unavailable` handling with the comment *"An empty 200 here
is indistinguishable from a successful read that found nothing."* **`MobileMessagingController`
was missed by that pass.**

---

## 5. What changed

| File | Change |
|---|---|
| `experience-bff` `MobileMessagingController` | All four swallowed failures now return `502` with a specific code (`CONVERSATIONS_UNAVAILABLE`, `MESSAGES_UNAVAILABLE`, `MESSAGE_NOT_SENT`, `CONVERSATION_NOT_CREATED`). A `null` upstream body is also a refusal, not a 201. `markRead` returns `501 READ_RECEIPTS_NOT_SUPPORTED`. Two dead fabrication helpers removed |
| `channels-service` `MessageService` | Outbound is `QUEUED`, not `SENT`; `deliveredAt` stays null. Inbound `DELIVERED` kept — arrival at the API *is* delivery to us |
| `channels-service` `SessionService` | `escalation.completed.v1` no longer emitted at request time |
| `offline-sync-service` `SyncPackService` | `replaySyncPack` throws `SyncReplayNotImplementedException` → `501`. Pack status left untouched so it stays visibly outstanding |
| `jobs-service` `JobExecutionService` | `trigger` throws `JobExecutionNotImplementedException` → `501`. No execution row created |
| `ChannelsEndpointTest` | The escalation test **required** `escalation.completed.v1` — it enforced the false success. Inverted |

**Why 501 and not deletion.** Per [[no-delete-to-hide-incompleteness]] and
[[dont-skirt-incomplete-functionality]]: offline replay and scheduled execution are genuine
platform capabilities that are unbuilt. Removing the surface would stop the gap from showing
without closing it. `501` keeps it visible and refuses honestly.

**Why the pack status is left alone.** Marking a pack `SYNCED` is not merely misreporting — an edge
device that believes its queue synced is entitled to discard the local copy. A false success there
is a **data-loss path** ([[refusing-beats-leak-or-dataloss]]).

### A test-harness defect found on the way

Neither `jobs-service` nor `offline-sync-service` could create its schema under test. The entities
carry `columnDefinition = "jsonb"`, which H2 rejects, so **every `create table` failed as a
Hibernate WARNING** and the context started green against a completely empty database. No test in
either module touched persistence, so nothing ever reported it — textbook
[[zero-failures-is-not-zero-problems]]. Fixed in both `application-test.yml` by adding
`CREATE SCHEMA` + `CREATE DOMAIN JSONB AS VARCHAR` to the H2 `INIT`.

---

## 6. Red-proof and test totals

Every guard was proved by breaking what it protects and confirming RED.

| Guard | Break applied | Result |
|---|---|---|
| `outboundMessageIsQueuedNotSent` | `QUEUED` → `SENT` | **RED** ✓ |
| `escalationOutbox` | re-emit `escalation.completed.v1` | **RED** ✓ |
| `JobTriggerRefusesTest` | restored `origin/main` `JobExecutionService` | **RED** ✓ |
| `SyncPackReplayRefusesTest` | restored `origin/main` `SyncPackService` | **RED** ✓ |
| `MobileMessagingControllerTest` | restored `origin/main` controller | **RED** ✓ — 6 of 10 failed; the 4 success-path tests stayed green, proving they do not merely assert failure |

Each refusal test also carries a **negative control** — an unknown id still returns `404`, proving
`501` means "not built" and not "this route refuses everything"
([[negative-controls-target-the-boundary]]).

### Totals (all green, post-restore)

| Module | Run | Failures | Errors | Skipped |
|---|---|---|---|---|
| `jobs-service` | 20 | 0 | 0 | 2 |
| `offline-sync-service` | 20 | 0 | 0 | 2 |
| `channels-service` | 33 | 0 | 0 | 2 |
| `experience-bff` (full suite) | 1901 | 0 | 0 | 4 |

The 2 skips per service module are the pre-existing `GoldenContractTest` v1.1 auto-discovery skips.

---

## 7. Left in place, deliberately — and why

1. **No service deleted, no chart or compose entry removed.** All three remain live in
   `impilo-full-preview`. Removing a Deployment is an estate change requiring the user's
   authorisation, and the standing `helm upgrade` HOLD applies. **jobs-service and
   offline-sync-service have zero callers and are the strongest retirement candidates in the
   estate — that is a PO decision, and it is now backed by evidence rather than by a doc line.**
2. **The frozen architecture doc was not edited.** `v1.3.11` is ARCHITECTURE-FROZEN (ADR-0054,
   amended ADR-0055) and its own §Post-freeze implementation control reserves changes to ADR
   treatment. Correcting line 3207 is a governance act, not an implementation act. **It needs an
   ADR**: "delete these three" is unsafe as written and, more importantly, aims at the wrong
   target — the false success was in the BFF path in front of channels-service.
3. **No outbox publishers added.** All three still write events nothing drains. Adding publishers
   means choosing Kafka topics and touching the `EventEnvelope` wire contract shared by 33
   hand-rolled publishers — out of scope here and gated on the same routing decision already open
   for PCT's 19 unrouted events. The two services whose writes were the problem now refuse before
   reaching the outbox; channels-service still writes session/message events that reach nobody.
4. **The five non-existent `ChannelsServiceClient` routes were not implemented or removed** (§3C).
   They fail closed today. Implementing a channel registry, or deleting the methods and the BFF
   `ChannelsController` that fronts them, is a scoped piece of work needing a product decision on
   whether a channel registry belongs in channels-service or notification-service — which already
   owns `/internal/v1/channels/status`.
5. **`GET /internal/v1/channels/messages/sessions/{id}` is still missing**, so the web
   secure-messaging message list cannot load. It fails honestly (502), and building the read side
   is a channels-service feature, not a false-success correction.
6. **The stale mobile references were not rewritten** — `channelClient.ts` (SSE to a route that
   does not exist) and `syncEngine.ts` (names offline-sync-service, calls an unserved path). Both
   are in `apps/mobile`, neither is wired to a shipping screen through these services, and
   correcting them means deciding what those capabilities should call. Recorded here so they are
   not later mistaken for evidence that these services are consumed.
7. **Out of scope per the brief:** `connector-fhir-adapter` (workstream G), notification retry and
   `MockProvider`.

---

## 8. Gate condition #5 status

**Not met, but materially advanced.** Five false-success paths were closed and red-proved. The
brief's premise — that these three services carry 97 live call sites — is corrected: two carry
none. What remains is items 3–6 above, none of which is a false success; they are honest failures,
unbuilt capabilities and stale comments.
