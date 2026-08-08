# ZIBO terminology programme — migration lease

**Lane:** ZIBO terminology & semantic authority (`docs/architecture/zibo-terminology-service-spec.md`)
**Claimed:** 2026-08-08

## Committed block

| Service | Block | Status |
|---|---|---|
| `zibo-service` | **V400–V449** | claimed by this lane |

## Why V400 and not V009

`zibo-service`'s namespace already carries four committed claims from other lanes:

| Block | Lane | Source |
|---|---|---|
| V008–V014 | Surgery / Procedures | `iatg-adult-medicine-leases.md:69` |
| V035–V049 | Adult Medicine | `iatg-adult-medicine-leases.md:69`, `iatg-emergency-leases.md:538` |
| V200–V219 | Emergency | `iatg-emergency-leases.md:217` |
| V300–V329 | Surgery / Procedures | `iatg-surgery-procedures-leases.md:120` |

`V009` is inside surgery's block. The obvious "next free number after the highest file on disk"
(`V300` → `V301`) is inside surgery's block too.

The surgery lane's standing rule, quoted at `iatg-emergency-leases.md:531-535`, is why this lane
takes a distant block rather than an adjacent one:

> *a reservation is a claim about the future written in a namespace anyone may extend. Adjacency
> puts the claim exactly where the next incremental writer will land, so the claim and the collision
> occupy the same address by construction.*

V400–V449 is clear of every committed block and of the incremental writer's path.

## Standing rule inherited from the emergency lane

**Read the lease files, not the announcement messages.** A message is a draft; the committed lease
is the only source of truth. This file is the claim.

## Contents of the block

| Migration | Purpose |
|---|---|
| `V400__validation_telemetry_and_version_scheme.sql` | Z1 — make the coding gap measurable; version resolution by declared scheme rather than creation time |
| V401–V449 | reserved for Z2 (`zibo_concept` index), the rights manifest, and content loading |

## Co-edited services

This lane expects to touch `oros-service` and `butano-service` for their dead terminology routes,
and `clinical-knowledge-platform-service` for EPI-derived vaccine content. Those are code changes,
not migrations. If a migration becomes necessary in any of them, a block is claimed here first.
