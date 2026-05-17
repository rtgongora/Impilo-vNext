/**
 * @impilo/mobile-integration — Integration Service SDK for Impilo mobile apps.
 *
 * Exports:
 *   - types (status, domain, severity, handoff)
 *   - friendly copy helpers (`integrationStatusCopy`, `integrationLine`)
 *   - read-only client (`integrationMobileClient`)
 *   - React hook (`useIntegrationStatuses`)
 *
 * The SDK never directly calls vendors. Mobile UI degrades gracefully when the
 * BFF surface is not present — empty list rather than a red error screen.
 */

export type {
  IntegrationDomain,
  IntegrationStatus,
  IntegrationSeverity,
  IntegrationStatusSummary,
  IntegrationHandoff,
  IntegrationHandoffMode,
} from "./types";

export { integrationStatusCopy, integrationLine } from "./copy";

export { integrationMobileClient } from "./client";
export type { IntegrationCaller, IntegrationMobileClient } from "./client";

export { useIntegrationStatuses } from "./hooks";
export type { UseIntegrationStatusesState } from "./hooks";
