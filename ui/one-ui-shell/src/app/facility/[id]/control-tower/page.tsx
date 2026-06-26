"use client";

/**
 * Facility control tower — real-time ops summary, occupancy, alerts.
 * Route: /facility/[id]/control-tower
 *
 * T4 owns this route. Backed by the TUSO ControlTowerController (occupancy +
 * alerts SoR) via the BFF. Alert acknowledge writes back to TUSO.
 */

import { useParams } from "next/navigation";
import Link from "next/link";
import { ArrowLeft, Bed, Users, Clock, BellRing, CheckCircle2, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useFacilitySummary,
  useFacilityAlerts,
  useAcknowledgeAlert,
} from "@/components/facility-mode/useControlTower";

export default function FacilityControlTowerPage() {
  const params = useParams();
  const id = params.id as string;
  const { data: summary, isLoading } = useFacilitySummary(id);
  const { data: alerts } = useFacilityAlerts(id, "OPEN");
  const ack = useAcknowledgeAlert(id);

  const occ = summary?.occupancy;
  const bedPct =
    occ && occ.totalBeds > 0 ? Math.round((occ.occupiedBeds / occ.totalBeds) * 100) : null;

  return (
    <AppLayout>
      <PageShell title="Control tower" subtitle="Real-time facility operations">
        <div className="mb-4">
          <Link
            href={`/facility/${id}/cockpit`}
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
          >
            <ArrowLeft className="h-4 w-4" /> Back to cockpit
          </Link>
        </div>

        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
          </div>
        ) : (
          <div className="max-w-4xl space-y-6">
            <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
              <Stat icon={<Bed className="h-4 w-4" />} label="Bed occupancy"
                value={bedPct != null ? `${bedPct}%` : "—"}
                hint={occ ? `${occ.occupiedBeds}/${occ.totalBeds} beds` : undefined} />
              <Stat icon={<Users className="h-4 w-4" />} label="Workspaces"
                value={summary?.workspaceCount ?? 0} />
              <Stat icon={<Clock className="h-4 w-4" />} label="Active shifts"
                value={summary?.activeShiftCount ?? 0} />
              <Stat icon={<BellRing className="h-4 w-4" />} label="Open alerts"
                value={summary?.activeAlertCount ?? 0} />
            </div>

            <section>
              <h3 className="mb-2 text-sm font-medium text-foreground">Open alerts</h3>
              {(alerts ?? []).length === 0 ? (
                <p className="rounded-lg border border-border bg-card p-4 text-sm text-muted-foreground">
                  No open alerts.
                </p>
              ) : (
                <ul className="space-y-2">
                  {(alerts ?? []).map((a) => (
                    <li
                      key={a.id}
                      className="flex items-start justify-between gap-3 rounded-lg border border-border bg-card p-3"
                    >
                      <div className="min-w-0">
                        <div className="flex items-center gap-2">
                          <span
                            className={`rounded-full px-2 py-0.5 text-[10px] font-medium uppercase ${severityClass(a.severity)}`}
                          >
                            {a.severity}
                          </span>
                          <span className="text-sm font-medium text-foreground">{a.title}</span>
                        </div>
                        {a.description && (
                          <p className="mt-0.5 truncate text-xs text-muted-foreground">
                            {a.description}
                          </p>
                        )}
                      </div>
                      <button
                        type="button"
                        disabled={ack.isPending}
                        onClick={() => ack.mutate(a.id)}
                        className="inline-flex shrink-0 items-center gap-1 rounded-md border border-border px-2.5 py-1.5 text-xs font-medium text-foreground hover:bg-muted disabled:opacity-50"
                      >
                        <CheckCircle2 className="h-3.5 w-3.5" /> Acknowledge
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </section>

            <section>
              <h3 className="mb-2 text-sm font-medium text-foreground">Recent telemetry</h3>
              <div className="rounded-lg border border-border bg-card">
                {(summary?.recentTelemetry ?? []).length === 0 ? (
                  <p className="p-4 text-sm text-muted-foreground">No telemetry recorded.</p>
                ) : (
                  <ul className="divide-y divide-border">
                    {(summary?.recentTelemetry ?? []).map((t, i) => (
                      <li key={i} className="flex items-center justify-between px-4 py-2 text-sm">
                        <span className="text-foreground">
                          {t.metricType}{" "}
                          <span className="text-xs text-muted-foreground">({t.source})</span>
                        </span>
                        <span className="font-medium text-foreground">
                          {t.metricValue}
                          {t.unit ? ` ${t.unit}` : ""}
                        </span>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
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
  hint,
}: {
  icon: React.ReactNode;
  label: string;
  value: string | number;
  hint?: string;
}) {
  return (
    <div className="rounded-lg border border-border bg-card p-4">
      <div className="mb-2 flex items-center gap-2 text-muted-foreground">
        {icon}
        <span className="text-xs font-medium uppercase tracking-wide">{label}</span>
      </div>
      <div className="text-2xl font-semibold text-foreground">{value}</div>
      {hint && <div className="mt-1 text-xs text-muted-foreground">{hint}</div>}
    </div>
  );
}

function severityClass(severity: string): string {
  switch (severity?.toUpperCase()) {
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
