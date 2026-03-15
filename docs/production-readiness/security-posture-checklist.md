# Wave 19 — Security Posture Checklist

> Date: 2026-03-15
> Scope: Ring 0 services + extended Ring 0 (MSIKA, BUTANO, MUSHEX)
> Branch: `claude/review-project-manifest-jb5O0`
> Prerequisites: [Wave 14 Security Hardening](../security/wave14-security-hardening.md)

---

## 1. mTLS Posture Verification

### 1.1 Current State

| Layer | Component | mTLS Status | Evidence |
|-------|-----------|:-----------:|---------|
| Gateway → Service | Envoy → Ring 0 services | Configured | `infra/envoy/envoy-runtime.yaml` — upstream clusters define TLS transport socket |
| Service → Service | Internal gRPC calls | Configured | `tshepo-authz-service` gRPC port 9090 with TLS context |
| Service → PostgreSQL | JDBC connections | Not enforced | `sslmode=prefer` in connection strings (not `verify-full`) |
| Service → Redis | Redis client | Not enforced | Plain TCP (no TLS) in local dev; TLS available via `spring.data.redis.ssl` |
| Service → Kafka | Kafka producer/consumer | Not enforced | `PLAINTEXT` listener in dev; `SSL` listener available |

### 1.2 mTLS Verification Checklist

| # | Check | How to Verify | Status |
|---|-------|---------------|:------:|
| M-1 | Envoy rejects plaintext connections on port 10000 | `curl -k http://localhost:10000/v1/... && echo FAIL \|\| echo PASS` (should fail if TLS enforced) | ⬜ Verify in staging |
| M-2 | Service-to-service calls use mTLS | Check `federation-connector` library TLS context: `libs/federation-connector/` | ✅ Code exists |
| M-3 | PostgreSQL `sslmode=verify-full` in production | Check Helm `values.yaml` for `SPRING_DATASOURCE_URL` with `sslmode=verify-full` | ⬜ Not yet in Helm values |
| M-4 | Redis TLS enabled in production | Check Helm `values.yaml` for `spring.data.redis.ssl.enabled=true` | ⬜ Not yet in Helm values |
| M-5 | Kafka SSL listener configured in production | Check Helm `values.yaml` for `KAFKA_SECURITY_PROTOCOL=SSL` | ⬜ Not yet in Helm values |
| M-6 | Certificate rotation does not cause downtime | Execute cert rotation during load test; measure error rate spike | ⬜ Verify in staging (G-08) |
| M-7 | mTLS overhead measured | Run `read-heavy-baseline.js` with and without mTLS; compare p95/p99 | ⬜ Verify in staging (G-08) |

### 1.3 mTLS Remediation Plan

| Priority | Action | Target |
|----------|--------|--------|
| P1 | Add `sslmode=verify-full` to all Ring 0 Helm `values.yaml` | Pre-production |
| P1 | Add `spring.data.redis.ssl.enabled=true` to Helm values | Pre-production |
| P1 | Switch Kafka listener to `SSL` in production Helm | Pre-production |
| P2 | Run mTLS load test (G-08) | Staging available |
| P2 | Document certificate rotation runbook | Post-launch sprint |

---

## 2. Secrets Rotation Plan

### 2.1 Secret Inventory

| Secret | Consumers | Current Storage | Rotation Method |
|--------|-----------|----------------|-----------------|
| PostgreSQL credentials | All Ring 0 + MSIKA, BUTANO, MUSHEX | Kubernetes Secret / env var | Update Secret → rolling restart |
| Redis password | All Ring 0 (if AUTH enabled) | Kubernetes Secret / env var | Update Secret → rolling restart |
| Kafka SASL credentials | All Ring 0 (if SASL enabled) | Kubernetes Secret / env var | Update Secret → rolling restart |
| Keycloak admin password | Keycloak deployment | Kubernetes Secret | Keycloak admin console |
| Keycloak client secrets | TSHEPO, services with OAuth2 | Kubernetes Secret / env var | Rotate in Keycloak → update Secret |
| MinIO access/secret keys | Document-service, services using S3 | Kubernetes Secret / env var | Rotate in MinIO → update Secret |
| TSHEPO KEK (Key Encryption Key) | tshepo-keys-service | **HARDCODED (G-22)** | Must move to Vault/Secret |
| MOSIP KEK | tshepo-identity-service | **HARDCODED (G-22)** | Must move to Vault/Secret |

### 2.2 Rotation Procedure

```bash
# Step 1: Generate new credentials
NEW_PASSWORD=$(openssl rand -base64 32)

# Step 2: Update the infrastructure component (e.g., PostgreSQL)
PGPASSWORD=<current> psql -h <host> -U impilo -c "ALTER USER impilo PASSWORD '${NEW_PASSWORD}';"

# Step 3: Update Kubernetes Secret
kubectl create secret generic pg-credentials \
  --from-literal=POSTGRES_PASSWORD="${NEW_PASSWORD}" \
  --dry-run=client -o yaml | kubectl apply -f -

# Step 4: Rolling restart of consuming services (zero-downtime)
for svc in tshepo vito varapi tuso zibo msika butano mushex; do
  kubectl rollout restart deployment/${svc}-service
done

# Step 5: Verify health after restart
for svc in tshepo:8081 vito:8082 varapi:8083 tuso:8084 zibo:8085; do
  kubectl exec deploy/${svc%-*}-service -- wget -qO- http://localhost:${svc#*:}/actuator/health | jq .status
done
```

### 2.3 Rotation Schedule

| Secret | Rotation Frequency | Last Rotated | Next Due |
|--------|:-----------------:|:------------:|:--------:|
| PostgreSQL credentials | 90 days | Never (dev creds) | Pre-production |
| Keycloak client secrets | 90 days | Never | Pre-production |
| TSHEPO KEK | 180 days | N/A (hardcoded — G-22) | After G-22 resolved |
| MinIO keys | 90 days | Never (dev creds) | Pre-production |

### 2.4 SecretProvider Adoption

| Service | SecretProvider | Implementation |
|---------|:--------------:|---------------|
| TSHEPO | ✅ | `EnvSecretProvider` (dev) / `VaultSecretProvider` (prod contract) |
| VITO | ✅ | Same |
| VARAPI | ✅ | Same |
| TUSO | ✅ | Same |
| ZIBO | ✅ | Same |
| MSIKA | ✅ | Same (via `SecurityBaselineConfig`) |
| BUTANO | ⬜ | Needs verification — HAPI FHIR wrapper |
| MUSHEX | ⬜ | Needs verification |

---

## 3. RBAC / PAM Review

### 3.1 Authorization Model

Impilo uses a centralized authorization model:

| Layer | Component | Mechanism |
|-------|-----------|-----------|
| Gateway | Envoy ext_authz → TSHEPO | Every request evaluated before reaching services |
| Policy engine | `PolicyEngine.java` | RBAC + ABAC: role-based + attribute-based (tenant, facility, purpose) |
| Trust headers | 14-header contract | `X-Actor-Type`, `X-Purpose-Of-Use`, `X-Facility-Id`, etc. |
| Service-level | `RateLimitFilter` | Per-actor rate limiting at service boundary |
| Admin audit | `AdminAuditEmitter` | All admin actions emit immutable audit events to outbox |

### 3.2 RBAC Checklist

| # | Check | Evidence | Status |
|---|-------|----------|:------:|
| R-1 | All API endpoints require trust header validation | `TrustHeaders.java` → 6 mandatory headers | ✅ |
| R-2 | Actor types are enumerated and enforced | `X-Actor-Type`: PROVIDER, ADMIN, CITIZEN, SYSTEM, SERVICE | ✅ |
| R-3 | Purpose-of-use is validated per request | `X-Purpose-Of-Use` header → policy engine input | ✅ |
| R-4 | Admin endpoints have elevated audit logging | `AdminAuditEmitter` in all Ring 0 `SecurityBaselineConfig` | ✅ |
| R-5 | Rate limiting applied to all `/v1/*` endpoints | `RateLimitFilter` at `HIGHEST_PRECEDENCE + 5` | ✅ |
| R-6 | Input sanitization on all user-facing inputs | `InputSanitizer` rejects XSS, SQLi, template injection, null bytes | ✅ |
| R-7 | No service-to-service calls bypass trust headers | `V11HeaderFilter` in tech-companion validates presence | ✅ |
| R-8 | Keycloak realm configuration reviewed | `impilo` realm with appropriate client scopes | ⬜ Verify in staging |
| R-9 | PAM: no shared service accounts in production | Each service uses dedicated DB user | ⬜ Verify in staging |
| R-10 | PAM: break-glass procedure documented | Emergency admin access via Keycloak direct grant | ⬜ Document |

### 3.3 Sensitive Endpoint Inventory

| Endpoint | Service | Risk | Protection |
|----------|---------|------|-----------|
| `POST /v1/authorize` | TSHEPO | Critical — authorization gateway | Rate-limited, audit-logged, internal-only |
| `POST /v1/identity/register` | VITO | High — creates patient identity | Idempotency-key required, rate-limited, admin-audited |
| `POST /v1/internal/facilities` | TUSO | High — creates facility records | Trust headers required, admin-audited |
| `POST /v1/registry/provisional/issue` | VITO | High — issues provisional IDs | Trust headers, rate-limited |
| `POST /v1/internal/dedup/cases/{id}/merge` | VITO | Critical — merges patient records | Federation authority guard, idempotency-key, admin-audited |
| `POST /v1/internal/patients/merge` | VITO | Critical — merges at national level | `X-Pod-ID=national` required, `FederationAuthorityGuard` |

### 3.4 RBAC Gap Summary

| ID | Gap | Priority | Remediation |
|----|-----|----------|-------------|
| R-8 | Keycloak realm config review | MEDIUM | Audit realm during staging setup |
| R-9 | Dedicated DB users per service | MEDIUM | Currently shared `impilo` user in dev; production should use per-service users |
| R-10 | Break-glass procedure | LOW | Document emergency admin access runbook |

---

## 4. Security Posture Summary

| Domain | Items | Pass | Deferred | Fail |
|--------|:-----:|:----:|:--------:|:----:|
| mTLS | 7 | 1 | 6 | 0 |
| Secrets Rotation | 4 categories | 0 (plan defined) | 4 | 0 |
| RBAC/PAM | 10 | 7 | 3 | 0 |
| **Total** | **21** | **8** | **13** | **0** |

**Assessment:** Security primitives (InputSanitizer, RateLimitGuard, AdminAuditEmitter, SecretProvider, trust headers) are code-complete and adopted across all primary Ring 0 services. Infrastructure-level hardening (mTLS enforcement, secrets rotation execution, Keycloak audit) requires staging environment and is deferred to pre-production deployment.

---

## 5. Revision History

| Date | Author | Change |
|------|--------|--------|
| 2026-03-15 | Wave 19D | Initial security posture checklist |
