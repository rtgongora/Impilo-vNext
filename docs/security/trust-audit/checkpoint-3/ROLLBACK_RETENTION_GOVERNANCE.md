# Checkpoint 3 — Keycloak Migration Rollback-Retention Governance Record

Date recorded: 2026-08-01 · Branch: `claude/tshepo-trust-cp1-truth-audit`

This record inventories the retained rollback artifacts for the H2 → Keycloak 25/
PostgreSQL → Keycloak 26.7 migration and states the governance decisions that exist
versus those that remain outstanding. Nothing was deleted, extended, or re-created in
producing this record; artifact verification was read-only (names, sizes, hashes).

## Retained artifacts (verified live 2026-08-01)

| Artifact | Storage location / reference | Created | Integrity | Verified |
|---|---|---|---|---|
| Pre-migration H2 snapshot (rollback source) | PVC `keycloak-data` (2Gi, `impilo-full-preview`, volume `pvc-fb9c8728…`); never mounted by export jobs since freeze | 2026-07-18 (PVC age 14d at verification) | Filesystem-level; frozen read-only copy was the rehearsal source | Bound, present |
| Migration working backup | PVC `keycloak-migration-backup` (5Gi, `impilo-full-preview`, volume `pvc-f4b5b8b3…`) | 2026-07-31/08-01 (migration window) | Job-produced snapshot artifacts | Bound, present |
| PostgreSQL pre-26.7-upgrade dump (encrypted) | VM `impilo.mohcc.gov.zw`: `/home/robert/impilo-backups/keycloak/rehearsals/20260801T013548Z/keycloak25-preupgrade.dump.gpg` (88 997 bytes, mode `-rw-------`) | 2026-08-01 03:42 | Encrypted SHA-256 `70581147…a2d91b` — **re-verified matching** the committed evidence; content SHA-256 `fe2609b9…` in sidecar `keycloak25-preupgrade.dump.sha256` | Present, hash match |
| Identity signatures (source / kc25 / kc25-restore / kc26) | Same rehearsal directory, four `*.signature.json` files (46 823 bytes each, mode `-rw-------`) | 2026-08-01 03:36–03:38 | Deterministic identity-set signatures used for the parity comparison | Present |
| Pre-MFA rollback image digests (BFF/shell/authz/audit/Keycloak 25) | `docs/security/evidence/mfa-preview-release-evidence-20260801.md` (committed) | 2026-08-01 | Git history | Present |

## Governance state

| Governance requirement | State |
|---|---|
| Storage location/reference | **RECORDED** (table above) |
| Creation date | **RECORDED** |
| Integrity/hash check | **RECORDED and re-verified** for the encrypted dump; signatures present |
| Retention owner | **OUTSTANDING** — no named owner exists in any committed policy. De facto custodian: VM operator account `robert` (files) and `impilo-full-preview` namespace admin (PVCs). Requires product-owner designation. |
| Rollback expiry date | **OUTSTANDING** — no approved retention duration (e.g. N days after workforce-MFA activation) exists anywhere in the repository. Until one is approved, artifacts must be treated as retain-indefinitely. |
| Deletion approval requirement | **OUTSTANDING as formal policy.** Interim rule recorded here: none of the artifacts above may be deleted without explicit product-owner written approval referencing this document; the H2 PVC additionally remains the last-resort rollback source and must outlive every other artifact. |

## Explicit statement

**The rollback-retention governance decision is outstanding.** Artifact inventory,
locations, dates, and integrity are now recorded; the retention owner, the approved
retention window, and the formal deletion-approval procedure require a product-owner
decision that Checkpoint 3 cannot make unilaterally. No live retention was extended
or shortened by this checkpoint.
