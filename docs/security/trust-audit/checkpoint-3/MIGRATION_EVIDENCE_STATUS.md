# Checkpoint 3 — Keycloak migration / restore evidence status

Do not recreate missing evidence. Unavailable items are marked `INSUFFICIENT_EVIDENCE`.

Primary sources:

- `docs/security/evidence/keycloak-migration-rehearsal-20260801.md`
- `docs/security/evidence/mfa-preview-release-evidence-20260801.md`
- Live K8s Jobs/PVCs and VM rehearsal directory (names/sizes only; no secrets opened)

## Evidence matrix

| Required proof | Status | Evidence |
|---|---|---|
| Pre-migration H2 snapshot | **PRESENT** | Job `keycloak-h2-snapshot-mfa-20260801-0313` COMPLETE; PVC `keycloak-data` 2Gi retained; PVC `keycloak-migration-backup` 5Gi retained |
| Offline realm/user export | **PRESENT** | Job `keycloak-h2-export-mfa-20260801-0313` COMPLETE; rehearsal dir `kc25-export/` |
| Keycloak 25 PostgreSQL import | **PRESENT** | Job `keycloak-pg25-import-mfa-20260801-0313` COMPLETE; rehearsal PASS |
| User IDs / roles / attributes / password credentials / required-action comparison | **PRESENT (with documented limitation)** | Rehearsal: 42 human users, 3 workload SAs; identity signatures `source.signature.json` / `kc25.signature.json` / `kc26.signature.json`. Explicit note: two `password-history` records for one account were not re-imported (cannot authenticate; original H2 retained). |
| PostgreSQL backup before 26.7 upgrade | **PRESENT** | Encrypted dump `keycloak25-preupgrade.dump.gpg` (88 997 bytes) + SHA-256 sidecar on VM under `/home/robert/impilo-backups/keycloak/rehearsals/20260801T013548Z/`; content SHA-256 `fe2609b9…`, encrypted SHA-256 `70581147…` |
| Keycloak 26.7 reconciliation | **PRESENT** | Jobs `keycloak-create-reconciler-mfa`, bootstrap/admin/event-reader COMPLETE; realm hash `9c903e22…` with no post-apply drift (release evidence) |
| Post-upgrade comparison | **PRESENT** | Rehearsal: Keycloak 26.7 identities and active credential hashes PASS; `kc26.signature.json` retained |
| Rollback artifacts | **PRESENT** | Retained: original H2 PVC, migration-backup PVC, postgres PVCs, encrypted dump + key directory, pre-MFA rollback digests (BFF/shell/authz/audit/Keycloak 25) in release evidence |
| Retention window | **RECORDED / GOVERNANCE OUTSTANDING** | Artifact inventory, locations, dates and hashes recorded and re-verified in [`ROLLBACK_RETENTION_GOVERNANCE.md`](ROLLBACK_RETENTION_GOVERNANCE.md); retention owner, approved duration and formal deletion-approval procedure remain an outstanding product-owner decision |

## Isolated disposable rehearsal

- Prior isolated rehearsal already executed and documented (`20260801T013548Z`).
- This checkpoint **did not** re-run a disposable migration/restore against live preview data (forbidden) and did not recreate missing evidence.
- Live preview Keycloak was not touched.

## Classification summary

Migration path H2 → Keycloak 25/PostgreSQL → Keycloak 26.7 is **evidence-backed** for identities, active credentials, and rollback artifacts. The only remaining gap in the required list is an explicit **retention window policy** (`INSUFFICIENT_EVIDENCE`).
