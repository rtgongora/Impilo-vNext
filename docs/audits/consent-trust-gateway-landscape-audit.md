# Consent · Trust · Gateway Landscape Audit (Envoy · Keycloak · OPA · Tshepo · Mvumo)

**Date:** 2026-06-26 · **Branch:** `intake/citizen-zero-to-one` · **Method:** read-only, two parallel
Explore passes + targeted verification. Every claim cites file:line.

> Prompted by the consent rearchitecture: before building further we audited the *whole* trust/
> gateway landscape — who decides, who enforces, who stores, what's real vs orphaned, and where the
> boundaries leak. **One critical authorization-integrity finding** (header-over-JWT identity) and
> several structural gaps emerged.

## 0. Canonical wiring (so we audit reality, not examples)

- **Live Envoy config:** `infra/envoy/envoy-runtime.yaml` (mounted at `docker-compose.runtime.yml:191`).
  `infra/envoy/envoy.yaml` is dev; `tools/tech-companion/gateway/envoy/envoy-ext-authz-reference.yaml`
  is an OPA reference, **not live**; `deploy/helm/.../envoy.yaml` is the K8s template.
- **Live ext_authz:** Envoy `:10000` → **tshepo-authz gRPC `:9090`**, `timeout 0.5s`,
  `failure_mode_allow:false` (fail-closed) (`envoy-runtime.yaml:155-166,198`).

## 1. Who does what (component map)

| Plane | Component | Port | Role (verified) |
|------|-----------|------|-----------------|
| AuthN | **Keycloak** | 8080 | OIDC login, realm roles, `acr`. Protocol mappers *can* emit `x_actor_id/x_tenant_id/x_pod_id` (from user attributes) + hardcoded `tenant_id=moh-zw`. **No consent.** |
| PEP (edge) | **Envoy** | 10000 / 9901 | Gateway. ext_authz→tshepo-authz gRPC, fail-closed. **No header sanitization, no JWT validation, no upstream-header allow-list.** Routes most `/api/v1/*`,`/v1/*` **direct to services**; only `/internal/v1/*`,`/external/v1/*`→experience-bff. |
| Edge policy | **OPA** | 8181 | **Orphaned.** Container runs, cluster `opa_sidecar` defined, but **NOT in the ext_authz chain**. Two unrelated policy sets exist (see §4). |
| PDP | **tshepo-authz** | 8081 / 9090 | The real decision point: `PolicyEngine` 7 steps (risk→purpose→break-glass→RBAC/ABAC→**consent**→step-up→obligations). Validates Keycloak JWT (`KeycloakAdapter`). Emits `policy_decision_log` + Kafka. |
| Consent verdict | **tshepo-consent** | 8182 | FHIR R4 Consent store + `GET /v1/consent/evaluate` ("whether"). The clinical record-of-truth. |
| Consent orchestration | **Mvumo** | 8195 | The "how/who/when" act-of-record: templates, requests, remote/assisted, adaptive assurance, proof, comms-prefs, **legal agreements** (new, Phase 1). Writes through to tshepo-consent on clinical grant. |
| Privacy data | **data-governance** | 8220 | Privacy/display prefs (FULL/PARTIAL/MINIMAL) + DSR erasure. A data steward / PIP — **not consumed by the PDP** (§6). |
| FHIR egress | **fhir-gateway** | 8091 | Gates FHIR reads to BUTANO via its own `ConsentEnforcementService` (a *second* consent enforcement point). |
| Assurance | **identity-assurance** | 8201 | Canonical LoA + self-service upgrade. BFF resolves citizen level per-request (G-CZO-01 fix). |
| Other Ring-0 | tshepo-audit (8183), tshepo-keys (8184), tshepo-offline (8185), tshepo-identity | — | audit sink · JWKS/signing · capability-token offline authz · CPID/identity. |

One-line doctrine (target): **Keycloak authenticates · Envoy enforces at the edge · OPA *should* decide
policy · tshepo-authz orchestrates+enforces+audits · tshepo-consent is the clinical verdict · Mvumo is
the consent/agreement act-of-record · data-governance owns privacy prefs.**

## 2. End-to-end request flow (two real paths)

```
WEB/MOBILE ──JWT + client-set trust headers──▶ Envoy :10000
   │                                              │ ext_authz gRPC :9090 (fail-closed)
   │                                              ▼
   │                                        tshepo-authz PolicyEngine ──step5──▶ tshepo-consent /evaluate
   │                                              │ (ALLOW + obligation headers injected upstream)
   ├── /api/v1/*, /v1/*  ───────────────▶ service DIRECT (vito, pct, oros, pharmacy, …)
   └── /internal/v1/*, /external/v1/* ──▶ experience-bff ──▶ downstream services
                                              (ServiceClientConfig forwards/overrides trust headers)
fhir-gateway path: clinical FHIR read ─▶ ConsentEnforcementService ─▶ tshepo-consent /evaluate (GET) + Redis revocation cache
```

Note: **most traffic does not go through the BFF** — only `/internal|/external/v1/*` does. So BFF-side
header hardening (e.g. the new assurance resolver) does **not** protect the direct service routes.

## 3. Envoy gateway findings

- **ext_authz → tshepo-authz gRPC only**; OPA is not chained (`envoy-runtime.yaml:155-166`).
- **No `request_headers_to_remove`** anywhere → client-supplied trust headers pass through untouched.
- **No `allowed_upstream_headers`** block → tshepo-authz's response headers (obligations/visibility) flow
  upstream permissively (acceptable, but unbounded).
- **No JWT auth filter** at Envoy — token validation is entirely tshepo-authz's job.
- **Fail-closed**: tshepo-authz down/timeout ⇒ 403, request never reaches the service.
- Route inconsistency: some routes `prefix_rewrite`, some don't; public paths (`/verify`,`/share`) still
  hit ext_authz (tshepo-authz makes the ALLOW decision for them).

## 4. OPA status — orphaned, and two unrelated policy sets

- **Mounted but not enforced:** the running container mounts `tools/ops/gateway/opa` (header-contract,
  federation, idempotency rego) — gateway guardrails — but ext_authz never calls OPA, so **these are
  loaded and ignored** (`docker-compose.runtime.yml:144`).
- **A whole ABAC corpus is unmounted:** `infra/opa/impilo/*.rego` (7 doctrine modules — vashandi/work/
  organisation/registry/hsc/tabs/marketplace) with a rich `input` shape exists, is **not mounted, not
  tested** (zero `*_test.rego`), and runs nowhere.
- So "OPA" today = aspirational. Phase 3 of the rearchitecture is *promoting* this corpus into the live
  path, not greenfield.

## 5. Consent enforcement points — FOUR callers, THREE contracts

| # | Caller | Contract | Target | Failure mode | Gates access? |
|---|--------|----------|--------|--------------|---------------|
| 1 | tshepo-authz `ConsentClient` (PolicyEngine step-5) | **POST body** (tenant,resourceType,resourceId,actor,purpose) | tshepo-consent `/v1/consent/evaluate` | fail-closed DENY | **Yes** (clinical resources) |
| 2 | fhir-gateway `ConsentEnforcementService` | **GET query** (+ local Redis revocation cache, Kafka-fed) | tshepo-consent `/v1/consent/evaluate` | fail-closed DENY | **Yes** (FHIR egress) |
| 3 | Mvumo `TshepoConsentClient.evaluateDirective` | **GET query** | tshepo-consent | **throws** (not deny) | No (summary/orchestration) |
| 4 | experience-bff `TshepoConsentServiceClient` | GET query | tshepo-consent | — | No (display only) |

**Divergence:** POST vs GET shapes; authz omits scope while fhir-gateway maps resource→scope; Mvumo throws
where others deny. Same engine, three contracts → maintenance + drift hazard. (Phase 2 consolidates to one.)

## 6. data-governance privacy prefs are NOT enforced

`PrivacyPreferenceService` stores FULL/PARTIAL/MINIMAL visibility intent, but `VisibilityObligationComposer`
(tshepo-authz step-7) composes masking **purely from purpose + policy rule + sensitivity** and **never
queries the user's privacy preference**. Net: a user who chose MINIMAL can still be shown FULL_IDENTIFIED
data by policy. The PIP exists; the PDP ignores it. (Candidate obligation input — see §9.)

## 7. 🔴 CRITICAL — client trust headers override the validated JWT (identity confusion)

**Finding.** At `ExtAuthzGrpcService.java:110-119` (mirrored in the HTTP `AuthorizeController`), the validated
Keycloak session only fills `actorId`/`actorType`/`tenantId` **when the client header is blank**:

```java
if ((actorId == null || actorId.isBlank()) && session.actorId() != null) actorId = session.actorId();
```

So a **client-supplied `X-Actor-ID` takes precedence over the JWT `sub`**. Combined with: Envoy does **not
strip** client trust headers (§3), and the BFF `ServiceClientConfig` **forwards them blindly** for the
`/internal/v1/*` path. Result: a caller can present a valid token (own roles/loa, which *are* JWT-bound)
while setting `X-Actor-ID`/`X-Actor-Type`/`X-Tenant-ID`/`X-Purpose-Of-Use`/`X-Facility-ID`/`X-Subject-ID`
to arbitrary values, and the PDP evaluates against the **spoofed** identity/context.

**Why it matters.** Policies keyed on `actorId == subject` (own-record access), tenant scoping, purpose
gating, and the new delegation `X-Subject-ID` seam are all defeated by header injection. The session
signature is checked, but identity is taken from the header, so it's *authenticated-but-impersonatable*.

**Partial mitigations today.** Roles + loaLevel come from the JWT (not headers), so RBAC role checks aren't
directly spoofable. `X-Assurance-Level` is authoritatively overridden for **citizen** actors by the BFF
(`AssuranceLevelResolutionInterceptor`) — but only on the BFF path, only for citizens, and only when
identity-assurance is reachable. `X-Subject-ID` is *not forwarded* by the BFF (implicitly stripped on that
path) — but external/direct callers reach Envoy and tshepo-authz with it intact.

**This is the most important finding of the audit and is NOT yet in the rearchitecture scope.** It belongs
there (see §9).

## 8. Keycloak → trust-context gaps

- `KeycloakAdapter` extracts `sub`→actorId (fallback only, per §7), realm roles, and `acr`→loaLevel; it does
  **not** read the `x_actor_id/x_tenant_id/x_pod_id` claims the realm mappers can emit. `actor_type` has no
  mapper (inferred from roles). `tenant_id` is hardcoded `moh-zw` per realm (no per-user multi-tenancy).
- Net: the platform has the *means* to bind identity into the token (claim mappers) but doesn't use them as
  the authoritative source — which is exactly what §7 needs.

## 9. Consolidated risk register

| ID | Sev | Finding | Where the rearchitecture addresses it |
|----|-----|---------|----------------------------------------|
| **TPL-1** | 🔴 Critical | Client `X-Actor-ID`/type/tenant/purpose/subject override the JWT (§7) | **NEW scope** — add an edge trust-boundary fix: Envoy strips client trust headers (or marks them untrusted) + tshepo-authz makes JWT claims authoritative over headers (header only for *trusted* internal callers). Fold into Phase 3 (OPA/PDP rework) or a Phase 0 hardening before it. |
| **TPL-2** | 🟠 High | OPA orphaned; ABAC corpus unmounted/untested (§4) | Phase 3 (promote `infra/opa/impilo` → live, shadow→enforce). |
| **TPL-3** | 🟠 High | 3 consent evaluate contracts diverge (§5) | Phase 2 (consolidate to one contract; verdict-as-PDP-input). |
| **TPL-4** | 🟡 Med | Mvumo↔tshepo-consent can drift (Mvumo state vs FHIR status) | Phase 2 (reconciliation job). |
| **TPL-5** | 🟡 Med | Privacy preference (MINIMAL/PARTIAL) ignored by obligations (§6) | **NEW scope** — make privacy-pref a clamping obligation input in step-7 (data-governance as a PIP). |
| **TPL-6** | 🟡 Med | Direct service routes bypass BFF header hardening (§2) | Implies edge (Envoy/OPA) is the only safe place for trust-header policy → reinforces TPL-1's fix location. |
| **TPL-7** | 🔵 Future | Keycloak claim-mappers unused as authoritative identity (§8) | Pairs with TPL-1 (token-as-truth). |
| — | ✅ | L5 delegation, legal-consent home, LOA propagation | Phase 4 / Phase 1 (done) / done. |

## 10. What this changes about the plan

The approved 4-phase plan stands, **with two additions surfaced by this audit**:

1. **TPL-1 (header-over-JWT) is a critical trust-boundary defect** that must be fixed — ideally **before or
   alongside Phase 3**, because OPA-as-PDP only matters if the `input` identity is trustworthy. Recommend a
   short **Phase 2.5 / "edge trust hardening"**: (a) Envoy `request_headers_to_remove` for client-set trust
   headers on external listeners (or move them to an `x-ext-*` namespace), (b) tshepo-authz: JWT claims
   authoritative over headers, headers honoured only from an authenticated *trusted internal caller*
   (S2S/BFF) identity.
2. **TPL-5 (privacy-pref → obligation)** is a small, high-value add to Phase 3's obligation composition.

Everything else (Phase 1 done; Phase 2 coherence; Phase 3 OPA; Phase 4 L5) is unchanged and consistent with
the verified landscape.
