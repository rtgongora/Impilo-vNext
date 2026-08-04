# Keycloak realm files

## Realm JSON carries no comments — Keycloak rejects unknown properties

`RealmRepresentation` and its nested representations are deserialised with
unknown-property rejection. A key Keycloak does not recognise — including a
well-meant `"_comment"` — does not degrade anything: it throws
`UnrecognizedPropertyException` and **Keycloak fails to boot**, taking all web
and mobile authentication with it.

This is not hypothetical. Between 2026-07-20 and 2026-07-26 eleven misspelled
WebAuthn fields crash-looped realm import for six days of total auth outage.
And on 2026-07-27 a `"_comment"` was added inside a protocol mapper of
`realm-impilo-preview.json`; it was removed on 2026-08-04 after a real
`kc.sh import` against the estate's own image rejected it.

**Why such a defect can sit unnoticed:** `--import-realm` *skips realms that
already exist*. On a running estate a broken realm file therefore changes
nothing and looks harmless — right up until Keycloak restarts for any reason
and cannot come back.

**Rule: rationale for realm configuration belongs in this file, not in the
realm JSON.** Validate any realm change before merging:

```bash
bash scripts/guard/check-keycloak-realm-import.sh
```

## Governed realm files

| File | Purpose |
|---|---|
| `deploy/helm/impilo-vnext/files/realm-impilo-preview.json` | Preview estate realm, imported by the Helm chart |
| `infra/keycloak/realm-impilo-production.json` | Production realm |
| `tools/auth/impilo-realm.json` | Local/tooling realm |

## Rationale notes

### `impilo-backend` → protocol mapper `realm roles`

Preserved from the `_comment` removed on 2026-08-04, because the reasoning is
worth keeping:

> Without this mapper the realm roles granted to the service account never
> reach the token. This realm has no built-in `roles` client scope, so nothing
> emits `realm_access.roles`. Services that authorise on realm roles — Ndila's
> `SecurityConfig` reads `realm_access.roles` — would see an empty authority
> set and deny every service-originated call: a grant that looks applied but is
> inert. It mirrors the `client roles` mapper immediately above it, which was
> added for the identical reason (`resource_access` was empty).

The mapper itself is unchanged; only the unsupported `_comment` key was removed.
