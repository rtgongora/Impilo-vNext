# ADR-0056 — Scheduler locking: correcting the measured claim, and deferring general locking

- **Status:** Accepted
- **Date:** 2026-08-07
- **Phase:** 0 · Workstream C
- **Supersedes:** the scheduler-locking rows of the v1.3.11 gap register (§17.1 item 7, §21 remediation
  list, §22 priority table item 4) — corrected in place, with the reasoning recorded here
- **Relates to:** `docs/architecture/hybrid-federated-target-architecture-v1.3.11.md` (frozen under
  ADR-0054)

> Numbering note: per ADR-0054, the ADR sequence is **shared** across two directories — `docs/adr/`
> for service and product decisions, `docs/architecture/adr/` for architecture-scoped ones. This ADR
> corrects the frozen architecture document's gap register, so it is filed here alongside ADR-0054
> and ADR-0055. **ADR-0056** is the next identifier in the shared sequence and is not reused in
> either directory.
>
> *(This ADR was first filed under `docs/adr/` in commit `271116183` — an error: the cross-link from
> the architecture document pointed here all along, so the link did not resolve. Moved with history
> preserved.)*

## Context

The frozen architecture asserted a live, active defect:

> "Distributed scheduler locking — **139 `@Scheduled` annotations across 74 services and zero locks
> anywhere**"
>
> "**⚠ Double-firing is not hypothetical — it is happening now.** `experience-bff` already runs
> `replicaCount: 2` and carries three `@Scheduled` beans … Appointment reminders are being scheduled
> twice in the current estate."

Phase 0 · Workstream C re-measured this before building against it. **The double-firing claim is
false.** The correction is recorded here because a design assertion in a frozen document is a
contract term, and a wrong one recruits work that does not need doing while hiding the one that does.

This ADR records **evidence**, not an architecture decision. It changes no decision the freeze
governs; it corrects a current-estate measurement in the gap register and defers a remediation item.

## What was measured (2026-08-07)

| Claim in v1.3.11 | Measured | Method |
|---|---|---|
| 139 `@Scheduled` annotations | **131**, across **74** services | `git grep -cE "^\s*@Scheduled" -- '*.java'` |
| — | (a bare `grep -c "@Scheduled"` returns **142**) | Javadoc and inline comments reference the annotation in prose; `ServiceClientConfig`, `SystemTrustContext` and `TenantContextFilter` each mention it and carry no annotation. **Anchor the pattern.** |
| 3 scheduled beans in `experience-bff` | **4** | `AppointmentReminderScheduler`, `BookingOutboxCommsPoller`, `WorkContextRevalidationJob`, `ShadowObserverHealthReporter` |
| "zero locks anywhere" | **True in code** | All 11 hits for ShedLock / `SKIP LOCKED` are this document and its archived drafts — the document was matching itself. No advisory lock or leader election anywhere. |
| "reminders are being scheduled twice **now**" | **False — the send is atomically deduplicated** | Both dispatch paths claim through `AppointmentReminderReceiptStore.tryClaim()`, a Redis `SET key value NX EX`. That is a genuine cross-replica primitive; it is simply not a general-purpose one. |

**Only `experience-bff` is multi-replica among Java services.** `kubectl get deploy -n
impilo-full-preview` returns exactly three workloads at `replicas: 2` — `experience-bff` (Java),
`one-ui-shell` and `public-website` (both Next.js, no `@Scheduled` beans). A `replicaCount` in a
values file is not what is running; the cluster was asked.

**Therefore 130 of the 131 annotations are latent, not live.** They sit in single-replica services
where a scheduler cannot race itself.

### The cursor races; the send does not

The scan in `AppointmentReminderScheduler` genuinely runs on both replicas, and both replicas
genuinely evaluate the same due appointments. What cannot happen twice is the **send** — the losing
replica learns it lost at `tryClaim` before it notifies anyone. The defect the document described
sits one layer above the layer that already prevents it.

### Empirical verification (C1)

Two separate JVM processes — one per replica, each with its own
`AppointmentReminderReceiptStore` and therefore its own in-memory state — raced the same dedup key
against one real Redis, released from a common wall-clock barrier:

- **Healthy Redis, 4 runs:** `CLAIM=true` × 1, `CLAIM=false` × 1, every time.
- **Negative control (Redis unreachable):** `CLAIM=true` × **2** — both replicas send.

The control matters as much as the result: it proves the measurement could detect a double-send, so
the 1-of-2 outcome means something. It also reproduced the one real defect (below) directly.

## The one real defect, and its fix (C2)

`AppointmentReminderReceiptStore` caught any Redis failure and fell back to a **per-pod**
`ConcurrentHashMap`:

```java
} catch (Exception ex) {
    log.debug("Redis reminder dedup unavailable, using local fallback: {}", ex.getMessage());
    return localFallback.add(dedupKey);          // PER POD — not a claim at all
}
```

A per-pod set is not a claim: every replica wins its own copy independently, so **every replica
sends**. When Redis was unreachable, dedup silently degraded to duplicate SMS to real patients — and
because the degradation was logged at `debug`, it was invisible in production.

**Decision — refuse rather than duplicate.** The store now returns `UNAVAILABLE` when no shared claim
can be made, and no replica sends. The product call behind it: for an appointment reminder, **a late
reminder is recoverable and a duplicate one is not**.

Refusing is only safe because refused work is retried, never dropped, and both callers were checked:

- `dispatchUpcomingReminders` scans a window two hours wide (24h ± 1h) on an hourly cron, so each due
  appointment is offered **at least twice**; a refusal during a short outage is picked up next run.
- `BookingOutboxCommsPoller` now **holds its cursor** at a refused event instead of advancing past
  it. This was the sharp edge: the outbox is read forward-only, so advancing past an unhandled event
  is a permanent drop. A boolean `tryClaim` could not express the difference between "a peer has this"
  (settled — advance) and "nobody could claim it" (unsettled — hold), so `claim()` now returns a
  `ClaimOutcome` and the poller branches on `mustRetry()`.

The outage is now logged at `error`, once per healthy→degraded edge rather than once per appointment.

**This creates a deliberate coupling: Redis availability is now on the patient-facing send path.**
The v1.3.11 durable-Redis item (§22 priority table item 8) carries that note.

## Decision

1. **The double-firing claim is withdrawn.** Scheduler locking is not a live current-estate defect.
2. **The one real hole — the Redis-unreachable fallback — is fixed** under Phase 0, with the refusal
   posture stated in the code rather than left implicit.
3. **General scheduler locking is deferred.** No ShedLock, no advisory locks, no leader election now.

## Why deferred, not omitted

There is no second multi-replica Java service, so 130 of the 131 annotations cannot race. Building a
general locking layer today would be unexercised infrastructure guarding a condition that does not
exist — and unexercised guards rot silently.

**Trigger condition — build it when this becomes true:**

> **The second Java service goes `replicas > 1`.**

Whoever raises a Java service above one replica owns this work as part of that change. The check is
one command:

```bash
kubectl get deploy -n <namespace> -o custom-columns='NAME:.metadata.name,DESIRED:.spec.replicas' --no-headers | awk '$2>1'
```

If that returns a Java workload other than `experience-bff`, this deferral has expired.

**Where it goes when it does:** a `SchedulerLockService` in `libs/tech-companion`. That library is
already a dependency of **95 of 105 service modules**, and it already owns the exact idiom the lock
needs — `INSERT … ON CONFLICT DO NOTHING` in
[`JdbcIdempotencyRepository`](../../../libs/tech-companion/src/main/java/zw/gov/mohcc/impilo/companion/idempotency/JdbcIdempotencyRepository.java)
(line 64). A database-backed lock is preferred over k8s leader election: it needs no new RBAC, works
identically in Compose and Kubernetes, and fails in the same direction as the data it guards.

⚠ **Do not mistake the k8s Lease RBAC for existing infrastructure.** `infra/k8s/rbac/service-roles.yaml`
grants `coordination.k8s.io/leases` (lines 52–53), but it was never applied: `impilo-full-preview`
holds one role (`estate-health-watch`) and **zero Lease objects**. It is dead configuration.

## Explicitly out of scope

Two `experience-bff` beans do **not** need locks, and adding them would be churn:

- **`WorkContextRevalidationJob`** — an idempotent status transition, and disabled by default
  (`impilo.work-context.revalidation.enabled:false`). Running it twice reaches the same state.
- **`ShadowObserverHealthReporter`** — logs per-pod counters. Double execution across two pods is the
  *correct* behaviour; locking it would hide one pod's telemetry.

## Consequences

- The Phase 0 scheduler-locking item is closed as a **corrected measurement plus one targeted fix**,
  not a locking programme.
- A Redis outage now delays appointment reminders instead of duplicating them. This is intentional
  and raises the priority of the durable-Redis work.
- The 130 latent annotations remain unlocked and are documented as such. The trigger above is the
  control that stops this being a silent omission.
- If either caller's retry property is ever removed, the refusal posture turns into a silent drop.
  Both are named in the class Javadoc so the coupling is visible at the point of change.

## Verification

- Two-JVM race, healthy Redis: exactly one send (4/4 runs). Negative control with dedup unavailable:
  two sends — the measurement can detect the failure it claims to rule out.
- Red-proof: reinstating the per-pod fallback turns **3 tests RED**, including one whose failure
  message shows the duplicate notifications directly. Restored: green.
- `experience-bff` suite: **1891 tests, 0 failures, 0 errors, 4 skipped** (`Tests run:` lines
  observed — a compile failure under `mvn -q` exits 0 and prints nothing).
