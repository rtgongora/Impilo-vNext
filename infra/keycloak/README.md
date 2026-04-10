# Keycloak — Impilo production realm

This folder holds the **production-oriented** realm export `realm-impilo-production.json` for Keycloak 25.x. It extends the dev-oriented template under `tools/auth/impilo-realm.json` with:

- Hardened realm settings (SSL external, brute-force policy, password policy, session/token lifespans)
- **8 OIDC clients**: `impilo-ui`, `impilo-ops-console`, `impilo-ehr`, `impilo-portal`, `impilo-mobile-citizen`, `impilo-mobile-provider`, `impilo-bff`, `impilo-admin-cli`
- **35 realm roles** plus composites `CLINICAL_STAFF`, `ALLIED_HEALTH`, `ADMIN_STAFF`, `COMMUNITY_HEALTH`
- Optional client scopes `impilo-trust-headers`, `impilo-clinical`, `impilo-admin` (mappers emit claims; map user attributes `actor_id`, `tenant_id`, `pod_id` for trust headers)
- Disabled **identity provider placeholders** for MOSIP (OIDC) and facility LDAP

Replace all `CHANGE_ME_*` client secrets before use. Clone or extend authentication flows in the admin console for **provider mandatory OTP** and **citizen password-only** variants; this JSON keeps the built-in `browser` / `direct grant` bindings as a baseline.

## Import (Docker)

```bash
# Copy the file into the container import path, then:
docker exec -it keycloak /opt/keycloak/bin/kc.sh import --file /opt/keycloak/data/import/realm-impilo-production.json
```

## Import (Admin REST API)

Obtain an admin access token, then:

```bash
curl -X POST "http://localhost:8080/admin/realms" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d @infra/keycloak/realm-impilo-production.json
```

For an existing realm, prefer a partial import or manual merge via the admin UI to avoid overwriting live users.
