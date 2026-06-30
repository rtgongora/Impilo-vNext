"use client";

/** IoT / device status — active derived alerts (no fabricated telemetry), resolve + Khuluma-notify. */

import { Loader2, Radio } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  equipmentItems, useActiveAlerts, useResolveAlert, useNotifyAlert } from "@/hooks/queries/useEquipment";



export default function IotStatusPage() {
  const alerts = useActiveAlerts();
  const resolve = useResolveAlert();
  const notify = useNotifyAlert();
  const rows = equipmentItems(alerts.data);

  return (
    <AppLayout>
      <PageShell title="IoT & Device Status" subtitle="Derived alerts from real telemetry/heartbeat — offline, threshold, battery, tamper" icon={<Radio className="h-6 w-6" />}>
        <div className="space-y-6">
          <div className="rounded-md border border-border bg-neutral-50 px-4 py-3 text-xs text-muted-foreground">
            Alerts are derived from real device signals ingested by iot-ingestion-service. No synthetic telemetry is generated here. Clinical observations route to Butano; cold-chain breaches route to Madi.
          </div>

          {alerts.isLoading ? (
            <p className="flex items-center gap-2 text-sm text-muted-foreground"><Loader2 className="h-4 w-4 animate-spin" /> Loading alerts…</p>
          ) : rows.length === 0 ? (
            <p className="text-sm text-muted-foreground">No active device alerts.</p>
          ) : (
            <table className="w-full text-sm rounded-lg border border-border bg-card overflow-hidden">
              <thead className="bg-neutral-50 text-left text-xs uppercase text-muted-foreground">
                <tr><th className="px-4 py-2">Device</th><th className="px-4 py-2">Alert</th><th className="px-4 py-2">Severity</th><th className="px-4 py-2">Observed</th><th className="px-4 py-2"></th></tr>
              </thead>
              <tbody>
                {rows.map((a) => {
                  const id = String(a.alert_id);
                  return (
                    <tr key={id} className="border-t border-border">
                      <td className="px-4 py-2 font-mono text-xs">{String(a.device_id)}</td>
                      <td className="px-4 py-2">{String(a.alert_type)}{a.metric_type ? ` · ${a.metric_type}` : ""}</td>
                      <td className="px-4 py-2">
                        <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${a.severity === "CRITICAL" ? "bg-red-100 text-red-800" : "bg-amber-100 text-warning-foreground"}`}>{String(a.severity)}</span>
                      </td>
                      <td className="px-4 py-2 text-muted-foreground">{a.observed_value != null ? String(a.observed_value) : "—"}</td>
                      <td className="px-4 py-2 text-right space-x-2">
                        <button disabled={notify.isPending || a.khuluma_requested === true} onClick={() => notify.mutate({ alertId: id })} className="rounded-md border border-border px-2 py-1 text-xs hover:bg-neutral-50 disabled:opacity-60">
                          {a.khuluma_requested === true ? "Notified" : "Notify (Khuluma)"}
                        </button>
                        <button disabled={resolve.isPending} onClick={() => resolve.mutate(id)} className="rounded-md border border-border px-2 py-1 text-xs hover:bg-neutral-50 disabled:opacity-60">Resolve</button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}
        </div>
      </PageShell>
    </AppLayout>
  );
}
