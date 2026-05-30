# Dev Preview Security Notes

## SSH

- Client port: **2276** (must remain allowed in UFW)
- Do not disable password auth without key-based fallback documented

## UFW (applied by bootstrap)

| Port | Purpose |
|------|---------|
| 2276/tcp | SSH |
| 80/tcp | HTTP preview ingress |
| 443/tcp | HTTPS (future) |

## Intentionally Not Exposed Publicly

- PostgreSQL (5432)
- Redis (6379)
- Kafka
- Keycloak admin
- Kubernetes API (6443) — cluster-internal

## fail2ban

Installed by bootstrap for SSH brute-force mitigation.

## Limitations

- Preview uses test credentials (`preview-change-me` Postgres password in values — change for shared environments)
- No TLS on initial HTTP preview
- AUTH_FALLBACK_ENABLED may be true in preview only

## Future Hardening

- SSH key-only auth
- TLS + DNS for preview
- Sealed Secrets / external secret manager
- NetworkPolicies per namespace
