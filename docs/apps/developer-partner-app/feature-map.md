# Developer / Partner App — Feature Map

## A) Core Shell

| Feature | Status | Location |
|---------|--------|----------|
| Auth/session management | Implemented | `src/stores/sessionStore.ts` |
| Org/client context | Implemented | `src/stores/sessionStore.ts` (WorkContext) |
| Environment selector (SANDBOX/PROD) | Implemented | `src/app/(developer)/layout.tsx` |
| Dashboard | Implemented | `src/app/(developer)/dashboard/page.tsx` |
| Sidebar navigation | Implemented | `src/app/(developer)/layout.tsx` |
| Trust header injection | Implemented | `src/lib/apiClient.ts` |

## B) Registration + Key Management

| Feature | Status | Location |
|---------|--------|----------|
| Register client/app | Implemented | `src/app/(developer)/clients/register/page.tsx` |
| View client details | Implemented | `src/app/(developer)/clients/[clientId]/page.tsx` |
| List clients | Implemented | `src/app/(developer)/clients/page.tsx` |
| Issue API key | Implemented | `src/app/(developer)/clients/[clientId]/page.tsx` |
| Rotate key | Implemented | `src/app/(developer)/clients/[clientId]/page.tsx` |
| Revoke key | Implemented | `src/app/(developer)/clients/[clientId]/page.tsx` |
| Scope/capability visibility | Implemented | Key label and status display |
| Deprecation posture config | Implemented | `src/app/(developer)/clients/[clientId]/page.tsx` |

## C) Contract/Certification

| Feature | Status | Location |
|---------|--------|----------|
| Run certification checks | Implemented | `src/app/(developer)/certification/page.tsx` |
| View pass/fail reports | Implemented | Check-by-check detail with modal report view |
| Certification history | Implemented | Tabular history with date and trigger info |
| Schema version notices | Implemented | `src/app/(developer)/catalog/page.tsx` |

## D) Discovery/Catalog

| Feature | Status | Location |
|---------|--------|----------|
| API endpoint discovery | Implemented | `src/app/(developer)/catalog/page.tsx` (API tab) |
| Event catalog | Implemented | `src/app/(developer)/catalog/page.tsx` (Events tab) |
| Schema registry browser | Implemented | `src/app/(developer)/catalog/page.tsx` (Schemas tab) |
| Compatibility checking | Implemented | `src/lib/developerApi.ts` (checkSchemaCompatibility) |
| Onboarding checklist | Implemented | `src/app/(developer)/dashboard/page.tsx` |

## E) Sandbox/Testing

| Feature | Status | Location |
|---------|--------|----------|
| Sandbox launcher | Implemented | `src/app/(developer)/sandbox/page.tsx` |
| Sample payload browser | Implemented | 3 sample payloads with code view |
| Client key visibility | Implemented | Keys listed in sandbox view |
| Certification history | Implemented | Recent cert runs in sandbox view |
| Enable sandbox for client | Implemented | One-click sandbox activation |

## F) Federation/Pod Readiness

| Feature | Status | Location |
|---------|--------|----------|
| Readiness checklist | Implemented | `src/app/(developer)/federation/page.tsx` |
| Overall readiness badge | Implemented | Green/amber overall status |
| Token validation guide | Implemented | Pod registration guide section |
| Pod registration docs | Implemented | Inline protocol documentation |

## Backend Service Features

| Feature | Service | Endpoint |
|---------|---------|----------|
| Client registration | developer-portal | POST /internal/v1/developer/clients |
| Client listing | developer-portal | GET /internal/v1/developer/clients |
| Key issuance | developer-portal | POST /internal/v1/developer/clients/{id}/keys |
| Key rotation | developer-portal | POST /internal/v1/developer/keys/{id}/rotate |
| Key revocation | developer-portal | DELETE /internal/v1/developer/keys/{id} |
| Sandbox config | developer-portal | PUT /internal/v1/developer/clients/{id}/sandbox |
| Deprecation posture | developer-portal | PUT /internal/v1/developer/clients/{id}/deprecation-posture |
| Certification | developer-portal | POST /internal/v1/developer/clients/{id}/certify |
| Certification history | developer-portal | GET /internal/v1/developer/clients/{id}/certifications |
| Federation readiness | developer-portal | GET /internal/v1/developer/clients/{id}/federation-readiness |
| Dashboard stats | developer-portal | GET /internal/v1/developer/dashboard/stats |
| API discovery | developer-portal | GET /internal/v1/developer/discovery |
| Schema catalog | schema-registry | GET /internal/v1/schemas/catalog |
| Schema subjects | schema-registry | GET /internal/v1/schemas/subjects |
| Compatibility check | schema-registry | POST /internal/v1/schemas/compatibility |
