"use client";

/**
 * Integration Status — live integration-hub route registry (Phase D).
 * Route: /admin/integration-status
 */

import { Globe, Info, Loader2, Plug } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { JsonApiDataTable } from "@/components/common/JsonApiDataTable";
import { INTEGRATION_DEAD_LETTER_COLUMNS, INTEGRATION_ROUTE_COLUMNS } from "@/lib/json-api/generic-table-columns";
import { IntegrationSyncReplayOrchestrationPanel } from "@/components/platform/IntegrationSyncReplayOrchestrationPanel";
import { useIntegrationHubDeadLetters, useIntegrationHubRoutes } from "@/hooks/queries/useIntegrationHub";

function summarizeRoutes(payload: unknown): { rows: Array<Record<string, unknown>>; raw: string } {
  if (payload == null) return { rows: [], raw: "" };
  if (Array.isArray(payload)) {
    return {
      rows: payload.filter((x) => x && typeof x === "object") as Array<Record<string, unknown>>,
      raw: "",
    };
  }
  if (typeof payload === "object") {
    return { rows: [], raw: typeof payload === "object" ? "" : String(payload) };
  }
  return { rows: [], raw: String(payload) };
}

export default function IntegrationStatusPage() {
  const routesQ = useIntegrationHubRoutes();
  const deadQ = useIntegrationHubDeadLetters(0, 10);

  const routePayload = routesQ.data?.data;
  const { rows: routeRows, raw: routeRaw } = summarizeRoutes(routePayload);
  const deadPayload = deadQ.data?.data;

  return (
    <AppLayout>
      <PageShell title="Integration Status" subtitle="National integration hub — route registry and recent dead letters">
        <div className="space-y-6">
          <IntegrationSyncReplayOrchestrationPanel />
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Globe className="w-5 h-5 text-teal-600" />
              <h2 className="text-lg font-semibold text-foreground">Integration hub</h2>
            </div>
            <span className="text-xs text-muted-foreground">GET /internal/v1/integration-hub/*</span>
          </div>

          <div className="rounded-lg border border-primary/25 bg-primary-soft/90 p-4 flex gap-3">
            <Info className="w-5 h-5 text-primary shrink-0 mt-0.5" />
            <div className="text-sm text-impilo-800">
              <p className="font-medium">Live data from Experience BFF</p>
              <p className="mt-2 text-xs leading-relaxed text-impilo-800/90">
                Route rows are loaded from <code className="text-[11px]">GET /internal/v1/integration-hub/routes</code>.
                Dead letters preview uses{" "}
                <code className="text-[11px]">GET /internal/v1/integration-hub/deadletters</code>. Other external system
                probes remain product backlog until dedicated health endpoints exist.
              </p>
            </div>
          </div>

          <div className="grid grid-cols-3 gap-4">
            <div className="bg-card rounded-lg p-4 flex items-center gap-3 border border-border">
              <Plug className="w-8 h-8 text-teal-600" />
              <div>
                <p className="text-2xl font-bold text-foreground">
                  {routesQ.isLoading ? "…" : String(routeRows.length || (routeRaw ? "1" : "0"))}
                </p>
                <p className="text-xs text-muted-foreground">Hub routes (parsed rows)</p>
              </div>
            </div>
            <div className="bg-card rounded-lg p-4 flex items-center gap-3 border border-border">
              <Plug className="w-8 h-8 text-amber-600" />
              <div>
                <p className="text-2xl font-bold text-foreground">
                  {deadQ.isLoading ? "…" : deadPayload && typeof deadPayload === "object" && "totalElements" in deadPayload
                    ? String((deadPayload as { totalElements?: number }).totalElements ?? "—")
                    : "—"}
                </p>
                <p className="text-xs text-muted-foreground">Dead letters (page 0)</p>
              </div>
            </div>
            <div className="bg-card rounded-lg p-4 flex items-center gap-3 border border-border">
              <Plug className="w-8 h-8 text-muted-foreground" />
              <div>
                <p className="text-2xl font-bold text-muted-foreground">—</p>
                <p className="text-xs text-muted-foreground">External probes (not tracked)</p>
              </div>
            </div>
          </div>

          <div className="bg-card rounded-lg border border-border overflow-hidden">
            <div className="px-4 py-3 border-b flex items-center justify-between">
              <h3 className="text-sm font-semibold text-foreground">Route registry</h3>
              {routesQ.isFetching && <Loader2 className="h-4 w-4 animate-spin text-muted-foreground" aria-hidden />}
            </div>
            {routesQ.isError && (
              <div className="px-4 py-6 text-sm text-danger">
                Could not load routes. Confirm you have an admin-equivalent role and the integration-hub service is
                reachable.
              </div>
            )}
            {!routesQ.isError && routeRows.length > 0 && (
              <div className="p-4">
                <JsonApiDataTable
                  data={routeRows.slice(0, 50)}
                  columns={INTEGRATION_ROUTE_COLUMNS}
                  emptyTitle="No routes"
                />
              </div>
            )}
            {!routesQ.isError && routeRows.length === 0 && routeRaw && (
              <pre className="p-4 text-xs overflow-x-auto bg-background text-foreground max-h-96">{routeRaw}</pre>
            )}
            {!routesQ.isError && routeRows.length === 0 && !routeRaw && !routesQ.isLoading && (
              <div className="px-4 py-12 text-center text-sm text-muted-foreground">No routes returned (empty registry).</div>
            )}
            {routesQ.isLoading && (
              <div className="px-4 py-12 flex justify-center text-muted-foreground text-sm">
                <Loader2 className="h-5 w-5 animate-spin mr-2" /> Loading routes…
              </div>
            )}
          </div>

          <div className="bg-card rounded-lg border border-border overflow-hidden">
            <div className="px-4 py-3 border-b">
              <h3 className="text-sm font-semibold text-foreground">Dead letters (preview)</h3>
            </div>
            {deadQ.isError && (
              <div className="px-4 py-6 text-sm text-warning-foreground">Dead letter list unavailable (hub down or forbidden).</div>
            )}
            {!deadQ.isError && (
              <div className="p-4">
                <JsonApiDataTable
                  data={deadQ.data}
                  columns={INTEGRATION_DEAD_LETTER_COLUMNS}
                  isLoading={deadQ.isPending}
                  error={deadQ.error as Error | null}
                  emptyTitle="No dead letters on this page"
                />
              </div>
            )}
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
