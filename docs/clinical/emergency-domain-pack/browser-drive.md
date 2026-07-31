# Emergency pack — local browser drive

Definition-of-done for experience surfaces: drive through a real browser against a live local
estate, with screenshots under `reports/journeys/emergency-pack-w15/` (W15) and
`reports/journeys/emergency-pack-w18/` (W18 full-chain / J-EP-B2).

## What the drive proves

### W15 — `e2e/emergency-pack-w15.spec.ts`

| Surface | Assertion |
|---|---|
| `/clinical/emergency/command` | Deep link holds (no bounce to `/home`); board reads live pct tallies |
| `/clinical/emergency/activation` | POST opens a real `pct.emergency_episode` row and lands on its spine |
| `…/spine/{id}/observation` | Panel distinguishes empty from unreadable |
| `…/spine/{id}/disposition` | Form renders for an undisposed episode (absence ≠ unreadability) |
| `/clinical/emergency/pre-arrival` | Live ED projection reads |
| `/clinical/emergency/analytics` | Named absence of measures — no zeros |
| `/work/mental-health*` | Clinical MH workspace and restraint backlog render |

### W18 — `e2e/emergency-pack-w18.spec.ts` (J-EP-B2)

Single full-chain tour: command board → activation → observation → disposition → pre-arrival →
analytics (live keys **or** named absences) → MH referrals + restraint review. Screenshots
`01-…` through `08-…` under `reports/journeys/emergency-pack-w18/`.

Companion API proofs: `scripts/runtime-proof/emergency-episode-journeys.sh` (J-EP-5..9).
Pathway content proofs remain in `emergency-pathway-integrity.sh` (J-EP-1..4).

If the estate is not up, the suite **skips** rather than passing.

## Boot the estate

```bash
# Build jars once (from the worktree root)
cd services && mvn -q -pl pct-service,mental-health-service,experience-bff -am -DskipTests package

# Boot throwaway postgres+redis, pct, mental-health, BFF, identity harness, next
bash scripts/dev/emergency-drive-rig.sh up
bash scripts/dev/emergency-drive-rig.sh status
```

The rig:

- uses non-default ports (`15991` postgres, `16991` redis, `29388` pct, `29397` MH, `8160` BFF,
  `8161` identity harness, `3007` next) so it never collides with another lane's rig
- points pct and the BFF at that redis (without this, pct hangs on `:6379` and every BFF POST
  500s in the idempotency filter)
- sets `impilo.security.disable-oauth-for-tests=true` on pct/MH and `allow-anonymous` on the
  BFF — this box has no Keycloak; the token path is covered by the services' own tests
- answers only the four professional-identity reads via
  `scripts/dev/browser-drive-identity-harness.mjs`; every emergency read and write is proxied
  unmodified to the real BFF

Tear down with `bash scripts/dev/emergency-drive-rig.sh down`.

## Run the drives

```bash
cd ui/one-ui-shell
export PLAYWRIGHT_SKIP_WEBSERVER=1 PLAYWRIGHT_BASE_URL=http://localhost:3007

# W15 surface tour
npx playwright test e2e/emergency-pack-w15.spec.ts --project=chromium --workers=1

# W18 full-chain (J-EP-B2)
npx playwright test e2e/emergency-pack-w18.spec.ts --project=chromium --workers=1

# J-EP-5..9 API spine (against the same BFF)
cd ../..
bash scripts/runtime-proof/emergency-episode-journeys.sh
```

Screenshots: `reports/journeys/emergency-pack-w15/` and `reports/journeys/emergency-pack-w18/`.

## Defects this drive found (and fixed)

1. **Deep-link bounce** — `AuthGuardProvider` acted on an empty store before `StoreHydrator`
   restored the session, and on a brief citizen-only heuristic before identity reads settled.
   Fixed with `sessionRestoreAttempted` and `resolveWorkRouteVisibility`.
2. **jsonb varchar bind** — `entry_context_json` (and sibling columns) lacked
   `@JdbcTypeCode(SqlTypes.JSON)`, so opening an episode failed against real Postgres. Guard:
   `scripts/guard/check-jsonb-column-binding.sh`.
3. **Disposition absence as 404** — GET disposition threw NOT_FOUND when nothing was recorded,
   which the UI honestly treated as unreadability and refused to show the form. Fixed to return
   empty data so absence and unreadability stay distinct.
