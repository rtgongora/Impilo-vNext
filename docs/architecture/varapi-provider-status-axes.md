# Varapi Provider Status Axes — Wave-2 Consolidation

**Status:** implemented (IATG Wave-2, W2-1)
**Scope:** `services/varapi-service` only. No BFF/UI/other-service changes.
**Migration:** `V019__provider_status_axis_consolidation.sql`

## Problem

`varapi.provider` historically encoded provider status across **five overlapping
axes**, so "is this provider operational?" was answerable four different ways and
"suspended" three different ways:

| Column | Type | Role before Wave-2 |
|--------|------|--------------------|
| `status` | VARCHAR | operational flag (ACTIVE/SUSPENDED/INACTIVE/REVOKED) |
| `lifecycle_status` | VARCHAR (`ProviderLifecycleStatus`) | operational + registration lifecycle |
| `licence_status` | VARCHAR | licence flag |
| `professional_standing_status` | VARCHAR | **dead** — declared (V005) + echoed in standing-summary, never written by any code path |
| `active_flag` | boolean | operational flag |

Plus the two additive Wave-1 axes `trust_level` (`ProviderTrustLevel`) and
`registry_status` (`ProviderRegistryStatus`).

## Target model (implemented)

Three **CANONICAL** axes — the source of truth, kept as-is:

- **`lifecycle_status`** — operational + registration lifecycle, including the frozen
  bootstrap values `PRELOADED` / `CLAIMED`.
- **`registry_status`** — Wave-1 honest cross-source registry verdict.
- **`trust_level`** — Wave-1 evidence ladder.

Three **DERIVED** axes — kept as columns (external readers depend on them) but now
**only ever a deterministic projection of `lifecycle_status`**, never set independently:

- `status`, `active_flag`, `licence_status`.

One **DROPPED** axis:

- `professional_standing_status` — removed end-to-end (V019 drops the column; entity
  field + getter/setter removed; removed from `ProviderStandingSummary` DTO and the
  controller standing-summary). Audit confirmed **zero writers** and only a self-echo
  read, so removal is safe.

## The single normalization point

`ProviderEntity.deriveStatusProjections()` recomputes `status`, `active_flag`, and
`licence_status` from `lifecycle_status`. Every writer sets `lifecycle_status` and then
calls it, instead of setting the derived columns independently:

| Writer | Before | After |
|--------|--------|-------|
| `ProviderService.createProvider` | `setStatus("ACTIVE")` | `setLifecycleStatus("REGISTERED")` + derive |
| `ProviderService.changeStatus` | `setStatus(newStatus)` | `setLifecycleStatus(lifecycleForStatusChange(newStatus))` + derive |
| `ProviderApplicationService.approveAndActivate` | set status/lifecycle/licence/active | `setLifecycleStatus("LICENCED_ACTIVE")` + derive |
| `ProviderBootstrapService` PRELOADED | set status/lifecycle/active | `setLifecycleStatus(PRELOADED)` + derive |
| `ProviderBootstrapService` CLAIMED | set status/lifecycle/active | `setLifecycleStatus(CLAIMED)` + derive |

`isActive()` now reads `lifecycle_status` **only** (previously ANDed `status` +
`active_flag`, both of which are now themselves projections).

### Projection map (`lifecycle_status` → derived axes)

| lifecycle_status | status | active_flag | licence_status |
|------------------|--------|-------------|----------------|
| `LICENCED_ACTIVE`, `LICENCE_DUE_FOR_RENEWAL`, `RENEWAL_IN_PROGRESS` | ACTIVE | true | ACTIVE |
| `REGISTERED`, `CLAIMED` | ACTIVE | true | null |
| `SUSPENDED`, `RESTRICTED` | SUSPENDED | false | SUSPENDED |
| `REMOVED` | REVOKED | false | REVOKED |
| everything else (`PRELOADED`, `DRAFT`, `APPLICATION_IN_PROGRESS`, `UNDER_VERIFICATION`, `PENDING_REVIEW`, `PENDING_COMMITTEE_REVIEW`, `LAPSED`, `RESTORATION_IN_PROGRESS`, `RETIRED`, `DECEASED`) / unknown | INACTIVE | false | null |

### Status-change vocabulary round-trip

The `changeStatus` API keeps its external vocabulary. `lifecycleForStatusChange` maps it
onto the canonical lifecycle value that projects back to the requested status:

`ACTIVE → LICENCED_ACTIVE`, `SUSPENDED → SUSPENDED`, `INACTIVE → RETIRED`, `REVOKED → REMOVED`.

## Behavioural notes / deviations

- `changeStatus` with target `ACTIVE` now also sets `lifecycle_status = LICENCED_ACTIVE`
  and `licence_status = ACTIVE` (previously it touched only `status`). This is the intended
  consolidation: the derived columns are now strict projections.
- `ProviderBiometricService` (~L240) sets `active_flag` on the **biometric profile**
  entity (`ProviderBiometricProfileEntity`), not on the provider's status axes, so it was
  **not** routed through the provider normalizer (doing so would be incorrect — different
  entity, different axis).

## What a future full column-removal would need

Dropping `status`, `active_flag`, and `licence_status` entirely (not just consolidating)
would require, before the drop:

1. Migrating every **external reader** off these columns onto `lifecycle_status`
   (and/or `registry_status`), including any downstream services/reports/queries that
   filter on `status = 'ACTIVE'`.
2. Replacing repository queries such as `findByTenantIdAndStatus(...)` with lifecycle-based
   equivalents.
3. Re-pointing the `ProviderStandingSummary` consumers (commerce/booking/PIC gating) at
   lifecycle-derived views.
4. A follow-up Flyway migration to drop the columns once no reader remains.

That is intentionally **out of scope** for Wave-2; the columns stay as a stable projection.
