"use client";

/**
 * Lab Reconciliation — absorbs oros-web sidecar
 * Reconcile lab orders with external LIS systems.
 * Route: /lab/reconciliation | Zone: lab | Guard: shift
 */

import { RefreshCcw, Search, Filter, AlertTriangle, CheckCircle2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

export default function LabReconciliationPage() {
  return (
    <AppLayout>
      <PageShell
        title="Lab Reconciliation"
        subtitle="Reconcile lab orders and results with external Laboratory Information Systems"
        icon={<RefreshCcw className="h-6 w-6" />}
      >
        <div className="space-y-6">
          {/* Status summary */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            {[
              { label: "Matched", value: "0", Icon: CheckCircle2, color: "bg-green-50 text-green-600" },
              { label: "Unmatched", value: "0", Icon: AlertTriangle, color: "bg-amber-50 text-amber-600" },
              { label: "Discrepancies", value: "0", Icon: AlertTriangle, color: "bg-red-50 text-red-600" },
            ].map(({ label, value, Icon, color }) => (
              <div key={label} className="rounded-lg border border-gray-200 bg-white p-4">
                <div className="flex items-center justify-between mb-2">
                  <span className="text-sm text-gray-500">{label}</span>
                  <div className={`rounded-lg p-1.5 ${color.split(" ")[0]}`}>
                    <Icon className={`h-4 w-4 ${color.split(" ")[1]}`} />
                  </div>
                </div>
                <p className="text-2xl font-bold text-gray-900">{value}</p>
              </div>
            ))}
          </div>

          {/* Search and filter */}
          <div className="flex gap-3">
            <div className="relative flex-1">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" />
              <input
                type="text"
                placeholder="Search by order ID or accession number..."
                className="w-full rounded-lg border border-gray-300 py-2 pl-10 pr-4 text-sm focus:border-violet-500 focus:outline-none focus:ring-1 focus:ring-violet-500"
              />
            </div>
            <button className="inline-flex items-center gap-2 rounded-lg border border-gray-300 px-4 py-2 text-sm text-gray-700 hover:bg-gray-50 transition-colors">
              <Filter className="h-4 w-4" />
              Filters
            </button>
            <button className="inline-flex items-center gap-2 rounded-lg bg-violet-600 px-4 py-2 text-sm font-medium text-white hover:bg-violet-700 transition-colors">
              <RefreshCcw className="h-4 w-4" />
              Run Reconciliation
            </button>
          </div>

          {/* Empty state */}
          <div className="rounded-lg border border-dashed border-gray-300 bg-gray-50 p-12 text-center">
            <RefreshCcw className="mx-auto h-12 w-12 text-gray-400" />
            <h3 className="mt-4 text-sm font-semibold text-gray-900">No reconciliation data</h3>
            <p className="mt-2 text-sm text-gray-600">
              Run a reconciliation to compare orders and results between Health OS and your external LIS.
            </p>
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
