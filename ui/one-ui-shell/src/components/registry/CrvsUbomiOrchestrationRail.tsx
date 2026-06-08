"use client";

import Link from "next/link";
import { Baby, Loader2, Skull } from "lucide-react";
import { useUbomiRegistry } from "@/hooks/queries/useUbomiRegistry";
import { useUbomiBirths, useUbomiDeaths } from "@/hooks/queries/useUbomi";

export function CrvsUbomiOrchestrationRail() {
  const statusQ = useUbomiRegistry();
  const birthsQ = useUbomiBirths();
  const deathsQ = useUbomiDeaths();
  const birthRows = Array.isArray(birthsQ.data) ? birthsQ.data : [];
  const deathRows = Array.isArray(deathsQ.data) ? deathsQ.data : [];

  return (
    <section
      className="mb-4 rounded-2xl border border-rose-200 bg-rose-50/80 p-4"
      data-testid="crvs-ubomi-orchestration-rail"
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="flex items-center gap-2 text-sm font-semibold text-rose-900">
            <Baby className="h-4 w-4" />
            UBOMI civil registration orchestration
          </p>
          <p className="mt-1 text-xs text-rose-800" data-testid="ubomi-status-probe">
            {statusQ.isLoading
              ? "Probing UBOMI BFF availability…"
              : statusQ.data?.available
                ? "UBOMI service reachable · births and deaths wired through BFF"
                : "UBOMI service unavailable — governed fail-close"}
          </p>
          <p className="mt-1 text-xs text-rose-700" data-testid="ubomi-crvs-kpi-strip">
            {birthsQ.isLoading || deathsQ.isLoading
              ? "Loading vital event queues…"
              : `${birthRows.length} birth notification(s) · ${deathRows.length} death notification(s)`}
          </p>
        </div>
        <div className="flex flex-wrap gap-2 text-xs">
          <Link
            href="/ubomi"
            className="inline-flex items-center gap-1 rounded-lg border border-rose-200 bg-white px-2.5 py-1.5 font-medium text-rose-900 hover:border-rose-300"
          >
            <Skull className="h-3.5 w-3.5" />
            CRVS console
          </Link>
          <Link
            href="/registry"
            className="rounded-lg border border-rose-200 bg-white px-2.5 py-1.5 font-medium text-rose-900 hover:border-rose-300"
          >
            Registry hub
          </Link>
        </div>
      </div>
      {(birthsQ.isLoading || deathsQ.isLoading) && (
        <div className="mt-2 flex items-center gap-2 text-xs text-rose-700">
          <Loader2 className="h-3.5 w-3.5 animate-spin" />
          Binding web CRVS → citizen mobile verification path…
        </div>
      )}
    </section>
  );
}
