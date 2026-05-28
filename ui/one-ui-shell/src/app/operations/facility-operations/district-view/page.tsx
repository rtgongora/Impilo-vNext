"use client";

/**
 * District / national operational snapshot â€” multi-facility queue pressure (live PCT via BFF).
 * Route: /operations/facility-operations/district-view
 */

import Link from "next/link";
import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { ArrowLeft, Globe2, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useRoleGroup } from "@/hooks/useRoleGroup";
import { OpsMapPanel } from "@/components/operations/OpsMapPanel";
import { useFacilityQueueSnapshots, type FacilityQueueSnapshotRow } from "@/hooks/queries/useFacilityOperations";
import { apiClient, type ApiResponse } from "@/lib/api-client";

type FacilityRow = { id: string; attributes?: { name?: string; province?: string; district?: string } };

export default function DistrictOperationsPage() {
  const { isOperationsOversight } = useRoleGroup();
  const [province, setProvince] = useState("");

  const facilitiesQ = useQuery({
    queryKey: ["district-facilities", province],
    queryFn: () =>
      apiClient.get<ApiResponse<FacilityRow[]>>(
        `/internal/v1/facilities?size=40&page=0${province ? `&province=${encodeURIComponent(province)}` : ""}`,
      ),
    enabled: isOperationsOversight,
  });

  const facilityIds = useMemo(
    () => (facilitiesQ.data?.data ?? []).map((f) => f.id).filter((id): id is string => !!id && id.length > 0),
    [facilitiesQ.data],
  );

  const snapQ = useFacilityQueueSnapshots(facilityIds.length ? facilityIds : undefined);

  const merged = useMemo(() => {
    const facilities = facilitiesQ.data?.data ?? [];
    const snaps: FacilityQueueSnapshotRow[] = snapQ.data?.data ?? [];
    const byId = new Map(snaps.map((s) => [s.facility_id, s]));
    return facilities.map((f) => {
      const s = byId.get(f.id);
      return {
        id: f.id,
        name: f.attributes?.name ?? f.id,
        province: f.attributes?.province ?? "â€”",
        district: f.attributes?.district ?? "â€”",
        waiting: s?.waiting ?? 0,
        called: s?.called ?? 0,
        inService: s?.inService ?? 0,
        completed: s?.completed ?? 0,
        noShow: s?.noShow ?? 0,
        avgWaitSeconds: s?.avgWaitSeconds ?? 0,
        source: s?.source ?? "â€”",
        error: s?.error,
      };
    });
  }, [facilitiesQ.data, snapQ.data]);

  if (!isOperationsOversight) {
    return (
      <AppLayout>
        <PageShell title="District operations" subtitle="Multi-facility queue intelligence">
          <div className="rounded-xl border border-amber-200 bg-amber-50/80 p-6 text-sm text-amber-950">
            <p className="font-medium">Your account does not have operations oversight roles.</p>
            <p className="mt-2 text-amber-900/90">
              District and national aggregates require public-health, HIE, or combined clinical/queue oversight roles
              aligned with the Experience BFF policy for{" "}
              <code className="rounded bg-white/80 px-1">/internal/v1/operations/facility-queue-snapshots</code>.
            </p>
            <Link href="/operations/facility-operations" className="mt-3 inline-block text-sm font-semibold text-impilo-700 underline">
              Back to facility operations
            </Link>
          </div>
        </PageShell>
      </AppLayout>
    );
  }

  const loading = facilitiesQ.isLoading || (facilityIds.length > 0 && snapQ.isLoading);

  return (
    <AppLayout>
      <PageShell
        title="District & national operations"
        subtitle="Aggregated queue pressure from PCT queue summaries â€” no simulated KPIs. Facilities without a valid PCT binding show as UNAVAILABLE."
      >
        <div className="mb-4">
          <Link
            href="/operations/facility-operations"
            className="inline-flex items-center gap-1 text-xs font-semibold text-impilo-700 hover:text-impilo-900"
          >
            <ArrowLeft className="h-3.5 w-3.5" aria-hidden />
            Facility operations hub
          </Link>
        </div>

        <div className="mb-6 flex flex-wrap items-end gap-3">
          <div>
            <label htmlFor="prov-filter" className="block text-xs font-medium text-slate-600">
              Province filter (optional)
            </label>
            <input
              id="prov-filter"
              value={province}
              onChange={(e) => setProvince(e.target.value)}
              placeholder="e.g. Harare"
              className="mt-1 w-56 rounded-lg border border-slate-300 px-3 py-2 text-sm"
            />
          </div>
          <p className="text-xs text-slate-500">
            Loads up to 40 facilities from the registry, then requests live queue snapshots in one BFF call.
          </p>
        </div>

        {loading ? (
          <div className="flex items-center gap-2 py-16 text-sm text-slate-500">
            <Loader2 className="h-5 w-5 animate-spin text-impilo-500" aria-hidden />
            Loading facilities and queue snapshotsâ€¦
          </div>
        ) : merged.length === 0 ? (
          <div className="rounded-xl border border-slate-200 bg-slate-50 p-8 text-sm text-slate-600">
            No facilities returned for this filter. Clear the province filter or widen your search.
          </div>
        ) : (
          <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white shadow-sm">
            <table className="min-w-[720px] w-full text-left text-sm">
              <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
                <tr>
                  <th className="px-4 py-3">Facility</th>
                  <th className="px-4 py-3">Province</th>
                  <th className="px-4 py-3">District</th>
                  <th className="px-4 py-3 text-right">Waiting</th>
                  <th className="px-4 py-3 text-right">Called</th>
                  <th className="px-4 py-3 text-right">In service</th>
                  <th className="px-4 py-3 text-right">Completed</th>
                  <th className="px-4 py-3 text-right">No-show</th>
                  <th className="px-4 py-3 text-right">Avg wait (s)</th>
                  <th className="px-4 py-3">Source</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {merged.map((row) => (
                  <tr key={row.id} className="hover:bg-slate-50/80">
                    <td className="px-4 py-3 font-medium text-slate-900">{row.name}</td>
                    <td className="px-4 py-3 text-slate-600">{row.province}</td>
                    <td className="px-4 py-3 text-slate-600">{row.district}</td>
                    <td className="px-4 py-3 text-right tabular-nums">{row.waiting}</td>
                    <td className="px-4 py-3 text-right tabular-nums">{row.called}</td>
                    <td className="px-4 py-3 text-right tabular-nums">{row.inService}</td>
                    <td className="px-4 py-3 text-right tabular-nums">{row.completed}</td>
                    <td className="px-4 py-3 text-right tabular-nums">{row.noShow}</td>
                    <td className="px-4 py-3 text-right tabular-nums">{row.avgWaitSeconds}</td>
                    <td className="px-4 py-3 text-xs text-slate-500">
                      <span className="inline-flex items-center gap-1">
                        <Globe2 className="h-3.5 w-3.5 text-slate-400" aria-hidden />
                        {row.source}
                      </span>
                      {row.error ? <span className="ml-1 text-rose-600">({row.error})</span> : null}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        <div className="mt-6">
          <OpsMapPanel
            title="District facility map"
            subtitle="Ndila-ready geo layer — markers from facility registry when coordinates exist"
            markers={merged.map((row) => ({
              id: row.id,
              label: row.name,
              status: `${row.waiting} waiting`,
            }))}
          />
        </div>
      </PageShell>
    </AppLayout>
  );
}
