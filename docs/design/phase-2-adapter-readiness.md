# Phase 2 — Adapter readiness design

| Field | Value |
| ----- | ----- |
| Status | Implemented (Phase 2 batch) |
| Doctrine | [`docs/doctrine/mushex-gateway-neutrality.md`](../doctrine/mushex-gateway-neutrality.md) |
| Audit | [`docs/audits/costa-mushex-experience-layer-wiring-audit.md`](../audits/costa-mushex-experience-layer-wiring-audit.md) |
| Related design | [`docs/design/g4-rail-selection-policy.md`](g4-rail-selection-policy.md) |
| Release readiness | [`docs/release/mushex-costa-finance-phase-1-release-readiness.md`](../release/mushex-costa-finance-phase-1-release-readiness.md) |

## 1. Purpose

Add a conservative, read-only **adapter readiness model** so operators can see exactly which payment-rail adapters are wired and which are stubs, **without** introducing live credentials, real-time health probes, or any path to unsafe money movement.

The current `RailSelectionPolicy` (G-4) treats every *registered* adapter as available. That is acceptable for the structural foundation but does not tell an operator whether `MOBILE_MONEY` is actually configured for live routing or whether it is a `@Component` stub that returns `PENDING` forever. Phase 2 closes that visibility gap.

## 2. Non-goals (deferred)

The following are explicitly **out of scope** for Phase 2 and remain reserved for later phases:

- Real payment-provider credential handling.
- Round-trip health probing against a live provider.
- Tenant-/facility-scoped readiness or routing.
- Provisioning UI for adding new adapters.
- Any write action that activates a rail, blocks a card, or moves money.
- A `DEGRADED` state driven by runtime telemetry (the enum value is reserved but not produced).

## 3. State machine

The model has six states, of which five are produced today:

| State | Meaning | Live capable? | Sandbox capable? |
| ----- | ------- | ------------- | ---------------- |
| `NOT_REGISTERED` | No `PaymentRailAdapter` Spring bean for this `AdapterType`. | false | false |
| `DISABLED` | A bean exists but the operator has explicitly disabled the rail. | false | false |
| `READY_SANDBOX` | The `SANDBOX` rail is enabled. **Only the SANDBOX rail can reach this state.** | false | true |
| `CREDENTIALS_MISSING` | A real-money rail is enabled but the operator has not declared that credentials are configured. | false | false |
| `READY_LIVE` | A real-money rail is enabled and the operator declares credentials are configured. | true | false |
| `DEGRADED` *(reserved)* | A future state for runtime-health feedback. Not produced today. | n/a | n/a |

The state is **derived deterministically** from `AdapterRegistry` presence and `MushexProperties` flags. There is no asynchronous probing, no caching, and no external network call.

## 4. Configuration surface

New conservative flags under `MushexProperties.Adapters`:

```yaml
mushex:
  adapters:
    sandbox:
      enabled: true              # existing
      simulate-delay-ms: 0       # existing
    mobile-money:
      enabled: false             # NEW — operator must explicitly enable
      credentialsConfigured: false  # NEW — operator declares creds wired
    bank-transfer:
      enabled: false             # NEW
      credentialsConfigured: false  # NEW
    card-gateway:
      enabled: false             # NEW
      credentialsConfigured: false  # NEW
```

Defaults are intentionally **off** for every real-money rail. The production posture is:

- `SANDBOX` → `READY_SANDBOX` (sandbox-only, never live capable).
- `MOBILE_MONEY` → `DISABLED`.
- `BANK_TRANSFER` → `DISABLED`.
- `CARD_GATEWAY` → `DISABLED`.

To make a real-money rail show as `READY_LIVE` an operator must (a) set `enabled=true` and (b) set `credentialsConfigured=true` after wiring real secrets via their secret-management system. The boolean is a self-attestation, not a credential. The application **never reads credentials** to compute readiness.

## 5. Architecture (additive)

```
PaymentRailAdapter (unchanged SPI)
       │
       ▼
AdapterRegistry (unchanged) ──── AdapterRegistry.has(AdapterType) (added in Stage 3.5)
                                                       │
                                                       │
       ┌───────────────────────────────────────────────┘
       ▼
AdapterReadinessService  ◀──── MushexProperties.Adapters.{mobileMoney, bankTransfer, cardGateway, sandbox}
       │
       ▼
AdapterReadiness records (read-only DTO)
       │
       ▼
GET /mushex/v1/platform/adapter-readiness   (AdapterReadinessController)
       │
       ▼  (transparent proxy with trust-header pass-through)
GET /internal/v1/finance/mushex-platform/adapter-readiness   (FinanceMushexPlatformController)
       │
       ▼
useMushexPlatformAdapterReadiness  ──▶  /finance/mushex-platform page (read-only table)
```

Crucially, the `PaymentRailAdapter` SPI is **unchanged**. Phase 2 introduces a sibling service rather than expanding the interface. Reasons:

1. Readiness is environment-scoped (config + registry), not adapter-scoped.
2. The four existing adapter implementations need no edits; nothing they do today changes.
3. A future adapter that wishes to contribute an opinion can implement its own `isAvailable()` once a `DEGRADED` state is wired through the readiness service.

## 6. New / changed files

| File | Kind | Why |
| ---- | ---- | --- |
| `services/mushex-service/.../domain/enums/AdapterReadinessStatus.java` | New enum | Six states, of which five are produced today. |
| `services/mushex-service/.../service/rail/AdapterReadiness.java` | New record | Read-only DTO: `(adapterType, status, liveCapable, sandboxCapable, detail)`. |
| `services/mushex-service/.../service/rail/AdapterReadinessService.java` | New service | Deterministic readiness computation from registry + properties. |
| `services/mushex-service/.../api/AdapterReadinessController.java` | New controller | `GET /mushex/v1/platform/adapter-readiness` returning `ApiResponse<List<AdapterReadiness>>`. |
| `services/mushex-service/.../config/MushexProperties.java` | Updated | Added nested `RealRail` class and three real-money rail configs; existing `Sandbox` config unchanged. |
| `services/experience-bff/.../client/MushexServiceClient.java` | Updated | Added `platformAdapterReadiness()` passthrough. |
| `services/experience-bff/.../controller/FinanceMushexPlatformController.java` | Updated | Added `GET /adapter-readiness` proxying to MusheX. |
| `ui/one-ui-shell/src/hooks/queries/useMushexPlatformAdmin.ts` | Updated | Added `useMushexPlatformAdapterReadiness()` and `AdapterReadinessRow` type. |
| `ui/one-ui-shell/src/app/finance/mushex-platform/page.tsx` | Updated | Replaced static "Platform routing & gateway readiness" copy with a live table. No write actions added. |

No file removed, no SPI changed, no schema changed.

## 7. Safety properties

This phase upholds the global non-negotiables stated in the roadmap:

1. **No silent fabrication.** The readiness payload either succeeds (live data) or surfaces an honest error state in the UI. The service never invents a `READY_LIVE` row.
2. **No unsafe money movement.** Readiness is read-only; no write endpoints added.
3. **No accidental production rail routing.** Production defaults keep every real-money rail at `DISABLED`. The `RailSelectionPolicy` is unchanged and continues to default to `SANDBOX`.
4. **No breaking OpenAPI changes.** The new endpoint follows the convention of other `/mushex/v1/platform/*` paths (which are intentionally not in the public OpenAPI spec); no existing schema is modified.
5. **No DB migration.** No persistence at all.
6. **No credential handling.** The application boundary never sees a secret. Operators self-attest via a boolean.
7. **No deletion** of sidecars or legacy routes.
8. **No new shell / API client / state library / auth model.**
9. **Idempotency preserved.** The endpoint is a pure read; no idempotency surface to regress.

## 8. Test plan & results

`AdapterReadinessServiceTest` covers nine cases:

1. `describeAll` returns one row per `AdapterType` in stable enum order.
2. A not-registered adapter is reported as `NOT_REGISTERED` (not omitted).
3. `SANDBOX` enabled → `READY_SANDBOX`, `liveCapable=false`, `sandboxCapable=true`.
4. `SANDBOX` disabled → `DISABLED`.
5. Real-money rail defaults → `DISABLED` (asserts the production posture).
6. Real-money rail enabled without credentials → `CREDENTIALS_MISSING`, never `liveCapable`.
7. Real-money rail enabled with credentials configured → `READY_LIVE`, `liveCapable=true`, detail string still reminds operators that the underlying adapter is a stub.
8. Default config snapshot → exactly zero `liveCapable` rails.
9. Structural check: the readiness service runs for every combination of `enabled` × `credentialsConfigured` without exception and never references a credential field.

Test result on the change-set: **147 / 147 pass** in the full `services/mushex-service` suite (was 138 before Phase 2; +9 readiness tests).

The new BFF passthrough is one line over an existing, well-exercised proxy pattern; no new BFF test was added in this batch (the seven sibling passthroughs have no controller-level tests either, and adding the convention should happen in a separate batch covering the whole controller).

## 9. UX

`/finance/mushex-platform` now contains a single new "Platform routing & gateway readiness" table:

| Column | Source |
| ------ | ------ |
| Rail | `adapterType` |
| Readiness | `status` mapped to a chip with a colour palette: green (READY_LIVE), sky (READY_SANDBOX), amber (CREDENTIALS_MISSING), slate (DISABLED / NOT_REGISTERED), orange (DEGRADED — reserved). |
| Capability | "Live capable" / "Sandbox only" / "Not available" derived from `liveCapable` / `sandboxCapable`. |
| Detail | Plain-language `detail` string returned by the service. |

The page remains read-only. There is no "enable rail" or "set credentials" button. No `apiClient.post/put/patch/delete` is added.

## 10. Backward compatibility

- Configuration: existing deployments that do not set the new properties keep the production-safe defaults.
- Wire shape: a brand-new path; nothing pre-existing changes.
- Code: `RailSelectionPolicy` is **not** changed by Phase 2. The next phase (Phase 3 — attempt-time enforcement) will consume the same metadata that G-4 already persists; Phase 2 does not preempt that contract.
- `PaymentRailAdapter` SPI: unchanged.
- `AdapterType` enum: unchanged.
- Existing `/mushex/v1/platform/*` endpoints: unchanged.

## 11. Future hooks (deferred — not implemented here)

1. **`DEGRADED` state from runtime telemetry.** Once any adapter implements a real `isAvailable()` signal, `AdapterReadinessService` would fold that signal in. Today the enum value exists but is never produced.
2. **Tenant-/facility-scoped readiness.** Would require a tenant-store model and governance design.
3. **Provisioning UI.** Out of scope; gated by Phase 4 safety design.
4. **Drill-down per rail.** A detail page showing per-rail policy decisions over the last N intents could be added cheaply later by reusing the rail-selection metadata already on `payment_intents`.

## 12. Doctrine alignment

This is fully consistent with `docs/doctrine/mushex-gateway-neutrality.md`:

- *Gateway-neutral by design* — readiness shows which gateways are wired without changing the routing contract.
- *Gateway-capable by default* — the SANDBOX rail remains `READY_SANDBOX` so MusheX never blocks care while operators are wiring real rails.
- *Health-focused always* — no Phase 2 work changes how a clinical/patient surface behaves.
