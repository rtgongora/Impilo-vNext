# vNext BFF Orchestration Model

> **Generated:** 2026-06-13  
> **Service:** `experience-bff` (port 8160)  
> **Role:** Experience orchestration layer — not a random proxy

---

## 1. Architectural position

The Experience BFF is the **composition and orchestration layer** between `one-ui-shell` (and mobile apps) and sovereign domain services. It:

- Aggregates launcher and hub data
- Propagates trust/context headers
- Composes multi-service workflows
- Normalizes errors for the shell
- Applies honest fallback policies
- Correlates audit and telemetry

It must **never** become a source of truth for clinical, registry, trust, or finance data.

---

## 2. Downstream URL generation doctrine

### Problem
`services/experience-bff/src/main/resources/application.yml` defines **70+ `*_BASE_URL` defaults to `http://localhost:*`**. Inside Kubernetes pods, localhost refers to the pod itself — downstream calls fail silently or fall back to stubs.

### Solution (preview/full-boot)
**Generator:** `scripts/full-boot/generate-full-preview-bff-downstream-env.mjs`

```
http://{service-id}:{port-from-runtime-values}
```

**Inputs:**
- `deploy/helm/impilo-vnext/values-full-preview-runtime.generated.yaml` — port truth
- `SERVICE_ENV` mapping table (72 entries)

**Output:**
- `deploy/helm/impilo-vnext/values-full-preview-bff-env.generated.yaml`

**Helm apply:** Merged into BFF deployment env at upgrade time.

### Rule: no localhost default in preview
| Environment | Downstream URL source |
|-------------|----------------------|
| Local dev (compose) | localhost ports per `port-allocation.md` |
| Preview K8s | **Generated cluster DNS only** |
| Production | Generated from production runtime values |

**Verification:** Inspect running BFF pod env; grep for `localhost` — must be zero for `*_BASE_URL`.

---

## 3. Fallback policy model

| Mode | Env var | Behaviour | UI requirement |
|------|---------|-----------|----------------|
| `live` | `IMPILO_BFF_PROVIDER_HUBS_MODE` | Call downstream; surface errors | Show error or data |
| `stub` | `IMPILO_BFF_FACILITIES_MODE` | Return canned data | Label `Preview stub` |
| `stub_fallback` | `*_FAILURE_POLICY` | On failure, stub | Label `Fallback` + log |

**Current preview defaults (generated):**
```yaml
IMPILO_BFF_FACILITIES_MODE: stub          # Should move to live when TUSO seeded
IMPILO_BFF_PROVIDER_HUBS_MODE: live
IMPILO_BFF_PROVIDER_HUBS_FAILURE_POLICY: stub_fallback
IMPILO_BFF_CITIZEN_LONGTAIL_MODE: stub
IMPILO_BFF_CITIZEN_LONGTAIL_FAILURE_POLICY: stub_fallback
```

**Doctrine:** Stubs are preview-only, documented, and visible in UI — never silent success.

---

## 4. Launcher orchestration

| Endpoint | Purpose |
|----------|---------|
| `GET /internal/v1/launcher/apps` | Role/facility-aware app list |
| `GET /internal/v1/launcher/apps/{appCode}/state` | Per-app readiness |

**Shell consumer:** `useHealthOsLauncher.ts` → `ShellStartMenu`

**Parity status:** **partial** (HIGH) — Start menu uses contract but not all 89 services represented.

**Orchestration rule:** Launcher tiles must reflect **running + authorized** services, not static lists.

---

## 5. Facility hub orchestration

| Data | Source service | BFF path |
|------|----------------|----------|
| Facility registry | TUSO | `/internal/v1/facilities` |
| Workspaces | TUSO | `/internal/v1/registry/*` |
| Site geo | Indawo + Ndila | Public health routes |

**Blocker:** `FACILITIES_MODE: stub` prevents facility truth validation in preview walkthrough.

---

## 6. Provider hub orchestration

| Data | Source | BFF path |
|------|--------|----------|
| Provider registry | VARAPI | `/internal/v1/registry/*` |
| Licenses, CPD | VARAPI | `useRegistry.ts`, `useLicenses.ts` |
| Work assignments | workforce-governance | Session contract |
| Provider public ID | VARAPI | `resolveProviderPublicId()` in session |

**Fix deployed:** `providerPublicId` mapping (was `providerId` mismatch).

---

## 7. Notification aggregation

| Source | BFF surface |
|--------|-------------|
| notification-service | `/internal/v1/notifications/*` |
| channels-service | Omnichannel routes |
| Booking outbox poller | Scheduled comms to guidance |

**Risk:** `BookingOutboxCommsPoller` uses localhost default if env missing — verify in cluster.

---

## 8. Marketplace / service tile enrichment

| Domain | Services | Shell routes |
|--------|----------|--------------|
| Commerce | msika-service, msika-flow-service, msika-apps-service | `/marketplace/*` |
| Finance | mushex-service, mushe-wallet-service, costing-engine-service | `/finance/*` |
| Wallet | mushe-wallet-service | `/wallet` |

**Parity:** partial — honest blocked states required on list routes.

---

## 9. Trust / header / token propagation

### Mandatory headers (shell → BFF → services)
From `ui/one-ui-shell/src/lib/api-client.ts` / `CompanionHeaders.java`:

- `X-Tenant-ID`, `X-Pod-ID`, `X-Request-ID`, `X-Correlation-ID`
- `X-Actor-ID`, `X-Actor-Type`, `X-Provider-ID`
- `X-Facility-ID`, `X-Workspace-ID`, `X-Purpose-Of-Use`
- `X-Assurance-Level`, `X-Access-Mode`

### BFF mediation
- Injects headers on outbound `RestTemplate`/`WebClient` calls
- Validates session before composition
- Forwards JWT where required (preview: often bypassed)

---

## 10. Error normalization

BFF controllers should:
- Map downstream 4xx/5xx to consistent JSON error shapes
- Include `correlationId` in responses
- Log downstream failure with service name (not swallow)
- Trigger `stub_fallback` only when policy allows — with audit log

**Anti-pattern:** Empty 200 with no data and no label.

---

## 11. Service health aggregation

| Endpoint | Purpose |
|----------|---------|
| `GET /health/version` | Public preview version probe |
| `GET /actuator/health` | BFF liveness |
| Downstream probes | Optional aggregation (not fully implemented) |

**Preview check:** `curl http://41.57.127.235/health/version` → commit alignment.

---

## 12. BFF environment generation rules

1. Run `generate-full-preview-runtime-values.mjs` first (wave-aware)
2. Run `generate-full-preview-bff-downstream-env.mjs`
3. Helm upgrade merges both generated files
4. Never hand-edit generated YAML without regenerating
5. Add new service to `SERVICE_ENV` table when helm enables it
6. Port must exist in runtime values for that service

---

## 13. BFF runtime verification tests

| Test | Command / location |
|------|-------------------|
| Unit + integration | `services/experience-bff` `./mvnw test` |
| OpenAPI contracts | `MobileCitizenOpenApiContractTest`, `CoreTransactionOpenApiContractTest` |
| Golden thread | Shell tests → BFF → service |
| Env verification | `kubectl exec` BFF pod — no localhost in `*_BASE_URL` |
| Downstream smoke | BFF IT with Testcontainers or preview curl |

---

## 14. BFF anti-patterns

| Anti-pattern | Evidence | Fix |
|--------------|----------|-----|
| localhost in pod | `application.yml` defaults | Generator + helm env |
| Missing SERVICE_ENV entry | 15 services | Extend generator |
| Silent stub fallback | `stub_fallback` policies | UI maturity labels |
| BFF as SoR | Static JSON in controllers | Proxy to domain service |
| God endpoints | Monolithic controllers | Domain client per service |
| OAuth bypass everywhere | Global helm flag | Per-service preview profiles |
| Registration without Keycloak grant | 403 on register | `keycloak-grant-backend-registration-roles.sh` |

---

## 15. Missing downstream mappings (action list)

Add to `SERVICE_ENV` in `generate-full-preview-bff-downstream-env.mjs`:

```
AUDIT_LEDGER_BASE_URL → audit-ledger-service
CONNECTOR_FHIR_BASE_URL → connector-fhir-adapter
IDENTITY_ASSURANCE_BASE_URL → identity-assurance-service
PHARMACY_ELMIS_BASE_URL → pharmacy-elmis-adapter
PRODUCT_REGISTRY_BASE_URL → product-registry-service
REFERRAL_BASE_URL → referral-service
SHARE_SLIP_BASE_URL → share-slip-service
```

Document N/A with reason for: `observability-service`, `jobs-service`, `schema-registry-service`, `security-hardening-service`, `card-print-agent`, `developer-portal-service`, `offline-edge-service`, `offline-sync-service` (ops/mobile paths).

---

## References

| File | Role |
|------|------|
| `scripts/full-boot/generate-full-preview-bff-downstream-env.mjs` | URL generator |
| `values-full-preview-bff-env.generated.yaml` | Generated output |
| `services/experience-bff/src/main/resources/application.yml` | Localhost defaults (dev only) |
| `docs/architecture/API_ENDPOINT_INVENTORY.md` | BFF route catalog |
