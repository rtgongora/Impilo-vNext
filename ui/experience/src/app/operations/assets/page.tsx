"use client";

/**
 * Asset Management — absorbs ops-console sidecar
 * Track, assign, and lifecycle-manage organizational assets.
 * Route: /operations/assets | Zone: operations | Guard: role ADMIN
 */

import { Package, Search, Plus, Filter, BarChart3 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

export default function AssetsPage() {
  return (
    <AppLayout>
      <PageShell
        title="Asset Management"
        subtitle="Track, assign, and lifecycle-manage organizational assets"
        icon={<Package className="h-6 w-6" />}
      >
        <div className="space-y-6">
          {/* Summary */}
          <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
            {[
              { label: "Total Assets", value: "0" },
              { label: "In Service", value: "0" },
              { label: "In Maintenance", value: "0" },
              { label: "Retired", value: "0" },
            ].map(({ label, value }) => (
              <div key={label} className="rounded-lg border border-gray-200 bg-white p-4">
                <p className="text-sm text-gray-500">{label}</p>
                <p className="text-2xl font-bold text-gray-900">{value}</p>
              </div>
            ))}
          </div>

          {/* Actions bar */}
          <div className="flex items-center justify-between">
            <div className="flex gap-3">
              <div className="relative w-80">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" />
                <input
                  type="text"
                  placeholder="Search assets by name, tag, or serial..."
                  className="w-full rounded-lg border border-gray-300 py-2 pl-10 pr-4 text-sm focus:border-slate-500 focus:outline-none focus:ring-1 focus:ring-slate-500"
                />
              </div>
              <button className="inline-flex items-center gap-2 rounded-lg border border-gray-300 px-3 py-2 text-sm text-gray-700 hover:bg-gray-50 transition-colors">
                <Filter className="h-4 w-4" />
                Filters
              </button>
            </div>
            <div className="flex items-center gap-2">
              <button className="inline-flex items-center gap-2 rounded-lg border border-gray-300 px-3 py-2 text-sm text-gray-700 hover:bg-gray-50 transition-colors">
                <BarChart3 className="h-4 w-4" />
                Reports
              </button>
              <button className="inline-flex items-center gap-2 rounded-lg bg-slate-700 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800 transition-colors">
                <Plus className="h-4 w-4" />
                Register Asset
              </button>
            </div>
          </div>

          {/* Empty state */}
          <div className="rounded-lg border border-dashed border-gray-300 bg-gray-50 p-12 text-center">
            <Package className="mx-auto h-12 w-12 text-gray-400" />
            <h3 className="mt-4 text-sm font-semibold text-gray-900">No assets registered</h3>
            <p className="mt-2 text-sm text-gray-600">
              Register organizational assets to track location, assignment, maintenance, and lifecycle status.
            </p>
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
