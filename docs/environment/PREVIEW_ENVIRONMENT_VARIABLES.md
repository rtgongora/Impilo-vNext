# Preview Environment Variables

| Variable | Purpose | Example | Required | Used By |
|----------|---------|---------|----------|---------|
| `IMPILO_ENV` | Environment label | `preview` | yes | BFF |
| `IMPILO_GIT_BRANCH` | Deployed branch | `claude/staging-ux-orchestration-remediation-Yypyl` | yes | BFF, UI |
| `IMPILO_GIT_COMMIT` | Deployed commit SHA | `abc123...` | yes | BFF, UI |
| `IMPILO_BUILD_DATE` | Build timestamp UTC | `2026-05-29T12:00:00Z` | optional | BFF, UI |
| `REDIS_HOST` | Redis hostname | `redis` | yes | BFF |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka brokers | `` (empty for MVP) | optional | BFF |
| `KEYCLOAK_URL` | OIDC issuer base | `http://keycloak:8080` | when auth enabled | BFF |
| `AUTH_FALLBACK_ENABLED` | Dev auth fallback | `true` (preview only) | optional | BFF |
| `NEXT_PUBLIC_BFF_URL` | Browser BFF base URL — leave **empty** for same-origin (browser uses relative paths routed to the BFF by Traefik; IP-independent) | `` (empty, recommended) | optional | one-ui-shell |
| `NEXT_PUBLIC_IMPILO_ENV` | UI environment badge | `preview` | yes | one-ui-shell |
| `POSTGRES_PASSWORD` | DB password | `preview-change-me` | yes | postgres (Helm secret) |

See also: `.env.preview.example`, `deploy/env/preview.example.env`
