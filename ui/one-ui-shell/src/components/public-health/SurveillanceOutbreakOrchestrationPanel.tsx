"use client";

import Link from "next/link";
import { AlertTriangle, Loader2, Map, Radio } from "lucide-react";
import { useAlerts, useCases, useSignals } from "@/hooks/queries/useSurveillance";
import { useNdilaTileConfig } from "@/hooks/queries/useNdila";

function ndilaProbeLabel(
  isLoading: boolean,
  isError: boolean,
  config: { provider?: string } | undefined,
): string {
  if (isLoading) return "Ndila map: probing…";
  if (isError) return "Ndila map: unavailable (open /ndila when service is up)";
  return `Ndila map: ${config?.provider ?? "live"} tiles ready for outbreak proximity`;
}

export function SurveillanceOutbreakOrchestrationPanel() {
  const signalsQ = useSignals();
  const casesQ = useCases();
  const alertsQ = useAlerts();
  const ndilaQ = useNdilaTileConfig();

  const signalCount = signalsQ.data?.length ?? 0;
  const caseCount = casesQ.data?.length ?? 0;
  const alertCount = alertsQ.data?.length ?? 0;

  return (
    <section
      className="mb-6 rounded-2xl border border-rose-200 bg-rose-50/70 p-4"
      data-testid="surveillance-outbreak-orchestration-panel"
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="flex items-center gap-2 text-sm font-semibold text-rose-900">
            <Radio className="h-4 w-4" />
            Surveillance & outbreak orchestration
          </p>
          <p className="mt-1 text-xs text-rose-800" data-testid="surveillance-kpi-strip">
            {signalsQ.isLoading || casesQ.isLoading || alertsQ.isLoading
              ? "Loading syndrome signals…"
              : `${signalCount} signal(s) · ${caseCount} case(s) · ${alertCount} alert(s)`}
          </p>
          <p className="mt-1 text-xs text-rose-700" data-testid="surveillance-ndila-probe">
            {ndilaProbeLabel(ndilaQ.isLoading, ndilaQ.isError, ndilaQ.data)}
          </p>
        </div>
        <div className="flex flex-wrap gap-2 text-xs">
          <Link
            href="/public-health?tab=outbreaks"
            className="inline-flex items-center gap-1 rounded-lg border border-rose-200 bg-white px-2.5 py-1.5 font-medium text-rose-900 hover:border-rose-300"
          >
            <AlertTriangle className="h-3.5 w-3.5" />
            Outbreaks
          </Link>
          <Link
            href="/ndila"
            className="inline-flex items-center gap-1 rounded-lg border border-rose-200 bg-white px-2.5 py-1.5 font-medium text-rose-900 hover:border-rose-300"
          >
            <Map className="h-3.5 w-3.5" />
            Ndila workspace
          </Link>
        </div>
      </div>
      {(signalsQ.isLoading || ndilaQ.isLoading) && (
        <div className="mt-2 flex items-center gap-2 text-xs text-rose-700">
          <Loader2 className="h-3.5 w-3.5 animate-spin" />
          Correlating surveillance with geospatial context…
        </div>
      )}
    </section>
  );
}
