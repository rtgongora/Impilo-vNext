# Msika / VITO Auth & Context Triage

> **Date:** 2026-06-13  
> **Branch:** `claude/staging-ux-orchestration-remediation-Yypyl`  
> **Runtime commit:** `103a5aab9229543238fe0d27cfc56e980c09b726`  
> **Namespace:** `impilo-full-preview`  
> **Diagnosis only** — no code/deploy/runtime changes performed.

---

## 1. Current runtime facts

| Item | Value |
|------|--------|
| `msika-apps-service` | 1/1 Ready, image `preview`, health UP |
| `vito-service` | 1/1 Ready, image `preview`, health UP |
| `experience-bff` | 1/1 Ready, image `preview-103a5aab` |
| BFF → msika base URL | `http://msika-apps-service:8181` |
| BFF → VITO base URL | `http://vito-service:8082` |

### Pod auth-related env (both services)

```
IMPILO_ENV=full-preview
KEYCLOAK_URL=http://keycloak:8080
KEYCLOAK_REALM=impilo
IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS=true
```

VITO additionally has `VITO_HMAC_PEPPER` (preview pepper).

### Public BFF endpoint results (trust headers, no user JWT)

| Endpoint | HTTP | Body |
|----------|------|------|
| `/internal/v1/marketplace/launcher?facilityId=fac-001` | **500** | `INTERNAL_ERROR` |
| `/internal/v1/client-registry/clients` | **200** | degraded meta, empty items |
| `/internal/v1/launcher/apps?facilityId=fac-001` | **200** | 31 catalog + 0 marketplace |
| `/internal/v1/facilities` | **200** | live TUSO data |
| `/internal/v1/registry/providers` | **200** | empty data |
| `/internal/v1/notifications` | **200** | empty list |

### Direct S2S probes from `experience-bff` pod (companion + INTERNAL headers)

| Target | Path | HTTP |
|--------|------|------|
| msika-apps | `GET /internal/v1/marketplace/launcher` | **403** |
| msika-apps | `GET /internal/v1/marketplace/items` | **403** |
| vito | `GET /v1/client-registry/clients` | **403** |
| tuso | `POST /v1/internal/facilities/search` | **200** |
| varapi | `POST /v1/internal/providers/search` | **200** |
| notification | `GET /internal/v1/notifications` | **200** |

### Log signatures

**BFF (`experience-bff`):**
```
GET /internal/v1/marketplace/launcher → 500
Client registry list failed: 403 : [no body]
```

**msika-apps / vito:** no ERROR lines for these probes — **403 returned at Spring Security / method-security layer** (empty body).

---

## 2. Msika diagnosis

### Failing surface

- **BFF:** `GET /internal/v1/marketplace/launcher` → **500** (uncaught downstream error)
- **Upstream:** `GET /internal/v1/marketplace/launcher` on `msika-apps-service` → **403**
- **Main launcher merge:** `HealthOsLauncherController` catches msika failure → **0 marketplace tiles** (graceful)
- **Shell path:** Start menu uses `/internal/v1/launcher/apps` (works — 31 static catalogue tiles)

### Security stack (msika-apps)

```java
// SecurityConfig — HTTP layer
.requestMatchers(disableOauthForTests ? "/internal/**" : "...").permitAll()

// MarketplaceControllers.LauncherController — METHOD layer
@PreAuthorize("isAuthenticated()")
public List<LauncherAppResponse> launcher(...)
```

- `@EnableMethodSecurity(prePostEnabled = true)` is active.
- Most marketplace controllers use **`@PreAuthorize("hasAnyRole(...)")`**; launcher uses **`isAuthenticated()`**.
- `impilo.companion.enabled: true` — `V11HeaderFilter` enforces v1.1 headers on `/internal/v1/**` and populates `RequestContextHolder` (tenant/pod/correlation).
- **Companion context ≠ Spring Security Authentication.** Method security reads `SecurityContext`, not `RequestContextHolder`.

### Expected vs actual context on BFF → msika call

| Field | Expected (doctrine) | Actual on BFF S2S |
|-------|---------------------|-------------------|
| v1.1 quartet | Present | **Yes** (BFF interceptor forwards) |
| `X-Access-Mode: INTERNAL` | Present | **Yes** (BFF always sets) |
| `X-Service-Id: experience-bff` | Present | **Yes** (when absent on inbound) |
| `Authorization: Bearer <JWT>` | User JWT and/or BFF workload JWT | **Usually absent** on header-only API smokes |
| Spring `Authentication` at msika | JWT principal or trusted internal principal | **Absent / anonymous** |
| `RequestContext` at msika | tenant/pod/correlation | **Present** (companion filter) |

### Root cause (specific)

**Preview OAuth HTTP bypass (`IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS=true`) opens `/internal/**` at the HTTP filter chain, but Spring Method Security (`@PreAuthorize`) still requires a JWT-backed `Authentication` principal. BFF S2S calls propagate companion/trust headers but do not establish a SecurityContext principal at msika-apps, so method-level checks return 403.**

Secondary: `HealthOsMarketplaceController` **re-throws** msika 403 as an unhandled `HttpClientErrorException` → BFF global handler maps to **500 INTERNAL_ERROR** (worse than launcher merge path).

**Not the root cause:**
- Route mismatch (path is correct: `/internal/v1/marketplace/launcher`)
- msika pod down
- Missing v1.1 headers (would be 400 `MISSING_REQUIRED_HEADER`, not 403)
- Keycloak audience mismatch (JWT RS is disabled in preview when bypass flag is true)

### Smallest preview fix

1. **msika-apps:** Add preview-conditional security chain (Ubomi pattern): when `impilo.security.disable-oauth-for-tests=true`, use `anyRequest().permitAll()` and **disable method-security enforcement for internal S2S paths** OR replace `@PreAuthorize("isAuthenticated()")` on launcher with trust-header-aware expression (e.g. allow `X-Access-Mode: INTERNAL` + `X-Service-Id: experience-bff`).
2. **BFF:** Wrap `HealthOsMarketplaceController.launcher()` with the same degraded/error handling as `HealthOsLauncherController.parseMarketplaceLauncher()` (return honest empty/502, not 500).

### Proper production fix

Per `docs/architecture/SERVICE_TO_SERVICE_TRUST_PATTERN.md`:

- BFF originates S2S calls with **user OIDC JWT + BFF workload JWT** (`X-Request-Source: HUMAN`).
- msika validates JWT + optional integration-hub **S2S contract** (`experience-bff` → `msika-apps-service`).
- Method security uses **roles/scopes from validated JWT**, not broad preview bypass.
- Register contract in integration-hub; enforce via Envoy/OPA when strict mode enabled.

---

## 3. VITO diagnosis

### Failing surface

- **BFF:** `GET /internal/v1/client-registry/clients` → **200 degraded** (catch block masks upstream)
- **Upstream:** `GET /v1/client-registry/clients` on `vito-service` → **403**
- **Shell:** `/registry/clients` uses `/internal/v1/client-registry/clients` — shows honest degraded guidance (PCW-2)

### Security stack (VITO)

```java
// SecurityConfig — single production chain only
.addFilterBefore(trustContextFilter, ...)
.authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
// JWT RS only if issuer-uri non-blank
```

- `TrustContextFilter` sets `TrustContextHolder` (INTERNAL when `X-Access-Mode: INTERNAL`).
- `ClientIdentityOperationsController` uses `TrustContextHolder.require()` — **business layer expects trust context**.
- **No** `impilo.security.disable-oauth-for-tests` conditional chain (contrast Ubomi/TUSO/VARAPI/notification).
- `application.yml` issuer-uri defaults **empty** unless `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI` set → JWT RS off, but **`authenticated()` still blocks** anonymous/header-only calls.

### Expected vs actual

| Field | Expected | Actual |
|-------|----------|--------|
| INTERNAL trust via `TrustContextFilter` | Yes for platform S2S | **Filter runs, context set** |
| Spring Security pass-through | Trusted internal caller or JWT | **Blocked at `authenticated()`** |
| Env `IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS` | Preview bypass | **Set in deployment but unused by VITO SecurityConfig** |
| `application.yml` mapping | `impilo.security.disable-oauth-for-tests: ${IMPILO...}` | **Missing** (env still binds via Spring relaxed binding, but config ignores it) |

### Root cause (specific)

**VITO SecurityConfig has no preview/internal trusted-caller path. `anyRequest().authenticated()` rejects BFF S2S calls that carry companion + INTERNAL headers but no Bearer JWT, before `ClientIdentityOperationsController` executes. BFF catches 403 and returns degraded empty state.**

**Not the root cause:**
- Tshepo policy denial at runtime (request never reaches policy engine — blocked in VITO Spring Security)
- Seed/data restriction (controller not reached)
- Wrong BFF path (uses correct `/v1/client-registry/clients`)
- `TrustContextHolder` failure (would be 500/400 inside controller, not 403 at edge)

### Smallest preview fix

1. **VITO:** Add Ubomi-style **dual `SecurityFilterChain`**:
   - `impilo.security.disable-oauth-for-tests=true` → `anyRequest().permitAll()` + keep `TrustContextFilter`
   - production → existing JWT + `authenticated()` chain
2. Add `impilo.security.disable-oauth-for-tests: ${IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS:false}` to `application.yml` (explicit, like VARAPI).
3. Add integration test: BFF-style headers → `GET /v1/client-registry/clients` returns 200.

### Proper production fix

- BFF forwards **end-user JWT** (from shell session) on VITO calls where policy requires actor identity.
- For pure S2S orchestration, mint **BFF workload JWT** via Keycloak client credentials; validate at VITO.
- Keep `TrustContextFilter` for actor/facility/purpose dimensions; align with Tshepo ext_authz when Envoy path is live.
- Document INTERNAL vs EXTERNAL mode in VITO controller guards (already partially present on legacy endpoints).

---

## 4. Working-service comparison

| Service | BFF client path | Preview bypass | Method security | Trust filter | BFF S2S result |
|---------|-----------------|----------------|-----------------|--------------|----------------|
| **TUSO** | `POST /v1/internal/facilities/search` | `/v1/**` permitAll when bypass | None on controller | `TrustContextFilter` (servlet) | **200** |
| **VARAPI** | `POST /v1/internal/providers/search` | `/v1/**` permitAll when bypass | None | `TrustContextFilter` | **200** |
| **notification** | `GET /internal/v1/notifications` | `/internal/**` permitAll | None | None | **200** |
| **msika-apps** | `GET /internal/v1/marketplace/launcher` | `/internal/**` permitAll | **`@PreAuthorize`** | Companion `V11HeaderFilter` only | **403** |
| **VITO** | `GET /v1/client-registry/clients` | **Not implemented** | N/A (blocked earlier) | `TrustContextFilter` | **403** |

### Why TUSO/VARAPI “work” but look empty

- BFF reaches upstream successfully; empty lists are **data**, not auth failures.
- Registry controllers may still show empty/degraded UI when VARAPI returns zero rows.

### BFF header propagation (all services)

From `ServiceClientConfig.trustHeaderForwardingInterceptor()`:

- Forwards: tenant, pod, request/correlation IDs, **Authorization** (if present on inbound), actor, facility, purpose, service identity headers.
- Always sets: `X-Access-Mode: INTERNAL`, `X-Service-Id: experience-bff`.
- **Gap:** Does not set `X-Request-Source: HUMAN|SYSTEM` per S2S trust pattern doc.
- **Gap:** Unauthenticated API smokes / direct curls without user session → **no Authorization forwarded**.

---

## 5. Trust / context matrix

| Layer | Shell → BFF | BFF → msika | BFF → VITO | Production target |
|-------|-------------|-------------|------------|-------------------|
| User JWT | Keycloak session (when logged in) | Forward if present | Forward if present | Required for HUMAN flows |
| BFF workload JWT | N/A | **Missing** | **Missing** | Client-credentials JWT |
| v1.1 headers | Via api-client | Forwarded | Forwarded | Mandatory |
| `X-Access-Mode` | N/A | INTERNAL (BFF sets) | INTERNAL | INTERNAL for platform S2S |
| `X-Service-Id` | N/A | experience-bff | experience-bff | Registered in S2S contracts |
| Spring Authentication at callee | N/A | **Missing** | **Missing** | JWT-validated principal |
| Companion `RequestContext` | N/A | **Present** | N/A (uses TrustContext) | Present |
| `TrustContext` at callee | N/A | N/A | **Present but unreachable** | Required |
| Method `@PreAuthorize` | BFF RBAC | **Blocks** | N/A | Role/scope from JWT |
| Preview bypass | BFF `allow-anonymous` path | HTTP only | **Not wired** | Preview-only flag |

---

## 6. Recommended fix options

### Msika marketplace 500 / 403

| Option | Type | Preview safe? | Production safe? | Notes |
|--------|------|---------------|------------------|-------|
| A. Ubomi-style preview `permitAll` + disable method security for internal paths | Service security | Yes (documented preview-only) | **No** if left enabled | Smallest preview unblock |
| B. Replace launcher `@PreAuthorize("isAuthenticated()")` with INTERNAL+service-id check | Service policy | Yes | **Yes** with S2S contract | Aligns with first-party doctrine |
| C. BFF propagate user JWT + mint BFF workload JWT | BFF + Keycloak | Yes | **Yes** | Proper production path |
| D. BFF degraded handling on `/marketplace/launcher` | BFF only | Yes | Yes | Stops 500; does not fix tiles |
| E. Global preview role injection filter | Service | Risky | **No** | Hides problem |

**Recommended preview:** **A + D** (unblock + honest errors)  
**Recommended production:** **B + C** (+ integration-hub S2S contract)

### VITO client-registry 403

| Option | Type | Preview safe? | Production safe? | Notes |
|--------|------|---------------|------------------|-------|
| A. Ubomi dual SecurityFilterChain with preview bypass | Service security | Yes (documented) | **No** alone | Matches TUSO/VARAPI pattern |
| B. `application.yml` explicit disable-oauth mapping | Config | Yes | Yes | Clarity only |
| C. BFF forward user JWT on client-registry calls | BFF | Yes | **Yes** | Needed for actor-scoped registry ops |
| D. InternalAuthenticationFilter from trust headers | Shared lib | Partial | **Yes** with Tshepo validation | Production-grade INTERNAL mode |
| E. Broad `permitAll` without TrustContext | Service | Yes | **No** | Weakens doctrine |

**Recommended preview:** **A + B**  
**Recommended production:** **C + D** (JWT + trusted INTERNAL filter behind Envoy ext_authz)

---

## 7. Risk assessment

| Fix | Weakens security? | First-party alignment | Tests needed |
|-----|-------------------|----------------------|--------------|
| msika preview permitAll + disable method security | Preview only; must be flag-gated | Medium — unblocks S2S | IT: BFF headers → launcher 200 |
| msika INTERNAL+service-id `@PreAuthorize` | No | **High** | Unit + contract test |
| BFF workload JWT propagation | No | **High** | Golden-path IT with Keycloak |
| VITO preview dual chain | Preview only | Medium | IT: client-registry list 200 |
| BFF marketplace degraded wrapper | No | High (honest errors) | Controller test |

---

## 8. Recommended next implementation prompt

Use this verbatim after authorizing code changes:

```
Implement focused msika/VITO first-party trust fixes on branch
`claude/staging-ux-orchestration-remediation-Yypyl`. Diagnosis is in
`reports/product/msika-vito-auth-context-triage.md`.

Scope (smallest correct preview path + production hooks):

1. msika-apps-service
   - Add preview-conditional SecurityFilterChain (Ubomi pattern) OR
     trust-header-aware @PreAuthorize for `/internal/v1/marketplace/launcher`
     allowing INTERNAL calls from `experience-bff` with companion headers.
   - Add `impilo.security.disable-oauth-for-tests` mapping in application.yml.
   - Integration test: companion headers without JWT → launcher 200.

2. vito-service
   - Add dual SecurityFilterChain (preview bypass + production JWT chain).
   - Add explicit `impilo.security.disable-oauth-for-tests` in application.yml.
   - Integration test: INTERNAL headers → GET /v1/client-registry/clients 200.

3. experience-bff
   - HealthOsMarketplaceController.launcher(): catch downstream 403/502 and
     return honest degraded response (mirror HealthOsLauncherController).
   - Optional: set `X-Request-Source: HUMAN` on outbound S2S when user JWT present.
   - Tests for marketplace launcher degraded path.

4. Do NOT redeploy until VM gates pass.
   Do NOT use broad global anonymous bypass beyond documented preview flags.
   Preserve PCW-2 degraded meta patterns.

After implementation: run VM gates, report API table for marketplace launcher
and client-registry (expect 200 live or 200 honest degraded, not 500).
```

---

## 9. Acceptance checklist

- [x] Root cause specific (method security vs HTTP bypass; VITO missing preview chain)
- [x] Fix does not merely hide problem (production path = JWT + S2S contract)
- [x] Internal vNext first-party doctrine respected
- [x] No runtime mutations during diagnosis
- [x] Implementation authorized and completed (2026-06-13)

---

## 10. Implementation (2026-06-13)

### Files changed

| File | Change |
|------|--------|
| `services/msika-apps-service/.../security/MsikaInternalTrustAuthorization.java` | **New** — preview-gated trusted BFF launcher authorization bean |
| `services/msika-apps-service/.../api/MarketplaceControllers.java` | Launcher `@PreAuthorize` allows trusted BFF internal S2S in preview |
| `services/msika-apps-service/.../config/SecurityConfig.java` | Unchanged HTTP chain; method security aligned via bean |
| `services/msika-apps-service/src/main/resources/application.yml` | Explicit `impilo.security.disable-oauth-for-tests` mapping |
| `services/msika-apps-service/src/test/resources/application-test.yml` | Preview bypass enabled for tests |
| `services/msika-apps-service/pom.xml` | Added `spring-boot-starter-security` |
| `services/msika-apps-service/.../security/MarketplaceLauncherSecurityTest.java` | **New** — preview allows trusted BFF; catalogue stays protected |
| `services/msika-apps-service/.../security/MarketplaceLauncherProductionSecurityTest.java` | **New** — rejects header-only when bypass off |
| `services/vito-service/.../config/SecurityConfig.java` | Dual chain: preview permits `/v1/client-registry/**`; production `authenticated()` |
| `services/vito-service/src/main/resources/application.yml` | Explicit `impilo.security.disable-oauth-for-tests` mapping |
| `services/vito-service/.../config/SecurityConfigSourceGuardTest.java` | Updated — production chain guard + preview flag/scoping guard |
| `services/vito-service/.../api/ClientRegistrySecurityTest.java` | **New** — preview path returns 200 |
| `services/vito-service/.../api/ClientRegistryProductionSecurityTest.java` | **New** — production path returns 403 |
| `services/experience-bff/.../controller/HealthOsMarketplaceController.java` | Degraded handling for msika 403/5xx on `/marketplace/launcher` |
| `services/experience-bff/.../support/BffDegradedMeta.java` | Added `degradedWithStatus()` |
| `services/experience-bff/.../controller/HealthOsMarketplaceControllerTest.java` | **New** — degraded + passthrough + launcher merge tests |

### Preview fix delivered

1. **msika-apps:** `MsikaInternalTrustAuthorization` permits launcher when `IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS=true` **and** `X-Access-Mode: INTERNAL` + `X-Service-Id: experience-bff` + v1.1 quartet. Role-protected marketplace endpoints unchanged.
2. **VITO:** Flag-gated `testFilterChain` permits `/v1/client-registry/**` only; `productionFilterChain` unchanged (`anyRequest().authenticated()`).
3. **BFF:** `/internal/v1/marketplace/launcher` returns **200** with honest `{data:[], meta:{degraded, upstream, status, guidance}}` on msika failure — no more 500.

### Production hardening follow-up (not in this pass)

- BFF workload JWT minting + user JWT propagation per `SERVICE_TO_SERVICE_TRUST_PATTERN.md`
- Register `experience-bff` → `msika-apps-service` / `vito-service` S2S contracts in integration-hub
- Production-grade `MsikaInternalTrustAuthorization` path without preview flag (JWT-validated principal + S2S contract)
- BFF: set `X-Request-Source: HUMAN` on outbound calls when user session present

### Tests run and results

| Test suite | Result |
|------------|--------|
| `MarketplaceLauncherSecurityTest` | **PASS** |
| `MarketplaceLauncherProductionSecurityTest` | **PASS** |
| `ClientRegistrySecurityTest` | **PASS** |
| `ClientRegistryProductionSecurityTest` | **PASS** |
| `SecurityConfigSourceGuardTest` (VITO) | **PASS** |
| `HealthOsMarketplaceControllerTest` | **PASS** |
| `HealthOsLauncherControllerTest` | **PASS** |
| `RegistryControllerTest` | **PASS** |
| `bash scripts/pipeline/run-local-quality-gates.sh` | **PASS 21/21** |

### Production security intact?

**Yes.**

- msika: production (`disable-oauth-for-tests=false`) — launcher requires JWT via `isAuthenticated()`; `allowLauncher()` returns false; role endpoints unchanged.
- VITO: production chain still `anyRequest().authenticated()`; preview `permitAll` only on `/v1/client-registry/**` behind `@ConditionalOnProperty`.
- BFF: degraded handling is honest metadata only; does not bypass upstream auth.

### Risks

| Risk | Mitigation |
|------|------------|
| Preview bypass too broad on VITO | Scoped to `/v1/client-registry/**` only; other VITO APIs still `authenticated()` in test chain |
| msika launcher open without roles in preview | Limited to `experience-bff` + INTERNAL + companion quartet; catalogue/admin endpoints still role-gated |
| BFF degraded response shape differs from success passthrough | Documented; shell primary path uses `/internal/v1/launcher/apps` |

### Remaining work

- **Preview deploy required** to activate fixes at `http://41.57.127.235` (runtime still on `103a5aab`; code not yet built/deployed)
- Rebuild images: `msika-apps-service`, `vito-service`, `experience-bff` (and optionally shell if BFF-only deploy insufficient)
- Post-deploy smoke: marketplace launcher 200 (live or degraded), client-registry live 200, launcher apps 31+marketplace

### Recommended commit message

```
fix: align msika/VITO preview trust and BFF marketplace degraded handling

- msika launcher accepts trusted experience-bff INTERNAL S2S when preview bypass enabled
- VITO honors disable-oauth-for-tests for /v1/client-registry/** only
- BFF marketplace launcher returns honest degraded meta instead of 500
```

### Next deploy authorization prompt

```
AUTHORIZE PREVIEW REDEPLOY FOR MSIKA/VITO/BFF AUTH-CONTEXT FIX

Rebuild and rollout in impilo-full-preview:
- msika-apps-service (preview tag)
- vito-service (preview tag)
- experience-bff (commit-tagged image)

Do not touch Postgres/PVCs/secrets.
Post-deploy smoke:
- GET /internal/v1/marketplace/launcher → 200 (live or degraded, not 500)
- GET /internal/v1/client-registry/clients → 200 live (not degraded)
- GET /internal/v1/launcher/apps → 200 with catalogue + marketplace tiles when msika live
```

