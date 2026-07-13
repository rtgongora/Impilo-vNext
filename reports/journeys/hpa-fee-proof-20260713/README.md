# HPA Regulatory Fee Rails — Runtime Proof (2026-07-13)

Runtime proof for **K2 — regulatory fee rails** (SI 78 of 2017; amounts never
invented in code). Rig: `scripts/runtime-proof/hpa-fee-journeys.sh`.

## What ran

Scratch Postgres 16 + Redis 7 + Redpanda; **tuso + costing-engine + mushex**
booted from freshly built jars. The publisher runs in its production default
**DUAL emit mode** (legacy + v1.1) — not a legacy-only shortcut. A TEST fee
amount is configured through the governed endpoint and explicitly marked
`RIG-TEST-CONFIG (not SI 78)`; no SI 78 amount is hard-coded anywhere.

Result: **PASS=16 FAIL=0** (see `journal.txt`).

| Journey | Proves |
|---|---|
| **J-FEE-1** | configured fee → submit lands `DUE`/`AWAITING_FEE` → COSTA opens a `REGULATORY_APPLICATION_FEE` charge (from the `fee_due` Kafka event) → charge id written back as `fee_reference` → MusheX intent minted → sandbox settle → `mushex.payment.status.changed` → tuso `PAID` → **COSTA charge SETTLED** → ready-for-inspection allowed |
| **J-FEE-2** | gate holds: ready-for-inspection rejected while fee `DUE` |
| **J-FEE-3** | waiver: `DUE` → `WAIVED` with reason → **COSTA charge WAIVED** → gate opens |
| **J-FEE-4** | honesty: fee required but no active amount → `NOT_CONFIGURED` recorded (never silently skipped), no charge fabricated, never gates inspection |

## Root cause fixed to reach green under DUAL

The first DUAL runs stalled at 14/16 (`fee_paid`/`fee_waived` never reached
COSTA). Root cause was a **platform-wide latent bug**, not a rig artifact:

`EventEnvelope`'s constructor used `Map.copyOf(payload)`, which NPEs on any
JSON-null field value. The fee payload legitimately carries `classCode: null`
(a fee that applies to all HPA classes). The NPE was swallowed by the outbox
drain, head-of-line-blocking every later fee row — **any service emitting a
payload with a null field would silently stall its v1.1 outbox.**

Fixes (committed): null-tolerant envelope copy + poison-row logging in the
shared outbox drain; `causation_id`/`correlation_id`/`occurred_at` stamped on
the tuso fee and classification emits.

## Not deployed

Fee amounts remain **NULL / PENDING** until the PO supplies the SI 78 of 2017
schedule through the governed fee-schedule endpoint. No deploy performed.
