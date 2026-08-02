# Checkpoint 3 — Keycloak Migration Rollback-Retention Governance

Date recorded: 2026-08-01 · Policy adopted: 2026-08-02  
Branch: `claude/tshepo-trust-cp1-truth-audit`

Authority: Product Owner authorization to adopt rollback retention through final trust
activation plus 30 stable calendar days, with Product Owner and Security Owner written
approval required before deletion.

**Nothing was deleted by this checkpoint.** Artifacts remain in place.

## Adopted retention policy

| Item | Policy |
|---|---|
| Trigger | **Final trust activation** (workforce MFA / OAuth / OPA / Envoy enforcement activation that retires the pre-migration rollback path) |
| Duration | **30 stable calendar days** after the trigger (stable = no rollback to the retained artifacts during the window) |
| Owners | **Product Owner** and **Security Owner** (joint) |
| Deletion approval | Written approval from **both** Product Owner and Security Owner, referencing this document |
| Pre-deletion checks | Verify backup hashes still match the recorded sidecars; confirm rollback obsolescence (the activated trust path no longer depends on these artifacts) |
| Hold | Preserve artifacts under any incident, legal or investigation hold — the 30-day clock does not authorize deletion while a hold is active |
| Audit | Record every deletion (or approved destruction) in the trust-audit evidence trail with approver names, dates, hash verification results and hold status |
| Immediate action | **Do not delete anything now** |

Until the trigger occurs, artifacts are retained indefinitely under this policy.

## Retained artifacts (verified live 2026-08-01; reaffirmed 2026-08-02)

| Artifact | Storage location / reference | Created | Integrity |
|---|---|---|---|
| Pre-migration H2 snapshot (rollback source) | PVC `keycloak-data` (2Gi, `impilo-full-preview`, volume `pvc-fb9c8728…`) | 2026-07-18 | Filesystem-level; frozen read-only copy was the rehearsal source |
| Migration working backup | PVC `keycloak-migration-backup` (5Gi, `impilo-full-preview`, volume `pvc-f4b5b8b3…`) | 2026-07-31/08-01 | Job-produced snapshot artifacts |
| PostgreSQL pre-26.7-upgrade dump (encrypted) | VM `impilo.mohcc.gov.zw`: `/home/robert/impilo-backups/keycloak/rehearsals/20260801T013548Z/keycloak25-preupgrade.dump.gpg` (88 997 bytes, mode `-rw-------`) | 2026-08-01 03:42 | Encrypted SHA-256 `70581147…a2d91b` (re-verified 2026-08-01); content SHA-256 in sidecar `keycloak25-preupgrade.dump.sha256` |
| Identity signatures (source / kc25 / kc25-restore / kc26) | Same rehearsal directory, four `*.signature.json` files | 2026-08-01 03:36–03:38 | Deterministic identity-set signatures |
| Pre-MFA rollback image digests | `docs/security/evidence/mfa-preview-release-evidence-20260801.md` | 2026-08-01 | Git history |

## Governance state

| Requirement | State |
|---|---|
| Storage location/reference | **RECORDED** |
| Creation date | **RECORDED** |
| Integrity/hash check | **RECORDED and re-verified** for the encrypted dump |
| Retention owner | **ADOPTED** — Product Owner + Security Owner |
| Rollback expiry | **ADOPTED** — final trust activation + 30 stable calendar days (clock not started; trigger has not occurred) |
| Deletion approval | **ADOPTED** — written approval from both owners; hash + obsolescence checks; hold override; audit record |

## Explicit statement

The previously outstanding retention-governance decision is **closed by Product Owner
authorization on 2026-08-02**. No artifact was deleted or retention-extended beyond this
policy. The H2 PVC remains the last-resort rollback source and must outlive every other
artifact until both owners approve deletion after the retention window.
