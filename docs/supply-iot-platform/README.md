# Supply & IoT Platform — Asset Registry + Dispatch

> **Status:** Skeleton — no deep logic yet.
> **Date:** 2026-03-11

## Strategic Intent

**Track every health asset from procurement to decommission.**

The Supply & IoT Platform provides asset lifecycle management and logistics
coordination for the Impilo health platform. It bridges the gap between
inventory management (what's in stock) and operational visibility (where
equipment is, whether it's working, and when it needs maintenance).

## Services

| Service | Port | Artifact ID | Purpose |
|---------|------|-------------|---------|
| Asset Registry | 8310 | `asset-registry-service` | Lifecycle management for medical equipment, devices, cold-chain units, vehicles, and IoT-capable assets |
| Dispatch | 8320 | `dispatch-service` | Logistics coordination, delivery tracking, cold-chain compliance, fleet management |

## Architecture Position

These services sit in the **Integration/Ops plane**, complementing existing
supply chain services:

```
┌─────────────────────────────────────────────────────┐
│                 Supply Chain Stack                    │
├──────────────────┬──────────────────────────────────┤
│  inventory-      │  What's in stock at each         │
│  service         │  facility (Ring-1)               │
├──────────────────┼──────────────────────────────────┤
│  pharmacy-       │  Medication dispensing &          │
│  service         │  stock management (Ring-1)       │
├──────────────────┼──────────────────────────────────┤
│  asset-registry- │  Equipment & device lifecycle    │
│  service         │  tracking (NEW — Ring-1/Ops)     │
├──────────────────┼──────────────────────────────────┤
│  dispatch-       │  Logistics, delivery, cold-      │
│  service         │  chain compliance (NEW — Ops)    │
└──────────────────┴──────────────────────────────────┘
```

## v1.1 Compliance

Both services are born v1.1-native:

- **Header enforcement** via `tech-companion` auto-configuration (`V11HeaderFilter`)
- **Idempotency** via `IdempotencyFilter` on POST/PUT/PATCH commands
- **Event outbox** table with all v1.1 context columns (correlation_id, causation_id, idempotency_key, producer, tenant_id, pod_id, subject_id, subject_type, partition_key)
- **GoldenContractIT** extending `GoldenContractSuite` for automated contract verification
- **Error envelope** via `ErrorEnvelope.of(...)` on all error responses

## Schema Prefixes

| Service | Prefix |
|---------|--------|
| Asset Registry | `asr_` |
| Dispatch | `dsp_` |

## MVP Scope (This Iteration)

### Asset Registry
- Asset CRUD (register, update, decommission)
- Asset assignment to facilities
- Asset type classification (MEDICAL_EQUIPMENT, COLD_CHAIN, VEHICLE, IOT_DEVICE)
- Maintenance schedule tracking

### Dispatch
- Dispatch order lifecycle (CREATE, ASSIGN, IN_TRANSIT, DELIVERED, FAILED)
- Driver/vehicle assignment
- Delivery confirmation with proof-of-delivery
- Cold-chain temperature compliance checkpoints

## Future: Device & IoT Connectors (Next Wave)

The Asset Registry is designed to serve as the **device identity anchor** for
future IoT integration:

| Capability | Status | Notes |
|-----------|--------|-------|
| Device telemetry ingestion | Planned | MQTT/HTTP gateway for sensor data |
| Cold-chain temperature monitoring | Planned | Real-time alerts via notification-service |
| GPS fleet tracking | Planned | Integration with dispatch-service |
| Device attestation | Planned | Integration with identity-assurance-service |
| Predictive maintenance | Future | ML-based failure prediction from telemetry |

**Non-scope this iteration:** No device telemetry ingestion, no MQTT gateway,
no real-time sensor processing. These are explicitly deferred to the next wave.

## Event Types

### Asset Registry Events
| Event Type | Trigger |
|-----------|---------|
| `impilo.asset.registry.asset.created.v1` | New asset registered |
| `impilo.asset.registry.asset.updated.v1` | Asset details modified |
| `impilo.asset.registry.asset.assigned.v1` | Asset assigned to facility |
| `impilo.asset.registry.asset.decommissioned.v1` | Asset end-of-life |

### Dispatch Events
| Event Type | Trigger |
|-----------|---------|
| `impilo.dispatch.order.created.v1` | New dispatch order |
| `impilo.dispatch.order.assigned.v1` | Driver/vehicle assigned |
| `impilo.dispatch.shipment.departed.v1` | Shipment left origin |
| `impilo.dispatch.shipment.delivered.v1` | Delivery confirmed |
| `impilo.dispatch.coldchain.violation.v1` | Temperature excursion detected |

## Non-Goals (This Iteration)

- No Kafka consumer/producer wiring
- No Redis caching
- No Testcontainers integration tests
- No deep endpoint logic beyond skeleton structure
- No refactoring of existing inventory/pharmacy services
- No device telemetry ingestion (deferred to next wave)
