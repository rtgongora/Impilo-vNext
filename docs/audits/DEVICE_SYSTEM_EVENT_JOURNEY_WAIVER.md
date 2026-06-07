# Device / System Event Journey — Ops-Only Waiver

> **Status:** Documented by-design waiver (not a UI gap)  
> **Journey ID:** `device-system-event`  
> **Date:** 2026-06-07

## Rationale

The Device / System Event journey is intentionally **ops-only**:

- Sovereign device registry and trust events are administered through internal ops surfaces (`/admin/devices`, trust admin BFF).
- Citizen/provider product journeys do not require a standalone transaction-complete UX for device telemetry ingestion.
- Promoting this journey to `transaction-complete` would invent orphan consumer UX without a canonical person/provider transaction lifecycle.

## Evidence

- Admin surface: [`ui/one-ui-shell/src/app/admin/devices/page.tsx`](../../ui/one-ui-shell/src/app/admin/devices/page.tsx)
- BFF: `DeviceRegistryController` → `/internal/v1/devices`
- Classifier leaves journey as `backend-partial` by design — **not** listed in `COMPLETION_EVIDENCE`.

## Measured outcome

**21/46 transaction-complete + 1 documented ops-only waiver** is the honest measured baseline after the 2026-06-07 product-truth skeptical pass (see [`PRODUCT_TRUTH_SKEPTICAL_AUDIT.md`](./PRODUCT_TRUTH_SKEPTICAL_AUDIT.md)).
