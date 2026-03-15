# Developer / Partner App — Certification Flow

## Overview

The certification flow validates that a partner client meets all requirements for production readiness. It runs 7 automated checks against the client's configuration, keys, and sandbox activity.

## Certification Checks

| # | Check | Pass Criteria | Category |
|---|-------|--------------|----------|
| 1 | `active_api_key` | Client has at least one ACTIVE API key | Key Management |
| 2 | `contact_email` | Contact email is configured and non-empty | Registration |
| 3 | `client_active` | Client status is ACTIVE | Registration |
| 4 | `deprecation_posture_configured` | Posture is WARN or BLOCK (not NONE) | Compliance |
| 5 | `sandbox_tested` | Sandbox has been enabled or configured | Testing |
| 6 | `key_labelled` | At least one API key has a label | Key Management |
| 7 | `key_freshness` | All active keys issued within 90 days | Security |

## Flow Diagram

```
Developer Console                  developer-portal-service
       |                                     |
       |  POST /clients/{id}/certify         |
       | ----------------------------------> |
       |                                     |-- Load client entity
       |                                     |-- Load active API keys
       |                                     |-- Run 7 checks
       |                                     |-- Persist CertificationEntity
       |                                     |-- Emit outbox event
       |  { certification_id, result, ... }  |
       | <---------------------------------- |
       |                                     |
```

## Result Format

```json
{
  "certification_id": "uuid",
  "client_id": "uuid",
  "status": "COMPLETED",
  "result": "PASS | FAIL",
  "checks_total": 7,
  "checks_passed": 7,
  "checks_failed": 0,
  "checks": [
    {
      "check": "active_api_key",
      "passed": true,
      "detail": "Client has 2 active key(s)"
    }
  ],
  "started_at": "2026-03-15T10:00:00Z",
  "completed_at": "2026-03-15T10:00:01Z"
}
```

## Event Emission

Every certification run emits an outbox event:
- **Event type**: `impilo.developer-portal.certification.completed.v1`
- **Aggregate**: `Certification`
- **Payload**: certification_id, client_id, result, passed, failed, total

## Re-certification Triggers

Per the [sandbox certification flow](../../ecosystem/sandbox-certification-flow.md):
1. API key rotation
2. Schema version bump
3. Deprecation posture change
4. Manual trigger from Developer Console or CI pipeline

## Database Schema

```sql
CREATE TABLE dvp_certifications (
    id              UUID PRIMARY KEY,
    client_id       UUID REFERENCES dvp_clients(id),
    tenant_id       UUID NOT NULL,
    status          VARCHAR(32) DEFAULT 'RUNNING',
    result          VARCHAR(32),
    checks_total    INT DEFAULT 0,
    checks_passed   INT DEFAULT 0,
    checks_failed   INT DEFAULT 0,
    report_json     TEXT,
    triggered_by    VARCHAR(255),
    started_at      TIMESTAMPTZ DEFAULT now(),
    completed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ DEFAULT now()
);
```

## Federation Readiness

After passing certification, clients can check their federation readiness at:
`GET /internal/v1/developer/clients/{id}/federation-readiness`

The readiness check evaluates:
1. Client is registered
2. Active API key exists
3. Certification has passed
4. Sandbox has been tested
5. Deprecation posture is configured
