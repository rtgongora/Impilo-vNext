# Nhume — Dispatch, Delivery, Fleet Tracking & Last-Mile Logistics

> Nhume is the logistics nervous system of Impilo. It moves health-related
> goods, samples, medicines, devices, documents and supplies across the system
> safely, visibly, intelligently and accountably.

- **Display name:** Nhume
- **Formal name:** Nhume Dispatch and Delivery Service
- **Module name:** `services/nhume-service`
- **API base path:** `/api/v1/nhume`
- **Internal API base path:** `/internal/v1/nhume`
- **Event prefix:** `nhume.*`
- **Database table prefix:** `nhume_`
- **Java package:** `zw.gov.mohcc.impilo.nhume`

## 1. Scope

Nhume supports the full last-mile lifecycle for any health-relevant movement:

- Medicines, prescription refills, vaccines, blood products, equipment,
  assistive devices, lab samples, documents (where allowed), marketplace
  orders, telehealth-linked items, public-health campaign supplies,
  emergency supplies, facility-to-facility transfers, programme commodities,
  reverse logistics / returns, pickup-point and multi-stop deliveries.
- Modes range from walking couriers, bicycles, motorbikes, cars, vans and
  trucks to ambulances, boats, drones / UAVs, autonomous ground vehicles,
  robots, smart lockers, partner couriers, third-party fleets, public-sector
  vehicles, community health workers and facility-arranged transport.

## 2. Architectural placement

| Plane | What Nhume contributes |
|-------|------------------------|
| Experience | Operations dashboard, dispatcher console, fleet map, courier console, delivery & policy management, citizen tracking. |
| Clinical | Telehealth-linked deliveries, lab sample pickups, prescription refills, chain-of-custody for specimens. |
| Registry | Reads VITO (recipients), VARAPI (providers / couriers), TUSO (facilities), INDAWO (public-health sites), MSIKA (commodities); is itself the dispatch fleet & courier registry. |
| Integration | Adapters for autonomous platforms (drone, AV, robot, smart locker), third-party logistics APIs, telematics providers, route optimisers, cold-chain sensors; secure webhook receiver. |
| Trust | Every sensitive action is gated by `TrustLayerGuard` and stamped with mandatory headers. |
| Data | Outbox events drive analytics / surveillance roll-ups. Privacy rules masquerade home location to coarse zones. |
| Enterprise | Costing & payment references hook into MUSHEX. |

## 3. Domain model

Core JPA aggregates (Java entities in `zw.gov.mohcc.impilo.nhume.domain`):

```
DeliveryRequest ─┬─ DeliveryItem[]
                 ├─ DeliveryPackage[]
                 ├─ DeliveryAssignment[]
                 ├─ DeliveryRoute ─┬─ DeliveryStop[]
                 ├─ DeliveryStatusEvent[]
                 ├─ DeliveryTrackingEvent[]
                 ├─ DeliveryProof[]
                 ├─ ChainOfCustodyEvent[]
                 ├─ DeliveryException[]
                 ├─ DeliveryNotificationEvent[]
                 ├─ DeliveryCostingRecord
                 └─ DeliveryPaymentReference

FleetAsset ─┬─ VehicleTelemetry[]
            └─ AutonomousMission[]   (for drone/AV/robot fleets)

DriverCourierProfile
DispatchZone
DeliveryPolicy
DeliverySLA
DeliveryIntegrationProvider
DeliveryWebhookEvent
DeliveryAuditEvent
OutboxEvent (eventing)
IdempotencyKey (replay-safe writes)
```

Lifecycle:

```
DRAFT → SUBMITTED → VALIDATION_REQUIRED / AWAITING_APPROVAL →
APPROVED → (AWAITING_PAYMENT / AWAITING_STOCK)? →
AWAITING_PICKUP → DISPATCH_PENDING → ASSIGNED → ACCEPTED →
EN_ROUTE_TO_PICKUP → PICKED_UP → IN_TRANSIT → AT_DESTINATION →
(ATTEMPTED?) → DELIVERED | PARTIALLY_DELIVERED |
FAILED → (RETURNED) | CANCELLED | DISPUTED → CLOSED
```

Transitions and side-effects are enforced by `NhumeDeliveryService` and
covered by unit tests (`NhumeDeliveryServiceTest`).

## 4. APIs

Public (`/api/v1/nhume/*`):

| Method | Path | Purpose |
|--------|------|---------|
| GET / POST | `/deliveries` | List & create delivery requests. |
| GET | `/deliveries/{id}` | Detail. |
| POST | `/deliveries/{id}/submit` | Move DRAFT → SUBMITTED. |
| POST | `/deliveries/{id}/approve` | Approve a submitted delivery. |
| POST | `/deliveries/{id}/reject` | Reject (with reason). |
| POST | `/deliveries/{id}/cancel` | Cancel before close. |
| POST | `/deliveries/{id}/assign` | Assign courier / asset (idempotent). |
| POST | `/deliveries/{id}/reassign` | Reassign. |
| POST | `/deliveries/{id}/accept` | Courier acceptance. |
| POST | `/deliveries/{id}/decline` | Courier decline (with reason). |
| POST | `/deliveries/{id}/pickup` | Start pickup. |
| POST | `/deliveries/{id}/start` | Start transit. |
| POST | `/deliveries/{id}/location` | Record tracking event. |
| GET | `/deliveries/{id}/tracking` | Tracking log. |
| GET | `/deliveries/{id}/timeline` | Status timeline. |
| GET | `/deliveries/{id}/items` | Items in this delivery. |
| GET | `/deliveries/{id}/custody` | Custody log. |
| POST | `/deliveries/{id}/custody` | Record custody event. |
| POST | `/deliveries/{id}/proof` | Capture proof of delivery. |
| POST | `/deliveries/{id}/fail` | Mark failed. |
| POST | `/deliveries/{id}/return` | Initiate / record return. |
| GET | `/dashboard` | Operational counters, fleet/courier snapshot, alerts. |
| GET | `/dispatcher-console` | Pending queue, suggestions, available couriers / assets, breaches. |
| GET / POST | `/fleet` | List / create fleet asset. |
| GET / PATCH | `/fleet/{id}` | Asset detail / update. |
| GET / POST | `/couriers` | List / create driver, courier or operator. |
| GET / PATCH | `/couriers/{id}` | Profile detail / update. |
| GET / POST | `/zones` | Dispatch zones. |
| GET / POST / PATCH | `/policies[/{code}]` | Delivery policies. |
| GET / POST | `/integrations` | Integration providers (drone, AV, robot, smart locker, 3PL, telematics). |
| GET / POST | `/autonomous-missions` | Autonomous mission lifecycle. |
| POST | `/autonomous-missions/{id}/cancel` | Cancel mission. |
| GET | `/map-token` | Ndila map provider metadata (used by the web map page). |
| POST | `/webhooks/{providerCode}` | Authenticated webhook ingress. |

Internal (`/internal/v1/nhume/*`) mirrors the public surface for service-to-service
calls; the BFFs (mobile, web) call internal paths so that obligations and
header propagation are uniform.

## 5. Events

Outbox topics (`nhume.*`), all with envelope contract from
`shared-kernel-java`:

```
nhume.delivery.requested.v1
nhume.delivery.submitted.v1
nhume.delivery.validated.v1
nhume.delivery.approved.v1
nhume.delivery.rejected.v1
nhume.delivery.payment_required.v1
nhume.delivery.stock_confirmed.v1
nhume.delivery.assigned.v1
nhume.delivery.assignment_accepted.v1
nhume.delivery.pickup_started.v1
nhume.delivery.picked_up.v1
nhume.delivery.in_transit.v1
nhume.delivery.location_updated.v1
nhume.delivery.arrived.v1
nhume.delivery.attempted.v1
nhume.delivery.completed.v1
nhume.delivery.failed.v1
nhume.delivery.return_started.v1
nhume.delivery.return_completed.v1
nhume.delivery.cancelled.v1
nhume.delivery.exception_raised.v1
nhume.delivery.chain_of_custody_updated.v1
nhume.delivery.cold_chain_breach.v1
nhume.delivery.route_deviation.v1
nhume.delivery.proof_captured.v1
nhume.fleet.asset_location_updated.v1
nhume.autonomous.mission_created.v1
nhume.autonomous.mission_status_updated.v1
nhume.autonomous.telemetry_received.v1
```

## 6. Integrations

### Ndila (maps, geocoding, routing, ETA, geofencing)

Nhume never hard-codes Mapbox, Google Maps, OpenStreetMap or Azure Maps
directly. It calls Ndila via the `NdilaClient` interface
(`integration/ndila/NdilaClient.java`). Until Ndila ships, the
`SimulatedNdilaClient` returns deterministic provider metadata, ETAs and
geocoded labels. Swap the bean to a real HTTP client when Ndila is live.

### Comms Hub (omnichannel notifications)

Every notable lifecycle transition emits a notification through `CommsHubClient`
(`integration/commshub/CommsHubClient.java`). The default
`LoggingCommsHubClient` logs the structured intent; in production the bean is
replaced with the HTTP client that posts to Comms Hub. Templates are managed in
Comms Hub content management (not in frontend code).

### Autonomous adapters

Drone / AV / robot / smart-locker platforms plug into `AutonomousDeliveryAdapter`.
The `AutonomousAdapterRegistry` resolves the adapter for the configured
`providerCode`, with a `SimulatedAutonomousAdapter` as a clearly-labelled
fallback for demos.

### Registries

| Registry | Used for |
|----------|----------|
| VITO | Recipient identity, contact, language, consent, Impilo ID/QR confirmation. |
| VARAPI | Prescriber / provider courier verification. |
| TUSO | Facility pickup/drop-off coordinates, capabilities, managers. |
| INDAWO | Public-health sites (schools, markets, water points, outbreak depots, community pickup hubs). |
| MSIKA | Product eligibility, cold-chain / regulated flags, vendor fulfilment. |
| MUSHEX | Payment, claim switching, subsidy, exemption, programme attribution, refund. |

### Trust Layer (TSHEPO)

Mandatory headers on every write:
`X-Tenant-Id, X-Correlation-Id, X-Actor-Id, X-Actor-Type, X-Purpose-Of-Use,
X-Facility-Id?, X-Workspace-Id?, X-Shift-Id?, X-Device-Fingerprint?`.

Header presence is enforced by `TrustLayerGuard`; policy decisions are
evaluated for every create / view / assign / accept / pickup / proof /
custody / cancel / export action — including drone & autonomous trigger
paths.

## 7. Web frontend (one-ui-shell)

| Route | Page |
|-------|------|
| `/nhume` | Hub landing — cards to every Nhume surface. |
| `/nhume/dashboard` | Operations dashboard (counters, map banner, SLA panel, cold-chain & custody alerts). |
| `/nhume/deliveries` | Searchable delivery list with filters. |
| `/nhume/deliveries/new` | New delivery request form. |
| `/nhume/deliveries/[id]` | Delivery detail (overview + lifecycle controls, timeline, custody, items). |
| `/nhume/dispatcher` | Dispatcher console (queue, suggestions, availability, SLA breaches). |
| `/nhume/map` | Fleet tracking map (Ndila-driven). |
| `/nhume/courier` | Courier / driver console. |
| `/nhume/fleet` / `/nhume/fleet/[id]` | Fleet & asset management. |
| `/nhume/couriers` / `/nhume/couriers/[id]` | Driver / courier profiles. |
| `/nhume/policies` | Delivery policies. |
| `/nhume/autonomous` | Autonomous mission management. |
| `/nhume/analytics` | Operational analytics. |
| `/nhume/custody/[id]` | Chain-of-custody focused view. |
| `/nhume/track/[id]` | Citizen-facing privacy-safe tracking view. |

Frontend data access lives in `ui/one-ui-shell/src/lib/nhume.ts` and the React
Query hooks in `ui/one-ui-shell/src/hooks/useNhume.ts`.

## 8. Mobile surfaces

| App | Feature |
|-----|---------|
| `apps/mobile/citizen-app` | `NhumeTrackingScreen` (track, OTP confirm, cancel) accessible from Personal → Track Delivery. Existing marketplace delivery surface continues to work. |
| `apps/mobile/provider-app` | New `courier` mode (`CourierTabs`) for drivers, CHW delivery agents, facility runners, drone/robot operators. Includes `CourierDashboardScreen` (assignments / lifecycle) and `CourierProofScreen` (OTP, signature, facility stamp, chain-of-custody, failure reporting). |

Both apps go through the BFF mobile surface
(`/internal/v1/mobile/citizen/nhume` and `/internal/v1/mobile/provider/nhume`).
The Nhume backend itself is reachable for direct integration through
`/api/v1/nhume/*`.

## 9. Demo / seed data

`V003__nhume_seed_demo.sql` seeds (all rows tagged `is_demo = TRUE`):

- 3 delivery policies (Medicine, Lab pickup, Cold chain)
- 3 SLAs (Standard, Urgent, Emergency)
- 2 dispatch zones (Harare Central, Bulawayo Metro)
- 5 integration providers (sim drone, sim robot, sim smart locker, sim 3PL,
  sim cold-chain sensor)
- 5 fleet assets (motorbike, van, ambulance, drone, robot)
- 4 courier profiles (driver, CHW, facility runner, drone operator)
- 10 delivery requests, including:
  - 4 active deliveries
  - 2 failed deliveries
  - 1 cold-chain vaccine delivery
  - 1 lab sample pickup
  - 1 telehealth prescription delivery
  - 1 marketplace delivery
  - 1 simulated drone mission
  - 1 simulated autonomous robot mission

## 10. How to run locally

```
# Backend service
cd services/nhume-service
mvn spring-boot:run   # exposes :8120 by default

# Migrations run automatically (Flyway V001/V002/V003).

# Unit tests
mvn test
```

Frontend (one-ui-shell) and mobile apps use the existing `pnpm dev` flows;
no additional setup is required for Nhume routes.

## 11. Environment variables

| Variable | Purpose |
|----------|---------|
| `NHUME_TENANT_ID` | Default tenant for demo seed and dev. |
| `NHUME_SIMULATION_ENABLED` | Allow the simulated Ndila and autonomous adapters. |
| `NHUME_AUTONOMOUS_FALLBACK_PROVIDER` | Provider code used by the registry when no adapter is matched (default `sim-drone-1`). |
| `NHUME_WEBHOOK_HMAC_KEY` | Shared secret for webhook signature verification. |
| `NHUME_OUTBOX_TOPIC_PREFIX` | Override the `nhume.` Kafka topic prefix if you need to namespace per environment. |
| `NHUME_NDILA_BASE_URL` | Once the real Ndila service is online, point the client here. |
| `NHUME_COMMS_HUB_BASE_URL` | Production Comms Hub HTTP base; without this the logging client is used. |
| `NEXT_PUBLIC_NDILA_MAP_TOKEN` | Ndila map provider token (mapbox / google / azure) for the WebGL renderer. |
| `EXPO_PUBLIC_NHUME_TRACK_DEEPLINK_BASE` | Citizen-app deep link base for tracking URLs (e.g. `https://impilo.zw/nhume/track`). |

## 12. Known gaps

- The mobile BFF (`/internal/v1/mobile/{citizen,provider}/nhume/*`) needs the
  matching `experience-bff` controllers added; mobile screens are wired to
  these paths but fall through gracefully if 404.
- The real Ndila WebGL map component is not yet bundled with Nhume; the web
  fleet-tracking page currently renders the same data as text-and-list
  fallbacks plus the Ndila provider banner.
- Suggested-assignment scoring is implemented as a simple heuristic
  (`/dispatcher-console`); a learned dispatch optimiser is a Nompilo / data
  platform follow-on.
- Real autonomous adapters (DJI, Zipline, KiwiBot, Cleveron Smart Lockers,
  partner 3PLs) are not bundled; the registry will pick them up automatically
  when their `AutonomousDeliveryAdapter` beans are added.
- Webhook signing currently supports HMAC-SHA256 only.
