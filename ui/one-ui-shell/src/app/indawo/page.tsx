"use client";

/**
 * Indawo place mode — public-health PLACE intelligence cockpit.
 * Route: /indawo
 *
 * T4 owns the indawo/** namespace. Surfaces the Indawo surveillance / outbreak /
 * field-team SoR (net-new) via the BFF place-mode composer. No PII (CPID only).
 */

import Link from "next/link";
import { Activity, AlertTriangle, Users, ArrowRight, Loader2, ShieldAlert } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  usePlaceModeSummary,
  useSurveillanceAlerts,
  useOutbreaks,
} from "@/components/facility-mode/usePlaceMode";

export default function IndawoPlaceModePage() {
  const { data: summary, isLoading } = usePlaceModeSummary();
  const { data: alerts } = useSurveillanceAlerts("OPEN");
  const { data: outbreaks } = useOutbreaks();

  return (
    <AppLayout>
      <PageShell title="Place mode (Indawo)" subtitle="Public-health surveillance & response">
        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
          </div>
        ) : (
          <div className="max-w-4xl space-y-6">
            <div className="grid grid-cols-3 gap-3">
              <Stat icon={<AlertTriangle className="h-4 w-4" />} label="Open alerts"
                value={summary?.openAlerts ?? 0} href="/indawo/surveillance" />
              <Stat icon={<ShieldAlert className="h-4 w-4" />} label="Active outbreaks"
                value={summary?.activeOutbreaks ?? 0} href="/indawo/outbreaks" />
              <Stat icon={<Users className="h-4 w-4" />} label="Field teams"
                value={summary?.teams ?? 0} href="/indawo/field-teams" />
            </div>

            <section>
              <div className="mb-2 flex items-center justify-between">
                <h3 className="text-sm font-medium text-foreground">Open surveillance signals</h3>
                <Link href="/indawo/surveillance" className="text-xs text-primary hover:underline">
                  View all
                </Link>
              </div>
              {(alerts ?? []).length === 0 ? (
                <p className="rounded-lg border border-border bg-card p-4 text-sm text-muted-foreground">
                  No open signals.
                </p>
              ) : (
                <ul className="space-y-2">
                  {(alerts ?? []).slice(0, 5).map((a) => (
                    <li key={a.alertId} className="rounded-lg border border-border bg-card p-3">
                      <div className="flex items-center gap-2">
                        <span className={`rounded-full px-2 py-0.5 text-[10px] font-medium uppercase ${sev(a.severity)}`}>
                          {a.severity}
                        </span>
                        <span className="text-sm font-medium text-foreground">{a.signalType}</span>
                        {a.conditionCode && (
                          <span className="text-xs text-muted-foreground">· {a.conditionCode}</span>
                        )}
                        <span className="ml-auto text-xs text-muted-foreground">
                          {a.caseCount} case{a.caseCount === 1 ? "" : "s"}
                        </span>
                      </div>
                      {a.description && (
                        <p className="mt-1 text-xs text-muted-foreground">{a.description}</p>
                      )}
                    </li>
                  ))}
                </ul>
              )}
            </section>

            <section>
              <div className="mb-2 flex items-center justify-between">
                <h3 className="text-sm font-medium text-foreground">Active outbreaks</h3>
                <Link href="/indawo/outbreaks" className="text-xs text-primary hover:underline">
                  Manage
                </Link>
              </div>
              {(outbreaks ?? []).filter((o) => o.status !== "CLOSED").length === 0 ? (
                <p className="rounded-lg border border-border bg-card p-4 text-sm text-muted-foreground">
                  No active outbreaks.
                </p>
              ) : (
                <ul className="space-y-2">
                  {(outbreaks ?? [])
                    .filter((o) => o.status !== "CLOSED")
                    .map((o) => (
                      <li key={o.outbreakId} className="flex items-center justify-between rounded-lg border border-border bg-card p-3">
                        <div>
                          <span className="text-sm font-medium text-foreground">{o.name}</span>
                          <p className="text-xs text-muted-foreground">
                            {o.conditionCode} · {o.status} · {o.caseCount} cases
                          </p>
                        </div>
                        <span className={`rounded-full px-2 py-0.5 text-[10px] font-medium uppercase ${sev(o.severity)}`}>
                          {o.severity}
                        </span>
                      </li>
                    ))}
                </ul>
              )}
            </section>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}

function Stat({
  icon,
  label,
  value,
  href,
}: {
  icon: React.ReactNode;
  label: string;
  value: number;
  href: string;
}) {
  return (
    <Link
      href={href}
      className="group rounded-lg border border-border bg-card p-4 transition hover:border-primary/40"
    >
      <div className="mb-2 flex items-center gap-2 text-muted-foreground">
        {icon}
        <span className="text-xs font-medium uppercase tracking-wide">{label}</span>
        <ArrowRight className="ml-auto h-3.5 w-3.5 opacity-0 transition group-hover:opacity-100" />
      </div>
      <div className="text-2xl font-semibold text-foreground">{value}</div>
    </Link>
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
