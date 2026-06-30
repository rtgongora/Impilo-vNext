# Workstream #5 — Assets, Devices, Equipment & IoT: Discovery & Ownership Note

> Discovery-first deliverable (per operating envelope). Produced before any code, on
> branch `feature/assets-devices-iot` off the coordinator tip `53b401311`.

## 1. Does an asset/equipment/device/IoT owner already exist?

**Yes — two sovereign owners already exist.** This workstream is an **EXTEND**, not a create.

| Service | Port | DB | Owns today |
|---------|------|----|-----------|
| `asset-registry-service` | **8310** | `impilo_asset_registry` | `asr_assets` (governance/lifecycle asset registry), `asr_equipment` (first-class operational equipment with calibration/maintenance dates + `device_id` IoT link), `asset_fixed_details` + `asset_depreciation_schedule` (ERP fixed-asset accounting). v1.1 outbox, idempotency, TrustContextFilter, rate-limit baseline. |
| `iot-ingestion-service` | **8330** | `impilo_iot_ingestion` | `iot_telemetry_readings` (append-only telemetry), `iot_telemetry_dlq`, `iot.device_registry` (device identity + trust level + `linked_equipment_id`). HTTP + Kafka ingest, outbox → `telemetry.iot.device.reading`. |

Both are registered in `docs/registry/services-registry.yaml` (plane `integration`, domain `platform-ops`) with their ports already allocated in `docs/runbooks/port-allocation.md`. **No new service and no new port are required.**

## 2. Are assets represented inside other services today?

- **inventory-service (Dura, 8098)** — owns commodities / stock / spare-parts ledger. NOT fixed assets. Boundary preserved: maintenance spare-part needs are *referenced* (request to inventory/Oros), never owned here.
- **Tuso (facility) / Indawo (sites)** — own facility/site identity. Assets carry a `facility_ref` / `facility_id` string that *references* these; we do not duplicate the facility registry.
- **Ndila** — maps/location/routing. Field-deployment location is referenced, not owned.
- **Vashandi (workforce, 8087)** — owns staff/technicians/custodians. Custodian + technician assignments store a *reference* id; we do not duplicate the workforce registry.
- **Varapi (provider identity)** — provider-equipment-use links reference provider id.
- **PCT (care workflows) / Butano (clinical record + FHIR Device/Observation)** — clinical workflow + observation truth. Device-generated *clinical* observations route to Butano/PCT; we keep only the *operational* status. Equipment readiness can *inform* a clinical block but PCT owns the workflow decision.
- **Madi (blood/cold-chain workflows)** — cold-chain/blood domain. Web already has `/madi/blood-bank/fridges` (Fridge IoT). Cold-chain environmental breaches route to Madi/public-health; asset holds the operational fridge status.
- **Oros (orders/maintenance-requests)** — order rail. Corrective maintenance that needs procurement/spare-parts references Oros/Msika.
- **Rito (quality/safety, 8391)** — incident/safety routing. Safety-flagged faults *link* to Rito; routine faults create a local maintenance task.
- **Khuluma (comms, 8390)** — operational alerts. Threshold/offline/overdue alerts *request* a Khuluma notification (BFF already has `KhulumaServiceClient`).
- **guidance-service (Nompilo)** — config-driven `guidance.guidance_item` (tip V006). We seed `domain='assets'` rows (V007), reuse `NompiloContextualGuidance` web + `NompiloGuidanceSection` mobile.

## 3. Existing Asset/Equipment/Device object-ID classes

- `asr_assets.asset_id` (UUID) — governance Asset ID (object class).
- `asr_equipment.equipment_id` (UUID) — operational Equipment ID (object class), optional FK to `asset_id`, optional `device_id`.
- `iot.device_registry.device_id` (UUID) + `external_device_id` — IoT Device ID, optional `linked_equipment_id`.

## 4. Existing routes / screens / BFF

- **BFF lane (real):** `AssetRegistrySupplyBffController` `/internal/v1/asset-registry/**` → `AssetRegistryServiceClient`; `DeviceRegistryController` `/internal/v1/devices/**` → `IotIngestionServiceClient`. Base URLs `asset-registry-base-url:8310`, `iot-ingestion-base-url:8330`.
- **Web (real):** `/operations/assets` — fully wired to `useAssets` hook. `/erp/assets`, `/nhume/fleet[/assetId]`, `/admin/devices`, `/monitoring/devices`, `/madi/blood-bank/fridges`.
- **Web (decorative — dead buttons, hardcoded zeros):** `/operations/equipment` — prime extend target.
- **Mobile:** `apps/mobile/provider-app` (field/ops users) + `citizen-app`. `provider-app` `OpsReportsHubScreen` already references `/operations/assets` + `/operations/equipment` web paths.

## 5. Existing Oros maintenance-order / Rito incident / Khuluma alert / Butano FHIR Device patterns

- Oros = order rail; we *reference* a spare-part/maintenance request id, do not own it.
- Rito = `rito-quality-safety-service` (8391) case intake; safety faults *link* to Rito.
- Khuluma = BFF `KhulumaServiceClient` (8390) `notify`; we *request* alerts.
- Butano FHIR Device/Observation = clinical truth; clinical observations route out, operational status stays.

## 6. asr `AssetEventConsumer` — the asset↔IoT loop already exists

`asset-registry-service` already consumes `telemetry.iot.device.reading` + `telemetry.tuso.device.heartbeat` (Kafka, group `asset-registry-telemetry`) and calls `AssetService.applyDeviceHeartbeat()` → updates `asr_assets.last_seen_at` + `metadata_json.operational_status`. **No fake telemetry is generated.** We extend this to also derive offline / threshold-breach **IoT alerts** against the equipment's `device_id`.

## 7. Ownership-decision table

| Capability | Existing owner found | Extend or build | Notes |
|-----------|---------------------|-----------------|-------|
| Asset registry + registration | asset-registry-service `asr_assets` | **extend (reuse as-is)** | already real via BFF + `/operations/assets` |
| Equipment registry (CRUD) | `asr_equipment` table + `EquipmentEntity` + `EquipmentRepository` exist, **but NO controller/service** | **build the missing service+controller** (no new table) | wires the decorative `/operations/equipment` to real data |
| Classification + criticality | none (only free `equipment_type`) | **build** (criticality on equipment + readiness) | drives alert priority |
| Location/custody/assignment + transfer/handover | partial (`assigned_to`, `department_id`, `ward_id`) | **build** transfer + handover/receipt tables | audited lifecycle |
| Status lifecycle | `asr_assets` status + `updateStatus` | **extend** to equipment status + lifecycle-event log | full status set |
| Maintenance management | calibration/maintenance *date* fields only | **build** maintenance-task + lifecycle-event tables | technician=Vashandi ref, spare-part=inventory/Oros ref |
| Calibration management | `last/next_calibration` dates only | **build** calibration-record table | block/warn unsafe |
| IoT/telemetry | iot-ingestion-service (real) + asr consumer | **extend** (derive alert table; no fake telemetry) | offline/threshold → alert |
| Facility/service readiness | none | **build** readiness-requirement + readiness compute | real gaps, not a score |
| Fault/incident/safety | none | **build** fault-report table; link safety→Rito | |
| Deployment kits | none | **build** deployment-kit + items | field tracking via Ndila ref |
| Asset audit/verification | none | **build** asset-audit + items | scan/confirm/exceptions |
| Stock / commodities | inventory-service (Dura) | **DO NOT build** | reference only |
| Facility registry | Tuso/Indawo | **DO NOT build** | reference only |
| Workforce | Vashandi | **DO NOT build** | reference only |
| Clinical observation | Butano | **DO NOT build** | route out |
| Billing/payment/marketplace | Costa/MusheX/Msika | **DO NOT build** | reference only |

## 8. New-service decision: **NO**

Both `asset-registry-service` (8310) and `iot-ingestion-service` (8330) already exist, are registered, and own this capability. Doctrine ("prove no existing service owns this") is satisfied — they do. We **extend `asset-registry-service`** with the equipment-operations lifecycle (one Flyway migration `V006`, new service/controllers, reusing the existing outbox + idempotency + trust + audit pattern) and **integrate with `iot-ingestion-service`** for device status/alerts. Web extends the decorative `/operations/equipment` + adds equipment detail / maintenance / calibration / readiness / fault routes. Mobile adds a `provider-app` equipment slice.

## 9. asset-vs-stock (Dura) doctrine line

> **An *asset* is a managed physical/connected object tracked through its operational
> lifecycle (register → assign → operate → maintain → calibrate → retire). *Stock*
> (Dura / inventory-service) is consumable commodity/spare-part inventory tracked as a
> ledger.** A ventilator is an asset; the filters it consumes are stock. Maintenance here
> *references* a spare-part request to inventory/Oros — it never holds a stock ledger.

---

# Part B — Delivery (build complete, WS#5)

> Appended after the build. The discovery (Part A) decided EXTEND `asset-registry-service`;
> this section records what was actually shipped, the journeys, the API/BFF/policy surface,
> and honest parity/gaps.

## 10. Asset-vs-stock doctrine (restated, enforced in policy)

An **asset/equipment/device** is a managed physical/connected object tracked through its
operational lifecycle (register → assign → operate → maintain → calibrate → deploy → audit →
retire). **Stock** (Dura / inventory-service, 8098) is consumable commodity/spare-part inventory
tracked as a ledger. The `impilo.assets` OPA policy **explicitly denies** stock functions
(`inventory.stock.*`, `assets.stock.*`) from being served as asset functions, so the boundary is
machine-enforced, not just documented.

## 11. Lifecycle / maintenance / calibration / IoT / readiness journeys

- **Register → operate:** `POST /equipment` (duplicate serial/tag detection) → status lifecycle
  via `/equipment/{id}/status` + append-only `asr_equipment_lifecycle_event`.
- **Custody:** `initiate transfer → approve → confirm receipt` applies the facility/custodian change
  only on receipt (handover integrity); early receipt is rejected (`INVALID_STATE`).
- **Maintenance:** open (preventive/corrective/inspection) → assign technician (Vashandi ref) →
  complete (+ optional return-to-service flips status to OPERATIONAL). Spare parts are a *reference*
  to inventory/Oros, never a held ledger. `maintenance/due` driven by `next_maintenance`.
- **Calibration:** record outcome; **FAIL marks the equipment UNSAFE** and removes it from readiness
  until recalibrated. `calibration/due` driven by `next_calibration`.
- **Fault → safety:** routine fault auto-spawns a corrective maintenance task; a safety fault is
  **linked to Rito** (case ref stored, not owned).
- **IoT:** alerts are **derived from real telemetry/heartbeat** (existing `AssetEventConsumer` +
  explicit `/alerts` ingest) — **never fabricated**. Active-alert dedup per device+type; resolve +
  request-Khuluma-notify. Clinical observations route to Butano; cold-chain breaches route to Madi.
- **Readiness:** required-equipment profile per service point vs **operational** assigned equipment →
  concrete gaps (`MISSING`/`BROKEN`/`UNCALIBRATED`/`UNDER_MAINTENANCE`/`INSUFFICIENT`), never a score.
- **Field deployment:** kit prepare → deploy → return; loss/damage on return auto-flags equipment.
- **Asset audit:** open audit seeds expected items from the facility registry; confirm/exception per
  item; complete emits exception count.

## 12. API / BFF / policy surface

- **Service** `asset-registry-service` `/internal/v1/equipment/**` (see ledger row for the full verb
  list). Every mutation: TrustContext required, owner/tenant validation, lifecycle-event audit, outbox
  event (`impilo.asset.*.v1`), `ErrorEnvelope` on failure paths.
- **BFF** `experience-bff` `/internal/v1/equipment/**` proxies the service and **composes**
  `/alerts/{id}/notify` (Khuluma request-only) and `/readiness/guidance` (guidance-service
  `domain=assets`). Stateless — no datasource.
- **Policy** `infra/opa/impilo/assets.rego` (+ `assets_test.rego`, 37 tests): view/register/edit/assign/
  transfer/receipt/fault/mark-unsafe/maintenance/calibration/return-to-service/retire(elevated)/
  resolve-alert/readiness/deployment/audit/aggregate/cross-facility(scoped)/provider-equipment-use/
  technician-by-assignment/link-device-to-clinical(context)/citizen+low-trust DENY/stock-NOT-asset DENY.

## 13. Parity (honest)

| Surface | Web (one-ui-shell) | Mobile (provider-app) |
|---------|--------------------|-----------------------|
| Registry search/list | ✅ `/operations/equipment` | ✅ `EquipmentSearchScreen` (manual tag/serial; **no camera scan**) |
| Detail/profile | ✅ `/operations/equipment/[equipmentId]` | partial (search → fault entry) |
| Register | ✅ | — (facility-admin web flow) |
| Report fault | ✅ (detail) | ✅ `ReportEquipmentFaultScreen` |
| Maintenance | ✅ dashboard + complete | ✅ `MaintenanceTasksScreen` (complete+RTS) |
| Calibration | ✅ due + record | — (deferred on mobile) |
| Readiness | ✅ real gaps + Nompilo | — (deferred on mobile) |
| IoT status | ✅ resolve + notify | — (deferred on mobile) |
| Deployment kit | ✅ | — (deferred on mobile) |
| Asset audit | ✅ | — (deferred on mobile) |

Web is the complete facility-admin/biomedical surface; mobile is the **highest-value field slice**
(search + fault + maintenance) per the operating envelope.

## 14. Deferred / honest gaps

1. **Mobile not test-run.** `apps/mobile` uses pnpm `workspace:*`; deps are not installable in this
   env (EUNSUPPORTEDPROTOCOL). Screens were written and structurally verified against design-system
   exports + the BFF contract, but **vitest/tsc were not run**.
2. **Mobile camera/QR scan** substrate not wired — manual tag/serial search instead.
3. **IoT threshold-breach derivation rule.** Alerts are honestly recorded (consumer heartbeat +
   explicit ingest API); a dedicated rule converting iot-ingestion threshold breaches into
   `asr_iot_alert` rows is a candidate follow-up. No fabricated telemetry was added.
4. **Asset roles + `assets.*` actions** referenced in OPA are not yet in the central role/policy
   registry; seed `policy_rule` rows if the live Java PolicyEngine becomes the enforcement path
   (coordination item).
