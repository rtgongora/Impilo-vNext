# Wave 8 — Consistency Class Enforcement (A/B/C)

## Overview

Consistency classes ensure that each request-path has the right level of
policy decision freshness, from synchronous PDP (Class A) to offline with
entitlement (Class C).

## Consistency Classes

| Class | Name               | Requirement                                          | Error on Failure      |
|-------|--------------------|------------------------------------------------------|-----------------------|
| A     | Synchronous PDP    | Gateway policy headers OR internal PDP call required | 503 PDP_UNAVAILABLE   |
| B     | Bounded Staleness  | Projection freshness within threshold                | 409 STALE_CONTEXT     |
| C     | Offline Entitlement| Offline-Entitlement header required                  | 403 OFFLINE_ENTITLEMENT_REQUIRED |

## Architecture

```
Request → ConsistencyClassFilter (order 13)
            │
            ├── ActionRegistry.lookup(path, method)
            │     └── Returns ActionRegistryEntry with ConsistencyClass
            │
            ├── CLASS_A → Check X-Policy-Decision header
            │              └── If missing → call PdpClient.evaluate()
            │                   └── If PDP down → 503 (or break-glass if allowed)
            │
            ├── CLASS_B → Check StalenessProvider.currentStalenessMs()
            │              └── If > threshold → 409 STALE_CONTEXT
            │              └── If OK → add X-Staleness-Ms header
            │
            └── CLASS_C → Check Offline-Entitlement header
                           └── If missing → 403 OFFLINE_ENTITLEMENT_REQUIRED
```

## ActionRegistry

Each service provides an `ActionRegistry` bean that maps routes to consistency classes:

```java
@Bean
public ActionRegistry actionRegistry() {
    ActionRegistry registry = new ActionRegistry();
    // Class A: sync PDP required for patient merge
    registry.register(ActionRegistryEntry.classA(
        "/internal/v1/patients/merge", "POST", "PATIENT_MERGE", false));
    // Class B: bounded staleness for stock queries (60s threshold)
    registry.register(ActionRegistryEntry.classB(
        "/internal/v1/inventory/stock", "GET", "STOCK_QUERY", 60_000));
    // Class C: offline vitals capture
    registry.register(ActionRegistryEntry.classC(
        "/internal/v1/offline/actions", "POST", "CAPTURE_VITALS"));
    return registry;
}
```

## PDP Decision Evidence

Every PDP decision (ALLOW/DENY/STEP_UP) now emits evidence headers:

| Header             | Description                                    |
|--------------------|------------------------------------------------|
| X-Policy-Decision  | ALLOW, DENY, STEP_UP_REQUIRED, ALLOW_BREAK_GLASS |
| X-Policy-Version   | Policy version that produced the decision (e.g. v1.1.0) |
| X-Decision-Reason  | Machine-readable reason codes                  |

These are persisted in `tshepo.policy_decision_log` with `policy_version` and `reason_codes` columns.

## Break-Glass (Class A)

When the PDP is unavailable and `breakGlassAllowed=true` on the action entry:
1. Client must include `X-Break-Glass: true` header
2. Filter allows with `X-Policy-Decision: ALLOW_BREAK_GLASS`
3. Elevated audit logging is triggered
4. If break-glass not allowed → 503 PDP_UNAVAILABLE

## High-Priority Control Channel

Revocations propagate via dedicated Kafka topics (not best-effort):

| Topic                                    | Events                                    |
|------------------------------------------|-------------------------------------------|
| `impilo.control.revocation.v1`           | CONSENT_REVOKED, IDENTITY_MERGED, IDENTITY_DEACTIVATED |
| `impilo.control.privilege_revocation.v1` | PRIVILEGE_REVOKED                         |

### Consumers

| Service | Consumer Group         | Handles                                   |
|---------|------------------------|-------------------------------------------|
| VITO    | vito-control-channel   | Consent holds, linkage chain updates       |
| PCT     | pct-control-channel    | Journey consent restrictions, identity updates |

All consumers are idempotent via `control_channel_watermark` table.

## Testing

See `ConsistencyClassFilterTest` in `libs/tech-companion/src/test/`.
Covers all three classes, error codes, break-glass, path matching.
