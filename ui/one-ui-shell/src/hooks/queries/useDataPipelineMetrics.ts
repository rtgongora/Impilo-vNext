"use client";

import { useMemo } from "react";
import { useIntegrationHubDeadLetters, useIntegrationHubRoutes } from "@/hooks/queries/useIntegrationHub";
import { useCoreTransactionFeed } from "@/hooks/queries/useCoreTransactionExperience";
import { normalizeIntegrationRouteRows } from "@/features/production-command-centre/tile-integration-health";

export interface DataPipelineMetricsSnapshot {
  integrationHubRouteCount: number | null;
  deadLetterTotal: number | null;
  coreTransactionCount: number | null;
  coreTransactionFailureCount: number | null;
  isLoading: boolean;
  isPartial: boolean;
  hubAvailable: boolean;
  transactionsAvailable: boolean;
}

export function extractDeadLetterTotal(payload: unknown): number | null {
  if (payload == null) return null;
  if (typeof payload === "object") {
    const root = payload as Record<string, unknown>;
    if (typeof root.totalElements === "number") return root.totalElements;
    if (Array.isArray(root.content)) return root.content.length;
    if (Array.isArray(root.items)) return root.items.length;
  }
  if (Array.isArray(payload)) return payload.length;
  return null;
}

export function useDataPipelineMetrics(): DataPipelineMetricsSnapshot {
  const routesQ = useIntegrationHubRoutes();
  const deadQ = useIntegrationHubDeadLetters(0, 1);
  const txQ = useCoreTransactionFeed();

  const integrationHubRouteCount = useMemo(() => {
    if (!routesQ.isSuccess) return null;
    return normalizeIntegrationRouteRows(routesQ.data?.data).length;
  }, [routesQ.isSuccess, routesQ.data?.data]);

  const deadLetterTotal = useMemo(() => {
    if (!deadQ.isSuccess) return null;
    return extractDeadLetterTotal(deadQ.data?.data);
  }, [deadQ.isSuccess, deadQ.data?.data]);

  const coreTransactionCount = txQ.isSuccess ? txQ.items.length : null;
  const coreTransactionFailureCount = txQ.isSuccess
    ? txQ.items.filter((item) => item.failureModes.length > 0).length
    : null;

  const hubAvailable = routesQ.isSuccess && !routesQ.isError;
  const transactionsAvailable = txQ.isSuccess && !txQ.isError;

  return {
    integrationHubRouteCount,
    deadLetterTotal,
    coreTransactionCount,
    coreTransactionFailureCount,
    isLoading: routesQ.isLoading || deadQ.isLoading || txQ.isLoading,
    isPartial: !hubAvailable || !transactionsAvailable,
    hubAvailable,
    transactionsAvailable,
  };
}
