# vNext Environment Target Topology

Minimal topology map for the **full pipeline** (names fixed now; hosts activated progressively).

```text
                    ┌─────────────────────────────────────┐
                    │  11. shared platform services       │
                    │  (identity, obs, registry policy)   │
                    └──────────────────┬──────────────────┘
                                       │
     ┌─────────────────────────────────┼─────────────────────────────────┐
     │                                 │                                 │
     ▼                                 ▼                                 ▼
┌─────────────┐              ┌─────────────────┐              ┌──────────────────┐
│ 10. prod    │              │ 9. staging      │              │ 8. prod-sim lab  │
└─────────────┘              └─────────────────┘              └──────────────────┘
                                       │
                              ┌────────┴────────┐
                              ▼                 ▼
                    ┌─────────────────┐  ┌──────────────────────────┐
                    │ 7. full-integ   │  │ 6. cross-surface ctrl    │
                    │    sandbox      │  │    (web+mobile journeys) │
                    └────────┬────────┘  └────────────┬─────────────┘
                             │                         │
              ┌──────────────┴──────────────┐          │
              ▼                             ▼          ▼
    ┌──────────────────┐          ┌─────────────────────────────┐
    │ 5. web-test      │          │ 1. impilo-web-preview       │
    │    sandbox       │          │    41.57.127.235:2276       │
    └──────────────────┘          │    http://41.57.127.235     │
                                  └──────────────┬──────────────┘
                                                 │ API consumed by
                    ┌────────────────────────────┼────────────────────────────┐
                    ▼                            ▼                            ▼
         ┌────────────────────┐    ┌────────────────────┐    ┌─────────────────────┐
         │ 2. mobile-preview  │    │ 3. mobile-android  │    │ 4. mobile-ios       │
         │    control         │    │    sandbox (218)   │    │    sandbox / EAS    │
         │    (planned)       │    │    Maestro/KVM     │    │    (planned)        │
         └────────────────────┘    └────────────────────┘    └─────────────────────┘
```

## Active today (2026-06-27)

| Environment | Host | Relationship |
|-------------|------|--------------|
| **impilo-web-preview** | `41.57.127.235` | Hosts preview API, k3s, engineering control |
| **impilo-mobile-android-sandbox** | `41.57.127.218` | Consumes `http://41.57.127.235`; no backend |

> **Not a replacement model.** Maestro VM does not host backend, web preview, full integration, production simulation, or production.

## Operator entrypoints

| Environment | SSH | Repo |
|-------------|-----|------|
| Web preview | `ssh -p 2276 robert@41.57.127.235` | `/opt/impilo/repos/Impilo-vNext` |
| Android sandbox | `ssh facility@41.57.127.218 -p 2027` | `/opt/impilo/repos/Impilo-vNext` |

GitHub `rtgongora/Impilo-vNext` is the sync layer across all environments.

## Related docs

- [`VNEXT_ENVIRONMENT_LADDER.md`](./VNEXT_ENVIRONMENT_LADDER.md)
- [`VNEXT_TESTING_STRATEGY_BY_ENVIRONMENT.md`](./VNEXT_TESTING_STRATEGY_BY_ENVIRONMENT.md)
- [`DUAL_VM_OPERATING_MODEL.md`](./DUAL_VM_OPERATING_MODEL.md)
