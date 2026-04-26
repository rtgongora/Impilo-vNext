# Mobile parity governance (Tier-1)

This repository treats **`ui/one-ui-shell`** as the canonical web baseline, and defines **Tier-1 parity** as a bounded set of citizen + provider journeys that must stay aligned across:

- **Web** (`ui/one-ui-shell` routes + Experience runtime)
- **Mobile Citizen** (`apps/mobile/citizen-app`)
- **Mobile Provider** (`apps/mobile/provider-app`)
- **BFF** (`services/experience-bff`)
- **Contracts** (`contracts/openapi`)

## Tier-1 source of truth

- Canonical route inventory: `[ui/one-ui-shell/src/lib/routes.ts](C:\\Users\\rgong\\Impilo-vNext\\ui\\one-ui-shell\\src\\lib\\routes.ts)`
- Generated Tier-1 parity matrix:
  - `[docs/mobile/full-mobile-parity-matrix.md](C:\\Users\\rgong\\Impilo-vNext\\docs\\mobile\\full-mobile-parity-matrix.md)`
  - `[docs/mobile/full-mobile-parity-matrix.json](C:\\Users\\rgong\\Impilo-vNext\\docs\\mobile\\full-mobile-parity-matrix.json)`
- Generator: `[tools/parity/generate-mobile-parity-matrix.mjs](C:\\Users\\rgong\\Impilo-vNext\\tools\\parity\\generate-mobile-parity-matrix.mjs)`

## Rules (to prevent divergence)

- **If you change Tier-1 web routes**, you must update the generator mapping and regenerate the matrix.\n+- **If you add/change a Tier-1 mobile API call**, you must ensure:\n+  - the corresponding BFF route exists and is owned explicitly (no accidental proxy swallowing)\n+  - the contract (`contracts/openapi/*.yaml`) documents the endpoint\n+- **If you add a Tier-1 user-visible flow**, add:\n+  - a Maestro smoke path under `apps/mobile/maestro/flows/`\n+  - a Playwright compose smoke path for web if it’s a web-exposed Tier-1 journey\n+
## Tier-3 hubs: what “parity” means

Tier-3 “professional plane hubs” on mobile are allowed to be **catalog + navigation metadata only** (a curated list of `web_path` deep links aligned with `ui/one-ui-shell`). This keeps parity honest without forcing every web workflow into native mobile.

When we *do* deepen a hub, we treat it as a **vertical**:

- **In-app module (Approach A)**: Native screen + BFF-owned mobile endpoint + OpenAPI schema + Maestro smoke.
- **Deep link only (Approach D)**: Hub stays metadata; workflows are tracked as separate epics and explicitly out-of-scope for native parity until promoted.

Current Gap 3 vertical:

- **Facility reports**: implemented as a native module under provider “Tools → Reports”, backed by `/internal/v1/mobile/provider/reports/*` and a Maestro smoke flow.
## How to regenerate parity artifacts

From repo root:

```bash
node tools/parity/generate-mobile-parity-matrix.mjs
```

## Mobile E2E smoke (Maestro)

Flows live under `[apps/mobile/maestro/flows](C:\\Users\\rgong\\Impilo-vNext\\apps\\mobile\\maestro\\flows)`.\n+
Local run:

```bash
maestro test apps/mobile/maestro/flows
```

## CI posture

The CI wiring is currently **informational** for compose E2E and Maestro. Once stabilized, the next step is to flip them to required checks.\n+
