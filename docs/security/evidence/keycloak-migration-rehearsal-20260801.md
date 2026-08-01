# Keycloak 25 H2 to PostgreSQL to 26.7 rehearsal evidence

Date: 2026-08-01 (Africa/Harare)

Source: frozen, read-only copy of the preview `keycloak-data` PVC. The original H2 PVC was not mounted by the export job and remains the rollback source. Live Keycloak was restored automatically and revalidated at 42 enabled human users.

## Result

- Human users: 42
- Workload service accounts: 3 (`impilo-admin-cli`, `impilo-backend`, `impilo-bff`)
- Keycloak 25 H2 export to Keycloak 25/PostgreSQL import: PASS
- PostgreSQL pre-upgrade dump and restore into an independent database: PASS
- Restored Keycloak 25 identities and active credential hashes: PASS
- PostgreSQL schema upgrade through Keycloak 26.7.0: PASS
- Keycloak 26.7 identities and active credential hashes: PASS
- Source identity signature SHA-256: `c126a4eeb91040fc129dac939aaf1818a7a9ed69f5a3aae82b267c95aeec7bbb`
- Pre-upgrade PostgreSQL dump content SHA-256: `fe2609b91ea3a10d72cdc38efe6f4dd6a36a0f4e30719669f763f74f1dc1a068`
- Encrypted dump SHA-256: `705811479e618e00a0c50fa6282c6ffd2c2301ab3f14ef34e74f6a539aa2d91b`

The operator-protected evidence and PostgreSQL dump are retained on the VM under `/home/robert/impilo-backups/keycloak/rehearsals/20260801T013548Z/`. The dump is symmetrically encrypted with its separately permissioned key under the rehearsal key directory. The Kubernetes snapshot/export remains on the retained `keycloak-migration-backup` PVC.

## Explicit migration note

Keycloak's supported realm export/import preserved current password credentials but did not re-import two `password-history` records belonging to one account. Those records cannot authenticate and are excluded from active-credential parity. The original H2 snapshot is retained for rollback. Password-history policy remains enabled so history accrues again after migration; this limitation must remain visible in release evidence rather than being silently ignored.
