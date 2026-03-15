# Developer / Partner App

Operator-facing web application for partner onboarding, API key management, contract certification, schema discovery, sandbox testing, and federation readiness.

## Quick Start

```bash
cd ui/developer-console
npm install
npm run dev          # → http://localhost:3007
```

## Architecture

| Layer | Technology |
|-------|-----------|
| Framework | Next.js 14.2, React 18 |
| State | Zustand 4.5 |
| Styling | TailwindCSS 3.4, shared-ui design tokens |
| API | Trust-aware apiClient → Envoy (port 10000) → developer-portal-service (port 8370) / schema-registry-service (port 8371) |
| Auth | Keycloak via ext_authz → TSHEPO |

## Entry Points

- **UI**: `ui/developer-console/src/app/(developer)/dashboard/page.tsx`
- **API Client**: `ui/developer-console/src/lib/apiClient.ts`
- **API Methods**: `ui/developer-console/src/lib/developerApi.ts`
- **Session Store**: `ui/developer-console/src/stores/sessionStore.ts`

## Backend Services

| Service | Port | Purpose |
|---------|------|---------|
| developer-portal-service | 8370 | Client registration, key management, certification, federation readiness |
| schema-registry-service | 8371 | Schema registration, compatibility validation, event catalog |

## Trust Model

Every request injects the 14 mandatory trust headers from the session store:
- `x-tenant-id`, `x-actor-id`, `x-actor-type`, `x-purpose-of-use`
- `x-correlation-id`, `x-facility-id`, `Idempotency-Key` (on writes)

Purpose of use is always `OPERATIONS` for developer console users.

## Roles

| Role | Capabilities |
|------|-------------|
| DEVELOPER | Register clients, issue keys, run sandbox, view catalog |
| PARTNER_ADMIN | All DEVELOPER + manage team, configure deprecation posture |
| PLATFORM_ADMIN | All PARTNER_ADMIN + federation readiness, certification override |

## Pages

| Route | Feature |
|-------|---------|
| `/dashboard` | Stats overview, onboarding checklist, quick actions |
| `/clients` | List registered partner clients |
| `/clients/register` | Register a new client application |
| `/clients/[clientId]` | Client detail, key management, sandbox/posture config |
| `/certification` | Run certification checks, view pass/fail reports |
| `/catalog` | API endpoints, event catalog, schema registry browser |
| `/sandbox` | Sample payloads, sandbox launcher, test/cert history |
| `/federation` | Federation readiness checklist, pod registration guide |

## Environment Selector

The sidebar includes a SANDBOX / PRODUCTION environment toggle stored in Zustand. This allows developers to switch contexts between sandbox testing and production operations.
