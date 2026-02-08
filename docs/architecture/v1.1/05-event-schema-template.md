# Impilo vNext — Event Schema Template & Examples (v1.1)

**Date**: 2026-02-08

---

## 1. Canonical Event Envelope (v1.1 Law 3)

Every domain event emitted by any Impilo service MUST conform to this envelope.

### JSON Schema (v1)

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://impilo.gov.zw/schemas/event-envelope/v1",
  "title": "ImpiloEventEnvelope",
  "description": "Canonical event envelope for all Impilo vNext domain events (v1.1 Law 3)",
  "type": "object",
  "required": [
    "event_id",
    "event_type",
    "schema_version",
    "correlation_id",
    "idempotency_key",
    "producer",
    "tenant_id",
    "pod_id",
    "subject_id",
    "subject_type",
    "occurred_at",
    "emitted_at",
    "data"
  ],
  "properties": {
    "event_id": {
      "type": "string",
      "format": "uuid",
      "description": "Globally unique event identifier (UUID v7 recommended for time-ordering)"
    },
    "event_type": {
      "type": "string",
      "pattern": "^[a-z]+\\.[a-z_]+\\.[a-z_]+\\.[a-z_]+$",
      "description": "Dot-delimited event type: {bus}.{service}.{aggregate}.{action}",
      "examples": [
        "clinical.pct.journey.created",
        "kernel.vito.client.merged",
        "trust.revocation.consent"
      ]
    },
    "schema_version": {
      "type": "string",
      "pattern": "^\\d+\\.\\d+$",
      "description": "Schema version of this event type (semver major.minor)",
      "examples": ["1.0", "1.1", "2.0"]
    },
    "correlation_id": {
      "type": "string",
      "format": "uuid",
      "description": "End-to-end correlation ID propagated from the originating request"
    },
    "causation_id": {
      "type": ["string", "null"],
      "format": "uuid",
      "description": "ID of the event that caused this event (null for user-initiated)"
    },
    "idempotency_key": {
      "type": "string",
      "maxLength": 255,
      "description": "Consumer-safe deduplication key (typically: producer + subject_id + event_type + version)"
    },
    "producer": {
      "type": "string",
      "description": "Service that produced this event",
      "examples": ["vito-service", "pct-service", "tshepo-authz-service"]
    },
    "tenant_id": {
      "type": "string",
      "format": "uuid",
      "description": "Tenant (health authority) that owns this data"
    },
    "pod_id": {
      "type": "string",
      "default": "national-spine",
      "description": "Pod identifier — 'national-spine' for Level 1, pod UUID for Level 2"
    },
    "subject_id": {
      "type": "string",
      "description": "Primary entity ID this event concerns"
    },
    "subject_type": {
      "type": "string",
      "description": "Type of the subject entity",
      "examples": ["Client", "Provider", "Facility", "Journey", "Order", "PaymentIntent"]
    },
    "occurred_at": {
      "type": "string",
      "format": "date-time",
      "description": "When the domain event actually occurred (business timestamp)"
    },
    "emitted_at": {
      "type": "string",
      "format": "date-time",
      "description": "When the event was published to the bus (system timestamp)"
    },
    "data": {
      "type": "object",
      "description": "Event-specific payload — MUST be a delta for updates (Law 4)"
    }
  },
  "additionalProperties": false
}
```

---

## 2. Delta Event Format (v1.1 Law 4)

For `CREATE` events, `data` contains the full initial state of the entity.
For `UPDATE` events, `data` contains only the changed fields.
For `DELETE` events, `data` contains the entity ID and deletion reason.

### Delta Data Schema

```json
{
  "$id": "https://impilo.gov.zw/schemas/delta-payload/v1",
  "title": "DeltaPayload",
  "type": "object",
  "required": ["change_type"],
  "properties": {
    "change_type": {
      "enum": ["CREATE", "UPDATE", "DELETE", "MERGE", "REVOKE"]
    },
    "entity_version": {
      "type": "integer",
      "description": "Monotonically increasing version number of the entity"
    },
    "changed_fields": {
      "type": "object",
      "description": "For UPDATE: map of fieldName → { old, new }",
      "additionalProperties": {
        "type": "object",
        "properties": {
          "old": {},
          "new": {}
        },
        "required": ["new"]
      }
    },
    "state": {
      "type": "object",
      "description": "For CREATE: full initial entity state. For DELETE: entity ID + reason."
    }
  }
}
```

---

## 3. Example Events

### 3a. Delta Event — Client Updated (VITO)

```json
{
  "event_id": "0192a8d0-7c3e-7f00-b1a2-3d4e5f6a7b8c",
  "event_type": "kernel.vito.client.updated",
  "schema_version": "1.0",
  "correlation_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "causation_id": null,
  "idempotency_key": "vito-service:client:crid-12345:updated:7",
  "producer": "vito-service",
  "tenant_id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "pod_id": "national-spine",
  "subject_id": "crid-12345",
  "subject_type": "Client",
  "occurred_at": "2026-02-08T10:15:30.123Z",
  "emitted_at": "2026-02-08T10:15:30.456Z",
  "data": {
    "change_type": "UPDATE",
    "entity_version": 7,
    "changed_fields": {
      "phone": {
        "old": "+263771234567",
        "new": "+263779876543"
      },
      "address_district": {
        "old": "Harare South",
        "new": "Chitungwiza"
      }
    }
  }
}
```

### 3b. Merge Event — Client Merged (VITO Federation)

```json
{
  "event_id": "0192a8d1-4a2b-7f00-c3d4-5e6f7a8b9c0d",
  "event_type": "trust.federation.merge",
  "schema_version": "1.0",
  "correlation_id": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
  "causation_id": null,
  "idempotency_key": "vito-service:merge:crid-99999:crid-12345",
  "producer": "vito-service",
  "tenant_id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "pod_id": "national-spine",
  "subject_id": "crid-12345",
  "subject_type": "ClientMerge",
  "occurred_at": "2026-02-08T11:00:00.000Z",
  "emitted_at": "2026-02-08T11:00:00.234Z",
  "data": {
    "change_type": "MERGE",
    "surviving_crid": "crid-12345",
    "retired_crid": "crid-99999",
    "cpid_mapping": {
      "surviving_cpid": "cpid-abcde",
      "retired_cpid": "cpid-xyxyz"
    },
    "merge_reason": "DUPLICATE_DETECTED",
    "reconciliation_deadline": "2026-02-08T23:00:00.000Z",
    "affected_pods": ["national-spine", "pod-military-01"]
  }
}
```

### 3c. Revocation Event — Consent Revoked (TSHEPO)

```json
{
  "event_id": "0192a8d2-8e1f-7f00-d5e6-7f8a9b0c1d2e",
  "event_type": "trust.revocation.consent",
  "schema_version": "1.0",
  "correlation_id": "c3d4e5f6-a7b8-9012-cdef-123456789012",
  "causation_id": null,
  "idempotency_key": "tshepo-consent:revoke:consent-67890",
  "producer": "tshepo-consent-service",
  "tenant_id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "pod_id": "national-spine",
  "subject_id": "consent-67890",
  "subject_type": "Consent",
  "occurred_at": "2026-02-08T12:30:00.000Z",
  "emitted_at": "2026-02-08T12:30:00.100Z",
  "data": {
    "change_type": "REVOKE",
    "cpid": "cpid-abcde",
    "consent_scope": "TREATMENT",
    "revoked_by": "patient",
    "effective_immediately": true,
    "propagation_required": true,
    "target_pods": ["national-spine", "pod-military-01", "pod-private-group-a"]
  }
}
```

### 3d. Clinical Event — Order Placed (OROS)

```json
{
  "event_id": "0192a8d3-b2c4-7f00-e7f8-9a0b1c2d3e4f",
  "event_type": "clinical.oros.order.placed",
  "schema_version": "1.0",
  "correlation_id": "d4e5f6a7-b8c9-0123-defa-234567890123",
  "causation_id": null,
  "idempotency_key": "oros-service:order:01HXYZ123:placed",
  "producer": "oros-service",
  "tenant_id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "pod_id": "national-spine",
  "subject_id": "01HXYZ123",
  "subject_type": "Order",
  "occurred_at": "2026-02-08T09:45:00.000Z",
  "emitted_at": "2026-02-08T09:45:00.345Z",
  "data": {
    "change_type": "CREATE",
    "entity_version": 1,
    "state": {
      "order_id": "01HXYZ123",
      "cpid": "cpid-abcde",
      "order_type": "LAB",
      "priority": "ROUTINE",
      "facility_id": "fac-001",
      "workspace_id": "ws-lab-001",
      "placer_id": "prov-dr-smith",
      "status": "PLACED",
      "items": [
        {
          "item_id": "item-001",
          "code": "26604-1",
          "code_system": "LOINC",
          "display": "Complete Blood Count",
          "quantity": 1
        }
      ],
      "consistency_class": "B",
      "decision_evidence_id": "audit-evt-12345"
    }
  }
}
```

### 3e. Decision Evidence Event (Audit)

```json
{
  "event_id": "0192a8d4-c5d6-7f00-f901-2a3b4c5d6e7f",
  "event_type": "trust.decision_evidence",
  "schema_version": "1.0",
  "correlation_id": "d4e5f6a7-b8c9-0123-defa-234567890123",
  "causation_id": null,
  "idempotency_key": "tshepo-authz:decision:d4e5f6a7-b8c9-0123-defa-234567890123",
  "producer": "tshepo-authz-service",
  "tenant_id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "pod_id": "national-spine",
  "subject_id": "d4e5f6a7-b8c9-0123-defa-234567890123",
  "subject_type": "PolicyDecision",
  "occurred_at": "2026-02-08T09:44:59.900Z",
  "emitted_at": "2026-02-08T09:44:59.950Z",
  "data": {
    "actor_id": "prov-dr-smith",
    "actor_type": "PRACTITIONER",
    "patient_reference": "cpid-abcde",
    "action": "oros.order.place",
    "consistency_class": "B",
    "decision": "ALLOW",
    "reason_codes": ["ROLE_AUTHORIZED", "FACILITY_MATCH", "STALENESS_OK"],
    "policy_version": "rbac-v3.2.1",
    "projection_staleness_ms": 1200,
    "max_allowed_staleness_ms": 30000,
    "break_glass": false,
    "context": {
      "facility_id": "fac-001",
      "workspace_id": "ws-lab-001",
      "purpose_of_use": "TREATMENT",
      "device_fingerprint": "browser:chrome:129:linux"
    }
  }
}
```

---

## 4. Snapshot Response Format

```json
{
  "snapshot_timestamp": "2026-02-08T10:00:00.000Z",
  "schema_version": "1.0",
  "producer": "vito-service",
  "tenant_id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "pod_id": "national-spine",
  "resource_type": "Client",
  "total_count": 15234567,
  "items": [
    {
      "subject_id": "crid-00001",
      "entity_version": 3,
      "last_modified_at": "2026-02-07T14:30:00.000Z",
      "state": {
        "crid": "crid-00001",
        "cpid": "cpid-aaa01",
        "status": "ACTIVE",
        "created_at": "2024-06-15T08:00:00.000Z"
      }
    }
  ],
  "next_cursor": "eyJpZCI6ImNyaWQtMDAxMDAifQ==",
  "has_more": true
}
```

---

## 5. Kafka Header Contract

All events published to Kafka MUST include these headers:

| Header | Type | Required | Description |
|---|---|---|---|
| `X-Event-Id` | String (UUID) | Yes | Same as envelope `event_id` |
| `X-Event-Type` | String | Yes | Same as envelope `event_type` |
| `X-Schema-Version` | String | Yes | Schema version (e.g., "1.0") |
| `X-Correlation-Id` | String (UUID) | Yes | End-to-end correlation |
| `X-Causation-Id` | String (UUID) | No | Parent event ID |
| `X-Producer` | String | Yes | Service name |
| `X-Tenant-Id` | String (UUID) | Yes | Tenant identifier |
| `X-Pod-Id` | String | Yes | Pod identifier |
| `X-Schema-Id` | String | If registry | Schema Registry ID for deserialization |
| `X-Idempotency-Key` | String | Yes | Consumer deduplication key |

### Kafka Key (Partition Key)
- Format: `{tenant_id}:{subject_id}`
- Ensures all events for the same entity land on the same partition (ordering guarantee)

---

## 6. Schema Registry Integration

### Registry Configuration
- **Type**: Apicurio Registry (open source, supports JSON Schema + Avro + Protobuf)
- **Compatibility Mode**: BACKWARD (default) — new schema can read old data
- **CI Gate**: PR cannot merge if schema change breaks backward compatibility
- **Artifact Naming**: `{bus}.{service}.{aggregate}.{action}-value` (e.g., `kernel.vito.client.updated-value`)

### Schema Evolution Rules
1. **Adding optional fields**: ALLOWED (backward compatible)
2. **Removing optional fields**: ALLOWED with deprecation notice (1 release cycle)
3. **Changing field types**: FORBIDDEN (breaking — requires new schema version)
4. **Removing required fields**: FORBIDDEN (breaking)
5. **Adding required fields**: FORBIDDEN for existing events (add as optional with default)
6. **Breaking changes**: Must increment major version, publish migration guide, support old version for 2 release cycles
