# Gateway Policy Pack — Impilo vNext

Deployable OPA policies for Envoy gateway enforcement.

## Policies

| Policy | File | Purpose |
|--------|------|---------|
| Header enforcement | `opa/impilo_headers.rego` | Enforce X-Tenant-ID, X-Pod-ID, X-Request-ID, X-Correlation-ID on all v1.1 paths |
| Idempotency classification | `opa/impilo_idempotency.rego` | Require Idempotency-Key on command (POST/PUT/PATCH/DELETE) v1.1 endpoints |
| Federation authority | `opa/impilo_federation.rego` | Restrict national-authoritative operations to national pods only |

## Deployment

### OPA Sidecar

```bash
opa run --server --addr=:9191 \
  --set=decision_logs.console=true \
  ./tools/ops/gateway/opa/
```

### Envoy Integration

Configure Envoy ext_authz to call the OPA sidecar. See `envoy/opa-ext-authz.yaml`
for the reference configuration snippet.

## Relationship to tech-companion

These policies provide **gateway-level** enforcement (first line of defense).
The tech-companion shared library provides **service-level** enforcement
(second line of defense) via:

- `V11HeaderFilter` — header validation + RequestContext population
- `IdempotencyFilter` — replay/conflict semantics
- `TimeoutEnforcementFilter` — client timeout enforcement
- `CorrelationMdcFilter` — MDC logging injection

Both layers are required. Gateway policies catch malformed requests early;
service-level filters provide defense-in-depth.
