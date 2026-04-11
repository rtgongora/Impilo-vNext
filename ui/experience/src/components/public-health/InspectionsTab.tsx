"use client";

import { useState } from "react";
import { ClipboardCheck, Loader2, MapPin, Plus } from "lucide-react";
import { usePublicHealthSites } from "@/hooks/queries/usePublicHealth";

/**
 * Inspection register & enforcement: **unsupported** (no inspections API on public-health BFF).
 * **Indawo premises** list is real (same as Field Operations).
 */
export function InspectionsTab() {
  const [activeSubTab, setActiveSubTab] = useState<"premises" | "inspections" | "enforcement" | "compliance">("premises");
  const { data: sites = [], isLoading, isError } = usePublicHealthSites();

  return (
    <div className="space-y-4">
      <div className="rounded-lg border border-blue-200 bg-blue-50/90 p-3 text-xs text-blue-950">
        <strong>Inspections / enforcement:</strong> not available on{" "}
        <code className="text-[10px]">/internal/v1/public-health/*</code>. Use the <strong>Premises (Indawo)</strong> tab for
        live site registry data. Scheduling inspections, scores, and notices require dedicated services + BFF routes.
      </div>

      <div className="flex gap-1 border-b border-gray-200 flex-wrap">
        {[
          { key: "premises" as const, label: "Premises (Indawo)" },
          { key: "inspections" as const, label: "Inspection register" },
          { key: "enforcement" as const, label: "Enforcement" },
          { key: "compliance" as const, label: "Compliance overview" },
        ].map((tab) => (
          <button
            key={tab.key}
            onClick={() => setActiveSubTab(tab.key)}
            className={`px-3 py-2 text-sm font-medium border-b-2 transition-colors ${
              activeSubTab === tab.key
                ? "border-amber-600 text-amber-600"
                : "border-transparent text-gray-500 hover:text-gray-700"
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {activeSubTab === "premises" && (
        <div className="bg-white rounded-lg border border-gray-200">
          <div className="px-4 py-3 border-b flex items-center gap-2">
            <MapPin className="h-4 w-4 text-emerald-600" />
            <div>
              <h4 className="text-sm font-semibold text-gray-900">Registered premises</h4>
              <p className="text-xs text-gray-500">GET /internal/v1/public-health/sites → indawo-service</p>
            </div>
          </div>
          <div className="p-4">
            {isLoading && (
              <div className="flex items-center gap-2 text-sm text-gray-500 py-8 justify-center">
                <Loader2 className="h-5 w-5 animate-spin" /> Loading sites…
              </div>
            )}
            {isError && <p className="text-sm text-red-600 text-center py-4">Failed to load sites.</p>}
            {!isLoading && !isError && sites.length === 0 && (
              <p className="text-sm text-gray-500 text-center py-6">No sites returned.</p>
            )}
            {!isLoading && !isError && sites.length > 0 && (
              <ul className="divide-y divide-gray-100 max-h-[480px] overflow-y-auto">
                {sites.map((s) => (
                  <li key={s.id} className="py-3 flex flex-wrap justify-between gap-2">
                    <div>
                      <p className="font-medium text-sm text-gray-900">{s.name}</p>
                      <p className="text-xs text-gray-500">
                        {s.siteType} · {s.jurisdiction} · {s.operationalStatus}
                      </p>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>
      )}

      {activeSubTab === "inspections" && (
        <div className="bg-white rounded-lg border border-gray-200 p-8 text-center">
          <ClipboardCheck className="h-10 w-10 text-gray-300 mx-auto mb-3" />
          <h4 className="text-sm font-semibold text-gray-900">Inspection register</h4>
          <p className="text-xs text-gray-600 mt-2 max-w-lg mx-auto">
            <strong>Unsupported.</strong> Blocker: list/schedule/complete inspection endpoints (e.g.{" "}
            <code className="text-[10px]">GET /internal/v1/public-health/inspections</code>) not present. The prior demo table
            was removed.
          </p>
        </div>
      )}

      {activeSubTab === "enforcement" && (
        <div className="bg-white rounded-lg border border-gray-200 p-8 text-center">
          <Plus className="h-10 w-10 text-gray-300 mx-auto mb-3" />
          <h4 className="text-sm font-semibold text-gray-900">Enforcement actions</h4>
          <p className="text-xs text-gray-600 mt-2 max-w-lg mx-auto">
            <strong>Unsupported.</strong> No enforcement notice API on the public-health Experience contract. Demo notices
            were removed.
          </p>
        </div>
      )}

      {activeSubTab === "compliance" && (
        <div className="bg-white rounded-lg border border-gray-200 p-8 text-center">
          <h4 className="text-sm font-semibold text-gray-900">Compliance overview by category</h4>
          <p className="text-xs text-gray-600 mt-2 max-w-lg mx-auto">
            <strong>Unsupported.</strong> Aggregate compliance rates were illustrative only; removed pending a reporting or
            inspections backend.
          </p>
        </div>
      )}
    </div>
  );
}
