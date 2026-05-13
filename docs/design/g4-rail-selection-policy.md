# G-4 — RailSelectionPolicy: technical design

| Field         | Value |
| ------------- | ----- |
| Status        | Draft (design — not yet implemented) |
| Audit gap     | G-4 (see `docs/audits/costa-mushex-experience-layer-wiring-audit.md`) |
| Doctrine      | `docs/doctrine/mushex-gateway-neutrality.md` |
| Owner service | `services/mushex-service` |
| Owner package | `zw.gov.mohcc.impilo.mushex.service.rail` (new) |
| Scope         | Server-side rail selection at payment-intent creation time |
| Schema impact | **None.** All persistence is via existing `payment_intents.metadata` (JSONB) column and existing `event_outbox` payloads. |
| Wire impact   | Additive optional fields on `CreateIntentRequest`. All existing callers remain compatible. |

---

## 1. Purpose

Close audit gap **G-4** by introducing an explicit, deterministic policy for choosing
which **payment rail adapter** a `PaymentIntent` should be routed through, and by
recording that decision (and any fallback that occurred) on the intent and on the
outbox `INTENT_CREATED` event.

Today the orchestration plane has:

- four registered `PaymentRailAdapter` instances (`MOBILE_MONEY`, `BANK_TRANSFER`, `CARD_GATEWAY`, `SANDBOX`);
- one custodial wallet flow handled outside the adapter registry (`MusheWalletAdapter`);
- **no recorded link** between a `PaymentIntent` and the rail it will be (or was) attempted on, except as ad-hoc free-form keys inside `metadata`.

That makes intents un-auditable at the rail boundary, makes the Direct-Gateway
side of the dual-mode doctrine implicit rather than explicit, and prevents the
Experience BFF / `one-ui-shell` from exposing a meaningful "chosen rail" indicator
to finance operators.

This design adds the missing decision step **without** changing money-moving
behaviour today (because the create path does not actually move money — it sets up
state for later attempts that already exist in the codebase).

## 2. Goals and non-goals

### Goals

1. Record which rail a `PaymentIntent` is targeted at, and *why*, at creation time.
2. Allow trusted callers (BFF, COSTA) to express a **preferred rail** per intent.
3. Allow trusted callers to opt **in** or **out** of fallback behaviour.
4. Allow a future direct-gateway path (Mode A vs Mode B per doctrine) to be requested,
   but **default-off** in production.
5. Be backward-compatible: every existing caller of `POST /mushex/v1/payment-intents`
   keeps working without code or contract changes.
6. Be deterministic and unit-testable: same inputs → same selection → same metadata.
7. Be observable via outbox so downstream audit/finance tools can show the choice.

### Non-goals

1. **No** live external gateway call. Adapters today are stubs; this design does
   not change that.
2. **No** schema change. We use the existing `metadata` JSONB column and the
   existing `event_outbox.payload`. This satisfies the autonomy rule against
   schema changes.
3. **No** rail-selection in attempt creation or webhook paths (those flows
   already operate against an `adapterType` resolved from `PaymentAttemptEntity`).
4. **No** tenant-level or facility-level rail preference store (designed for, but
   left as a future hook — see section 11).
5. **No** new admin write actions on `/finance/mushex-platform` — that page stays
   read-only.
6. **No** rename or removal of `AdapterType` values.

## 3. Vocabulary

| Term                     | Meaning |
| ------------------------ | ------- |
| Rail                     | A `PaymentRailAdapter` registered in the `AdapterRegistry`, identified by its `AdapterType` enum value. |
| Preferred rail           | Caller-supplied request to use a specific rail. Optional. |
| Effective rail           | Rail actually selected by `RailSelectionPolicy` after applying preference, availability, fallback, and safety rules. |
| Fallback rail            | The effective rail when the preferred rail is unavailable and fallback is allowed. Today, this is always `SANDBOX`. |
| Selection reason         | A machine-readable enum value explaining *why* the effective rail was chosen. |
| Mode A (Direct Gateway)  | The doctrinal mode where MusheX dispatches directly to an external gateway adapter. |
| Mode B (Orchestration)   | The doctrinal mode where MusheX uses Mushe Wallet / SANDBOX / internal flows. Default mode. |

## 4. Configuration and safety switches

A new nested config class on `MushexProperties` named `RailSelection`:

```yaml
mushex:
  rail-selection:
    # Safety switch: when false, RailSelectionPolicy is computed and recorded but never
    # constrains downstream attempt creation (pure-shadow mode). Default = true (safe to
    # enable because today no other code branches on selectedRail).
    enabled: true

    # When no preferredRailAdapter is supplied, this is the default effective rail.
    # SANDBOX keeps production behaviour conservative (no accidental external rail).
    defaultRail: SANDBOX

    # Whether direct-gateway mode (Mode A per doctrine) may be requested at all.
    # When false, any caller-supplied directGatewayAllowed=true is overridden to false.
    # Default = false. Must be explicitly enabled per-environment when a live gateway
    # adapter exists and is configured.
    allowDirectGateway: false

    # Whether SANDBOX may serve as a fallback when the preferred rail is unavailable.
    # In production, operators can disable this to force a hard reject instead of a
    # silent demote-to-sandbox.
    allowSandboxFallback: true
```

These properties are **read-only** at config-binding time — no admin write actions
are added.

## 5. Domain model additions

### 5.1 New enum: `RailSelectionReason`

Package `zw.gov.mohcc.impilo.mushex.domain.enums`.

```java
public enum RailSelectionReason {
    EXPLICIT_PREFERRED,                  // caller's preferredRailAdapter is registered → used
    DEFAULT_NO_PREFERENCE,               // caller did not supply preferredRailAdapter → defaultRail
    DIRECT_GATEWAY_REQUESTED,            // caller asked directGatewayAllowed=true and policy permitted
    PREFERRED_UNAVAILABLE_FALLBACK,      // preferredRailAdapter is unknown/unregistered, allowFallback=true
    SAFETY_SWITCH_FORCED_SANDBOX,        // mushex.rail-selection.enabled=false → record-only sandbox
    REJECTED_UNKNOWN_RAIL,               // preferredRailAdapter does not match any AdapterType (caller error)
    REJECTED_UNAVAILABLE_NO_FALLBACK     // preferredRailAdapter unregistered AND allowFallback=false
}
```

### 5.2 New value record: `RailSelectionResult`

Package `zw.gov.mohcc.impilo.mushex.service.rail`.

```java
public record RailSelectionResult(
        AdapterType effectiveRail,           // the chosen, registered AdapterType
        AdapterType preferredRail,           // null when caller did not preference
        boolean fallbackApplied,             // true when effectiveRail != preferredRail because of unavailability
        boolean directGatewayRequested,      // mirrors the caller flag, after policy clamp
        RailSelectionReason reason,
        String reasonDetail                  // free-form human-readable, safe to log
) {}
```

A rejected selection is **not** modelled as a `RailSelectionResult`; it is thrown
as an `IllegalArgumentException` with a stable error code (see §10).

### 5.3 New policy interface and default impl

```java
public interface RailSelectionPolicy {
    RailSelectionResult select(RailSelectionRequest request);
}

public record RailSelectionRequest(
        String preferredRailAdapter,   // raw caller value; may be null/blank/unknown
        Boolean allowFallback,         // tri-state: null = "use policy default" (true)
        Boolean directGatewayAllowed,  // tri-state: null = "use policy default" (false)
        SourceType sourceType,
        java.math.BigDecimal amount,
        String currency,
        java.util.UUID facilityId,
        boolean isImpiloSimulation     // already known by PaymentIntentService
) {}
```

Default impl: `DefaultRailSelectionPolicy` (deterministic; see §6).

## 6. Selection algorithm (pseudocode)

```
INPUT: request, properties = mushex.rail-selection.*, adapterRegistry

# Step 0 — safety switch
if not properties.enabled:
    return SANDBOX, reason=SAFETY_SWITCH_FORCED_SANDBOX

# Step 1 — clamp Mode A request to policy
directGateway = request.directGatewayAllowed ?? false
if directGateway and not properties.allowDirectGateway:
    directGateway = false   # silently demote: never auto-enable Mode A in prod

# Step 2 — preferred rail parsing
preferred = null
if request.preferredRailAdapter is non-blank:
    try:
        preferred = AdapterType.valueOf(uppercase(request.preferredRailAdapter))
    except IllegalArgumentException:
        throw RailSelectionException(REJECTED_UNKNOWN_RAIL, request.preferredRailAdapter)

# Step 3 — explicit preference is registered
if preferred is not null and adapterRegistry.has(preferred):
    return preferred, reason=EXPLICIT_PREFERRED

# Step 4 — preferred but unavailable
if preferred is not null and not adapterRegistry.has(preferred):
    allowFallback = request.allowFallback ?? true
    if allowFallback and properties.allowSandboxFallback:
        return SANDBOX, preferred=preferred, fallbackApplied=true,
               reason=PREFERRED_UNAVAILABLE_FALLBACK
    else:
        throw RailSelectionException(REJECTED_UNAVAILABLE_NO_FALLBACK, preferred)

# Step 5 — no preference, direct gateway requested (Mode A path)
if directGateway:
    # Pick the first registered non-SANDBOX rail in a stable order:
    #   CARD_GATEWAY, BANK_TRANSFER, MOBILE_MONEY
    # This is deterministic by enum declaration order, not registration order.
    for candidate in [CARD_GATEWAY, BANK_TRANSFER, MOBILE_MONEY]:
        if adapterRegistry.has(candidate):
            return candidate, directGatewayRequested=true,
                   reason=DIRECT_GATEWAY_REQUESTED
    # No external rail available → fall through to default

# Step 6 — no preference, default
return properties.defaultRail, reason=DEFAULT_NO_PREFERENCE
```

## 7. Persistence — how the choice is recorded

### 7.1 In `payment_intents.metadata` (JSONB)

`PaymentIntentService.createIntent(...)` will, **after** parsing the inbound
`metadata` string, merge a new top-level object under the key `rail_selection`:

```json
{
  "patient": "P-001",
  "rail_selection": {
    "effective_rail": "SANDBOX",
    "preferred_rail": "MOBILE_MONEY",
    "fallback_applied": true,
    "direct_gateway_requested": false,
    "reason": "PREFERRED_UNAVAILABLE_FALLBACK",
    "reason_detail": "MOBILE_MONEY adapter is not registered; fell back to SANDBOX",
    "selected_at": "2026-05-12T12:34:56Z",
    "selection_version": "1"
  }
}
```

Why JSONB merge rather than a new column:

- The `metadata` column is already JSONB and is already used as the canonical
  free-form site for COSTA hand-off metadata (e.g. `impilo_simulation`,
  `simulation_outcome`, `payer_id`, `intent_type`). Adding one more
  reserved key is consistent with that pattern and requires **no migration**.
- The merge is idempotent: re-creating an intent with the same idempotency key
  returns the already-stored row unchanged. We never re-run selection.

If inbound metadata is `null` or blank we create a fresh object with only the
`rail_selection` key. If inbound metadata is non-JSON, we leave the original
string intact in `metadata` and instead publish the rail-selection payload only
on the outbox event (§7.2). A `WARN` log is emitted in that case.

### 7.2 In `event_outbox.payload`

The existing `INTENT_CREATED` outbox event already serialises a `Map<String,String>`.
We add three new keys, all derived from the `RailSelectionResult`:

```
effectiveRail          = result.effectiveRail.name()
preferredRail          = result.preferredRail?.name() ?? "NONE"
railSelectionReason    = result.reason.name()
```

Adding optional keys to an existing event is contractually safe per the platform
event-versioning rule: consumers MUST tolerate unknown keys (see
`docs/registry/README.md`).

## 8. SANDBOX / dev vs production behaviour

| Concern                                       | Dev / SANDBOX                        | Production (default)            |
| --------------------------------------------- | ------------------------------------ | ------------------------------- |
| `mushex.rail-selection.enabled`               | `true`                               | `true`                          |
| `mushex.rail-selection.defaultRail`           | `SANDBOX`                            | `SANDBOX`                       |
| `mushex.rail-selection.allowDirectGateway`    | `true` (so demos can exercise Mode A path) | `false` (operator must opt-in per env) |
| `mushex.rail-selection.allowSandboxFallback`  | `true`                               | `true` (operator may disable per env to force hard reject) |
| Effect when `impilo_simulation=true` metadata | Always selects `SANDBOX` regardless of preference, with `reason=SAFETY_SWITCH_FORCED_SANDBOX` (because credential checks are bypassed and we should not pretend to use a real rail) | Same |

Rationale: **production must never accidentally route to a live external gateway**
just because a caller passes `directGatewayAllowed=true`. The
`allowDirectGateway` property is the explicit safety lock.

## 9. Idempotency

`PaymentIntentService.createIntent(...)` already short-circuits when an
intent with the same `idempotencyKey` exists; this design preserves that
behaviour unchanged:

- Rail-selection happens **only** on the first (non-idempotent-hit) path.
- The second call with the same idempotency key returns the stored intent
  whose `metadata.rail_selection` reflects the *original* decision.
- The second call does **not** publish a new outbox event (consistent with
  current behaviour).
- The second call's `preferredRailAdapter` / `allowFallback` / `directGatewayAllowed`
  fields are ignored, with a `DEBUG` log noting the idempotency-replay. This is
  the same compatibility model used for `metadata` today.

Test coverage: see §13, "idempotency replay" case.

## 10. Error codes

| Code                              | HTTP | When |
| --------------------------------- | ---- | ---- |
| `RAIL_ADAPTER_UNKNOWN`            | 400  | `preferredRailAdapter` value is not a valid `AdapterType` enum. |
| `RAIL_ADAPTER_UNAVAILABLE`        | 400  | `preferredRailAdapter` is a valid enum but no adapter is registered for it, and `allowFallback=false`. |
| (no new code)                     | —    | Successful fallback to SANDBOX is **not** an error. It is a normal response with `rail_selection.fallback_applied=true`. |

All errors follow the existing `ApiResponse.error(code, message, status, correlationId)` envelope.

## 11. Backward compatibility

| Surface                                | Behaviour after change |
| -------------------------------------- | ---------------------- |
| `POST /mushex/v1/payment-intents` (existing callers omit new fields) | Identical observable behaviour, except `metadata.rail_selection` and three extra outbox keys appear. |
| `CreateIntentRequest` Java record       | Three new optional fields appended at the end. Existing positional-style construction in tests continues to work via a backward-compatible constructor (records get an extra explicit canonical constructor; existing `new CreateIntentRequest(sourceType, sourceId, amount, currency, facilityId, idempotencyKey, metadata)` callers compile through a compact secondary constructor). |
| `PaymentIntentService.createIntent(...)` | Adds one overload with `RailSelectionRequest`; existing 7-arg overload remains and internally calls the new overload with empty selection request → default policy path. |
| `PaymentIntentEntity`                    | No new column. `metadata` is extended only. |
| Database migrations                      | None. |
| Event consumers                          | New optional keys on `INTENT_CREATED`. Per `docs/registry/README.md`, unknown keys MUST be tolerated. |
| `AdapterType` enum                       | Unchanged. |
| `PaymentRailAdapter` SPI                 | Unchanged. |
| `AdapterRegistry` API                    | Adds one method: `boolean has(AdapterType)`. No removal or rename. |

## 12. OpenAPI changes (additive)

`contracts/openapi/mushex.openapi.yaml` → `components.schemas.CreateIntentRequest`:

```yaml
CreateIntentRequest:
  type: object
  required: [sourceType, sourceId, amount, idempotencyKey]
  properties:
    # ... existing fields unchanged ...
    preferredRailAdapter:
      type: string
      description: |
        Optional. Caller's preferred payment rail. Must match a registered
        AdapterType (MOBILE_MONEY, BANK_TRANSFER, CARD_GATEWAY, SANDBOX).
        When omitted, MusheX uses its configured default rail. See
        docs/design/g4-rail-selection-policy.md.
      enum: [MOBILE_MONEY, BANK_TRANSFER, CARD_GATEWAY, SANDBOX]
    allowFallback:
      type: boolean
      description: |
        Optional. When the preferredRailAdapter is unavailable, allow MusheX to
        fall back to the SANDBOX rail (when permitted by deployment config).
        Defaults to true.
      default: true
    directGatewayAllowed:
      type: boolean
      description: |
        Optional. Request that MusheX route directly to an external gateway
        adapter (Mode A per the dual-mode doctrine), rather than the default
        orchestration/SANDBOX path. Deployment configuration may override this
        to false in production environments. Defaults to false.
      default: false
```

The doc also adds a new `RailSelection` schema describing the metadata block
written into `payment_intents.metadata` and into the `INTENT_CREATED` event
payload, for consumer reference. No request or response top-level shapes change.

## 13. Tests

Required test cases (all in `services/mushex-service/src/test/.../PaymentIntentServiceTest.java`
and a new focused `DefaultRailSelectionPolicyTest.java`):

1. **Explicit preferred rail, registered** → `effectiveRail=preferred`,
   `fallbackApplied=false`, `reason=EXPLICIT_PREFERRED`, outbox keys present.
2. **Explicit preferred rail, unregistered, allowFallback=true** →
   `effectiveRail=SANDBOX`, `fallbackApplied=true`, `reason=PREFERRED_UNAVAILABLE_FALLBACK`.
3. **Explicit preferred rail, unregistered, allowFallback=false** → throws,
   error code `RAIL_ADAPTER_UNAVAILABLE`, no intent saved, no outbox event saved.
4. **Unknown rail string** → throws, error code `RAIL_ADAPTER_UNKNOWN`.
5. **No preference** → `effectiveRail=defaultRail`, `reason=DEFAULT_NO_PREFERENCE`.
6. **directGatewayAllowed=true, allowDirectGateway=true** → picks first
   registered non-SANDBOX rail in `[CARD_GATEWAY, BANK_TRANSFER, MOBILE_MONEY]`,
   `reason=DIRECT_GATEWAY_REQUESTED`.
7. **directGatewayAllowed=true, allowDirectGateway=false (prod default)** →
   silently demotes to `defaultRail`, `reason=DEFAULT_NO_PREFERENCE`.
8. **Safety switch off** → `effectiveRail=SANDBOX`,
   `reason=SAFETY_SWITCH_FORCED_SANDBOX`.
9. **Impilo simulation metadata present** → forced SANDBOX with
   `reason=SAFETY_SWITCH_FORCED_SANDBOX`, regardless of preference.
10. **Idempotency replay** — second call with same key + different
    `preferredRailAdapter` returns the original intent and does **not**
    re-run selection or republish the event.
11. **Outbox payload assertions** — `effectiveRail`, `preferredRail`,
    `railSelectionReason` present and correctly populated.
12. **Metadata merge** — given inbound `metadata={"patient":"P-001"}`, the
    saved entity's metadata contains both `patient` and `rail_selection`.

## 14. Implementation order (small commits)

1. **Domain only.** Add `RailSelectionReason` enum, `RailSelectionResult` record,
   `RailSelectionRequest` record, `RailSelectionPolicy` interface,
   `DefaultRailSelectionPolicy` implementation. Add `MushexProperties.RailSelection`
   nested config. Add `AdapterRegistry.has(AdapterType)`. Unit-test the policy
   in isolation (no DB, no Spring context). **No wiring into `PaymentIntentService`
   yet** — this commit is purely dead code from the orchestrator's perspective.
2. **Wire into `PaymentIntentService` with default inputs.** Add an overloaded
   `createIntent(..., RailSelectionRequest)` that delegates from the existing 7-arg
   version. Compute `RailSelectionResult` after credential/contract checks and
   before `intent.setMetadata(...)`. Merge into `metadata` JSONB. Add three keys
   to the `INTENT_CREATED` outbox payload. Keep behaviour stable for existing
   tests (the default path with no preferred rail still produces a stable shape).
3. **Extend `CreateIntentRequest` and `PaymentIntentController`.** Three new
   optional fields, passed through to the new overload. Update OpenAPI.
4. **Integration tests.** Extend `PaymentIntentServiceTest` with the cases in
   §13. Add a new `PaymentIntentControllerTest` case asserting the
   preferred-rail path end-to-end (using `MockMvc`).
5. **Documentation.** Update `docs/audits/costa-mushex-experience-layer-wiring-audit.md`
   G-4 row to "Implemented (Stage 3.5)". Update
   `docs/doctrine/mushex-gateway-neutrality.md` deferred-row to point at this design.
   Update `docs/architecture/experience-bff-downstream-route-map.md` only if the
   wire shape of any BFF route changes (it does not — BFF passes the body
   through). Add a one-line entry in `contracts/openapi/README.md` if such a file
   exists.

Each commit is independently reviewable and revertable.

## 15. Future hooks (explicitly out of scope here)

These are *not* part of G-4 but the design leaves room for them:

- **Tenant/facility default rail.** A future `tenant_payment_preferences` table
  could supply the `defaultRail` per tenant. `DefaultRailSelectionPolicy` already
  reads only its input — swap in a `TenantAwareRailSelectionPolicy` later.
- **Real-time adapter health.** When adapters become real (not stubs), add a
  `boolean isAvailable()` to `PaymentRailAdapter` and consult it in step 3 of the
  algorithm. The current design treats "registered" as "available" because the
  adapters are stubs.
- **Attempt-time enforcement.** When `PaymentAttemptEntity` creation is wired
  to the registry, it should read `metadata.rail_selection.effective_rail`
  rather than re-deriving the choice.
- **Operator-visible UI.** The read-only `/finance/mushex-platform` page can
  surface the rail-selection columns from the outbox stream once that view is
  built. That stays out of G-4.

---

**End of design.**
