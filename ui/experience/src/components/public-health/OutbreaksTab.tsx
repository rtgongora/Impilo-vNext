"use client";

import Link from "next/link";
import { Loader2, Siren, AlertTriangle } from "lucide-react";
import { usePublicHealthCases } from "@/hooks/queries/usePublicHealth";

/**
 * Formal outbreak / incident register: **unsupported** on Experience BFF (no `/public-health/outbreaks`).
 * Surveillance **cases** below are real data from surveillance-service for situational awareness only.
 */
export function OutbreaksTab() {
  const { data: cases = [], isLoading, isError } = usePublicHealthCases();

  return (
    <div className="space-y-4">
      <div className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-xs text-amber-950">
        <strong>Outbreak register:</strong> not exposed under{" "}
        <code className="text-[10px]">/internal/v1/public-health/*</code>. EPI curves, contact queues, and declare-outbreak
        actions require a dedicated outbreaks API (backlog). The list below is{" "}
        <strong>live surveillance cases</strong> — not a substitute for outbreak incident records.
      </div>

      <div className="bg-white rounded-lg border border-gray-200">
        <div className="px-4 py-3 border-b flex items-center gap-2">
          <Siren className="h-5 w-5 text-red-600" />
          <div>
            <h4 className="text-sm font-semibold text-gray-900">Surveillance cases (related context)</h4>
            <p className="text-xs text-gray-500">GET /internal/v1/public-health/cases → surveillance-service</p>
          </div>
        </div>
        <div className="p-4">
          {isLoading && (
            <div className="flex items-center gap-2 text-sm text-gray-500 py-8 justify-center">
              <Loader2 className="h-5 w-5 animate-spin" /> Loading cases…
            </div>
          )}
          {isError && (
            <p className="text-sm text-red-600 py-4 text-center">Failed to load surveillance cases.</p>
          )}
          {!isLoading && !isError && cases.length === 0 && (
            <p className="text-sm text-gray-500 py-6 text-center">No cases returned.</p>
          )}
          {!isLoading && !isError && cases.length > 0 && (
            <ul className="divide-y divide-gray-100">
              {cases.slice(0, 25).map((c) => (
                <li key={c.id} className="py-3 flex flex-wrap items-center justify-between gap-2">
                  <div>
                    <p className="font-medium text-sm text-gray-900">{c.disease}</p>
                    <p className="text-xs text-gray-500">
                      {c.facility} · {c.patientRef} · {c.reportedAt || "—"}
                    </p>
                  </div>
                  <span className="text-[10px] px-2 py-0.5 rounded-full bg-slate-100 text-slate-700">{c.status}</span>
                </li>
              ))}
            </ul>
          )}
          <p className="text-[10px] text-gray-500 mt-4">
            For threshold signals and weekly IDSR context, use{" "}
            <Link href="/public-health?tab=surveillance" className="text-blue-600 hover:underline">
              Surveillance / eIDSR
            </Link>
            .
          </p>
        </div>
      </div>

      <div className="rounded-lg border border-gray-200 bg-gray-50 p-4 flex gap-2">
        <AlertTriangle className="h-5 w-5 text-gray-400 shrink-0" />
        <p className="text-xs text-gray-600">
          Contact tracing, response teams, and epi curve widgets were removed — they were hardcoded demo content. Reintroduce
          only when backed by real endpoints.
        </p>
      </div>
    </div>
  );
}
