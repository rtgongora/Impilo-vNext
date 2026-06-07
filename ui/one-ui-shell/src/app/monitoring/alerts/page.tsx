"use client";

/**
 * Monitoring Alerts — Health OS §2
 * Threshold breaches from wellness personal-data remote alerts.
 * Route: /monitoring/alerts | Zone: monitoring | Guard: auth
 */

import { AlertTriangle, Loader2, CheckCheck } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useMonitoringAlerts, useReviewMonitoringAlert } from "@/hooks/queries/useCitizenMonitoring";

function severityBucket(severity: string): "critical" | "warning" | "info" {
  const s = severity.toUpperCase();
  if (s === "CRITICAL" || s === "HIGH") return "critical";
  if (s === "MEDIUM" || s === "WARNING") return "warning";
  return "info";
}

function fmtWhen(iso: string) {
  if (!iso) return "—";
  return new Date(iso).toLocaleString("en-ZW", { dateStyle: "medium", timeStyle: "short" });
}

export default function MonitoringAlertsPage() {
  const patientId = useAuthStore((s) => s.user?.id);
  const { data: alerts = [], isLoading, isError } = useMonitoringAlerts(patientId);
  const reviewAlert = useReviewMonitoringAlert(patientId);

  const counts = alerts.reduce(
    (acc, alert) => {
      acc[severityBucket(alert.severity)] += 1;
      return acc;
    },
    { critical: 0, warning: 0, info: 0 },
  );

  return (
    <AppLayout>
      <PageShell
        title="Monitoring Alerts"
        subtitle="Threshold breaches and escalations from wellness remote monitoring"
        icon={<AlertTriangle className="h-6 w-6" />}
      >
        <div className="space-y-6">
          {!patientId && (
            <p className="text-sm text-gray-500">Sign in to view monitoring alerts.</p>
          )}

          {patientId && (
            <div className="grid grid-cols-3 gap-4">
              {[
                { label: "Critical", count: counts.critical, color: "border-red-200 bg-red-50 text-red-700" },
                { label: "Warning", count: counts.warning, color: "border-amber-200 bg-amber-50 text-amber-700" },
                { label: "Info", count: counts.info, color: "border-impilo-200 bg-impilo-50 text-impilo-600" },
              ].map(({ label, count, color }) => (
                <div key={label} className={`rounded-lg border p-4 text-center ${color}`}>
                  <p className="text-2xl font-bold">{count}</p>
                  <p className="text-sm font-medium">{label}</p>
                </div>
              ))}
            </div>
          )}

          {patientId && isLoading && (
            <div className="flex items-center gap-2 text-sm text-gray-500">
              <Loader2 className="h-4 w-4 animate-spin" />
              Loading alerts…
            </div>
          )}

          {patientId && isError && (
            <p className="text-sm text-red-600">Could not load monitoring alerts.</p>
          )}

          {patientId && !isLoading && !isError && alerts.length === 0 && (
            <div className="rounded-lg border border-dashed border-gray-300 bg-gray-50 p-12 text-center">
              <AlertTriangle className="mx-auto h-12 w-12 text-gray-400" />
              <h3 className="mt-4 text-sm font-semibold text-gray-900">No alerts</h3>
              <p className="mt-2 text-sm text-gray-600">
                When vitals breach configured thresholds, alerts appear here from wellness remote monitoring.
              </p>
            </div>
          )}

          {patientId && !isLoading && !isError && alerts.length > 0 && (
            <ul className="space-y-3">
              {alerts.map((alert) => (
                <li key={alert.id} className="rounded-xl border border-gray-200 bg-white p-4">
                  <div className="flex flex-wrap items-start justify-between gap-3">
                    <div>
                      <p className="text-sm font-semibold text-gray-900">
                        {alert.vitalType.replace(/_/g, " ")}
                        {alert.observedValue != null ? (
                          <span className="font-normal text-gray-600">
                            {" "}
                            · {alert.observedValue} {alert.unit}
                          </span>
                        ) : null}
                      </p>
                      <p className="text-xs text-gray-500 mt-1">
                        {fmtWhen(alert.measuredAt)} · {alert.severity} · {alert.status}
                      </p>
                    </div>
                    {alert.status === "OPEN" ? (
                      <button
                        type="button"
                        disabled={reviewAlert.isPending}
                        onClick={() => reviewAlert.mutate({ alertId: alert.id })}
                        className="inline-flex items-center gap-1.5 rounded-lg border border-gray-300 px-3 py-1.5 text-xs font-medium text-gray-700 hover:bg-gray-50"
                      >
                        <CheckCheck className="h-3.5 w-3.5" />
                        Acknowledge
                      </button>
                    ) : null}
                  </div>
                </li>
              ))}
            </ul>
          )}
        </div>
      </PageShell>
    </AppLayout>
  );
}
