# Keycloak — Impilo production realm and runtime

This folder holds the production-oriented realm export
`realm-impilo-production.json`. It extends the dev-oriented template under
`tools/auth/impilo-realm.json` with:

- Hardened realm settings, brute-force policy, password policy, and session/token lifespans.
- The Impilo web, operations, clinical, portal, mobile, BFF, and administrative OIDC clients.
- Realm roles and the `CLINICAL_STAFF`, `ALLIED_HEALTH`, `ADMIN_STAFF`, and
  `COMMUNITY_HEALTH` composites.
- Optional client scopes for trust-header, clinical, and administrative claims.
- Disabled identity-provider placeholders for MOSIP and facility LDAP.

The tracked export is evidence and a new-realm seed; it is not an updater for an existing realm.
Never import it over a realm that contains users.

## Optimized runtime

`Dockerfile` builds an optimized Keycloak 26.7 runtime with the PostgreSQL provider selected at
build time. Deploy it by immutable digest. The image deliberately contains no realm, users,
client secrets, database credentials, SMTP credentials, or environment-specific AAGUID allow-list.

Realm policy is versioned separately and reconciled through
`scripts/operator/keycloak-mfa-reconcile.sh`. The reconciler is fail-closed, requires the current
managed-state hash before apply, and does not delete users or credentials.

## New realm import only

For a brand-new, empty environment, import the realm offline. Replace every `CHANGE_ME_*` value
through the environment/secret mechanism before use:

```bash
/opt/keycloak/bin/kc.sh import \
  --file /opt/keycloak/data/import/realm-impilo-production.json
```

For an existing realm, run the reconciler in `plan` mode and follow the MFA migration runbook.
Do not use the admin console or a partial import as an unreviewed configuration path.
