# Registry Plane Endpoint Inventory

Date: 2026-05-14  
Source of truth: Registry OpenAPI contracts under `contracts/openapi/`.

## `vito-service` (`contracts/openapi/vito.openapi.yaml`)

- `/v1/portal/id/request`
- `/v1/portal/id/recovery/start`
- `/v1/portal/id/recovery/verify`
- `/v1/portal/me`
- `/v1/portal/health-id/qr`
- `/v1/portal/delegated-pickup/create`
- `/v1/portal/delegated-pickup/redeem`
- `/v1/identity/register`
- `/v1/identity/resolve`
- `/v1/identity/rotate`
- `/v1/clients`
- `/v1/clients/{healthId}`
- `/v1/clients/{healthId}/verify`
- `/v1/clients/{healthId}/deactivate`
- `/v1/clients/{healthId}/deceased`
- `/v1/internal/issuance/submit`
- `/v1/internal/issuance/{requestId}/proofing`
- `/v1/internal/issuance/{requestId}/approve`
- `/v1/internal/issuance/{requestId}/issue`
- `/v1/internal/issuance/{requestId}/deliver`
- `/v1/internal/issuance/{requestId}/reject`
- `/v1/internal/issuance/queue`
- `/v1/internal/issuance/{requestId}`
- `/v1/cards/request`
- `/v1/cards/{cardId}/print`
- `/v1/cards/{cardId}/activate`
- `/v1/cards/{cardId}/inactivate`
- `/v1/cards/{cardId}/revoke`
- `/v1/cards/active/{healthId}`
- `/v1/cards/history/{healthId}`
- `/v1/cards/by-status/{status}`
- `/v1/wallet/create`
- `/v1/wallet/{healthId}`
- `/v1/wallet/topup`
- `/v1/wallet/pay`
- `/v1/wallet/offline`
- `/v1/wallet/{walletId}/journal`
- `/v1/biometric/enroll`
- `/v1/biometric/{healthId}`
- `/v1/match/{healthId}`
- `/v1/match/pending`
- `/v1/match/{matchId}/resolve`
- `/v1/recovery/handover`
- `/v1/recovery/shs/create`
- `/v1/recovery/shs/verify`
- `/v1/registry/mode`
- `/v1/registry/provisional/issue`
- `/v1/registry/provisional/{provisionalRef}/reconcile`
- `/v1/registry/provisional/pending`
- `/v1/registry/dedup/pending`
- `/v1/registry/dedup/{caseId}/resolve`
- `/v1/registry/opencr/match`
- `/v1/print/card/job`
- `/v1/qr/resolve/{token}`
- `/v1/qr/public-key`
- `/v1/slips/emergency-capsule.pdf`
- `/v1/slips/pickup.pdf`

## `varapi-service` (`contracts/openapi/varapi.openapi.yaml`)

- `/internal/v1/health`
- `/internal/v1/test-command`
- `/internal/v1/test-federation`
- `/v1/internal/providers`
- `/v1/internal/providers/search`
- `/v1/internal/providers/{providerPublicId}`
- `/v1/internal/providers/{providerPublicId}/status`
- `/v1/internal/councils`
- `/v1/internal/councils/{councilId}`
- `/v1/internal/councils/{councilId}/imports`
- `/v1/internal/providers/{providerPublicId}/cpd/cycles`
- `/v1/internal/providers/{providerPublicId}/cpd/cycles/{cycleId}/events`
- `/v1/internal/providers/{providerPublicId}/cpd/events/{eventId}/verify`
- `/v1/internal/providers/{providerPublicId}/cpd/summary`
- `/v1/internal/providers/{providerPublicId}/licenses`
- `/v1/internal/providers/{providerPublicId}/licenses/{licenseId}/renew`
- `/v1/internal/providers/{providerPublicId}/licenses/{licenseId}/suspend`
- `/v1/internal/providers/{providerPublicId}/licenses/{licenseId}/revoke`
- `/v1/internal/providers/{providerPublicId}/privileges`
- `/v1/internal/providers/{providerPublicId}/privileges/grant`
- `/v1/internal/providers/{providerPublicId}/privileges/revoke`
- `/v1/internal/privileges/pending`
- `/v1/internal/privileges/{privilegeId}/decide`
- `/v1/internal/eligibility/check`
- `/v1/fhir/practitioner/{providerPublicId}`
- `/v1/fhir/practitionerrole/{providerPublicId}`
- `/v1/fhir/bundle/provider/{providerPublicId}`
- `/v1/portal/me`
- `/v1/portal/cpd`
- `/v1/portal/cpd/evidence`
- `/v1/portal/certificates`
- `/v1/portal/certificates/{id}/download`
- `/v1/internal/provider-token/issue`
- `/v1/internal/provider-token/rotate`
- `/v1/internal/provider-token/recovery/start`
- `/v1/internal/provider-token/recovery/verify`
- `/v1/internal/reconciliation/queue`
- `/v1/internal/reconciliation/{caseId}/decision`
- `/internal/v1/snapshots/providers`
- `/internal/v1/snapshots/providers/emit`

## `tuso-service` (`contracts/openapi/tuso.openapi.yaml`)

- `/internal/v1/health`
- `/internal/v1/test-command`
- `/internal/v1/test-federation`
- `/v1/internal/facilities`
- `/v1/internal/facilities/search`
- `/v1/internal/facilities/{id}`
- `/v1/internal/facilities/{id}/merge`
- `/v1/internal/facilities/{id}/close`
- `/v1/public/facilities/search`
- `/v1/public/facilities/{id}/profile`
- `/v1/internal/facilities/{facilityId}/workspaces`
- `/v1/internal/workspaces/{workspaceId}`
- `/v1/internal/workspaces/{workspaceId}/override`
- `/v1/internal/facilities/{facilityId}/resources`
- `/v1/internal/resources/{resourceId}/bookings`
- `/v1/internal/bookings/{bookingId}`
- `/v1/internal/facilities/{facilityId}/calendars/slots`
- `/v1/internal/facilities/{facilityId}/start-shift/options`
- `/v1/internal/facilities/{facilityId}/start-shift`
- `/v1/internal/telemetry/pct`
- `/v1/internal/telemetry/oros`
- `/v1/internal/control-tower/facilities/{facilityId}/summary`
- `/v1/internal/control-tower/alerts`
- `/v1/internal/facilities/{facilityId}/config/effective`
- `/v1/internal/facilities/{facilityId}/config`
- `/v1/internal/facilities/{facilityId}/config/history`
- `/v1/internal/facilities/{facilityId}/config/rollback`
- `/internal/v1/snapshots/facilities`
- `/internal/v1/snapshots/facilities/emit`

## `zibo-service` (`contracts/openapi/zibo.openapi.yaml`)

- `/v1/artifacts`
- `/v1/artifacts/{artifactId}`
- `/v1/artifacts/{artifactId}/publish`
- `/v1/artifacts/{artifactId}/deprecate`
- `/v1/artifacts/{artifactId}/retire`
- `/v1/artifacts/{artifactId}/versions`
- `/v1/artifacts/resolve`
- `/v1/packs`
- `/v1/packs/{packId}`
- `/v1/packs/{packId}/artifacts`
- `/v1/packs/{packId}/artifacts/{artifactId}`
- `/v1/packs/{packId}/publish`
- `/v1/packs/{packId}/deprecate`
- `/v1/validate/coding`
- `/v1/validate/resource`
- `/v1/validate/job`
- `/v1/validate/job/{jobId}`
- `/v1/map`
- `/v1/map/sources`
- `/v1/map/rebuild`
- `/v1/assignments`
- `/v1/assignments/{assignmentId}`
- `/v1/import/fhir-bundle`
- `/v1/import/csv`
- `/v1/export/packs/{packId}/bundle`
- `/v1/export/artifacts/{artifactId}`
- `/v1/governance/validation-logs`
- `/v1/governance/top-failures`
- `/v1/governance/stats`

## `ubomi-service` (`contracts/openapi/ubomi.openapi.yaml`)

- `/internal/v1/health`
- `/internal/v1/test-command`
- `/v1/births`

## `indawo-service` (`contracts/openapi/indawo.openapi.yaml`)

- `/internal/v1/addresses`
- `/internal/v1/addresses/{id}`
- `/internal/v1/snapshots/sites`

## `msika-service` (`contracts/openapi/msika-core.openapi.yaml`)

- `/v1/catalogs`
- `/v1/catalogs/{catalogId}`
- `/v1/catalogs/{catalogId}/submit-review`
- `/v1/catalogs/{catalogId}/approve`
- `/v1/catalogs/{catalogId}/publish`
- `/v1/catalogs/{catalogId}/rollback/{version}`
- `/v1/catalogs/{catalogId}/items`
- `/v1/items/{itemId}`
- `/v1/search`
- `/v1/packs/orderables`
- `/v1/packs/item-master`
- `/v1/packs/chargeables`
- `/v1/packs/capabilities/facility`
- `/v1/packs/capabilities/provider`
- `/v1/import/csv`
- `/v1/import/sources`
- `/v1/import/sources/{sourceId}/run`
- `/v1/import/jobs/{jobId}`
- `/v1/mappings/pending`
- `/v1/mappings/{mappingId}/approve`
- `/v1/mappings/{mappingId}/reject`
- `/v1/validate/item`
- `/v1/validate/pack`

## `product-registry-service` (`contracts/openapi/product-registry.openapi.yaml`)

- `/internal/v1/health`
- `/internal/v1/test-command`
- `/internal/v1/products`
- `/internal/v1/products/{id}`
- `/internal/v1/products/search`
- `/internal/v1/products/snapshot`
