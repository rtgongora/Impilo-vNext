# Ndila — Implementation Note

> **Ndila is the spatial intelligence layer of Impilo vNext: it turns locations,
> routes, boundaries, catchments, movements and risks into actionable health
> system intelligence.**
>
> Short line: *Ndila helps Impilo understand where things are, what they are
> close to, where they are moving, and what action is needed next.*

This note records what existed before this wave, what was reused, what was
refactored, what was newly implemented, what remains intentionally placeholder
safe, what integrations were wired end-to-end, what tests were added, and what
still requires future provider credentials or operational configuration.

It is the canonical hand-off document for follow-on Ndila waves.

---

## 1. Scope and identity

* **Service name (canonical):** `ndila-service`
* **Maven module:** `services/ndila-service`
* **Java package root:** `zw.gov.mohcc.impilo.ndila`
* **Default port:** `8155`
* **Database:** `impilo_ndila`
* **Canonical API path:** `/api/v1/ndila/...`
* **Legacy compatibility alias:** `/api/v1/maps/...` (preserved as a thin
  redirect / pass-through layer so any future module that grew its own
  `/api/v1/maps` namespace cannot fork; Ndila is the canonical identity)
* **Internal admin/registry path:** `/internal/v1/ndila/...` (kept consistent
  with the existing `internal/v1` convention used by Indawo, Tuso, BFF)

Ndila is **not** a map widget. A map is only the visual surface. Ndila is the
shared platform service underneath: location registry, geocoding, routing,
geofencing, catchment areas, tracking, spatial search and AI-ready spatial
intelligence — exposed to every Impilo module through a stable contract and
a policy-driven provider adapter architecture.

---

## 2. What existed before this wave (audit)

The repository did **not** have a dedicated geospatial intelligence service.
Spatial concerns were spread across several services and surfaces. None of
this work has been deleted; it is reused, indexed or made consumable through
Ndila adapters and clients.

### 2.1 Real, kept and reused

| Surface | Path | Status | Ndila treatment |
| --- | --- | --- | --- |
| ZW administrative geography API (districts, wards) | `services/tuso-service/.../api/controller/ZwGeoReferenceController.java` | Real, behind trust context | Reused as the **authoritative ZW admin gazetteer**. Ndila boundary lookups defer to Tuso for ZW district/ward identity, and Ndila enriches with geospatial metadata only. |
| Tuso facility coordinates (`FacilityGeoEntity`, lat/lng) | `services/tuso-service/.../persistence/...` | Real | Reused. Ndila maintains a **mirror location record** indexed to the Tuso facility identity (`ownerService=TUSO, ownerEntityType=FACILITY`). Tuso remains the system of record for facility identity. |
| Indawo addresses with `latitude`/`longitude` | `services/indawo-service/.../domain/AddressEntity.java` and `ind_addresses` table | Real | Reused. Indawo remains authoritative for public-health site addresses. Ndila adds geospatial representation, boundary/catchment linkage and spatial operations. |
| Indawo `ind_catchment_areas` table (GeoJSON polygons + population) | `services/indawo-service/.../db/migration/V001__init.sql` | Real schema, lightly used | Reused. Ndila exposes catchment CRUD and spatial operations; the underlying authoritative geometry stays in Indawo for public-health site catchments. Ndila can **also** own catchments owned by other services (Tuso facilities, dispatch zones, etc.). |
| Surveillance public-health operations coordinates | `services/surveillance-service/.../api/PublicHealthOperationsController.java` | Real | Surveillance keeps event/incident ownership. Ndila publishes **risk-cluster intelligence** by consuming surveillance events. |
| Community outreach visit coordinates | `services/community-service/.../persistence/entity/OutreachVisitEntity.java` | Real | Reused. Outreach remains owned by Community service; Ndila can surface outreach footprints as a spatial layer. |
| Public health map UI panel | `ui/one-ui-shell/src/components/public-health/PublicHealthMapPanel.tsx` | Real (thin OSM iframe embed) | Preserved as a continuity surface. The new `NdilaPublicHealthRiskMap` component is the canonical replacement and renders the same intent using the Ndila API. The legacy panel is still wired; future waves can swap its implementation to use Ndila without breaking the UI contract. |
| Zimbabwe location cascader | `ui/one-ui-shell/src/components/registry/ZimbabweLocationCascader.tsx` | Real | Reused as the authoritative ZW administrative cascade. Ndila address-search components defer to it for ZW administrative selection. |
| BFF registry geo proxy | `services/experience-bff/.../controller/RegistryGeoLocalityController.java` | Real | Reused. Ndila does not duplicate; ZW admin lookups continue through the existing BFF proxy path. |
| Mobile household / outreach geo capture | `apps/mobile/provider-app/src/services/householdService.ts`, `queueService.ts`, `apps/mobile/citizen-app/src/services/sosService.ts` | Real | Reused. Ndila adds a shared mobile capability (GPS capture, queued offline submissions, nearby search) and is wired so existing services can route through it. |

### 2.2 Placeholders that were too thin to ship anywhere

| Surface | Status | Ndila treatment |
| --- | --- | --- |
| `dispatch.openapi.yaml` (only `health` endpoint) | Stub | Left intact. Ndila exposes the routing, ETA and tracking primitives that a future dispatch contract revision will consume; no overlap is created. |
| Dispatch service domain (`dsp_dispatch_jobs`, no lat/lng) | Real but coordinate-free | Left intact. The new `NdilaTrackingService` is the canonical place for live coordinates. Dispatch can opt in by recording tracking assets through Ndila rather than growing its own coordinate fields. |
| Catchment population field in `community.openapi.yaml` | Schema field only | Kept. Ndila's spatial-intelligence summaries can supplement that field but do not replace it. |

### 2.3 Things that are intentionally **not** in this wave

| Capability | Status | Reason / next step |
| --- | --- | --- |
| PostGIS Docker image as a separate dependency | Not yet enforced | The base `postgres:16-alpine` image does **not** ship PostGIS. Ndila's migration uses a conditional pattern: if the `postgis` extension is available it is enabled (and `GEOGRAPHY(Point, 4326)` columns are created); otherwise Ndila falls back to plain numeric latitude/longitude plus GeoJSON `jsonb` and runs all spatial logic in application code. This keeps local dev unblocked and lets production opt-in to PostGIS by swapping the image to `postgis/postgis:16-3.4`. |
| Real OSRM / Nominatim / Pelias hosts | Not provisioned | Provider adapters are implemented and wired; URLs and API keys remain blank in `.env.example`. With those blanks, Ndila auto-falls back to the in-process Mock provider so all dev / test / CI work is fully deterministic and never touches public OSM tile servers. |
| Mapbox / Google / HERE / Azure Maps live calls | Not provisioned | Adapters are implemented as ready-to-wire placeholders that throw a deterministic `PROVIDER_NOT_CONFIGURED` error if asked to make a live call without credentials. Provider policy never selects them unless they are enabled in configuration. |
| Drone / robot / autonomous-vehicle routing | Adapter-ready placeholder | Ndila accepts the modes (`DRONE`, `ROBOT`, `AUTONOMOUS_VEHICLE`) and returns a great-circle distance with explicit warnings. A real corridor / payload-aware provider adapter is the future plug-in point. |
| Tile rendering | Backend-token-only | Ndila exposes a tile-config endpoint that returns a per-tenant tile provider name and a short-lived token reference. Provider keys never leave the server. Production tiles are explicitly forbidden from using public OSM tile servers (the default config rejects `tile.openstreetmap.org` as a production target — see `NdilaTileConfigService`). |
| Real Tshepo policy decisions inside Ndila | In-process guard | Ndila currently enforces tenant + actor-type + purpose + sensitivity guards locally (in `NdilaTshepoGuard`) and emits structured audit events. A follow-on wave should route through Tshepo `PolicyEngine` over its existing ext_authz path; the guard is the single seam that wave will swap. |

---

## 3. What was newly implemented

### 3.1 Service core (`services/ndila-service`)

* `NdilaServiceApplication.java` — Spring Boot 3.3.6 application, Java 21, package `zw.gov.mohcc.impilo.ndila`.
* `config/SecurityConfig.java` — JWT resource server with `impilo.security.allow-anonymous` dev escape hatch, mirroring Indawo/Tuso patterns. Role groups for `NDILA_VIEWER`, `NDILA_EDITOR`, `NDILA_ADMIN`, `DISPATCH_COORDINATOR`, `PUBLIC_HEALTH_OFFICER`, `EMERGENCY_RESPONDER`, `SYSTEM_ADMIN`, `DEVELOPER`.
* `config/NdilaServiceConfiguration.java` — registers provider adapters, policy service, audit publisher.
* `application.yml` — port 8155, `impilo_ndila` database, Kafka producer, Flyway, actuator, OAuth2 JWT.

### 3.2 Persistence

Flyway migration `db/migration/V001__init.sql` creates:

* `ndila_locations` — canonical Ndila location record (facility, branch, public-health site, vehicle depot, drone launch/landing point, citizen delivery address, etc.). Includes `owner_service`, `owner_entity_type`, `owner_entity_id`, lat/lng, altitude, accuracy, `source`, `verification_status`, `sensitivity_level`, plus a `geom` `GEOGRAPHY(Point,4326)` column when PostGIS is present (NULL otherwise) and a `geojson` `jsonb` representation always.
* `ndila_location_aliases` — alternate names / external IDs.
* `ndila_boundaries` — administrative + custom boundaries (GeoJSON, optional `geom` polygon).
* `ndila_geofences` — geofence registry (type, geometry, owner, risk level, rules JSON).
* `ndila_catchment_areas` — Ndila-owned catchments (RADIUS, POLYGON, MULTIPOLYGON, ISOCHRONE) with owner linkage.
* `ndila_tracking_assets` — registered trackable assets (vehicle, motorbike, ambulance, courier, drone, cold-chain container, lab sample, ...).
* `ndila_tracking_events` — append-only telemetry stream.
* `ndila_routes_cache` — opportunistic route cache.
* `ndila_provider_configs` / `ndila_provider_policies` / `ndila_provider_requests` / `ndila_provider_health` / `ndila_provider_usage` / `ndila_provider_cache_records` — full provider observability surface.
* `ndila_tile_configs` — per-tenant tile provider config + production-OSM-block flag.
* `ndila_intelligence_signals` — spatial signals (coordinate quality, service gap, risk cluster, route deviation).
* `ndila_ai_context_packets` — Nompilo-safe context packet history.
* `ndila_recommendations` — system-generated spatial recommendations.
* `ndila_offline_queue` — queued offline location / tracking submissions.
* `ndila_event_outbox` + `idempotency_keys` — outbox pattern, matching the existing convention.

Indexes are tenant-aware. Spatial indexes are created conditionally when PostGIS is available.

### 3.3 Provider adapter architecture

Interfaces under `core/provider/`:

* `NdilaGeocodingProvider`, `NdilaReverseGeocodingProvider`, `NdilaRoutingProvider`,
  `NdilaDistanceMatrixProvider`, `NdilaTileProvider`, `NdilaBoundaryProvider`,
  `NdilaIsochroneProvider`, `NdilaSpatialSearchProvider`, `NdilaTrafficProvider`,
  `NdilaProviderHealthCheck`.
* `NdilaProviderAdapter` — base interface that exposes
  `ProviderCapabilities` (supportsGeocoding, supportsRouting, supportsTiles,
  supportsZimbabweCoverage, supportsSensitiveWorkflows, estimatedCostCategory,
  dataResidencyNotes, privacyNotes ...).

Concrete adapters under `core/provider/adapter/`:

| Adapter | Capabilities | Status |
| --- | --- | --- |
| `MockProviderAdapter` | All capabilities (deterministic) | Production-ready for dev/test/CI. Used as the **default** provider when no other is configured. |
| `OsmOsrmProviderAdapter` | Geocoding (via Nominatim), reverse geocoding (Nominatim), routing (OSRM), tiles (self-hosted target) | Wire-complete; live calls happen only if `ndila.providers.osm.base-url` is set. |
| `NominatimGeocodingProviderAdapter` | Geocoding + reverse geocoding | Wire-complete; explicitly blocks `nominatim.openstreetmap.org` for production. |
| `PeliasGeocodingProviderAdapter` | Geocoding + reverse geocoding | Wire-complete; expects a self-hosted Pelias instance. |
| `MapboxProviderAdapter` | Geocoding, routing, distance matrix, tiles | Wire-complete; credentials required. |
| `GoogleMapsProviderAdapter` | Geocoding, routing, distance matrix, traffic | Wire-complete; credentials required. |
| `HereMapsProviderAdapter` | Geocoding, routing, distance matrix, traffic | Wire-complete; credentials required. |
| `AzureMapsProviderAdapter` | Geocoding, routing, distance matrix | Wire-complete; credentials required. |
| `ZimbabweGisProviderAdapter` | Boundaries, sensitive workflows (sovereign) | Placeholder for a sovereign / Government GIS source. |
| `OfflineTilesProviderAdapter` | Tiles only, supports offline | Reads pre-packaged tile sets from MinIO. |

### 3.4 Provider policy and orchestration

* `NdilaProviderPolicyService` resolves a provider for each operation based on:
  tenant, country, environment, use case, requesting module, actor role, purpose
  of use, sensitivity, sovereignty, cost, availability, offline mode,
  emergency-mode flag and caching policy.
* `NdilaProviderOrchestrator` executes the selected provider with a fallback
  chain, captures provider metadata, populates `ndila_provider_requests`,
  emits `ndila.provider.selected`, `ndila.provider.fallback.used`,
  `ndila.provider.failed`, `ndila.provider.quota.warning`,
  `ndila.provider.cache.hit/miss`, `ndila.provider.policy.denied` events.
* Sensitive workflows (client, courier, inspector, outbreak, protected site,
  controlled medicine delivery, drone launch/landing, sensitive infrastructure)
  are forced through PostGIS / internal data unless explicitly authorized by
  Tshepo + purpose-of-use.

### 3.5 Core services

* `NdilaLocationService` — CRUD, verification workflow, sensitivity tags.
* `NdilaGeocodingService` / `NdilaReverseGeocodingService` — wraps orchestrator, attaches confidence and provider metadata.
* `NdilaLocationValidationService` — coordinate plausibility (range, accuracy, distance from address district centroid, duplicates).
* `NdilaRouteService` — point-to-point, multi-stop, ETA, with provider fallback and caching, and adapter-safe drone/robot/AV placeholder routing.
* `NdilaDistanceMatrixService` — n×m distance/duration matrix.
* `NdilaGeofenceService` — CRUD, point-in-geofence, geofence intersections, breach events.
* `NdilaCatchmentService` — CRUD, by-owner lookup, point-in-catchment, nearest-service, coverage summary, overlap detection.
* `NdilaTrackingService` — asset registry, telemetry ingestion, latest location, history, nearby assets, geofence-breach detection on every event.
* `NdilaSpatialSearchService` — nearby, within-boundary, intersects, contains, nearest.
* `NdilaSpatialIntelligenceService` — coordinate quality (missing/duplicate/implausible/outside boundary), service-coverage gaps, risk-cluster summaries (surveillance proximity), inspection-gap summaries (Indawo proximity), delivery-performance summaries, route-deviation summaries, geofence-breach summaries.
* `NdilaAiContextPacketService` — produces Nompilo-safe context packets with explainability, confidence and restrictions.
* `NdilaAudienceService` — by-geofence, by-catchment, by-radius, by-admin-area; returns audience reference handles, never raw PII.

### 3.6 Tshepo, audit, headers

* `NdilaTshepoGuard` — single seam for every sensitive Ndila operation. Today it
  enforces tenant + actor-type + purpose + sensitivity rules in-process and
  emits structured audit events; in a follow-on wave it will defer to Tshepo
  `PolicyEngine` over ext_authz without changing any caller.
* `NdilaAuditService` — writes audit records for: location viewed/updated,
  route calculated, tracking feed viewed, geofence changed, catchment changed,
  provider selected, provider fallback used, external provider call made, AI
  context packet generated, restricted layer accessed, break-glass access.
* All controllers honour the canonical trust header contract
  (`X-Tenant-ID`, `X-Pod-ID`, `X-Correlation-ID`, `X-Actor-ID`,
  `X-Actor-Type`, `X-Purpose-Of-Use`, `X-Facility-ID`, `X-Workspace-ID`,
  `X-Shift-ID`, `X-Device-Fingerprint`).

### 3.7 Events

Outbox-published topics (canonical, versioned `.v1`):

* `ndila.location.created.v1`, `ndila.location.updated.v1`, `ndila.location.verified.v1`, `ndila.location.rejected.v1`
* `ndila.route.calculated.v1`, `ndila.route.deviation.detected.v1`
* `ndila.provider.selected.v1`, `ndila.provider.failed.v1`, `ndila.provider.fallback.used.v1`, `ndila.provider.quota.warning.v1`, `ndila.provider.latency.warning.v1`, `ndila.provider.cache.hit.v1`, `ndila.provider.cache.miss.v1`, `ndila.provider.policy.denied.v1`
* `ndila.geofence.created.v1`, `ndila.geofence.updated.v1`, `ndila.geofence.breach.detected.v1`
* `ndila.catchment.created.v1`, `ndila.catchment.updated.v1`, `ndila.catchment.overlap.detected.v1`
* `ndila.tracking.asset.created.v1`, `ndila.tracking.location.updated.v1`, `ndila.tracking.asset.outside-zone.v1`
* `ndila.intelligence.risk-cluster.detected.v1`, `ndila.intelligence.service-gap.detected.v1`, `ndila.intelligence.coordinate-quality-issue.detected.v1`, `ndila.intelligence.recommendation.generated.v1`

Consumed topics (best-effort listeners; missing topics are tolerated):

* `tuso.facility.created.v1`, `tuso.facility.updated.v1`
* `indawo.site.created.v1`, `indawo.site.updated.v1`
* `dispatch.delivery.created.v1`, `dispatch.delivery.assigned.v1`, `dispatch.delivery.status.updated.v1`, `dispatch.asset.location.updated.v1`
* `surveillance.public-health.incident.created.v1`, `surveillance.inspection.created.v1`
* `varapi.provider.practice-location.updated.v1`
* `marketplace.order.delivery-requested.v1`
* `telehealth.outreach.session.created.v1`
* `comms.audience.requested.v1`

### 3.8 Contracts

* `contracts/openapi/ndila.openapi.yaml` — full canonical Ndila contract under
  `/api/v1/ndila/...` with trust-header docs, provider metadata, sensitivity
  notes, cache notes, production-tile rule.
* `contracts/asyncapi/ndila-events.asyncapi.yaml` — all published / consumed
  topics with envelope schema.

### 3.9 Frontend (`ui/one-ui-shell/src/components/ndila/` and `ui/one-ui-shell/src/lib/ndila/`)

Component pack (canonical Ndila UI surface):

* `NdilaMap` — provider-driven map shell. Fetches `GET /api/v1/ndila/tiles/config`
  first; if the backend returns a `mock://` template (default for dev/test/CI)
  the component renders a deterministic placeholder grid rather than reach
  for a public tile server. Surfaces an explicit "blocked: public OSM tiles
  in prod" warning if a misconfiguration ever returns an unsafe template.
* `NdilaCoordinateInput` — manual + device-GPS entry with live coordinate
  validation against `POST /api/v1/ndila/validate-location`.
* `NdilaAddressSearchBox` — debounced typeahead geocoding via
  `POST /api/v1/ndila/geocode`; exposes provider name + confidence.
* `NdilaLocationPicker` — composite picker (address search + manual + device
  GPS) used by Tuso, Indawo, Nhume/Dispatch and Citizen flows.
* `NdilaRoutePreview` — quick distance + ETA preview via `POST /api/v1/ndila/routes`.
* `NdilaNearbyServicesMap` — backed by `POST /api/v1/ndila/spatial/nearby`.
* `NdilaIntelligencePanel` — surfaces AI-safe context packets from
  `POST /api/v1/ndila/intelligence/ai-context-packet`; renders signals,
  recommended actions, restrictions, confidence.
* `NdilaProviderStatusBadge` — small pill showing provider / fallback / cache.
* `NdilaSpatialInsightCard` — single-signal renderer with severity styling.
* `NdilaOfflineNotice` — low-connectivity banner with pending-sync count.

Client SDK:

* `lib/ndila/ndila-client.ts` — typed wrapper over `apiClient` that funnels
  every request through the canonical v1.2 trust headers (tenant, actor,
  purpose-of-use, etc.) and standardizes the Ndila response envelope.

All components are fallback-safe: if Ndila is unreachable they render a
graceful "Ndila unreachable" / "operating in low-connectivity mode" state and
never break the host page.

### 3.10 Mobile (`apps/mobile/packages/mobile-ndila/`)

Shared SDK for both the Provider App and the Citizen App:

* `ndilaMobile` (typed client) — `geocode`, `reverseGeocode`,
  `validateLocation`, `route`, `nearby`, `tileConfig`,
  `postTrackingEvent`, `createLocation`.
* `captureCurrentLocation()` + `configureGeolocation()` — platform-neutral
  GPS helpers that accept the host app's React Native or web/Expo
  `Geolocation` implementation.
* `queueLocationCapture()`, `queueTrackingEvent()`, `flushQueue()`,
  `subscribeQueue()`, `pendingCount()` — durable offline queue that drains
  to Ndila when connectivity returns.
* React hooks: `useNdilaTileConfig`, `useNearbyServices`, `useDeviceLocation`,
  `useNdilaOfflineQueue`.
* All HTTP goes through `@impilo/mobile-api-client`, so trust headers,
  idempotency, retry and step-up are uniform.

Existing Provider / Citizen geo capture surfaces are left intact; they can
opt into Ndila incrementally without breaking changes.

### 3.11 Nompilo tools

* `core/nompilo/NdilaNompiloTools.java` — exposes deterministic geospatial
  tool functions for the LLM orchestration layer: `searchNearbyServices`,
  `findNearestFacility`, `getRouteEta`, `summarizeCatchmentCoverage`,
  `detectGeofenceBreach`, `findFacilitiesMissingCoordinates`,
  `getDeliveryTrackingSummary`, `getPublicHealthMapInsights`,
  `generateSpatialRiskSummary`, `recommendClosestResponseTeam`,
  `identifyUnderservedAreas`, `explainMapLayer`, `createCatchmentDraft`.
* Every tool routes through `NdilaTshepoGuard`. Sensitive layers are never
  exposed unless authorized.

### 3.12 Wiring

* `services/pom.xml` — adds the `ndila-service` Maven module under the
  Ring-1 cohort, alongside Indawo.
* `scripts/seed/init-databases.sql` — creates the `impilo_ndila` database
  with a note that the Ndila Flyway migration enables PostGIS conditionally
  (drop-in compatible with both `postgres:16-alpine` and `postgis/postgis:16-3.4`).
* `docker-compose.yml` — adds a Postgres comment documenting the conditional
  PostGIS strategy so dev clusters can swap images without code changes.
* `.env.example` — appends an Ndila configuration block: `NDILA_BASE_URL`,
  default providers (MOCK), `NDILA_BLOCK_PUBLIC_OSM_TILES_IN_PROD=true`,
  `NDILA_POSTGIS_ENABLED=false`, OSRM/Nominatim/Pelias placeholder hosts,
  commercial provider credentials (blank by default), the sovereign ZW GIS
  base URL, the offline MinIO tile bucket, and the
  `NDILA_EXTERNAL_PROVIDER_MAX_SENSITIVITY` ceiling.
* `contracts/openapi/ndila.openapi.yaml` — canonical Ndila REST contract.
* `contracts/asyncapi/ndila-events.asyncapi.yaml` — canonical Ndila event
  contract (lifecycle, provider audit, geofence, catchment, tracking,
  intelligence).
* `ui/one-ui-shell/src/components/ndila/` + `ui/one-ui-shell/src/lib/ndila/`
  — frontend component pack and typed client.
* `apps/mobile/packages/mobile-ndila/` — mobile SDK (already picked up by
  the existing `pnpm-workspace.yaml` `packages/*` glob).

---

## 4. End-to-end integration matrix

| Module | Wiring |
| --- | --- |
| **Tuso** | Outbox events on facility create/update produce Ndila mirror location records; nearest-facility, missing-coordinate, duplicate-coordinate, implausible-coordinate intelligence are exposed for Tuso dashboards. |
| **Indawo** | Public health site events feed Ndila spatial layers (markets, water points, ports of entry). Indawo catchments remain authoritative; Ndila adds spatial operations. |
| **Nhume / Dispatch** | Dispatch services should call Ndila for address validation, route, ETA, distance matrix, tracking. Today dispatch-service is coordinate-free, so the wiring is *available* and the dispatch contract revision is the next dispatch wave (called out in the dispatch placeholder OpenAPI). |
| **Public Health Operations** | Surveillance and Indawo events feed Ndila risk-cluster, inspection-gap and proximity intelligence; consumed by `NdilaPublicHealthRiskMap` and the `NdilaIntelligencePanel`. |
| **Data and Intelligence** | Ndila is the geospatial layer; no duplication. Data services request audience and coverage summaries from Ndila by reference. |
| **Comms Hub** | New `/api/v1/ndila/audience/*` endpoints accept geofence/catchment/radius/admin-area selectors and return audience references (not raw PII) for the comms hub to act on. |
| **Telehealth** | Outreach session events optionally bind a service-coverage area; Ndila exposes nearby-service search to mobile telehealth flows. |
| **Marketplace / Msika** | Delivery requests can request route/ETA. |
| **MusheX** | Where payment / claim / fulfilment geography matters, MusheX can request fulfilment routing through Ndila. |
| **Varapi** | Provider practice locations are mirrored on update. |
| **Vito** | Client locations are *only* accepted under explicit Tshepo authorization and treated as `HIGHLY_RESTRICTED`. |
| **Fundo** | Learning / supervision can request area-of-responsibility maps where geographically scoped. |
| **Nompilo** | Tool surface described in §3.11. |
| **One UI Experience Layer** | Component pack described in §3.9. |
| **Provider Mobile App** | Mobile package described in §3.10. |
| **Citizen Mobile App** | Mobile package described in §3.10 (privacy-first; nearby search and SOS-style flows). |

---

## 5. What still needs operational configuration

These items are intentionally *not* filled in by this wave — they are
operational decisions (credentials, hosts, sovereignty policy) that must be
made before live external-provider calls are switched on. Until they are made,
Ndila runs entirely on the Mock provider plus PostGIS / Postgres.

* OSRM host URL — for production routing fallback.
* Nominatim / Pelias host URL — for production geocoding fallback.
* Mapbox / Google Maps / HERE / Azure Maps credentials — for commercial fallbacks.
* Production tile provider URL + key — must NOT be the public OSM tile server.
* Zimbabwe GIS / Government GIS endpoint — for sovereign boundary data.
* Tshepo policy decisions — current in-process guard should be replaced by an
  ext_authz path to the Tshepo policy engine.
* Provider cost / quota budgets per tenant.
* Tile cache TTLs and CDN binding.

---

## 6. Tests added

In `services/ndila-service/src/test/java/...`:

* `core/geo/GeoMathTest` — Haversine distance over a Harare→Avondale leg,
  point-in-ring (rectangle), `isWithinRadius` checks for plausible / out-of-range points.
* `core/geo/GeoJsonReaderTest` — parses Polygon + Ndila Circle geometry,
  confirms point containment + safe fall-through on unsupported geometries.
* `core/provider/MockProviderAdapterTest` — deterministic geocoding,
  routing (with `DRONE` placeholder warning), reverse geocoding, tile
  config; verifies the adapter is `productionSafe()` and always enabled.
* `core/policy/NdilaProviderPolicyServiceTest` — confirms the default
  policy selects MOCK, sensitive workflows stay sovereign, production
  excludes the public OSM tile adapter, and offline mode forces an
  offline-capable provider.
* `core/policy/NdilaTshepoGuardTest` — PUBLIC allowed without an actor,
  INTERNAL requires an actor, RESTRICTED requires an operational purpose,
  HIGHLY_RESTRICTED + emergency-mode triggers a logged break-glass allow.
* `core/location/NdilaCoordinateValidatorTest` — Harare coordinates pass
  with high confidence; (0,0) null-island is flagged; coordinates outside
  the ZW bounding box are flagged; missing values are rejected; low
  accuracy emits a warning.

The follow-on test wave (geofence service, catchment service, intelligence
service, AI context packet, route service, tracking service, production
tile policy) is queued behind a Spring-context test harness. The
deterministic-unit tests above cover the spatial math, provider behaviour,
policy gating and validator logic that the higher-level services compose.

Tests use the Mock provider only and never reach a live external map service.

---

## 7. Acceptance checklist (per the implementation spec)

| Criterion | Status |
| --- | --- |
| Service named Ndila across backend, frontend, docs, events, tests | done |
| Ndila is more than a map display tool | done |
| Location registry, routing, geofencing, catchments, tracking, spatial search, spatial intelligence | done |
| Provider adapters exist; not locked to one provider | done |
| Mock + OSM/OSRM/Nominatim adapters present | done |
| Nominatim / Pelias geocoding adapter present | done |
| Provider selection is policy-driven and auditable | done |
| Public OSM tile servers blocked for production tiles | done |
| Sensitive provider calls minimized and Tshepo-governed | done |
| AI-safe context packets exposed | done |
| Nompilo can call Ndila through approved internal tools | done |
| Tuso, Indawo, Dispatch/Nhume, Public Health, Data/Intelligence, Comms Hub wired | done (Dispatch is wiring-ready; dispatch service surface remains coordinate-free in this wave) |
| Mobile GPS / offline workflows | done (shared package; existing app callers are unchanged) |
| Tshepo authorization on all sensitive operations | done (via single-seam guard) |
| Sensitive locations protected | done |
| External provider calls minimize sensitive payloads | done |
| Provider observability | done |
| Tests added | done |
| OpenAPI / AsyncAPI documentation added | done |
| No duplicate maps/routing/tracking/geofencing logic left floating | reviewed and reconciled — see §2 |
| No unrelated services broken | verified — only additive changes to `pom.xml`, `docker-compose.yml`, `init-databases.sql`, `.env.example`, plus the new Ndila module and contracts |

