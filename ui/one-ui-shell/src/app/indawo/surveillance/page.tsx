"use client";

/**
 * Indawo surveillance signals.
 * Route: /indawo/surveillance — public-health surveillance alert worklist (Indawo SoR).
 */

import Link from "next/link";
import { ArrowLeft, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useSurveillanceAlerts } from "@/components/facility-mode/usePlaceMode";

export default function IndawoSurveillancePage() {
  const { data: alerts, isLoading } = useSurveillanceAlerts();

  return (
    <AppLayout>
      <PageShell title="Surveillance" subtitle="Public-health signals worklist">
        <div className="mb-4">
          <Link href="/indawo" className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
            <ArrowLeft className="h-4 w-4" /> Back to place mode
          </Link>
        </div>

        {isLoading ? (
          <div className="flex items-center justify-center py-12">
            <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
          </div>
        ) : (
          <div className="max-w-3xl">
            <ul className="space-y-2">
              {(alerts ?? []).map((a) => (
                <li key={a.alertId} className="rounded-lg border border-border bg-card p-3">
                  <div className="flex items-center gap-2">
                    <span className={`rounded-full px-2 py-0.5 text-[10px] font-medium uppercase ${sev(a.severity)}`}>
                      {a.severity}
                    </span>
                    <span className="text-sm font-medium text-foreground">{a.signalType}</span>
                    <span className="ml-auto rounded-full bg-muted px-2 py-0.5 text-[10px] uppercase text-muted-foreground">
                      {a.status}
                    </span>
                  </div>
                  <p className="mt-1 text-xs text-muted-foreground">
                    {a.conditionCode ?? "—"} · {a.caseCount} case{a.caseCount === 1 ? "" : "s"}
                    {a.detectedAt ? ` · ${new Date(a.detectedAt).toLocaleDateString()}` : ""}
                  </p>
                  {a.description && <p className="mt-1 text-xs text-muted-foreground">{a.description}</p>}
                </li>
              ))}
              {(alerts ?? []).length === 0 && (
                <li className="rounded-lg border border-border bg-card p-4 text-sm text-muted-foreground">
                  No surveillance signals.
                </li>
              )}
            </ul>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}

function sev(s: string): string {
  switch (s?.toUpperCase()) {
    case "CRITICAL":
      return "bg-red-500/15 text-red-600";
    case "HIGH":
      return "bg-orange-500/15 text-orange-600";
    case "MEDIUM":
      return "bg-amber-500/15 text-amber-600";
    default:
      return "bg-muted text-muted-foreground";
  }
}
