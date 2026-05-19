"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import { ChevronLeft, Loader2, PauseCircle, PlayCircle } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useActivateInstallation,
  useInstallations,
  useSuspendInstallation,
  type Installation,
} from "@/hooks/queries/useHealthOsLauncher";

const LIFECYCLE_FILTERS: Array<Installation["lifecycle"] | "ALL"> = [
  "INSTALLED",
  "ACTIVE",
  "SUSPENDED",
  "DEPRECATED",
  "ALL",
];

export default function InstallationsAdminPage() {
  const [lifecycle, setLifecycle] = useState<Installation["lifecycle"] | "ALL">("ALL");
  const installationsQuery = useInstallations(lifecycle === "ALL" ? undefined : lifecycle);
  const activate = useActivateInstallation();
  const suspend = useSuspendInstallation();
  const [reasonById, setReasonById] = useState<Record<string, string>>({});

  const list = useMemo<Installation[]>(() => installationsQuery.data ?? [], [installationsQuery.data]);

  return (
    <AppLayout>
      <PageShell
        title="Capability installations"
        subtitle="Manage activation lifecycle, facility coverage, and suspension of Health OS marketplace capabilities."
      >
        <div className="space-y-5">
          <Link href="/marketplace/apps" className="inline-flex items-center gap-1 text-sm text-impilo-700 hover:underline">
            <ChevronLeft className="h-4 w-4" /> Back to marketplace
          </Link>

          <section className="flex flex-wrap items-center gap-2 rounded-2xl border border-slate-200 bg-white p-3 shadow-sm">
            {LIFECYCLE_FILTERS.map((s) => (
              <button
                key={s}
                type="button"
                onClick={() => setLifecycle(s)}
                className={`rounded-full px-3 py-1.5 text-xs font-semibold ${
                  lifecycle === s
                    ? "bg-impilo-600 text-white"
                    : "bg-slate-100 text-slate-700 hover:bg-slate-200"
                }`}
              >
                {s}
              </button>
            ))}
          </section>

          <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
            <table className="w-full text-left text-sm">
              <thead className="bg-slate-50 text-xs font-semibold uppercase tracking-wider text-slate-500">
                <tr>
                  <th className="px-4 py-2">Installation</th>
                  <th className="px-4 py-2">Capability</th>
                  <th className="px-4 py-2">Coverage</th>
                  <th className="px-4 py-2">Lifecycle</th>
                  <th className="px-4 py-2">Health</th>
                  <th className="px-4 py-2">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {installationsQuery.isLoading ? (
                  <tr>
                    <td colSpan={6} className="px-4 py-6 text-center text-slate-500">
                      <Loader2 className="mx-auto h-4 w-4 animate-spin" />
                    </td>
                  </tr>
                ) : list.length === 0 ? (
                  <tr>
                    <td colSpan={6} className="px-4 py-6 text-center text-slate-500">
                      No installations match the current filter.
                    </td>
                  </tr>
                ) : (
                  list.map((inst) => (
                    <tr key={inst.id}>
                      <td className="px-4 py-3 font-mono text-xs text-slate-600">
                        <div>{inst.id.slice(0, 8)}</div>
                        <div className="text-[10px] text-slate-400">{inst.tenantId}</div>
                      </td>
                      <td className="px-4 py-3 text-xs text-slate-700">
                        <div>{inst.itemId}</div>
                        <div className="text-[10px] text-slate-500">v{inst.itemVersion}</div>
                      </td>
                      <td className="px-4 py-3 text-xs text-slate-700">
                        <div>{inst.facilityIds.length === 0 ? "All facilities" : `${inst.facilityIds.length} facilities`}</div>
                        <div className="text-[10px] text-slate-500">
                          {inst.rolesEnabled.length > 0 ? inst.rolesEnabled.join(", ") : "All roles"}
                        </div>
                      </td>
                      <td className="px-4 py-3">
                        <LifecycleBadge value={inst.lifecycle} />
                      </td>
                      <td className="px-4 py-3 text-xs">
                        <HealthBadge value={inst.healthStatus} />
                      </td>
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-1">
                          {inst.lifecycle !== "ACTIVE" ? (
                            <button
                              type="button"
                              disabled={activate.isPending}
                              onClick={() => activate.mutate({ installationId: inst.id })}
                              className="inline-flex items-center gap-1 rounded-lg bg-emerald-600 px-2 py-1 text-xs font-semibold text-white hover:bg-emerald-700 disabled:opacity-60"
                            >
                              <PlayCircle className="h-3 w-3" /> Activate
                            </button>
                          ) : null}
                          <div className="flex items-center gap-1">
                            <input
                              value={reasonById[inst.id] ?? ""}
                              onChange={(e) =>
                                setReasonById((m) => ({ ...m, [inst.id]: e.target.value }))
                              }
                              placeholder="Reason"
                              className="w-32 rounded-lg border border-slate-200 px-2 py-1 text-xs"
                            />
                            <button
                              type="button"
                              disabled={suspend.isPending || !(reasonById[inst.id] ?? "").trim()}
                              onClick={() =>
                                suspend.mutate({ installationId: inst.id, reason: reasonById[inst.id] })
                              }
                              className="inline-flex items-center gap-1 rounded-lg bg-amber-600 px-2 py-1 text-xs font-semibold text-white hover:bg-amber-700 disabled:opacity-60"
                            >
                              <PauseCircle className="h-3 w-3" /> Suspend
                            </button>
                          </div>
                        </div>
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

function LifecycleBadge({ value }: { value: Installation["lifecycle"] }) {
  const tone: Record<string, string> = {
    INSTALLED: "bg-blue-50 text-blue-700",
    ACTIVE: "bg-emerald-50 text-emerald-700",
    SUSPENDED: "bg-amber-50 text-amber-700",
    DEPRECATED: "bg-slate-100 text-slate-500",
  };
  return (
    <span className={`rounded-full px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide ${tone[value] ?? "bg-slate-100 text-slate-700"}`}>
      {value}
    </span>
  );
}

function HealthBadge({ value }: { value: string }) {
  const tone: Record<string, string> = {
    HEALTHY: "bg-emerald-50 text-emerald-700",
    DEGRADED: "bg-amber-50 text-amber-700",
    UNAVAILABLE: "bg-rose-50 text-rose-700",
  };
  return (
    <span className={`rounded-full px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide ${tone[value] ?? "bg-slate-100 text-slate-700"}`}>
      {value}
    </span>
  );
}
