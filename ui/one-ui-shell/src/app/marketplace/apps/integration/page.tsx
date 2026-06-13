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
          <Link href="/marketplace/apps" className="inline-flex items-center gap-1 text-sm text-primary-hover hover:underline">
            <ChevronLeft className="h-4 w-4" /> Back to marketplace
          </Link>

          <section className="grid gap-3 rounded-2xl border border-border bg-card p-4 shadow-sm md:grid-cols-4">
            <Tile icon={Network} label="Registered apps" value={totals.total} loading={externalAppsQuery.isLoading} error={externalAppsQuery.isError} />
            <Tile icon={Activity} label="Active" value={totals.active} tone="emerald" loading={externalAppsQuery.isLoading} error={externalAppsQuery.isError} />
            <Tile icon={AlertTriangle} label="Suspended" value={totals.suspended} tone="amber" loading={externalAppsQuery.isLoading} error={externalAppsQuery.isError} />
            <Tile icon={Webhook} label="Pending approval" value={totals.pending} tone="violet" loading={externalAppsQuery.isLoading} error={externalAppsQuery.isError} />
          </section>

          <section className="overflow-hidden rounded-2xl border border-border bg-card shadow-sm">
            <header className="border-b border-border px-4 py-3">
              <h3 className="text-sm font-semibold text-foreground">Registered external applications</h3>
              <p className="text-xs text-muted-foreground">
                Pulled from <code className="text-[10px]">/internal/v1/marketplace/integration/external-apps</code>.
                No secrets are exposed; OAuth client ids and OAuth secret references are stored in vault-kms.
              </p>
            </header>
            <table className="w-full text-left text-sm">
              <thead className="bg-background text-xs font-semibold uppercase tracking-wider text-muted-foreground">
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
                    <td colSpan={6} className="px-4 py-6 text-center text-muted-foreground">
                      <Loader2 className="mx-auto h-4 w-4 animate-spin" />
                    </td>
                  </tr>
                ) : apps.length === 0 ? (
                  <tr>
                    <td colSpan={6} className="px-4 py-6 text-center text-muted-foreground">
                      No external applications registered yet.
                    </td>
                  </tr>
                ) : (
                  apps.map((a: ExternalApplication) => (
                    <tr key={a.id}>
                      <td className="px-4 py-3 text-sm text-foreground">
                        <div className="font-medium">{a.name}</div>
                        <div className="text-[10px] text-muted-foreground">{a.appCode}</div>
                      </td>
                      <td className="px-4 py-3 text-xs text-muted-foreground">{a.publisherName}</td>
                      <td className="px-4 py-3 text-xs text-muted-foreground">{a.integrationCategory}</td>
                      <td className="px-4 py-3 text-xs text-muted-foreground">{a.environment}</td>
                      <td className="px-4 py-3 text-xs text-muted-foreground">{a.authenticationMethod}</td>
                      <td className="px-4 py-3">
                        <StatusBadge value={a.status} />
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </section>

          <section className="overflow-hidden rounded-2xl border border-border bg-card shadow-sm">
            <header className="flex items-center justify-between border-b border-border px-4 py-3">
              <div>
                <h3 className="text-sm font-semibold text-foreground">Event catalogue</h3>
                <p className="text-xs text-muted-foreground">Internal platform events vs. externally publishable events.</p>
              </div>
              <select
                value={classification}
                onChange={(e) => setClassification(e.target.value as typeof classification)}
                className="rounded-lg border border-border px-2 py-1 text-xs"
              >
                <option value="ALL">All classifications</option>
                <option value="INTERNAL_PLATFORM">Internal platform</option>
                <option value="EXTERNALLY_PUBLISHABLE">Externally publishable</option>
              </select>
            </header>
            <table className="w-full text-left text-sm">
              <thead className="bg-background text-xs font-semibold uppercase tracking-wider text-muted-foreground">
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
                    <td colSpan={5} className="px-4 py-6 text-center text-muted-foreground">
                      <Loader2 className="mx-auto h-4 w-4 animate-spin" />
                    </td>
                  </tr>
                ) : events.length === 0 ? (
                  <tr>
                    <td colSpan={5} className="px-4 py-6 text-center text-muted-foreground">
                      No events available with the current filter.
                    </td>
                  </tr>
                ) : (
                  events.map((e: EventCatalogueEntry) => (
                    <tr key={e.id}>
                      <td className="px-4 py-3 font-mono text-xs text-foreground">{e.topic}</td>
                      <td className="px-4 py-3 text-xs text-muted-foreground">{e.ownerServiceId}</td>
                      <td className="px-4 py-3 text-xs text-muted-foreground">{e.sensitivityTier}</td>
                      <td className="px-4 py-3 text-xs text-muted-foreground">
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
    ACTIVE: "bg-success-soft text-primary-hover",
    REGISTERED: "bg-info-soft text-primary-hover",
    PENDING_APPROVAL: "bg-warning-soft text-warning-foreground",
    SUSPENDED: "bg-danger-soft text-danger",
    REVOKED: "bg-neutral-100 text-muted-foreground",
  };
  return (
    <span className={`rounded-full px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide ${tone[value] ?? "bg-neutral-100 text-foreground"}`}>
      {value.replace("_", " ")}
    </span>
  );
}

function ClassificationBadge({ value }: { value: string }) {
  const tone: Record<string, string> = {
    INTERNAL_PLATFORM: "bg-neutral-100 text-foreground",
    EXTERNALLY_PUBLISHABLE: "bg-success-soft text-primary-hover",
  };
  return (
    <span className={`rounded-full px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide ${tone[value] ?? "bg-neutral-100 text-foreground"}`}>
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
    slate: "bg-background text-foreground",
    emerald: "bg-success-soft text-primary-hover",
    amber: "bg-warning-soft text-warning-foreground",
    violet: "bg-violet-50 text-violet-700",
  };
  return (
    <div className="flex items-center gap-3">
      <div className={`flex h-10 w-10 items-center justify-center rounded-xl ${toneMap[tone]}`}>
        <Icon className="h-5 w-5" />
      </div>
      <div>
        <div className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{label}</div>
        <div className="text-2xl font-semibold text-foreground">{error ? "—" : loading ? "…" : value}</div>
      </div>
    </div>
  );
}
