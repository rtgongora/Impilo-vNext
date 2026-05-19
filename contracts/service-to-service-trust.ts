/**
 * Health OS Service-to-Service Trust Contract — TypeScript header constants
 * and small helper types. Java source of truth lives at
 * `libs/tech-companion/src/main/java/.../context/CompanionHeaders.java`.
 *
 * Doctrine: docs/doctrine/HEALTH_OS_EXTENSIBILITY_DOCTRINE.md §4 and §9
 */

import type { RequestSource } from "./health-os-extensibility";

/**
 * Headers carried on every internal service-to-service request, in addition
 * to the mandatory Manifest v1.2 quartet (X-Tenant-ID, X-Pod-ID,
 * X-Request-ID, X-Correlation-ID) and Authorization.
 */
export const S2S_HEADERS = {
  SERVICE_ID: "X-Service-Id",
  SERVICE_NAME: "X-Service-Name",
  SERVICE_VERSION: "X-Service-Version",
  REQUEST_SOURCE: "X-Request-Source",
  EXTERNAL_APP_ID: "X-External-App-Id",
} as const;

/**
 * Additional headers required when the request originates from an external
 * application registered through the Integration Service.
 */
export const EXTERNAL_APP_HEADERS = {
  EXTERNAL_APP_ID: "X-External-App-Id",
  INTEGRATION_TYPE: "X-Integration-Type",
  INTEGRATION_VERSION: "X-Integration-Version",
  REQUEST_SIGNATURE: "X-Request-Signature",
} as const;

export type RequestSourceHeaderValue = RequestSource;

export interface S2SCallContext {
  callerServiceId: string;
  callerServiceVersion: string;
  callerServiceName: string;
  requestSource: RequestSource;
  /** Present only when the request was originally triggered by an external app */
  externalAppId?: string;
  /** Present only when the request was originally triggered by a human actor */
  actorId?: string;
  actorType?: string;
}

/**
 * Build the additional headers a sovereign service should set when calling
 * another sovereign service. Helpers like `apiClient` and the BFF outbound
 * client use this to ensure consistent trust posture.
 */
export function buildS2SHeaders(ctx: S2SCallContext): Record<string, string> {
  const headers: Record<string, string> = {
    [S2S_HEADERS.SERVICE_ID]: ctx.callerServiceId,
    [S2S_HEADERS.SERVICE_NAME]: ctx.callerServiceName,
    [S2S_HEADERS.SERVICE_VERSION]: ctx.callerServiceVersion,
    [S2S_HEADERS.REQUEST_SOURCE]: ctx.requestSource,
  };
  if (ctx.externalAppId) {
    headers[S2S_HEADERS.EXTERNAL_APP_ID] = ctx.externalAppId;
  }
  return headers;
}
