# TUSO-VARAPI Interoperability Contract

## Overview

The TUSO (Facility Registry) and VARAPI (Provider Registry) services maintain a bidirectional interoperability contract for provider eligibility verification and facility access control.

## Provider Eligibility Verification

### Endpoint

```
POST /v1/internal/interop/eligibility
Content-Type: application/json
X-Tenant-ID: <tenant-uuid>
X-Request-ID: <request-uuid>
```

### Request

```json
{
  "providerId": 12345,
  "facilityId": 67890,
  "checkPicEligibility": true,
  "checkLicenseStatus": true,
  "checkCertificateStatus": true,
  "checkComplianceStatus": true,
  "checkAffiliationStatus": true
}
```

### Response

```json
{
  "providerId": 12345,
  "providerPublicId": "HCP-2024-001234",
  "eligible": true,
  "eligibilityStatus": "ELIGIBLE",
  "hasValidLicense": true,
  "hasActivePractisingCertificate": true,
  "hasNoOverdueCompliance": true,
  "hasActiveAffiliation": true,
  "canServeAsPic": true,
  "licenseExpiryDate": "2026-12-31",
  "certificateExpiryDate": "2026-12-31",
  "blockReasons": []
}
```

### Eligibility Status Codes

| Status | Description |
|--------|-------------|
| `ELIGIBLE` | Provider meets all criteria |
| `INELIGIBLE` | Provider fails one or more criteria |

### Block Reason Codes

| Code | Description | Action |
|------|-------------|--------|
| `PROVIDER_NOT_FOUND` | Provider ID not in VARAPI | Register provider first |
| `PROVIDER_NOT_ACTIVE` | Provider status not ACTIVE | Check with council |
| `NO_VALID_LICENSE` | No active, non-expired license | Renew license |
| `NO_ACTIVE_CERTIFICATE` | No active practising certificate | Apply for certificate |
| `OVERDUE_COMPLIANCE` | Has overdue CPD or requirements | Complete requirements |
| `NO_ACTIVE_AFFILIATION` | No affiliation to target facility | Apply for affiliation |
| `NOT_PIC_ELIGIBLE` | Provider ineligible for PIC role | Check council requirements |

## Facility-Specific Eligibility

### Check Eligibility for Facility

```
POST /v1/internal/interop/eligibility/provider/{providerId}/facility/{facilityId}
```

Returns detailed eligibility check for a specific facility context.

## PIC Eligibility Check

### Check Provider Can Serve as PIC

```
GET /v1/internal/interop/eligibility/provider/{providerId}/facility/{facilityId}/pic-eligible
```

Returns eligibility status for Practitioner-In-Charge designation.

**PIC Eligibility Criteria:**
- Provider status = ACTIVE
- At least one ACTIVE, non-expired license
- No suspended licenses
- No overdue compliance requirements

## Facility Provider Listing

### Get Eligible Providers for Facility

```
GET /v1/internal/interop/eligibility/facility/{facilityId}/eligible-providers
```

Returns list of providers with active affiliation to facility who meet eligibility criteria.

### Check Facility Has Active Provider

```
POST /v1/internal/interop/eligibility/facility/{facilityId}/has-active-provider
```

Returns boolean indicating if facility has at least one active provider.

## Usage Scenarios

### Scenario 1: Patient Registration (TUSO)

Before allowing patient registration at a facility, TUSO calls VARAPI to verify:
1. Provider has valid license
2. Provider has active affiliation to facility
3. Provider has no overdue compliance

### Scenario 2: Clinical Access (BUTANO)

BUTANO/HAPI FHIR calls VARAPI before granting clinical access:
1. Provider has practising certificate
2. Provider eligible for facility context
3. No step-up authentication required (if RESTRICTED scope)

### Scenario 3: PIC Designation (TUSO)

When designating a PIC, TUSO calls VARAPI to verify:
1. Provider is active
2. Provider has valid license
3. Provider has no disciplinary issues
4. Provider has no overdue compliance

## Event Publishing

VARAPI publishes domain events when provider status changes:

| Event | Topic | Payload |
|-------|-------|---------|
| `varapi.provider.status_changed` | `varapi.provider.events` | providerId, previousStatus, newStatus |
| `varapi.provider.activated` | `varapi.provider.events` | providerPublicId |
| `varapi.provider.suspended` | `varapi.provider.events` | providerPublicId, reason |
| `varapi.provider.revoked` | `varapi.provider.events` | providerPublicId, reason |

## Error Handling

All endpoints return appropriate HTTP status codes:

| Code | Meaning |
|------|---------|
| 200 | Check complete (eligible or ineligible) |
| 400 | Invalid request parameters |
| 404 | Provider not found |
| 500 | Internal error (graceful degradation) |

On TUSO unavailability, VARAPI returns last known eligibility status with flag `stale: true`.

## Caching

Eligibility results should be cached with short TTL (5 minutes) due to:
- Frequent status changes
- Compliance deadline sensitivity

Cache key format: `eligibility:{providerId}:{facilityId}`