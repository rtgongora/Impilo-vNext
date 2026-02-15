# Security Hardening Baseline

## Overview

This document defines the security hardening baseline for all Impilo v1.1 services.
Every service must comply with these requirements before deployment to production.

## Trust Pipeline

All requests flow through the trust pipeline:

1. **Envoy Proxy** (port 10000) — TLS termination, rate limiting, ext_authz callout
2. **TSHEPO** (port 8081) — policy evaluation (RBAC + ABAC), trust header injection
3. **Service** — validates trust headers, enforces tenant isolation

### Required Trust Headers

Every authenticated request carries 14 trust headers:

| Header | Description |
|--------|-------------|
| `X-Tenant-Id` | UUID of the requesting tenant |
| `X-Actor-Id` | Authenticated user/service identity |
| `X-Correlation-Id` | UUID for distributed tracing |
| `X-Actor-Roles` | Comma-separated role list |
| `X-Facility-Id` | Current facility context |
| `X-Workspace-Id` | Current workspace context |
| `X-Session-Id` | Session identifier |
| `X-Request-Timestamp` | ISO-8601 request time |
| `X-Client-Ip` | Originating IP address |
| `X-User-Agent` | Client user agent |
| `X-Auth-Method` | Authentication method used |
| `X-Policy-Decision` | TSHEPO authorization result |
| `X-Scope` | Authorized scope |
| `X-Audit-Chain` | Serialized audit chain hash |

## Network Security

### TLS

- All external traffic terminates TLS at Envoy
- Internal service-to-service communication uses mTLS via service mesh
- Minimum TLS 1.2, prefer TLS 1.3
- Strong cipher suites only (ECDHE + AES-GCM)

### Network Policies

- Default deny all ingress/egress
- Allow only required service-to-service paths
- Kafka brokers accessible only from service pods
- Database accessible only from service pods

## Authentication & Authorization

### JWT Validation

- All services validate JWT tokens from Keycloak
- Token issuer, audience, and expiry are verified
- Token scope is checked against endpoint requirements

### Service-to-Service Auth

- Internal calls use service account JWTs
- Service accounts have minimal required permissions
- Token rotation on 24-hour cycle

## Data Protection

### PII Isolation

- BUTANO (Shared Health Record) uses CPID only — no PII
- PII resides exclusively in VITO (Patient Registry)
- No PII in logs, metrics, or Kafka event payloads
- CPID-to-identity resolution requires explicit VITO lookup

### Encryption at Rest

- PostgreSQL: transparent data encryption (TDE) enabled
- Kafka: encrypted volumes
- MinIO: server-side encryption (SSE-S3)
- Secrets: Kubernetes secrets backed by external vault

### Encryption in Transit

- All HTTP: TLS 1.2+ (see Network Security)
- Kafka: SASL_SSL with SCRAM-SHA-512
- PostgreSQL: SSL required (sslmode=verify-full)
- Redis: TLS with certificate auth

## Application Security

### Input Validation

- All API inputs validated at controller boundary
- SQL injection prevention via parameterized queries (JPA)
- XSS prevention via JSON-only APIs (no HTML rendering)
- CSRF disabled (stateless JWT auth)

### Dependency Security

- Automated CVE scanning in CI pipeline
- No dependencies with known critical/high CVEs
- Regular dependency updates (monthly cycle)

### Secret Management

- No secrets in source code or container images
- Environment variables from Kubernetes secrets
- Database credentials rotated quarterly
- API keys scoped to minimum required access

## Audit & Compliance

### Audit Logging

- All state-changing operations produce audit events
- Audit events include: who, what, when, where, outcome
- Audit log is append-only and tamper-evident
- Retention: 7 years minimum

### Outbox Pattern

Every service uses the transactional outbox pattern:
- State changes and audit events written in same transaction
- `event_outbox` table polled by scheduled publisher
- At-least-once delivery to Kafka
- Idempotency keys prevent duplicate processing

## Policy Packs

The security-hardening-service (port 8220) manages policy pack definitions:

### Baseline Pack
Minimum security requirements for all services:
- Trust header validation enabled
- JWT authentication required
- Tenant isolation enforced
- Audit logging active
- Health endpoints exposed

### Advanced Pack
Enhanced security for sensitive services:
- All baseline rules plus:
- Rate limiting enabled
- Input validation strictness: HIGH
- PII detection scanning active
- Anomaly detection thresholds configured

### Compliance Scans

Automated scans evaluate service compliance against assigned policy packs.
Results record passed/failed/skipped rule counts with detailed findings.

## Incident Response

### Security Events via Kafka

| Event | Topic | Trigger |
|-------|-------|---------|
| Auth Failure Spike | `impilo.secharden.alert.auth-failure` | >10 failures in 1 minute |
| Unauthorized Access | `impilo.secharden.alert.unauthorized` | Policy decision = DENY |
| Data Exfiltration | `impilo.secharden.alert.data-export` | Large data export detected |
| Service Compromise | `impilo.secharden.alert.compromise` | Anomalous behavior pattern |
