"use client";

/**
 * Integration Status — External integration registry not implemented on Experience BFF.
 * Route: /admin/integration-status | pageTitle: "Integration Status"
 */

import { Globe, Plug, Info } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

export default function IntegrationStatusPage() {
  return (
    <AppLayout>
      <PageShell title="Integration Status" subtitle="External system connectivity (not yet on BFF)">
        <div className="space-y-6">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Globe className="w-5 h-5 text-teal-600" />
              <h2 className="text-lg font-semibold text-gray-900">Integration health</h2>
            </div>
            <span className="text-xs text-gray-400">Filters: not available</span>
          </div>

          <div className="rounded-lg border border-amber-200 bg-amber-50/90 p-4 flex gap-3">
            <Info className="w-5 h-5 text-amber-700 shrink-0 mt-0.5" />
            <div className="text-sm text-amber-950">
              <p className="font-medium">No live integration registry on Experience BFF</p>
              <p className="mt-2 text-xs leading-relaxed text-amber-900/90">
                This branch does not expose <code className="text-[11px]">GET /internal/v1/admin/integrations</code> or
                equivalent connectivity probes. Previous mock rows (DHIS2, MOSIP, gateways, etc.) were illustrative only and
                have been removed. When a tenant-scoped integration table and test endpoints exist, this page should list
                them and wire Test / Retry to real mutations.
              </p>
            </div>
          </div>

          <div className="grid grid-cols-3 gap-4">
            <div className="bg-gray-50 rounded-lg p-4 flex items-center gap-3 border border-gray-200">
              <Plug className="w-8 h-8 text-gray-400" />
              <div>
                <p className="text-2xl font-bold text-gray-400">—</p>
                <p className="text-xs text-gray-500">Connected (not tracked)</p>
              </div>
            </div>
            <div className="bg-gray-50 rounded-lg p-4 flex items-center gap-3 border border-gray-200">
              <Plug className="w-8 h-8 text-gray-400" />
              <div>
                <p className="text-2xl font-bold text-gray-400">—</p>
                <p className="text-xs text-gray-500">Degraded (not tracked)</p>
              </div>
            </div>
            <div className="bg-gray-50 rounded-lg p-4 flex items-center gap-3 border border-gray-200">
              <Plug className="w-8 h-8 text-gray-400" />
              <div>
                <p className="text-2xl font-bold text-gray-400">—</p>
                <p className="text-xs text-gray-500">Disconnected (not tracked)</p>
              </div>
            </div>
          </div>

          <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
            <div className="px-4 py-12 text-center text-sm text-gray-500">
              <p className="font-medium text-gray-700">No integration rows to display</p>
              <p className="mt-2 text-xs max-w-lg mx-auto text-gray-500">
                Test connection and retry actions are disabled until real endpoints exist so operators are not misled by
                simulated success.
              </p>
            </div>
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
