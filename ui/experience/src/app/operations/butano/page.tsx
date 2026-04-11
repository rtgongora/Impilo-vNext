"use client";

/**
 * SHR Operations (BUTANO) — absorbs ops-console sidecar
 * Shared Health Record operations: FHIR stats, reconciliation, trigger management.
 * Route: /operations/butano | Zone: operations | Guard: role ADMIN
 */

import { Database, RefreshCcw, Activity, Settings, BarChart3 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

const SECTIONS = [
  { label: "FHIR Statistics", description: "Resource counts, storage usage, and throughput metrics", Icon: BarChart3, color: "bg-blue-50 text-blue-600" },
  { label: "Reconciliation", description: "Reconcile SHR records with source-of-truth systems", Icon: RefreshCcw, color: "bg-amber-50 text-amber-600" },
  { label: "Subscription Triggers", description: "Manage FHIR subscription and event triggers", Icon: Activity, color: "bg-green-50 text-green-600" },
  { label: "Configuration", description: "SHR storage, replication, and retention settings", Icon: Settings, color: "bg-slate-100 text-slate-600" },
];

export default function ButanoOpsPage() {
  return (
    <AppLayout>
      <PageShell
        title="SHR Operations"
        subtitle="BUTANO (HAPI FHIR) reconciliation, FHIR stats, and trigger management"
        icon={<Database className="h-6 w-6" />}
      >
        <div className="space-y-6">
          {/* Summary metrics */}
          <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
            {[
              { label: "Total Resources", value: "0" },
              { label: "Patients (CPID)", value: "0" },
              { label: "Observations", value: "0" },
              { label: "Sync Errors", value: "0" },
            ].map(({ label, value }) => (
              <div key={label} className="rounded-lg border border-gray-200 bg-white p-4">
                <p className="text-sm text-gray-500">{label}</p>
                <p className="text-2xl font-bold text-gray-900">{value}</p>
              </div>
            ))}
          </div>

          {/* Operations sections */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {SECTIONS.map(({ label, description, Icon, color }) => (
              <div
                key={label}
                className="rounded-lg border border-gray-200 bg-white p-5 hover:border-slate-400 hover:shadow-sm transition-all cursor-pointer"
              >
                <div className="flex items-center gap-3 mb-2">
                  <div className={`rounded-lg p-2 ${color.split(" ")[0]}`}>
                    <Icon className={`h-5 w-5 ${color.split(" ")[1]}`} />
                  </div>
                  <h3 className="font-semibold text-gray-900">{label}</h3>
                </div>
                <p className="text-sm text-gray-600">{description}</p>
              </div>
            ))}
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
