# Service-to-Service Trust Pattern

> Implements `docs/doctrine/HEALTH_OS_EXTENSIBILITY_DOCTRINE.md` §4 and §9.
> Java constants: `libs/tech-companion/src/main/java/.../context/CompanionHeaders.java`.
> TS constants: `contracts/service-to-service-trust.ts`.

## 1. Scope

This document defines how internal sovereign Impilo services authenticate, identify, and audit each other. It complements the Health OS Manifest v1.2 quartet (`X-Tenant-ID`, `X-Pod-ID`, `X-Request-ID`, `X-Correlation-ID`) with **service identity** and **request source** headers.

It does **not** redefine TSHEPO authorization, Envoy `ext_authz`, OAuth/OIDC, or the v1.2 doctrine — it sits on top of them.

## 2. Five request-source categories

Every internal call carries `X-Request-Source` with exactly one of:

| Value | When |
|-------|------|
| `HUMAN` | User-initiated request flowing from a session to the BFF to a service |
| `SYSTEM` | Synchronous platform-internal orchestration (e.g. PCT calls BUTANO during encounter close) |
| `SCHEDULED_JOB` | A recurring batch or scheduled job (e.g. data-pipeline nightly aggregate) |
| `BACKGROUND_WORKER` | An async worker (outbox publisher, retry worker, reconciler) |
| `EVENT_CONSUMER` | A Kafka consumer reacting to an internal event |
| `AI_ASSISTED` | An AI-mediated action (Nompilo, guidance) — logged with `X-AI-Skill-Id` and model id |
| `EXTERNAL_APP` | The request was initiated by an external app and the current service is acting on its behalf — `X-External-App-Id` MUST also be present |

Audit records this value verbatim.

## 3. Service identity headers

Every internal service-to-service request MUST include:

```
X-Service-Id:        <registered-id>          // e.g. "experience-bff", "msika-apps-service"
X-Service-Name:      <human readable>         // e.g. "Experience BFF"
X-Service-Version:   <semver>                 // e.g. "0.4.1"
X-Request-Source:    HUMAN | SYSTEM | ...     // see §2
```

Conditional:

```
X-External-App-Id:   <external-app-id>        // when Source = EXTERNAL_APP or original request came from external app
X-Actor-Id:          <Health ID>              // when Source = HUMAN
X-Actor-Type:        PROVIDER | OPERATOR | CITIZEN | CAREGIVER  // when Source = HUMAN
X-Provider-Id:       <VARAPI provider id>     // when human is acting in a regulated role
```

## 4. The Service-to-Service Contract registry

Every (caller service, callee service) pair MUST be registered in the `s2s_contracts` table (managed by `integration-hub` REST at `/internal/v1/s2s-contracts`).

Each contract record contains:

- Caller and callee service identifiers
- Contract version
- Allowed operations (path patterns)
- Allowed scopes (in OAuth/Health-OS scope vocabulary)
- Allowed event topics (Kafka topics the caller may subscribe to from the callee)
- Allowed `X-Request-Source` values
- Owner team + support contact
- Status: `DRAFT`, `ACTIVE`, `DEPRECATED`
- Rate limit (optional)
- Last verified timestamp

Unregistered S2S calls SHOULD be rejected at the Envoy gateway via OPA policy (`tools/ops/gateway/opa/`) when `impilo.s2s.contract_enforcement = strict`. In development mode, they're logged at WARN and the request proceeds.

## 5. Trust posture matrix

| Caller class | Mandatory headers | Optional headers | Identity proof |
|--------------|-------------------|------------------|----------------|
| Internal sovereign service | v1.2 quartet + Authorization + `X-Service-Id` + `X-Service-Version` + `X-Request-Source` + `X-Purpose-Of-Use` | `X-Actor-*` if HUMAN; `Idempotency-Key` for state-changing | mTLS internal cert OR JWT issued for service workload |
| Experience BFF (acting for a user) | All HUMAN headers + `X-Service-Id=experience-bff` | `X-Provider-Id` if regulated role | User OIDC JWT + BFF workload JWT |
| External app | v1.2 quartet + Authorization (client-credentials JWT) + `X-External-App-Id` + `X-Integration-Type` + `X-Integration-Version` + `X-Request-Signature` + `X-Purpose-Of-Use` + `Idempotency-Key` | `X-Subject-Id`, `X-Access-Mode=EXTERNAL` | OAuth2 client-credentials OR mTLS + request signature |
| Background worker | v1.2 quartet + `X-Service-Id` + `X-Service-Version` + `X-Request-Source=BACKGROUND_WORKER` + `X-Purpose-Of-Use=OPERATIONS` | `X-Actor-Id=system:<worker-id>` | Service workload JWT |
| Event consumer | v1.2 quartet + `X-Service-Id` + `X-Request-Source=EVENT_CONSUMER` + `X-Correlation-ID` (copied from event envelope) | `X-Originator-Service-Id` (the producer) | Service workload JWT |

## 6. Audit obligations

Every S2S call SHALL be recorded with:

- All identity and context headers
- TSHEPO decision and reason
- Endpoint, method, status code, latency
- Outbox publish ids if any

These are emitted to `audit_events` (via `audit-ledger-service`) by either the gateway or the receiving service.

## 7. Migration notes

- The legacy `X-Tenant-Id` / `X-Tenant-ID` case-sensitivity issue is resolved at the tech-companion layer; new headers in this document use the same case (`X-Service-Id`, etc.).
- Services that do not yet emit `X-Service-Id` will be logged at WARN by the gateway during the soft-enforcement window. The hard-enforcement deadline is tracked in `docs/architecture/SERVICE_ACTIVATION_BACKLOG.md`.
- The `s2s_contracts` table starts empty; integration-hub auto-seeds well-known internal pairs from `docs/registry/services-registry.yaml` on first start.

## 8. Reference implementation pointers

- Header parsing & required-header validation: `libs/tech-companion/src/main/java/.../filter/RequestContextFilter.java`
- Outbound S2S call helper (Java): use `tech-companion`'s `OutboundCallContext.builder()` (planned) or set headers explicitly
- Outbound S2S call helper (TS / BFF): `experience-bff`'s `ServiceClient` (existing helpers under `experience-bff` proxy controllers)
- Web/mobile callers DO NOT need to set S2S headers — the BFF originates the S2S call

## 9. Service workload credentials — preview==prod auth wave (stage 2)

**Why this exists.** Trust headers are context, NOT authentication. On 2026-07-22 a
confused deputy was demonstrated live: calling fhir-gateway from a pct-service pod with a
forged `X-Actor-ID` was accepted. NetworkPolicies were then empirically proven to enforce
**nothing** on the preview k3s (a default-deny let traffic through), so network segmentation
is not an available compensating control. Meanwhile services that already enforce OAuth in
preview (clinical-knowledge-platform-service `anyRequest().authenticated()`; ndila-service on
`/internal/v1/ndila/**` and `POST /api/v1/ndila/distance-matrix`) 401 every unauthenticated
S2S call and the caller degrades **silently** — the BFF's distance-matrix call fell back to
haversine/null-ETA forever, and the IMAM→CKP path 503'd on the estate while 28 mocked tests
stayed green.

**Stage 2 slice 1 (landed): experience-bff mints its own credential.**
`services/experience-bff/.../auth/ServiceTokenProvider.java` obtains a `client_credentials`
token from Keycloak (client `KEYCLOAK_BACKEND_CLIENT_ID`, default `impilo-backend`; secret
`KEYCLOAK_BACKEND_SECRET` from k8s secret `impilo-app-secrets` / key `keycloak-backend-secret`
via `experienceBff.secretEnv` in `deploy/helm/impilo-vnext/values-full-preview.yaml`). The
shared `trustHeaderForwardingInterceptor` (`ServiceClientConfig`) applies this precedence on
every outbound call:

1. An `Authorization` header pre-set by the client method wins outright.
2. Otherwise the inbound user token is forwarded unchanged (JWT propagation, unchanged).
3. Only when neither exists (scheduled jobs, Kafka consumers, public-lane composition,
   service-originated calls) is the service token attached as `Bearer <token>`.

> ⚠ **Provenance — this list described intent, not behaviour, until 2026-07-27 (`940e50efd`).**
> Tier 1 did not exist. `AUTHORIZATION` was forwarded with an unconditional `set()`, and
> interceptors run *after* the client method has built its headers, so the inbound user token
> overwrote every deliberately pre-set bearer. Real behaviour was
> **inbound user token > pre-set > service token**. Tier 3 was always correct (gated on the
> header being absent) and is untouched. Fixed by forwarding Authorization only-if-absent, as
> `X-Actor-ID`/`X-Actor-Type` already were.
>
> The asymmetry was the security defect, not the ordering: `DaidzaiServiceClient` and
> `ParticipationServiceClient` set `X-Actor-Type: SYSTEM` beside a service bearer, and only the
> actor context survived — one outbound request asserting SYSTEM authority while carrying a
> citizen's token. **When two headers describe the same caller they must share a precedence
> rule; an asymmetry between them is an escalation primitive.**
>
> Recorded rather than silently corrected because a doc that becomes accidentally true still
> misleads the next reader about what was ever verified. Anything asserting this precedence is
> only trustworthy against `ServiceClientConfigTest` /
> `AuthorizationHeaderPrecedenceLoopbackTest`, which prove it over a real socket —
> `MockRestServiceServer` replaces the request factory and cannot observe transport behaviour.

**Keycloak is not a sovereign service — it rides `idpRestTemplate`.** Classes addressing the
IdP directly (`KeycloakAdminClient`, `AdminUserController`, `AuthSessionController`,
`AuthContactOtpController`) take the NON-intercepted `idpRestTemplate` bean: shipping ~30 Impilo
trust headers to Keycloak is meaningless, and a `client_credentials` call that *mints* a
credential must never inherit ambient credentials — it was carrying the caller's bearer.
`IdentityProviderTemplateWiringTest` enforces this as a **discovery rule** (any class binding a
`KEYCLOAK_URL` `@Value`), not a list; the rule is what found `AuthContactOtpController`, which
every hand-built register had missed because it pre-sets no Authorization at all. Note for the
next lane: a second `RestTemplate` bean requires `@Primary` on `serviceRestTemplate`, because
`WellnessServiceProxyController` injects by type alone and the context otherwise fails to start.

Failure is honest, never silent: a failed mint logs WARN with the grep-able marker
`SERVICE_TOKEN_MINT_FAILED` and the request proceeds without a token (preview keeps working
with the secret absent); a mint failure is never cached longer than 30s; the token itself is
cached until ~30s before expiry. Sibling implementations of the same shape:
`mvumo-service` `ClientCredentialsTokenProvider` (TSHEPO) and `pct-service`
`ServiceTokenProvider` (CKP lane).

**Remaining stages (deliberately NOT in this slice):**

- Per-service token providers for every other service with background/S2S callers
  (outbox publishers, Kafka consumers, scheduled jobs) — other lanes.
- Real UI auth end-to-end so preview stops depending on `IMPILO_SECURITY_ALLOW_ANONYMOUS`.
- The flag flip itself (`IMPILO_SECURITY_ALLOW_ANONYMOUS` / `IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS`
  off everywhere) once callers all carry credentials — the final preview==prod convergence.
- Verification law for every slice: assert the PRESENCE of authenticated content (a real ETA,
  a 200 with payload) plus a negative control (the same endpoint 401s without a token) —
  never merely the absence of errors.
