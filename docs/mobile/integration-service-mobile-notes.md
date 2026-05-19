# Integration Service mobile implementation notes

The Integration Service is the plug-and-play bridge between Impilo and
external/internal systems: PACS, LIMS, eLMIS, telemedicine, MusheX,
Comms Hub, Nhume logistics adapters, Ndila map providers, civil registration,
EMRs, registries, insurance, claims switches.

Mobile apps **must not** call any of those vendors directly. They consume
status, actions and handoffs through Integration Service contracts.

## SDK surface

`apps/mobile/packages/mobile-integration` exposes:

- Canonical mobile status enum (`IntegrationStatus`).
- Friendly copy (`integrationStatusCopy`, `integrationLine`) — one line of
  citizen-safe copy per status.
- Read-only client `integrationMobileClient` with `listStatuses(caller)` and
  `getStatus(caller, id)` where `caller` is `"PROVIDER"` or `"CITIZEN"`.
- React hook `useIntegrationStatuses(caller)`.

## Wire shape

The mobile SDK talks to:

- `/internal/v1/mobile/citizen/integration/statuses[/{id}]`
- `/internal/v1/mobile/provider/integration/statuses[/{id}]`

Both are served by `experience-bff` (`CitizenIntegrationController` /
`ProviderIntegrationController`). The BFF calls the underlying
`integration-hub-service` `/internal/v1/routes` endpoint and runs the raw
response through `IntegrationStatusMapper`, which:

1. Maps vendor-specific names → canonical `IntegrationDomain` (e.g.
   `MUSHEX → PAYMENT`, `OPENMRS-FHIR → EMR`).
2. Maps adapter health/route enabled flags → canonical
   `IntegrationStatus` (CONNECTED / PENDING / FAILED / …).
3. Strips vendor strings + correlation IDs for the citizen surface; keeps
   them for the provider surface.

## What citizens see

Just the friendly status:

> **Lab results** · Connected — Connection healthy.
> **Payments** · Retrying — We'll try again automatically.
> **Imaging** · Not set up — Not configured for this facility or service.

No vendor names, no correlation IDs, no stack traces.

## What providers see

The provider surface (`SystemStatusScreen`) adds:

- Adapter name (`OpenMRS-FHIR adapter`, `MusheX gateway`, …)
- Last successful sync timestamp
- Truncated correlation id for support escalation
- Retry count

Providers can long-press the badge to copy the correlation id straight into
a Comms Hub ticket.

## Failure handling

- BFF unreachable → SDK returns an empty list, screen shows "No integrations
  to show". The mobile UI **never** throws.
- Status `FAILED` / `RETRY_SCHEDULED` / `ACTION_REQUIRED` mark the row in
  the design-system `Badge` warning / destructive variant.
- The mobile copy never leaks technical jargon. `integrationStatusCopy()`
  guarantees a citizen-safe one-liner exists for every status.

## Handoffs

The `IntegrationHandoff` type supports four modes:

- `IN_APP_DEEP_LINK` — open another in-app screen (e.g. open the lab result
  detail inside the Provider App).
- `WEB_DEEP_LINK` — open a managed web URL (e.g. claims portal). Mobile
  wraps this in a step-up prompt if `requiresStepUp = true`.
- `SUPPORT_TICKET` — opens the Comms Hub support ticket form, prefilled with
  the correlation id.
- `NONE` — read-only status row, no action button.

This means a status row can carry a "Tap to fix" button without the mobile
code needing per-vendor logic.

## Adding a new integration

1. Register the adapter / route in `integration-hub-service`.
2. The BFF mapper (`IntegrationStatusMapper`) already covers most domains;
   if your adapter belongs to a new domain (`OTHER` fallback), add the
   keyword to `inferDomain()` in the mapper.
3. Optionally add a citizen-friendly label override in
   `mobile-integration/src/copy.ts` if the default is too generic.
4. No mobile app change required — the new integration shows up
   automatically.

## Banned patterns

- ❌ Importing a vendor SDK in mobile code (PACS DICOM viewer, video SDK,
  payment SDK, etc).
- ❌ Storing vendor API keys on the device.
- ❌ Surfacing raw error messages from vendor APIs.
- ❌ Bypassing the Integration Service to "speed up" a citizen flow.
