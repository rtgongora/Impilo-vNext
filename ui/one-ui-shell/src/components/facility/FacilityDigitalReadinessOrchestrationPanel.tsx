"use client";

import Link from "next/link";
import { Loader2, Signal } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";
import { useFacilityStore } from "@/hooks/useFacilityStore";

export function FacilityDigitalReadinessOrchestrationPanel() {
  const facility = useFacilityStore((s) => s.facility);
  const readinessQ = useQuery({
    queryKey: ["facility-digital-readiness-orchestration", facility?.id],
    queryFn: () =>
      apiClient.get<ApiResponse<{ id: string; attributes?: Record<string, unknown> }[]>>(
        "/internal/v1/registry/facilities?size=5&page=0",
      ),
    enabled: !!facility,
  });
  const rows = (readinessQ.data?.data ?? []).slice(0, 4);

  return (
    <section
      className="rounded-2xl border border-emerald-200 bg-emerald-50/50 p-4"
      data-testid="facility-digital-readiness-orchestration-panel"
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="flex items-center gap-2 text-sm font-semibold text-emerald-950">
            <Signal className="h-4 w-4" />
            TUSO digital readiness orchestration
          </p>
          <p className="mt-1 text-xs text-emerald-900/80" data-testid="facility-readiness-kpi">
            {!facility
              ? "Facility context required for digital readiness probe"
              : readinessQ.isLoading
                ? "Loading registry/TUSO facility operating model…"
                : `${rows.length} facility row(s) in readiness scope for ${facility.name}`}
          </p>
        </div>
        <Link
          href="/clinical/control-tower"
          className="text-xs font-semibold text-emerald-800 underline"
        >
          Open control tower
        </Link>
      </div>
      {facility && rows.length > 0 && (
        <ul className="mt-3 grid gap-2 sm:grid-cols-2 text-xs text-emerald-950">
          {rows.map((row) => (
            <li key={row.id} className="rounded-lg border border-emerald-100 bg-white/80 px-3 py-2">
              {String(row.attributes?.name ?? row.id)} · {String(row.attributes?.status ?? "unknown")}
            </li>
          ))}
        </ul>
      )}
      {facility && readinessQ.isLoading && (
        <div className="mt-2 flex items-center gap-2 text-xs text-emerald-800">
          <Loader2 className="h-3.5 w-3.5 animate-spin" />
          Bridging facility context → TUSO readiness dashboards…
        </div>
      )}
    </section>
  );
}
