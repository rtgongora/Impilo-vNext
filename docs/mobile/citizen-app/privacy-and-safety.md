# Citizen App — Privacy and Safety

## Data Protection Principles

### Minimum Exposure
- The citizen app only accesses data belonging to the authenticated citizen.
- All BFF controllers resolve the patient from the `X-Actor-ID` header; there is no endpoint that accepts arbitrary patient IDs.
- Query results are always scoped to `tenant_id` AND `patient_id`.

### No PII in Shared Health Record
- Following the platform-wide rule, BUTANO (HAPI FHIR) stores only CPIDs.
- PII (names, contact details, national IDs) is stored exclusively in VITO and accessed through the citizen profile endpoint.

### Consent Management
- Citizens can view and manage their data sharing consents via the Settings section.
- Each consent preference is stored with a timestamp and can be toggled independently.
- Consent changes publish domain events for downstream systems to react.

## Authentication and Authorization

### PKCE Flow
- The citizen app uses Keycloak's Authorization Code flow with PKCE.
- No client secret is stored on the device.
- Tokens are stored in secure on-device storage and refreshed automatically.

### Identity Resolution
- The `X-Actor-ID` header is set by the auth layer after token validation.
- The BFF resolves this to a patient record via CPID lookup in the `patients` table.
- If no patient is found, a `404 Patient not found` response is returned.

## Data Safety

### Tenant Isolation
Every database query in every citizen BFF controller includes `tenant_id` in the WHERE clause. This ensures:
- A citizen in Tenant A cannot access data from Tenant B.
- Even in multi-tenant deployments, data boundaries are enforced at the SQL level.

### Input Validation
- All POST/PATCH request bodies use Jakarta Bean Validation (`@NotBlank`, `@Valid`).
- Query parameters are validated and sanitized before use in SQL queries.
- Search terms use parameterized queries — no string concatenation in WHERE clauses.

### Account Deletion
- Citizens can request full account deletion from the Settings section.
- Deletion is handled server-side: the patient record is soft-deleted, and an `account-deleted.v1` domain event is published.
- Downstream services consume this event to purge related data.

## Event Audit Trail
- Every write operation in the citizen app publishes a domain event via the transactional outbox pattern.
- Events include correlation IDs, request IDs, tenant context, and timestamps.
- This provides a complete audit trail of citizen actions for compliance and debugging.

## Session Security
- Telehealth sessions generate unique per-session tokens for join operations.
- Session tokens are single-use and scoped to the authenticated citizen.
- Room URLs are generated server-side and not exposed to the client until join time.
