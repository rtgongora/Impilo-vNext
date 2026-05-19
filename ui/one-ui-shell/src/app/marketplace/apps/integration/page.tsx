"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { Activity, AlertTriangle, ChevronLeft, Loader2, Network, Webhook } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useEventCatalogue,
  useExternalApplications,
  type EventCatalogueEntry,
  type ExternalApplication,
} from "@/hooks/queries/useHealthOsLauncher";

export default function IntegrationOperationsPage() {
  const externalAppsQuery = useExternalApplications();
  const [classification, setClassification] = useState<"INTERNAL_PLATFORM" | "EXTERNALLY_PUBLISHABLE" | "ALL">("ALL");
  const eventsQuery = useEventCatalogue({
    classification: classification === "ALL" ? undefined : classification,
  });

  const apps = externalAppsQuery.data ?? [];
  const events = eventsQuery.data ?? [];

  const totals = useMemo(() => {
    const all = apps;
    return {
      total: all.length,
      active: all.filter((a) => a.status === "ACTIVE").length,
      suspended: all.filter((a) => a.status === "SUSPENDED").length,
      pending: all.filter((a) => a.status === "PENDING_APPROVAL" || a.status === "REGISTERED").length,
    };
  }, [apps]);

  return (
    <AppLayout>
      <PageShell
        title="Integration operations"
        subtitle="Registered external applications, integration contracts, webhook deliveries, and the curated externally-publishable event catalogue."
      >
        <div className="space-y-6">
          <Link href="/marketplace/apps" className="inline-flex items-center gap-1 text-sm text-impilo-700 hover:underline">
            <ChevronLeft className="h-4 w-4" /> Back to marketplace
          </Link>

          <section className="grid gap-3 rounded-2xl border border-slate-200 bg-white p-4 shadow-sm md:grid-cols-4">
            <Tile icon={Network} label="Registered apps" value={totals.total} loading={externalAppsQuery.isLoading} error={externalAppsQuery.isError} />
            <Tile icon={Activity} label="Active" value={totals.active} tone="emerald" loading={externalAppsQuery.isLoading} error={externalAppsQuery.isError} />
            <Tile icon={AlertTriangle} label="Suspended" value={totals.suspended} tone="amber" loading={externalAppsQuery.isLoading} error={externalAppsQuery.isError} />
            <Tile icon={Webhook} label="Pending approval" value={totals.pending} tone="violet" loading={externalAppsQuery.isLoading} error={externalAppsQuery.isError} />
          </section>

          <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
            <header className="border-b border-slate-100 px-4 py-3">
              <h3 className="text-sm font-semibold text-slate-900">Registered external applications</h3>
              <p className="text-xs text-slate-500">
                Pulled from <code className="text-[10px]">/internal/v1/marketplace/integration/external-apps</code>.
                No secrets are exposed; OAuth client ids and OAuth secret references are stored in vault-kms.
              </p>
            </header>
            <table className="w-full text-left text-sm">
              <thead className="bg-slate-50 text-xs font-semibold uppercase tracking-wider text-slate-500">
                <tr>
                  <th className="px-4 py-2">App</th>
                  <th className="px-4 py-2">Publisher</th>
                  <th className="px-4 py-2">Category</th>
                  <th className="px-4 py-2">Env</th>
                  <th className="px-4 py-2">Auth</th>
                  <th className="px-4 py-2">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {externalAppsQuery.isLoading ? (
                  <tr>
                    <td colSpan={6} className="px-4 py-6 text-center text-slate-500">
                      <Loader2 className="mx-auto h-4 w-4 animate-spin" />
                    </td>
                  </tr>
                ) : apps.length === 0 ? (
                  <tr>
                    <td colSpan={6} className="px-4 py-6 text-center text-slate-500">
                      No external applications registered yet.
                    </td>
                  </tr>
                ) : (
                  apps.map((a: ExternalApplication) => (
                    <tr key={a.id}>
                      <td className="px-4 py-3 text-sm text-slate-800">
                        <div className="font-medium">{a.name}</div>
                        <div className="text-[10px] text-slate-500">{a.appCode}</div>
                      </td>
                      <td className="px-4 py-3 text-xs text-slate-600">{a.publisherName}</td>
                      <td className="px-4 py-3 text-xs text-slate-600">{a.integrationCategory}</td>
                      <td className="px-4 py-3 text-xs text-slate-600">{a.environment}</td>
                      <td className="px-4 py-3 text-xs text-slate-600">{a.authenticationMethod}</td>
                      <td className="px-4 py-3">
                        <StatusBadge value={a.status} />
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </section>

          <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
            <header className="flex items-center justify-between border-b border-slate-100 px-4 py-3">
              <div>
                <h3 className="text-sm font-semibold text-slate-900">Event catalogue</h3>
                <p className="text-xs text-slate-500">Internal platform events vs. externally publishable events.</p>
              </div>
              <select
                value={classification}
                onChange={(e) => setClassification(e.target.value as typeof classification)}
                className="rounded-lg border border-slate-200 px-2 py-1 text-xs"
              >
                <option value="ALL">All classifications</option>
                <option value="INTERNAL_PLATFORM">Internal platform</option>
                <option value="EXTERNALLY_PUBLISHABLE">Externally publishable</option>
              </select>
            </header>
            <table className="w-full text-left text-sm">
              <thead className="bg-slate-50 text-xs font-semibold uppercase tracking-wider text-slate-500">
                <tr>
                  <th className="px-4 py-2">Topic</th>
                  <th className="px-4 py-2">Owner</th>
                  <th className="px-4 py-2">Sensitivity</th>
                  <th className="px-4 py-2">PII / PHI</th>
                  <th className="px-4 py-2">Classification</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {eventsQuery.isLoading ? (
                  <tr>
                    <td colSpan={5} className="px-4 py-6 text-center text-slate-500">
                      <Loader2 className="mx-auto h-4 w-4 animate-spin" />
                    </td>
                  </tr>
                ) : events.length === 0 ? (
                  <tr>
                    <td colSpan={5} className="px-4 py-6 text-center text-slate-500">
                      No events available with the current filter.
                    </td>
                  </tr>
                ) : (
                  events.map((e: EventCatalogueEntry) => (
                    <tr key={e.id}>
                      <td className="px-4 py-3 font-mono text-xs text-slate-700">{e.topic}</td>
                      <td className="px-4 py-3 text-xs text-slate-600">{e.ownerServiceId}</td>
                      <td className="px-4 py-3 text-xs text-slate-600">{e.sensitivityTier}</td>
                      <td className="px-4 py-3 text-xs text-slate-600">
                        {e.containsPhi ? "PHI" : e.containsPii ? "PII" : "—"}
                      </td>
                      <td className="px-4 py-3">
                        <ClassificationBadge value={e.classification} />
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </section>
        </div>
      </PageShell>
    </AppLayout>
  );
}

function StatusBadge({ value }: { value: string }) {
  const tone: Record<string, string> = {
    ACTIVE: "bg-emerald-50 text-emerald-700",
    REGISTERED: "bg-blue-50 text-blue-700",
    PENDING_APPROVAL: "bg-amber-50 text-amber-700",
    SUSPENDED: "bg-rose-50 text-rose-700",
    REVOKED: "bg-slate-100 text-slate-500",
  };
  return (
    <span className={`rounded-full px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide ${tone[value] ?? "bg-slate-100 text-slate-700"}`}>
      {value.replace("_", " ")}
    </span>
  );
}

function ClassificationBadge({ value }: { value: string }) {
  const tone: Record<string, string> = {
    INTERNAL_PLATFORM: "bg-slate-100 text-slate-700",
    EXTERNALLY_PUBLISHABLE: "bg-emerald-50 text-emerald-700",
  };
  return (
    <span className={`rounded-full px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide ${tone[value] ?? "bg-slate-100 text-slate-700"}`}>
      {value.replace("_", " ")}
    </span>
  );
}

function Tile({
  icon: Icon,
  label,
  value,
  loading,
  error,
  tone = "slate",
}: {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  value: number;
  loading: boolean;
  error: boolean;
  tone?: "slate" | "emerald" | "amber" | "violet";
}) {
  const toneMap: Record<string, string> = {
    slate: "bg-slate-50 text-slate-700",
    emerald: "bg-emerald-50 text-emerald-700",
    amber: "bg-amber-50 text-amber-700",
    violet: "bg-violet-50 text-violet-700",
  };
  return (
    <div className="flex items-center gap-3">
      <div className={`flex h-10 w-10 items-center justify-center rounded-xl ${toneMap[tone]}`}>
        <Icon className="h-5 w-5" />
      </div>
      <div>
        <div className="text-xs font-medium uppercase tracking-wide text-slate-500">{label}</div>
        <div className="text-2xl font-semibold text-slate-900">{error ? "—" : loading ? "…" : value}</div>
      </div>
    </div>
  );
}
