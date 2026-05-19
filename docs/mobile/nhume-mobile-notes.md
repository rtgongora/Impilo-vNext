# Nhume mobile implementation notes

Nhume is the dispatch and delivery service. It is mobile-first because
dispatch, handover, routing, tracking and confirmation typically happen in
the field.

## Roles and surfaces

| Role | Mobile surface | Backed by |
| --- | --- | --- |
| Courier (provider in `courier` mode) | `CourierTabs` → `CourierDashboardScreen` | `/internal/v1/mobile/provider/nhume/...` |
| Provider (dispatcher view) | `ProviderTabs` → planned dispatch summary card on `ProviderDashboardScreen` | same provider surface |
| Citizen | `HomeScreen → Track` → `NhumeTrackingScreen` | `/internal/v1/mobile/citizen/nhume/...` |

The mobile apps **never** call `nhume-service` directly. All requests go via
the experience-bff mobile controllers (`CitizenNhumeController`,
`ProviderNhumeController`) which forward through the trust-aware
`serviceRestTemplate`.

## Wire shape

Both controllers return a consistent envelope:

```json
{ "data": <payload>, "meta": { "request_id": "...", "correlation_id": "..." } }
```

### Citizen endpoints

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/internal/v1/mobile/citizen/nhume/deliveries` | List the citizen's deliveries (coarse view). |
| GET | `/internal/v1/mobile/citizen/nhume/deliveries/{id}` | Detail. |
| GET | `/internal/v1/mobile/citizen/nhume/deliveries/{id}/timeline` | Citizen-safe `status_events` only. |
| GET | `/internal/v1/mobile/citizen/nhume/deliveries/{id}/tracking` | Coarse tracking events (privacy trimmed by nhume). |
| POST | `/internal/v1/mobile/citizen/nhume/deliveries` | Self-service delivery request. |
| POST | `/internal/v1/mobile/citizen/nhume/deliveries/{id}/proof` | OTP receipt confirmation. |
| POST | `/internal/v1/mobile/citizen/nhume/deliveries/{id}/cancel` | Cancel. |

### Provider (courier) endpoints

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/internal/v1/mobile/provider/nhume/assigned` | Assigned-to-me list (falls back to `status=ASSIGNED` until nhume exposes a courier-scoped endpoint). |
| POST | `.../{id}/accept` | Accept assignment. |
| POST | `.../{id}/decline` | Decline with reason. |
| POST | `.../{id}/pickup` | Confirm pickup. |
| POST | `.../{id}/start` | Start transit. |
| POST | `.../{id}/location` | Periodic GPS ping (foreground only). |
| POST | `.../{id}/proof` | Capture proof of delivery. |
| POST | `.../{id}/custody` | Chain-of-custody event. |
| POST | `.../{id}/fail` | Mark failed with reason. |

## Graceful degradation

- If `nhume-service` is unreachable, GETs return an empty envelope (data:
  `[]` or `{}`) so the UI shows an empty state, not a red error screen.
- POSTs return `502` with `{ error: { code: "NHUME_UNAVAILABLE", message:
  "Delivery service is temporarily unavailable." } }` and the mobile code
  shows a retry-aware toast.
- `NhumeTrackingScreen` honest copy: "Live tracking not available for this
  delivery — last update at …" when the tracking event list is empty.

## Privacy and audit

- Citizen tracking endpoints only forward `tracking_events`; raw courier
  identity, vehicle id and exact coordinates are stripped by `nhume-service`
  before the BFF sees them.
- Provider tracking endpoints include the full payload (the BFF does no
  extra trimming) but only authorised provider roles can reach them via
  Tshepo.
- Every POST is performed through `serviceRestTemplate`, which auto-forwards
  the v1.1 trust headers (purpose of use, actor id/type, tenant, facility,
  workspace, shift, correlation id, device fingerprint).

## Maps

The Provider courier dashboard does **not** embed a vendor map widget. All
map work happens through `@impilo/mobile-ndila` (see
`docs/mobile/ndila-mobile-notes.md`). The wave 1 build ships with the
timeline / list view; the map overlay is a deferred follow-up tracked in
`mobile-catchup-wave-nhume-ndila-integration.md → Known gaps`.

## Future autonomous-delivery readiness

The DTOs and screens carry a `delivery_mode` field (courier / facility /
community-health-worker / fleet / drone / robot). The mobile UI labels
unknown values literally rather than pretending they are live. If a delivery
arrives with `delivery_mode = "DRONE"` from a future adapter, the citizen
timeline will say "Drone delivery — please stay near the pickup point"
without inventing flight telemetry.
