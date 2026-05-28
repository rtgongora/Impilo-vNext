"use client";

import { useCallback, useMemo } from "react";
import { useIntegrationHubRoutes } from "@/hooks/queries/useIntegrationHub";
import {
  normalizeIntegrationRouteRows,
  resolveTileIntegrationHealth,
  type TileIntegrationHealth,
} from "@/features/production-command-centre/tile-integration-health";

export interface CommandCentreHealthSnapshot {
  integrationHubAvailable: boolean;
  routeCount: number;
  isLoading: boolean;
  isError: boolean;
  getTileHealth: (keywords?: string[]) => TileIntegrationHealth;
}

export function useCommandCentreHealth(): CommandCentreHealthSnapshot {
  const routesQ = useIntegrationHubRoutes();

  const routeRows = useMemo(
    () => normalizeIntegrationRouteRows(routesQ.data?.data),
    [routesQ.data?.data],
  );

  const hubAvailable = routesQ.isSuccess && !routesQ.isError;

  const getTileHealth = useCallback(
    (keywords?: string[]) =>
      resolveTileIntegrationHealth(keywords, routeRows, hubAvailable, routesQ.isLoading),
    [routeRows, hubAvailable, routesQ.isLoading],
  );

  return {
    integrationHubAvailable: hubAvailable,
    routeCount: routeRows.length,
    isLoading: routesQ.isLoading,
    isError: routesQ.isError,
    getTileHealth,
  };
}
