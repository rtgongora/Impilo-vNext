"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import { ArrowLeft, ArrowUpRight, BadgeAlert, Loader2, Plus, Search, ShieldAlert, UserRoundSearch, Users } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { RegistryPlaneContextBar } from "@/components/experience/RegistryPlaneContextBar";
import { PageShell } from "@/components/PageShell";
import { ClientIntakeStatusBadges } from "@/components/registry/ClientIntakeStatusBadges";
import { useClientRegistryClients, useClientRegistryDashboard } from "@/hooks/queries/useClientRegistry";

const STATUS_STYLES: Record<string, string> = {
  ACTIVE: "bg-success-soft text-primary-hover border border-success/25",
  PROVISIONAL: "bg-warning-soft text-warning-foreground border border-warning/35",
  PENDING_VERIFICATION: "bg-sky-50 text-sky-700 border border-sky-200",
  PENDING_MATCH_REVIEW: "bg-orange-50 text-orange-700 border border-orange-200",
  FLAGGED_FOR_REVIEW: "bg-danger-soft text-danger border border-danger/28",
  MERGED: "bg-neutral-100 text-muted-foreground border border-border",
};

function labelize(value: string) {
  return value.replaceAll("_", " ");
}

export default function ClientRegistryPage() {
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState("");
  const [verificationState, setVerificationState] = useState("");

  const list = useClientRegistryClients({ query, status, verificationState, page: 0, size: 30 });
  const dashboard = useClientRegistryDashboard();
  const items = list.data?.data.items ?? [];
  const degraded = Boolean((list.data?.meta as { degraded?: boolean } | undefined)?.degraded);
  const guidance =
    (list.data?.meta as { guidance?: string } | undefined)?.guidance ??
    "No clients matched the current filters. Start a new registration or broaden your search.";

  const metrics = useMemo(
    () => [
      {
        title: "Total clients",
        value: dashboard.data?.data.totalClients ?? 0,
        icon: Users,
        tone: "bg-background text-foreground border-border",
      },
      {
        title: "Pending verification",
        value: dashboard.data?.data.pendingVerification ?? 0,
        icon: ShieldAlert,
        tone: "bg-sky-50 text-sky-700 border-sky-100",
      },
      {
        title: "Match review",
        value: dashboard.data?.data.pendingMatchReview ?? 0,
        icon: UserRoundSearch,
        tone: "bg-orange-50 text-orange-700 border-orange-100",
      },
      {
        title: "Stewardship open",
        value: dashboard.data?.data.openStewardshipActions ?? 0,
        icon: BadgeAlert,
        tone: "bg-danger-soft text-danger border-rose-100",
      },
    ],
    [dashboard.data],
  );

  return (
    <AppLayout>
      <PageShell
        title="Client Identity Operations"
        subtitle="Canonical person resolution, multi-channel registration, verification, duplicate review, and stewardship"
      >
        <RegistryPlaneContextBar />

        <div className="mb-4 flex items-center justify-between gap-3">
          <Link
            href="/registry"
            className="inline-flex items-center gap-1 text-sm text-muted-foreground transition-colors hover:text-foreground"
          >
            <ArrowLeft className="h-4 w-4" />
            Back to registry hub
          </Link>
          <Link
            href="/operations/vito"
            className="inline-flex items-center gap-2 rounded-xl border border-border px-4 py-2 text-sm font-medium text-foreground transition-colors hover:border-slate-400 hover:text-foreground"
          >
            Identity stewardship
            <ArrowUpRight className="h-4 w-4" />
          </Link>
        </div>

        <div className="mb-6 grid gap-4 md:grid-cols-2 xl:grid-cols-4">
          {metrics.map((metric) => {
            const Icon = metric.icon;
            return (
              <div key={metric.title} className={`rounded-2xl border p-5 ${metric.tone}`}>
                <div className="flex items-center justify-between">
                  <div>
                    <p className="text-xs font-medium uppercase tracking-[0.2em] opacity-80">{metric.title}</p>
                    <p className="mt-3 text-3xl font-semibold">{metric.value}</p>
                  </div>
                  <div className="rounded-2xl bg-card/70 p-3">
                    <Icon className="h-6 w-6" />
                  </div>
                </div>
              </div>
            );
          })}
        </div>

        <div className="mb-5 flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-border bg-card p-4">
          <div className="flex flex-1 flex-wrap items-center gap-3">
            <div className="relative min-w-[240px] flex-1">
              <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <input
                type="text"
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                placeholder="Search by name or Impilo ID"
                className="w-full rounded-xl border border-border px-10 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-slate-500"
              />
            </div>
            <select
              value={status}
              onChange={(event) => setStatus(event.target.value)}
              className="rounded-xl border border-border px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-slate-500"
            >
              <option value="">All lifecycle states</option>
              <option value="PROVISIONAL">Provisional</option>
              <option value="PENDING_VERIFICATION">Pending verification</option>
              <option value="PENDING_MATCH_REVIEW">Pending match review</option>
              <option value="ACTIVE">Active</option>
              <option value="FLAGGED_FOR_REVIEW">Flagged</option>
              <option value="MERGED">Merged</option>
            </select>
            <select
              value={verificationState}
              onChange={(event) => setVerificationState(event.target.value)}
              className="rounded-xl border border-border px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-slate-500"
            >
              <option value="">All verification states</option>
              <option value="UNVERIFIED">Unverified</option>
              <option value="SELF_ASSERTED">Self asserted</option>
              <option value="PROVIDER_CAPTURED">Provider captured</option>
              <option value="PARTIALLY_VERIFIED">Partially verified</option>
              <option value="VERIFIED">Verified</option>
              <option value="REVIEW_REQUIRED">Review required</option>
            </select>
          </div>
          <Link
            href="/registry/clients/new"
            className="inline-flex items-center gap-2 rounded-xl bg-neutral-900 px-4 py-2.5 text-sm font-medium text-white transition-colors hover:bg-slate-700"
          >
            <Plus className="h-4 w-4" />
            New registration
          </Link>
        </div>

        {list.isLoading ? (
          <div className="flex items-center justify-center py-16 text-muted-foreground">
            <Loader2 className="mr-2 h-5 w-5 animate-spin" />
            Loading client registry...
          </div>
        ) : items.length === 0 ? (
          <div className="rounded-2xl border border-border bg-card p-12 text-center">
            <Users className="mx-auto mb-3 h-10 w-10 text-muted-foreground" />
            <p className="text-sm text-muted-foreground">No clients matched the current filters.</p>
            <p className="mx-auto mt-3 max-w-lg rounded-lg border border-amber-100 bg-warning-soft px-4 py-3 text-sm text-warning-foreground">
              {guidance}
            </p>
          </div>
        ) : (
          <div className="overflow-hidden rounded-2xl border border-border bg-card">
            <table className="w-full text-sm">
              <thead className="bg-background text-left text-xs uppercase tracking-[0.18em] text-muted-foreground">
                <tr>
                  <th className="px-4 py-3 font-medium">Client</th>
                  <th className="px-4 py-3 font-medium">Identifiers</th>
                  <th className="px-4 py-3 font-medium">Lifecycle</th>
                  <th className="px-4 py-3 font-medium">Verification</th>
                  <th className="px-4 py-3 font-medium">Open work</th>
                  <th className="px-4 py-3 font-medium text-right">Action</th>
                </tr>
              </thead>
              <tbody>
                {items.map((client) => (
                  <tr key={client.healthId} className="border-t border-border align-top hover:bg-background/60">
                    <td className="px-4 py-4">
                      <div className="font-medium text-foreground">{client.displayName}</div>
                      <div className="mt-1.5">
                        <ClientIntakeStatusBadges
                          input={{
                            verificationStatus: client.verificationStatus,
                            latestRegistrationType: client.latestRegistrationType,
                            latestRegistrationChannel: client.latestRegistrationChannel,
                            lifecycleStatus: client.lifecycleStatus,
                          }}
                        />
                      </div>
                      <div className="mt-1 text-xs text-muted-foreground">
                        {client.latestRegistrationType ? labelize(client.latestRegistrationType) : "Registration pending"}{" "}
                        {client.latestRegistrationChannel ? `via ${labelize(client.latestRegistrationChannel)}` : ""}
                      </div>
                    </td>
                    <td className="px-4 py-4 text-muted-foreground">
                      <div className="font-mono text-xs text-foreground">{client.impiloId ?? "No Impilo ID yet"}</div>
                      <div className="mt-1 text-xs text-muted-foreground">CRID {client.crid.slice(0, 8)}</div>
                    </td>
                    <td className="px-4 py-4">
                      <span
                        className={`inline-flex rounded-full px-2.5 py-1 text-xs font-semibold ${
                          STATUS_STYLES[client.lifecycleStatus] ?? "bg-neutral-100 text-foreground border border-border"
                        }`}
                      >
                        {labelize(client.lifecycleStatus)}
                      </span>
                    </td>
                    <td className="px-4 py-4 text-muted-foreground">
                      <div className="text-xs text-muted-foreground">Assurance LOA {client.identityAssuranceLevel}</div>
                      <div className="mt-1 text-xs text-muted-foreground">{labelize(client.verificationStatus)}</div>
                    </td>
                    <td className="px-4 py-4 text-muted-foreground">
                      <div>{client.openStewardshipActions} stewardship action(s)</div>
                      <div className="text-xs text-orange-600">{client.openMatches} active match candidate(s)</div>
                    </td>
                    <td className="px-4 py-4 text-right">
                      <Link
                        href={`/registry/clients/${client.healthId}`}
                        className="inline-flex rounded-lg border border-border px-3 py-1.5 text-xs font-medium text-foreground transition-colors hover:border-slate-400 hover:text-foreground"
                      >
                        Open workspace
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
