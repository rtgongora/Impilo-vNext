# Doctrine Compliance Gate

Script: `bash scripts/guard/check-doctrine-compliance.sh`

## Blocking checks (now)

| Check | Evidence |
|-------|----------|
| Seven plane docs exist | `docs/architecture/planes/01..07` |
| Health OS doctrine | `docs/doctrine/health-os-doctrine.md` |
| Core transaction doctrine | `docs/doctrine/CORE_TRANSACTION_DOCTRINE.md` |
| No `ui/experience` fork | GAP-010 convergence |

## Advisory checks (now)

| Check | Notes |
|-------|-------|
| Compliance matrix generated | `VNEXT_DOCTRINE_COMPLIANCE_MATRIX.md` |
| Required services not in slice | Expected until full boot |

## Human review (not automated)

- UI/experience doctrine per route
- Core transaction journey wiring per feature
- Mobile/web parity exceptions (document in `config/parity-allowlist.yml`)

## Future blocking

- Per-service doctrine compliance from matrix (when evidence automation exists)
- Nompilo auditability rules on new surfaces
