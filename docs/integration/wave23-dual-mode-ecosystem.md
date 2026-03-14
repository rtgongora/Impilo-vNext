# Wave 23 — Dual-Mode Ecosystem Enablement

> Status: Not Started | Date: 2026-03-14

## Goal

Third parties can integrate safely. The developer portal is live, partner onboarding has contract tests, versioning/deprecation windows are enforced, and a sandbox + certification flow is operational.

## Prerequisites

| Wave | Dependency |
|------|-----------|
| 9 | Contract testing & CI gates (GoldenContractSuite enforcement) |
| 12 | Developer experience (developer-portal-service, API documentation) |
| 21 | Federation pilot (mTLS, authority enforcement — needed for external partner trust) |

## Deliverables

### 1. Developer Portal Live

#### Portal Capabilities

| Feature | Description | Status |
|---------|------------|--------|
| API catalog | Browseable catalog of all v1.1 endpoints with OpenAPI specs | Required |
| Authentication guide | Step-by-step partner authentication setup (OAuth2 + mTLS) | Required |
| SDK downloads | Java and TypeScript SDK packages | Required |
| Sandbox provisioning | Self-service sandbox environment creation | Required |
| Usage dashboard | API call volumes, error rates, quota usage | Required |
| Changelog | Per-API version history with deprecation notices | Required |
| Support tickets | Issue tracker for integration problems | Required |

#### Portal Architecture

```
┌──────────────────┐     ┌─────────────────────┐
│ Developer Portal  │     │ developer-portal-    │
│ (Next.js UI)      │ ──→ │ service (8350)       │
│                   │     │                      │
│ - API Catalog     │     │ - Partner management │
│ - Sandbox Mgmt    │     │ - Sandbox lifecycle  │
│ - Usage Dashboard │     │ - API key issuance   │
│ - Docs            │     │ - Usage metering     │
└──────────────────┘     └─────────────────────┘
                                   │
                    ┌──────────────┼──────────────┐
                    ▼              ▼              ▼
              ┌──────────┐ ┌──────────┐ ┌──────────┐
              │ Keycloak  │ │ TSHEPO   │ │ Sandbox  │
              │ (partner  │ │ (policy  │ │ (isolated │
              │  realm)   │ │  engine) │ │  env)    │
              └──────────┘ └──────────┘ └──────────┘
```

#### Verification Checklist

- [ ] Developer portal accessible at designated URL
- [ ] API catalog lists all v1.1 public endpoints
- [ ] OpenAPI specs downloadable per service
- [ ] Authentication guide complete and tested
- [ ] SDK packages published and installable

### 2. Partner Onboarding Contract Tests

#### Onboarding Flow

```
1. Partner registers on developer portal
2. Partner receives sandbox API credentials
3. Partner implements integration using SDK
4. Partner runs contract test suite against sandbox
5. Contract tests validate:
   - Header compliance (v1.1 trust headers)
   - Error handling (standard error envelope)
   - Idempotency behavior
   - Rate limit compliance
   - Event subscription handling
6. All contract tests pass → certification request
7. Impilo team reviews + approves
8. Production credentials issued
```

#### Contract Test Suite

| Test Category | Tests | Pass Criteria |
|---------------|-------|--------------|
| Authentication | OAuth2 flow, token refresh, mTLS handshake | Valid tokens accepted, invalid rejected |
| Headers | All 14 trust headers present on requests | No missing required headers |
| Error Handling | 400, 401, 403, 404, 409, 429, 500 responses | Partner handles all error envelopes correctly |
| Idempotency | Duplicate request with same idempotency key | Same response returned, no side effects |
| Rate Limiting | Exceed rate limit, respect Retry-After | Partner backs off correctly |
| Webhooks | Event delivery, retry on failure, acknowledgment | Partner acknowledges within 30s |
| Data Contracts | Request/response schema validation | Schemas match published OpenAPI spec |

#### Contract Test Runner

```bash
# Partner runs locally against sandbox
npx @impilo/contract-tests \
  --base-url https://sandbox.impilo.gov.zw \
  --client-id <partner-client-id> \
  --client-secret <partner-secret> \
  --cert <partner-mtls-cert.pem> \
  --report-output ./contract-results.json
```

#### Verification Checklist

- [ ] Contract test suite published as npm package
- [ ] Contract tests runnable against sandbox environment
- [ ] All test categories produce clear pass/fail results
- [ ] Test report format suitable for certification review
- [ ] Contract tests versioned alongside API versions

### 3. Versioning/Deprecation Windows Enforced

#### Versioning Policy

| Rule | Enforcement |
|------|------------|
| All endpoints use `/v{N}/` prefix | Envoy routing + tech-companion HeaderEnforcementFilter |
| Breaking changes require new major version | CI gate: OpenAPI diff rejects breaking changes on same version |
| Deprecated endpoints return `Sunset` header | Gateway filter injects header with deprecation date |
| Minimum deprecation window: 6 months | Policy engine rejects removal before window expires |
| Maximum concurrent versions: 2 (current + previous) | Build pipeline enforces |

#### Deprecation Lifecycle

```
v1 (current)  ──→  v2 released  ──→  v1 deprecated  ──→  v1 sunset (removed)
                                      (Sunset header)      (after 6 months)
                                      (Deprecation notice)
```

#### Deprecation Headers

```http
HTTP/1.1 200 OK
Sunset: Sat, 14 Sep 2026 00:00:00 GMT
Deprecation: true
Link: <https://developer.impilo.gov.zw/migration/v1-to-v2>; rel="successor-version"
```

#### Verification Checklist

- [ ] Version prefix enforced on all public endpoints
- [ ] Breaking change detection in CI (OpenAPI diff)
- [ ] Deprecated endpoints return `Sunset` header
- [ ] Deprecation notices visible in developer portal
- [ ] Removal blocked before deprecation window expires

### 4. Sandbox + Certification Flow

#### Sandbox Architecture

```
┌─────────────────────────────────────────────────┐
│ Sandbox Environment (isolated namespace per partner) │
│                                                      │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐          │
│  │ Envoy    │  │ TSHEPO   │  │ VITO     │          │
│  │ (sandbox)│  │ (sandbox)│  │ (sandbox)│          │
│  └──────────┘  └──────────┘  └──────────┘          │
│                                                      │
│  ┌──────────┐  ┌──────────┐                         │
│  │ Seed Data│  │ Synthetic│                         │
│  │ (fixture)│  │ Patients │                         │
│  └──────────┘  └──────────┘                         │
│                                                      │
│  Constraints:                                        │
│  - No real PII                                       │
│  - Rate limited (100 RPS per partner)                │
│  - Auto-reset every 24h                              │
│  - No federation connectivity                        │
└──────────────────────────────────────────────────────┘
```

#### Certification Flow

| Step | Actor | Description | Duration |
|------|-------|-------------|----------|
| 1 | Partner | Complete contract test suite (all green) | Self-paced |
| 2 | Partner | Submit certification request via portal | 1 day |
| 3 | Impilo | Review contract test results | 3 business days |
| 4 | Impilo | Security review (mTLS config, data handling) | 5 business days |
| 5 | Impilo | Load test partner integration in sandbox | 2 business days |
| 6 | Impilo | Issue production credentials | 1 business day |
| 7 | Partner | Verify production connectivity | 1 day |

#### Verification Checklist

- [ ] Sandbox provisioned per partner (isolated namespace)
- [ ] Sandbox seeded with synthetic data (no PII)
- [ ] Sandbox auto-resets on schedule
- [ ] Certification request form accessible in portal
- [ ] Contract test results reviewable by Impilo team
- [ ] Production credentials issued after certification
- [ ] Certification revocable if partner violates terms

## Deliverable: Partner Integration Kit + Contract Harness Reports

```markdown
# Partner Integration Kit — Impilo vNext

## Contents
- [ ] Developer portal URL and access instructions
- [ ] API catalog with OpenAPI specs per service
- [ ] Authentication guide (OAuth2 + mTLS setup)
- [ ] Java SDK package + documentation
- [ ] TypeScript SDK package + documentation
- [ ] Contract test suite (npm package)
- [ ] Sandbox provisioning instructions
- [ ] Certification flow documentation
- [ ] Versioning and deprecation policy
- [ ] Support escalation path

## Contract Harness Report (per partner)
- [ ] Authentication tests: ___/___  passed
- [ ] Header compliance tests: ___/___ passed
- [ ] Error handling tests: ___/___ passed
- [ ] Idempotency tests: ___/___ passed
- [ ] Rate limit tests: ___/___ passed
- [ ] Webhook tests: ___/___ passed
- [ ] Data contract tests: ___/___ passed
- [ ] Overall: ___% pass rate

## Sign-Off
- [ ] Developer Experience Lead: _________________ Date: _______
- [ ] Security Lead: _________________ Date: _______
- [ ] Platform Lead: _________________ Date: _______
```

## Exit Criteria

- [ ] Developer portal live with full API catalog
- [ ] Partner onboarding contract tests published and runnable
- [ ] Versioning/deprecation windows enforced in gateway
- [ ] Sandbox environment operational with synthetic data
- [ ] Certification flow completed by at least one test partner
- [ ] Partner integration kit delivered
