# W16b — Emergency offline writes

## What shipped

| Piece | Location |
|-------|----------|
| Feature-scoped SW (scope `/`) | `ui/one-ui-shell/public/impilo-sw.js` |
| Per-feature enrollment manifest | `ui/one-ui-shell/public/offline-feature-manifest.json` |
| IndexedDB outbox (`QueuedOperation` = mobile contract) | `ui/one-ui-shell/src/lib/offline/` |
| `api-client` offline enqueue + flush (Idempotency-Key preserved) | `ui/one-ui-shell/src/lib/api-client.ts` |
| Staleness wiring (`CACHED_OFFLINE`) | `src/lib/offline/staleness.ts` + ED visit page |
| Tier-B offline triage | `NOT_TRIAGEABLE_OFFLINE` — never invents an acuity |
| Prod Playwright spec | `e2e/emergency-offline-w16b.spec.ts` (`PLAYWRIGHT_PROD_BUILD=1`) |

## Kill switch

Navigate with `?sw=off` — the worker unregisters and clears Impilo caches.

## Tier stance (after W16a GO + Tier A preview)

W16a TeaVM spike was **GO**. Offline ED triage now:

1. **Of-record write** — still Tier B: `NOT_TRIAGEABLE_OFFLINE`; Complete triage stays disabled.
2. **Advisory preview** — Tier A: TeaVM `WhoIittEngine` via `/emergency/iitt-engine.js` and
   `OfflineIittPreviewPanel` (presentation only; no TypeScript criteria).

Guard: `check-no-ts-clinical-logic.sh` must stay green.

## Prove

```bash
cd ui/one-ui-shell
npm test -- src/lib/offline/__tests__/offlineQueue.test.ts
PLAYWRIGHT_PROD_BUILD=1 npx playwright test e2e/emergency-offline-w16b.spec.ts
```
