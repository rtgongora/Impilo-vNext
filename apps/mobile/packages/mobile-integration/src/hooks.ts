/**
 * Lightweight React hook for Integration Service mobile status.
 *
 * Loads the integration status list for the caller (PROVIDER or CITIZEN),
 * exposes a refresh function and a graceful "not yet loaded" state.
 */

import { useCallback, useEffect, useState } from "react";
import { integrationMobileClient, type IntegrationCaller } from "./client";
import type { IntegrationStatusSummary } from "./types";

export interface UseIntegrationStatusesState {
  statuses: IntegrationStatusSummary[];
  loading: boolean;
  /** True if the BFF returned no data — could be "service is fine, nothing wired" or "BFF endpoint missing". */
  empty: boolean;
  refresh: () => Promise<void>;
}

export function useIntegrationStatuses(
  caller: IntegrationCaller
): UseIntegrationStatusesState {
  const [statuses, setStatuses] = useState<IntegrationStatusSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [loaded, setLoaded] = useState(false);

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      const rows = await integrationMobileClient.listStatuses(caller);
      setStatuses(rows);
    } finally {
      setLoading(false);
      setLoaded(true);
    }
  }, [caller]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  return {
    statuses,
    loading,
    empty: loaded && statuses.length === 0,
    refresh,
  };
}
