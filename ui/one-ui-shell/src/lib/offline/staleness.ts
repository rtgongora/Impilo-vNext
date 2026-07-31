/**
 * Map a fetch/query outcome onto {@link StalenessState} for emergency surfaces (W16b).
 *
 * Offline cache hits from the service worker set `X-Impilo-Offline-Cache: 1`. Callers that
 * still have a last-known observation time should pass it as `asOf` so the badge can say
 * "Offline copy" rather than inventing freshness.
 */

import { classifyStaleness, type StalenessState } from "shared-ui";
import { isBrowserOffline } from "./offlineQueue";

export function stalenessFromTransport(input: {
  asOf?: string | null;
  thresholdSeconds: number;
  fromOfflineCache?: boolean;
  sourceReachable?: boolean;
  now?: number;
}): StalenessState {
  const sourceReachable =
    input.sourceReachable ?? (!isBrowserOffline() && !input.fromOfflineCache);
  return classifyStaleness({
    asOf: input.asOf,
    thresholdSeconds: input.thresholdSeconds,
    sourceReachable,
    fromOfflineCache: input.fromOfflineCache === true,
    now: input.now,
  });
}

export function responseWasOfflineCache(res: Response | null | undefined): boolean {
  return res?.headers?.get("X-Impilo-Offline-Cache") === "1";
}
