"use client";

/**
 * Campaign management — list, create, launch, close.
 * Route: /public-health/campaigns
 */

import Link from "next/link";
import { useMemo, useState } from "react";
import { AlertTriangle, Loader2, Megaphone, Play, Square } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { OrganizationPlaneContextBar } from "@/components/experience/OrganizationPlaneContextBar";
import { PageShell } from "@/components/PageShell";
import {
  useCampaigns,
  useCloseCampaign,
  useCreateCampaign,
  useLaunchCampaign,
  type PublicHealthCampaign,
} from "@/hooks/queries/useCampaigns";

function statusBadge(status: string): string {
  const u = status.toUpperCase();
  if (u.includes("DRAFT") || u.includes("PLAN")) return "bg-slate-100 text-slate-800";
  if (u.includes("ACTIVE") || u.includes("RUN")) return "bg-emerald-100 text-emerald-900";
  if (u.includes("COMPLET") || u.includes("CLOSE")) return "bg-indigo-100 text-indigo-900";
  if (u.includes("CANCEL")) return "bg-rose-100 text-rose-800";
  return "bg-amber-50 text-amber-900";
}

function coveragePct(c: PublicHealthCampaign): string {
  if (!c.targetPopulation) return "—";
  const p = Math.round((c.reachedPopulation / c.targetPopulation) * 1000) / 10;
  if (Number.isNaN(p)) return "—";
  return `${p}%`;
}

export default function PublicHealthCampaignsPage() {
  const [statusFilter, setStatusFilter] = useState<string>("");
  const campaignsQ = useCampaigns(statusFilter.trim() || undefined);
  const createM = useCreateCampaign();
  const launchM = useLaunchCampaign();
  const closeM = useCloseCampaign();

  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [campaignType, setCampaignType] = useState("OUTREACH");
  const [targetPopulation, setTargetPopulation] = useState("10000");
  const [channel, setChannel] = useState("SMS");

  const rows = useMemo(() => campaignsQ.data ?? [], [campaignsQ.data]);

  const errorBanner = (err: unknown) => {
    const msg =
      err && typeof err === "object" && "error" in err
        ? String((err as { error?: { message?: string } }).error?.message ?? "Request failed")
        : "Request failed";
    return (
      <div className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800 flex items-center gap-2">
        <AlertTriangle className="h-4 w-4 shrink-0" />
        {msg}
      </div>
    );
  };

  return (
    <AppLayout>
      <PageShell
        title="Campaign management"
        subtitle="National and sub-national programmes — campaigns-service via Experience BFF"
      >
        <OrganizationPlaneContextBar />
        <div className="mb-4 flex flex-wrap items-center gap-3 text-sm">
          <Link href="/public-health?tab=campaigns" className="text-indigo-600 hover:underline">
            ← Public health hub
          </Link>
        </div>

        <div className="space-y-6">
          <div className="flex flex-wrap items-end gap-3 rounded-xl border border-slate-200 bg-white p-4">
            <label className="text-xs font-medium text-slate-600">
              Status filter
              <select
                className="mt-1 block rounded-md border border-slate-300 px-2 py-1.5 text-sm"
                value={statusFilter}
                onChange={(e) => setStatusFilter(e.target.value)}
              >
                <option value="">All</option>
                <option value="DRAFT">DRAFT</option>
                <option value="ACTIVE">ACTIVE</option>
                <option value="COMPLETED">COMPLETED</option>
              </select>
            </label>
          </div>

          <div className="rounded-xl border border-slate-200 bg-white p-4 space-y-3">
            <h2 className="text-sm font-semibold text-slate-900 flex items-center gap-2">
              <Megaphone className="h-4 w-4 text-slate-500" />
              Create campaign
            </h2>
            <div className="grid gap-3 md:grid-cols-2 lg:grid-cols-3">
              <input
                className="rounded-md border border-slate-300 px-2 py-1.5 text-sm"
                placeholder="Campaign name"
                value={name}
                onChange={(e) => setName(e.target.value)}
              />
              <input
                className="rounded-md border border-slate-300 px-2 py-1.5 text-sm"
                placeholder="Description"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
              />
              <input
                className="rounded-md border border-slate-300 px-2 py-1.5 text-sm"
                placeholder="Type (e.g. OUTREACH, EPI)"
                value={campaignType}
                onChange={(e) => setCampaignType(e.target.value)}
              />
              <input
                className="rounded-md border border-slate-300 px-2 py-1.5 text-sm"
                placeholder="Target population (number)"
                value={targetPopulation}
                onChange={(e) => setTargetPopulation(e.target.value)}
              />
              <input
                className="rounded-md border border-slate-300 px-2 py-1.5 text-sm"
                placeholder="Channel (SMS, EMAIL, …)"
                value={channel}
                onChange={(e) => setChannel(e.target.value)}
              />
            </div>
            <button
              type="button"
              disabled={createM.isPending || !name.trim()}
              onClick={() => {
                const n = Number(targetPopulation);
                createM.mutate({
                  name: name.trim(),
                  description: description.trim(),
                  campaignType: campaignType.trim() || "OUTREACH",
                  targetGroup: JSON.stringify({ targetPopulation: Number.isFinite(n) ? n : 0 }),
                  messageTemplate: JSON.stringify({ template: "standard_outreach_v1" }),
                  channel: channel.trim() || "SMS",
                });
              }}
              className="rounded-lg bg-indigo-700 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-800 disabled:opacity-50"
            >
              {createM.isPending ? "Creating…" : "Create campaign"}
            </button>
            {createM.isError && errorBanner(createM.error)}
            {createM.isSuccess && <p className="text-xs text-emerald-700">Campaign created.</p>}
          </div>

          <div className="rounded-xl border border-slate-200 bg-white overflow-hidden">
            <div className="border-b border-slate-100 px-4 py-3">
              <h2 className="text-sm font-semibold text-slate-900">Campaigns</h2>
            </div>
            {campaignsQ.isLoading && (
              <p className="px-4 py-6 text-sm text-slate-500 flex items-center gap-2">
                <Loader2 className="h-4 w-4 animate-spin" /> Loading…
              </p>
            )}
            {campaignsQ.isError && <div className="p-4">{errorBanner(campaignsQ.error)}</div>}
            {!campaignsQ.isLoading && rows.length === 0 && (
              <p className="px-4 py-6 text-sm text-slate-500">No campaigns for this tenant.</p>
            )}
            {rows.length > 0 && (
              <div className="overflow-x-auto">
                <table className="w-full text-sm min-w-[720px]">
                  <thead className="bg-slate-50 text-left text-xs text-slate-600">
                    <tr>
                      <th className="px-3 py-2">Name</th>
                      <th className="px-3 py-2">Type</th>
                      <th className="px-3 py-2 text-right">Target pop.</th>
                      <th className="px-3 py-2">Status</th>
                      <th className="px-3 py-2">Start</th>
                      <th className="px-3 py-2">End</th>
                      <th className="px-3 py-2 text-right">Coverage</th>
                      <th className="px-3 py-2">Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {rows.map((c) => (
                      <tr key={c.id} className="border-t border-slate-100">
                        <td className="px-3 py-2 font-medium text-slate-900">{c.name}</td>
                        <td className="px-3 py-2 text-slate-600">{c.campaignType}</td>
                        <td className="px-3 py-2 text-right">{c.targetPopulation || "—"}</td>
                        <td className="px-3 py-2">
                          <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${statusBadge(c.status)}`}>
                            {c.status.toUpperCase()}
                          </span>
                        </td>
                        <td className="px-3 py-2 text-slate-600">{c.startDate || "—"}</td>
                        <td className="px-3 py-2 text-slate-600">{c.endDate || "—"}</td>
                        <td className="px-3 py-2 text-right font-medium">{coveragePct(c)}</td>
                        <td className="px-3 py-2">
                          <div className="flex flex-wrap gap-1">
                            <button
                              type="button"
                              disabled={launchM.isPending}
                              onClick={() => launchM.mutate(c.id)}
                              className="inline-flex items-center gap-1 rounded-md border border-emerald-300 bg-emerald-50 px-2 py-1 text-xs font-medium text-emerald-900 hover:bg-emerald-100 disabled:opacity-50"
                              title="Launch / dispatch deliveries"
                            >
                              <Play className="h-3 w-3" />
                              Launch
                            </button>
                            <button
                              type="button"
                              disabled={closeM.isPending}
                              onClick={() => closeM.mutate(c.id)}
                              className="inline-flex items-center gap-1 rounded-md border border-slate-300 bg-white px-2 py-1 text-xs font-medium text-slate-800 hover:bg-slate-50 disabled:opacity-50"
                              title="Mark campaign completed"
                            >
                              <Square className="h-3 w-3" />
                              Close
                            </button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
            {(launchM.isError || closeM.isError) && (
              <div className="p-4 space-y-2">
                {launchM.isError && errorBanner(launchM.error)}
                {closeM.isError && errorBanner(closeM.error)}
              </div>
            )}
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
