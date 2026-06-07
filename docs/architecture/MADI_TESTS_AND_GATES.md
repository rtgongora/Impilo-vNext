# MADI Tests and Quality Gates

## Backend

| Gate | Location |
|------|----------|
| Golden contract IT | `services/madi-service/.../MadiGoldenContractIT.java` |
| Unit tests | `DonorServiceTest`, `BloodOrderServiceTest`, `TransfusionServiceTest`, `BloodUnitServiceTest` |

Run:

```bash
cd services/madi-service && ./mvnw test
```

## Mobile

| Gate | Location |
|------|----------|
| Citizen `madiService` unit tests | `apps/mobile/citizen-app/src/services/madiService.test.ts` |
| Provider `madiService` unit tests | `apps/mobile/provider-app/src/services/madiService.test.ts` |

Run:

```bash
cd apps/mobile/citizen-app && pnpm exec vitest run src/services/madiService.test.ts
cd apps/mobile/provider-app && pnpm exec vitest run src/services/madiService.test.ts
```

Or full mobile suite:

```bash
cd apps/mobile && pnpm test
```

## Parity documentation gates

| Script | Purpose |
|--------|---------|
| `scripts/frontend/generate-parity-docs.mjs` | Embeds MADI CAPABILITIES entries (9 capabilities) |
| `scripts/guard/check-mobile-parity.sh` | Repository mobile parity guard (when BFF routes registered) |
| `scripts/guard/check-backend-frontend-parity.sh` | Backend–frontend alignment |

Regenerate parity docs:

```bash
node scripts/frontend/generate-parity-docs.mjs
```

## Core transaction contract

Blood-related types added to `contracts/core-transaction.ts`:

- `BLOOD_DONATION`
- `BLOOD_ORDER`
- `TRANSFUSION`
- `HAEMOVIGILANCE`

## CI / VM quality gates

Before preview deploy, run on VM:

```bash
bash scripts/pipeline/run-local-quality-gates.sh
bash scripts/pipeline/cursor-local-feedback.sh
```

## Acceptance checklist (mobile wave)

- [ ] Citizen hub reachable from Personal → Blood Donor
- [ ] Provider tools reachable from Clinical Tools MADI tabs
- [ ] `madiService` tests pass in both apps
- [ ] BFF mobile routes proxy to `madi-service` (BFF implementation wave)
- [ ] No production mocks on MADI mobile paths
- [ ] Trust headers present on all mobile API calls

## Known gaps (honest)

- Web `/madi/*` routes not yet in `one-ui-shell` route registry
- BFF mobile `/internal/v1/mobile/*/madi/*` controllers may require separate BFF wave — mobile clients are ready
- Processing, central bank, and dashboard capabilities are web/ops-first; mobile marked `no` in parity matrix
