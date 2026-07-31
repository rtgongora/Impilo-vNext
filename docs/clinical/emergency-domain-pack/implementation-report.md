# Emergency pack — implementation report (W15–W18)

## Lane

Worktree branch `worktree-emergency-pack-w15-w19`. Engineering on 235; no production deploy;
verification is local quality gates + browser drive against `emergency-drive-rig.sh`.

## Waves landed

| Wave | Result |
|------|--------|
| Phase 0 | Worktree, baselines, lease corrections (IT→Test, MH contract peer-authored, MH undeployed) |
| W15 | BFF gaps, honesty envelope, primitives, command board, remaining routes, MH UI, browser drive |
| W16a | Shared IITT JSON corpus + TeaVM spike — **GO** (ResourceSupplier plugin, 23/23, ~149 KiB, 49 ms) |
| W16b | Feature SW, IndexedDB outbox (mobile `QueuedOperation`), Tier-B offline triage, prod Playwright |
| W17 | Theatre-pattern projection + DSEC mapping + Rito after-action link types |
| W18 | Ten pack docs, honest-gap register, J-EP extension, consolidated browser drive |

## Defects found by real drives (kept)

1. Deep-link bounce before session restore — fixed.
2. jsonb bound as varchar — fixed + guard.
3. Disposition GET 404-as-unreadable — fixed to empty 200.
4. TeaVM resources omitted without ResourceSupplier — fixed via plugin.
5. `next build` requires gateway env for PLAYWRIGHT_PROD_BUILD — documented in W16b.

## Not claimed

- Full-boot `FULL_BOOT_PASS` / public preview deploy
- Mental-health runtime reachability
- W14 content completeness
- Fabricated Helm digests
