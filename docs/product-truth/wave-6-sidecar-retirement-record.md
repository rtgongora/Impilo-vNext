# Wave 6 sidecar retirement record

> **Program scope:** Tier A RR-flagged packages with proven absorption into `ui/one-ui-shell`.
> **Date:** 2026-06-08
> **Doctrine:** One unified experience shell — sidecars are not independently deployed in preview or full-boot builds.

## Retired (Wave 6)

| Ledger ID | Sidecar path | Canonical replacement | Evidence |
| --------- | ------------ | --------------------- | -------- |
| RR-01 | `ui/mushex-finance-console` | `/finance/ledger`, `/finance/settlements`, `/finance/reconciliation`, `/finance/refunds`, `/finance/costa`, `/finance/mushex-platform` | Parity audit; not in preview helm; removed from `ui/package.json` workspaces and full-boot `build_required` |
| RR-02 | `ui/mushex-ops-console` | `/finance/mushex-platform`, `/finance/payer-ops`, `/finance/payer-claims`, `/admin/audit/**` | Same |
| RR-03 | `ui/mushex-payer-portal` | `/finance/payments`, `/finance/payer-ops`, `/finance/payer-claims`, `/finance/refunds` | Same |
| RR-05 | `ui/ehr` | `/ehr/**` route family in `ui/one-ui-shell` | Same; already `retired sidecar path` in sidecar ledger |

### Actions taken

1. **`config/full-boot-service-classification.yml`** — entries reclassified to `deprecated_retired`, `build_required: false`, `deployment_lane: doctrine_only`.
2. **`scripts/full-boot/generate-full-boot-artifacts.mjs`** — `WAVE_6_RETIRED_SIDECARS` set prevents regeneration from re-enabling builds.
3. **`reports/full-boot/build-targets.json`** — regenerated; retired sidecars excluded from required builds.
4. **`ui/package.json`** — workspaces and `dev:ehr` script removed for retired packages (folders kept with `DEPRECATED.md`).
5. **`ui/one-ui-shell/src/lib/sidecar-retirement-ledger-v2.ts`** — MusheX entries promoted to `retired sidecar path`.
6. **`docs/retirement/retirement-readiness-ledger.md`** — RR-01, RR-02, RR-03, RR-05 → `retired`.
7. **`scripts/guard/check-retired-sidecars-full-boot.sh`** — guard blocks retired sidecars from re-entering full-boot build matrix.

### Folders retained (not deleted)

Physical folders remain on disk with `DEPRECATED.md` for audit trail and reference parity. They are **not** npm workspaces and **not** full-boot build targets.

## Held (not retired in Wave 6)

| Tier | Ledger ID | Path | Reason held |
| ---- | --------- | ---- | ----------- |
| D | RR-04 | `ui/experience` | Legacy web-shell fork — separate GAP-010 convergence track; not Tier A MusheX scope |
| D | RR-06 | Legacy mobile-citizen wallet routes | Backend/mobile tail — not a UI sidecar workspace |
| D | RR-07 | `costa-console` (if present) | Flagged only; no `DEPRECATED.md` in repo; not in Wave 6 Tier A list |
| D | — | `ui/ops-docs` | Tier D ops-docs — partial absorption only (`partially absorbed into Experience`); `blockerContract` on document issue/print workflows |

## Verification

```bash
bash scripts/guard/check-retired-sidecars-full-boot.sh
bash scripts/build/discover-build-targets.sh
# Expect: mushex-finance-console, mushex-ops-console, mushex-payer-portal, ehr absent from build-targets.json
```

## Cross-references

- [`docs/retirement/retirement-readiness-ledger.md`](../retirement/retirement-readiness-ledger.md)
- [`docs/audits/phase-7-retirement-parity-audit.md`](../audits/phase-7-retirement-parity-audit.md)
- [`ui/one-ui-shell/src/lib/sidecar-retirement-ledger-v2.ts`](../../ui/one-ui-shell/src/lib/sidecar-retirement-ledger-v2.ts)
