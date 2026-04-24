# Patient-mediated external provider collaboration

This document describes the **governed patient share** capability added across **VITO**, **VARAPI**, **TSHEPO**, **PCT**, the **Experience BFF**, and the **Experience UI**.

## Concepts (non-collapsed)

| Concept | Where it lives | Notes |
|--------|----------------|-------|
| Share request | `vito.patient_share_request` | Patient/client intent, scope, expiry; optional `correlation_id` threads into synthetic trust + PCT provenance |
| Share artifact | `vito.patient_share_artifact` | Opaque token (HMAC lookup hash) + OTP (Argon2) |
| Access grant | `vito.patient_share_access_grant` | Bounded permissions; activated after policy + identity |
| External provider profile | `varapi.external_provider_access_profile` | Provisional identity + **temporary Provider ID** surface (`temporary_provider_public_id`) |
| Verification attempt | `varapi.external_provider_verification_attempt` | Council registry lookup / outcomes |
| Policy | `tshepo.patient_share_policy_rule` | Evaluated via `POST /v1/patient-share-policy/evaluate` |
| Session | `vito.patient_share_session` | Post-OTP browser/API session (`X-Patient-Share-Session`); optional **step-up** gate (`step_up_verified`, `AWAITING_STEP_UP`) |
| Contribution | `vito.patient_share_contribution` | Provenance-rich bounded writes; **ADD_NOTE** is mirrored to **PCT** with provenance headers when PCT accepts the call |
| PCT clinical note | `pct.pct_clinical_notes` | Strangler store; optional columns for grant, contribution, temp provider, share correlation |

## Trust levels (Varapi + Tshepo)

Varapi assigns progressive trust labels (`SELF_ASSERTED`, `COUNCIL_NUMBER_FORMAT_VALID`, `COUNCIL_NUMBER_FOUND`, `COUNCIL_AND_IDENTITY_MATCH`, `FULLY_LINKED_OR_ONBOARDED`). Tshepo rules reference the same strings as **minimum trust** thresholds.

## Council registration format

`varapi.councils.registration_number_pattern` (nullable Java regex) overrides the default alphanumeric pattern when validating `councilRegistrationNumber` during external profile creation.

## Temporary Provider ID

A **26-character** `temporary_provider_public_id` is issued in Varapi for every external profile. VITO stores it on the **access grant** only when Tshepo `TEMP_PROVIDER_ISSUE` evaluation returns `permitTempProviderId=true`. It is **not** a full Varapi `provider` row and must not be treated as unrestricted workforce identity.

## PCT provenance headers (VITO → PCT, BFF → PCT)

Optional headers (see `pct-service` `PatientShareProvenanceHeaders` and BFF `CompanionHeaders`):

| Header | Purpose |
|--------|---------|
| `X-Patient-Share-Grant-Id` | VITO access grant id |
| `X-Vito-Contribution-Id` | Contribution row id |
| `X-Temporary-Provider-Public-Id` | Bounded temp provider public id |
| `X-Patient-Share-Correlation-Id` | Original workflow id from `patient_share_request.correlation_id` (string) |
| `X-External-Provider-Trust-Level` | Trust label at authoring |

The BFF `serviceRestTemplate` interceptor forwards these when present on the inbound request.

## Step-up (MVP)

Scopes **`DOCUMENT_SET`** and **`ENCOUNTER_CONTEXT`** require step-up after OTP: session enters `AWAITING_STEP_UP` until `POST .../verify-step-up` with body `{ "stepUpCode": "..." }` matches `vito.patient-share.step-up-test-code` (default `000000`, overridable via `VITO_PATIENT_SHARE_STEP_UP_CODE`).

## APIs

### VITO (authenticated citizen)

- `POST /v1/clients/{healthId}/patient-shares`
- `GET /v1/clients/{healthId}/patient-shares`
- `POST /v1/clients/{healthId}/patient-shares/{id}/revoke`
- `GET /v1/clients/{healthId}/patient-shares/{id}/contributions`

### VITO (public external lane)

- `POST /v1/public/patient-shares/validate` — returns `tenantId` + `shareWorkflowCorrelationId` when valid
- `GET /v1/public/patient-shares/councils?tenantId=` — active councils for picker
- `POST /v1/public/patient-shares/verify-otp` — returns `sessionStatus` (`AWAITING_STEP_UP` or `AWAITING_IDENTITY`)
- `POST /v1/public/patient-shares/verify-step-up` (header `X-Patient-Share-Session`)
- `POST /v1/public/patient-shares/complete-identity` (header `X-Patient-Share-Session`)
- `GET /v1/public/patient-shares/workspace`
- `POST /v1/public/patient-shares/contributions`

### VARAPI (internal)

- `POST /v1/internal/collaboration/external-providers`
- `GET /v1/internal/collaboration/external-providers/by-temp/{temporaryProviderPublicId}`
- `POST /v1/internal/collaboration/external-providers/{id}/link-provider`
- `GET /v1/internal/collaboration/councils?tenantId=`

### PCT

- `GET/POST /v1/clinical-notes` — provenance via headers + merged JSON `provenance` body (header grant fields win)
- `POST /v1/clinical-notes/{id}/sign` — stub response (`signed: false`) until a real sign workflow exists

### TSHEPO

- `POST /v1/patient-share-policy/evaluate`

### BFF

- `/internal/v1/citizen/clients/{healthId}/patient-shares/**` (JWT + `CITIZEN` roles)
- `/internal/v1/public/patient-shares/**` (`permitAll`) including `councils` and `verify-step-up`

## VITO configuration (patient share)

| Property | Default | Notes |
|----------|---------|--------|
| `vito.patient-share.varapi-base-url` | `http://localhost:8083` | Varapi internal collaboration |
| `vito.patient-share.pct-base-url` | `http://localhost:8088` | PCT for ADD_NOTE mirror |
| `vito.patient-share.pct-bearer-token` | empty | OAuth2 bearer (raw token) for PCT when JWT is enforced; if unset and PCT returns 401, the contribution is still stored in VITO but PCT mirror is skipped (logged) |
| `vito.patient-share.step-up-test-code` | `000000` | MVP shared secret for step-up |

## Manual testing (dev)

1. Start Postgres with **vito**, **varapi**, **pct**, **tshepo** databases; run services on ports **8082 / 8083 / 8088 / 8079** (defaults).
2. Ensure **at least one** active `varapi.councils` row for your tenant; optionally set `registration_number_pattern`.
3. Optionally seed `varapi.provider_council_registration_records` so council verification can return `COUNCIL_NUMBER_FOUND`.
4. As an authenticated citizen, open `/citizen/record-sharing`, create a share, copy **shareToken** + **OTP**.
5. Open `/collaboration/access?token=...`, call **validate** (automatic) to resolve **tenantId**, load councils, complete OTP, step-up if required, then identity.
6. Submit a bounded **ADD_NOTE** contribution when policy permits writes; confirm PCT row if bearer token is configured.

## Assumptions & gaps

- **PCT auth**: PCT `/v1/**` is JWT-protected when issuer-uri is set; configure `VITO_TO_PCT_BEARER_TOKEN` (or mesh service identity) for reliable mirroring.
- **Step-up**: MVP uses a shared code, not SMS to the patient.
- **VITO `SecurityConfig`** still permits all routes at the Spring layer; production should rely on **Envoy/ext_authz** and narrow anonymous paths to `/v1/public/patient-shares/**` only.
- **Identity alignment** for `COUNCIL_AND_IDENTITY_MATCH` is a simple normalized name equality check.

## Next wave

- SMS / WebAuthn step-up channel binding.
- Full onboarding handoff UI from provisional profile to Varapi provider application.
- PCT cryptographic signing workflow (replace sign stub).
