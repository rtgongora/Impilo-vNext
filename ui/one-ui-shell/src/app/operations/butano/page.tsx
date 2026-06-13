"use client";

/**
 * SHR Operations (BUTANO) — absorbs ops-console sidecar
 * Shared Health Record operations: FHIR stats, reconciliation, trigger management.
 * Route: /operations/butano | Zone: operations | Guard: role ADMIN
 */

import { Database, RefreshCcw, Activity, Settings, BarChart3, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useFhirCapabilityStatement } from "@/hooks/queries/useFhirInterop";

const SECTIONS = [
  { label: "FHIR Statistics", description: "Resource counts, storage usage, and throughput metrics", Icon: BarChart3, color: "bg-primary-soft text-primary" },
  { label: "Reconciliation", description: "Reconcile SHR records with source-of-truth systems", Icon: RefreshCcw, color: "bg-warning-soft text-amber-600" },
  { label: "Subscription Triggers", description: "Manage FHIR subscription and event triggers", Icon: Activity, color: "bg-green-50 text-green-600" },
  { label: "Configuration", description: "SHR storage, replication, and retention settings", Icon: Settings, color: "bg-neutral-100 text-muted-foreground" },
];

export default function ButanoOpsPage() {
  const { data: capabilityData, isLoading: capLoading } = useFhirCapabilityStatement();
  const capability = (capabilityData?.data ?? {}) as Record<string, unknown>;
  const fhirVersion = typeof capability.fhirVersion === "string" ? capability.fhirVersion : "--";
  const resourceTypes = Array.isArray(capability.rest)
    ? ((capability.rest as Record<string, unknown>[])[0]?.resource as unknown[] | undefined)?.length ?? 0
    : 0;
  const serverStatus = capability.status ?? (Object.keys(capability).length > 0 ? "active" : "--");

  return (
    <AppLayout>
      <PageShell
        title="SHR Operations"
        subtitle="BUTANO (HAPI FHIR) reconciliation, FHIR stats, and trigger management"
        serviceSlug="butano"
      >
        <div className="space-y-6">
          {/* Summary metrics */}
          <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
            {[
              { label: "FHIR Version", value: capLoading ? "..." : fhirVersion },
              { label: "Resource Types", value: capLoading ? "..." : String(resourceTypes) },
              { label: "Server Status", value: capLoading ? "..." : String(serverStatus) },
              { label: "Sync Errors", value: "0" },
            ].map(({ label, value }) => (
              <div key={label} className="rounded-lg border border-border bg-card p-4">
                <p className="text-sm text-muted-foreground">{label}</p>
                <p className="text-2xl font-bold text-foreground">{capLoading ? <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" /> : value}</p>
              </div>
            ))}
          </div>

          {/* Operations sections */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {SECTIONS.map(({ label, description, Icon, color }) => (
              <div
                key={label}
                className="rounded-lg border border-border bg-card p-5 hover:border-slate-400 hover:shadow-sm transition-all cursor-pointer"
              >
                <div className="flex items-center gap-3 mb-2">
                  <div className={`rounded-lg p-2 ${color.split(" ")[0]}`}>
                    <Icon className={`h-5 w-5 ${color.split(" ")[1]}`} />
                  </div>
                  <h3 className="font-semibold text-foreground">{label}</h3>
                </div>
                <p className="text-sm text-muted-foreground">{description}</p>
              </div>
            ))}
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
