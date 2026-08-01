# Consent evaluate contract incompatibility — Checkpoint 1 closure

**Status:** SOURCE_CONFIRMED defect.  
**Live PDP path:** DISCONNECTED (Envoy/ext_authz off).  
**Authoritative replacement:** **not chosen** in this checkpoint (ownership conflict remains open).

## Controllers and callers

| Role | Component | Verb + path | Shape |
|---|---|---|---|
| Producer (evaluate) | `tshepo-consent-service` `ConsentEvaluationController` | **GET** `/v1/consent/evaluate` | Query params → `ApiResponse<ConsentDecision>` |
| Broken consumer (PDP) | `tshepo-authz-service` `ConsentClient` | **POST** `/v1/consent/evaluate` | JSON body → raw `ConsentDecision` |
| Correct BFF consumer | `experience-bff` `TshepoConsentServiceClient` | **GET** `/v1/consent/evaluate` | Query params (matches producer) |
| Create (different endpoint) | `mvumo-service` `TshepoConsentClient` | **POST** `/v1/consent` | Create directive — **not** evaluate |

OpenAPI: `contracts/openapi/tshepo-consent.openapi.yaml` documents **GET** evaluate.

## Exact request differences

### GET (producer + BFF — intended wire)

Query parameters:

- `tenantId` (UUID)
- `actorId` (string)
- `subjectRef` (string — patient CPID/Health ID)
- `purpose` (string)
- `scope` (string)

Response envelope: `ApiResponse<ConsentDecision>` with correlation metadata wrapping:

```json
{
  "permitted": true|false,
  "consentId": "...",
  "provision": "permit|deny|null",
  "allowedScopes": [...],
  "deniedScopes": [...],
  "reason": "..."
}
```

### POST (authz ConsentClient — incompatible)

JSON body fields:

- `tenantId`
- `resourceType` (**not** in GET contract)
- `resourceId` (**≠** `subjectRef`)
- `actorId`
- `purposeOfUse` (**≠** `purpose`)
- *(no `scope`)*

Expected response: raw `ConsentDecision` (**no** `ApiResponse` envelope).

## Response / semantic differences

| Dimension | GET producer | POST client expectation |
|---|---|---|
| HTTP method | GET | POST |
| Subject field | `subjectRef` | `resourceId` (+ `resourceType`) |
| Purpose field | `purpose` | `purposeOfUse` |
| Scope | required query `scope` | omitted |
| Envelope | `ApiResponse<…>` | bare DTO |
| Failure mode if called today | N/A for POST | catch → `ConsentDecision.deny("CONSENT_SERVICE_UNAVAILABLE")` fail-closed |

## Which path is enforced in preview?

| Path | Preview status |
|---|---|
| Traefik → experience-bff → (optional) consent GET | BFF can call GET correctly; clinical gating still BYPASSABLE/ABSENT at applications |
| Traefik → Envoy → ext_authz → PolicyEngine → ConsentClient POST | **NOT LIVE** — Envoy/ext_authz DISCONNECTED |
| PolicyEngine Step 5 consent | **NOT ENFORCED** on live ingress; if enabled would fail-closed-deny clinical requests |

**Conclusion:** No evaluate path is PREVIEW_ENFORCED as a national PDP consent gate. The GET contract is the documented producer contract; the POST client is a latent break. This checkpoint does **not** silently designate either side as the permanent replacement.

## Unresolved ownership (carried to next architecture checkpoint)

- Mvumo owns governed consent UX/orchestration.
- `tshepo-consent-service` owns directive persistence and evaluate engine.
- Authoritative grant/revocation SoR designation and evaluate wire convergence remain **open**.
- Compatibility adapters in Checkpoint 2 must **not** resolve this ownership conflict.
