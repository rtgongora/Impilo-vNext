# Identity Assurance Service

**Port:** 8200
**Schema:** `ia`
**Version:** v1.1-native

## Purpose

The Identity Assurance Service provides device/identity attestation recording and risk
assessment scoring for the Impilo platform. It enables step-up authentication decisions
by evaluating device trust, biometric verification, and contextual risk factors.

## Domain Model

### Attestations
Records of device or identity verification events. Each attestation captures the type
(DEVICE_BINDING, BIOMETRIC, OTP, SMARTCARD, PIN), outcome (PENDING, VERIFIED, FAILED,
EXPIRED), confidence score, and optional device fingerprint.

### Risk Assessments
Point-in-time risk evaluations for a given actor and context. Produces a risk score
(0.0–1.0), risk level (LOW, MEDIUM, HIGH, CRITICAL), and recommendation
(ALLOW, STEP_UP, DENY, REVIEW).

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/internal/v1/attestations` | Record a device/identity attestation |
| GET | `/internal/v1/attestations` | List attestations for tenant |
| POST | `/internal/v1/risk/assess` | Perform a risk assessment |

## Kafka Events (Outbox)

| Event Type | Topic |
|------------|-------|
| `ATTESTATION_RECORDED` | `impilo.ia.attestation.recorded.v1` |
| `RISK_ASSESSED` | `impilo.ia.risk.assessed.v1` |

## Database Tables

- `ia.attestations` — attestation records
- `ia.risk_assessments` — risk scoring results
- `ia.event_outbox` — transactional outbox for Kafka
- `ia.idempotency_keys` — request deduplication

## Tech Stack

- Java 21, Spring Boot 3.3.6, PostgreSQL 16, Kafka
- Flyway migrations, JPA/Hibernate (validate mode)
- OAuth2 JWT + TrustContext headers
