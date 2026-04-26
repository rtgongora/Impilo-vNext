"use client";

/**
 * Tuso-backed service points / booking resources (read-only via BFF).
 * Route: /facility-operations/resources
 */

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { ArrowLeft, Armchair, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { apiClient, type ApiResponse } from "@/lib/api-client";

function normalizeResourceRows(payload: unknown): Record<string, unknown>[] {
  if (Array.isArray(payload)) return payload as Record<string, unknown>[];
  if (payload && typeof payload === "object") {
    const inner = (payload as { data?: unknown }).data;
    if (Array.isArray(inner)) return inner as Record<string, unknown>[];
  }
  return [];
}

export default function FacilityResourcesBoardPage() {
  const facility = useFacilityStore((s) => s.facility);
  const fid = facility?.id;

  const q = useQuery({
    queryKey: ["appointments-resources", fid],
    queryFn: () =>
      apiClient.get<ApiResponse<unknown>>(`/internal/v1/appointments/resources?facility_id=${encodeURIComponent(fid!)}`),
    enabled: !!fid,
  });

  const rows = normalizeResourceRows(q.data?.data);

  return (
    <AppLayout>
      <PageShell
        title="Service points & resources"
        subtitle="Tuso facility resources exposed through the Experience appointments façade. Empty rows mean Tuso returned no resources or the facility identifier is not in the Tuso numeric format."
      >
        <div className="mb-4">
          <Link
            href="/facility-operations"
            className="inline-flex items-center gap-1 text-xs font-semibold text-impilo-700 hover:text-impilo-900"
          >
            <ArrowLeft className="h-3.5 w-3.5" aria-hidden />
            Facility operations hub
          </Link>
        </div>

        {!fid ? (
          <div className="rounded-xl border border-amber-200 bg-amber-50/80 p-6 text-sm text-amber-950">
            Select a facility to load Tuso resources for that site.
          </div>
        ) : q.isLoading ? (
          <div className="flex items-center gap-2 py-16 text-sm text-slate-500">
            <Loader2 className="h-5 w-5 animate-spin text-impilo-500" aria-hidden />
            Loading resources…
          </div>
        ) : rows.length === 0 ? (
          <div className="rounded-xl border border-slate-200 bg-slate-50 p-8 text-sm text-slate-600">
            No resource rows returned. If this facility uses UUID identifiers in Experience but Tuso expects numeric
            facility keys, the BFF returns an empty list — coordinate registry alignment or use a Tuso-matched facility
            id.
          </div>
        ) : (
          <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white shadow-sm">
            <table className="min-w-[640px] w-full text-left text-sm">
              <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
                <tr>
                  <th className="px-4 py-3">Resource</th>
                  <th className="px-4 py-3">Identifier</th>
                  <th className="px-4 py-3">Type</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {rows.map((row, idx) => {
                  const id = String(row.id ?? row.resource_id ?? row.resourceId ?? idx);
                  const name = String(row.name ?? row.title ?? row.label ?? id);
                  const type = String(row.type ?? row.resource_type ?? row.resourceType ?? "—");
                  return (
                    <tr key={`${id}-${idx}`} className="hover:bg-slate-50/80">
                      <td className="px-4 py-3">
                        <span className="inline-flex items-center gap-2 font-medium text-slate-900">
                          <Armchair className="h-4 w-4 text-slate-400" aria-hidden />
                          {name}
                        </span>
                      </td>
                      <td className="px-4 py-3 font-mono text-xs text-slate-600">{id}</td>
                      <td className="px-4 py-3 text-slate-600">{type}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
