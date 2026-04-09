"use client";

/**
 * System Monitor — Experience BFF liveness plus admin audit tail.
 * Route: /admin/system-monitor | pageTitle: "System Monitor"
 *
 * Per-service mesh health and infra metrics are not aggregated here; audit entries
 * are governance actions, not application log lines.
 */

import { useMemo } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { Monitor, Loader2, RefreshCw, CheckCircle2, AlertTriangle, XCircle, Cpu, Info } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAuditLogSized } from "@/hooks/queries/useAudit";
import { useBffHealthQuery } from "@/hooks/queries/useAdminObservability";

interface ServiceCard {
  name: string;
  code: string;
  status: "healthy" | "degraded" | "down";
  latency: number;
  uptime: string;
  version: string;
  lastCheck: string;
}

interface AuditRow {
  id: string;
  timestamp: string;
  service: string;
  level: "ERROR" | "WARN" | "INFO";
  message: string;
}

const STATUS_ICON: Record<string, { icon: React.ElementType; color: string; bg: string }> = {
  healthy: { icon: CheckCircle2, color: "text-green-600", bg: "bg-green-100" },
  degraded: { icon: AlertTriangle, color: "text-amber-600", bg: "bg-amber-100" },
  down: { icon: XCircle, color: "text-red-600", bg: "bg-red-100" },
};

function inferAuditLevel(action: string): "ERROR" | "WARN" | "INFO" {
  const a = action.toUpperCase();
  if (a.includes("FAIL") || a.includes("ERROR") || a.includes("DENIED")) return "ERROR";
  if (a.includes("WARN") || a.includes("DELETE") || a.includes("REVOKE")) return "WARN";
  return "INFO";
}

function formatOccurredAt(iso: string | undefined): string {
  if (!iso) return "—";
  try {
    return new Date(iso).toLocaleString();
  } catch {
    return iso;
  }
}

export default function SystemMonitorPage() {
  const queryClient = useQueryClient();
  const healthQ = useBffHealthQuery();
  const auditQ = useAuditLogSized(0, 40);

  const services: ServiceCard[] = useMemo(() => {
    if (healthQ.isLoading) return [];
    if (healthQ.isError || !healthQ.data) {
      return [
        {
          name: "Experience BFF",
          code: "experience-bff",
          status: "down" as const,
          latency: 0,
          uptime: "—",
          version: "—",
          lastCheck: "—",
        },
      ];
    }
    const up = healthQ.data.status?.toUpperCase() === "UP";
    return [
      {
        name: "Experience BFF",
        code: "experience-bff",
        status: up ? "healthy" : ("down" as const),
        latency: healthQ.data.latencyMs,
        uptime: "—",
        version: "—",
        lastCheck: formatOccurredAt(healthQ.data.checkedAt),
      },
    ];
  }, [healthQ.data, healthQ.isLoading, healthQ.isError]);

  const auditRows: AuditRow[] = useMemo(() => {
    const rows = auditQ.data?.data ?? [];
    return rows.map((entry) => {
      const attr = entry.attributes as Record<string, unknown>;
      const action = String(attr.action ?? "");
      const resourceType = String(attr.resource_type ?? "—");
      const details = attr.details != null ? String(attr.details) : "";
      const msg = [action, resourceType, details ? details.slice(0, 200) : ""].filter(Boolean).join(" · ");
      return {
        id: entry.id,
        timestamp: formatOccurredAt(String(attr.occurred_at ?? attr.timestamp ?? "")),
        service: resourceType !== "—" ? resourceType : "Platform",
        level: inferAuditLevel(action),
        message: msg || action || "Audit event",
      };
    });
  }, [auditQ.data]);

  const healthyCount = services.filter((s) => s.status === "healthy").length;
  const degradedCount = services.filter((s) => s.status === "degraded").length;
  const downCount = services.filter((s) => s.status === "down").length;

  const isLoading = healthQ.isLoading || auditQ.isLoading;

  const onRefresh = () => {
    void queryClient.invalidateQueries({ queryKey: ["observability", "bff-health"] });
    void queryClient.invalidateQueries({ queryKey: ["audit"] });
  };

  return (
    <AppLayout>
      <PageShell title="System Monitor" subtitle="BFF liveness and recent admin audit activity">
        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading system status…</span>
          </div>
        ) : (
          <div className="space-y-6">
            {/* Header */}
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Monitor className="w-5 h-5 text-indigo-600" />
                <h2 className="text-lg font-semibold text-gray-900">Platform signals</h2>
                <div className="flex items-center gap-2 ml-4">
                  <span className="flex items-center gap-1 text-xs text-green-600"><CheckCircle2 className="w-3 h-3" /> {healthyCount} healthy</span>
                  {degradedCount > 0 && <span className="flex items-center gap-1 text-xs text-amber-600"><AlertTriangle className="w-3 h-3" /> {degradedCount} degraded</span>}
                  {downCount > 0 && <span className="flex items-center gap-1 text-xs text-red-600"><XCircle className="w-3 h-3" /> {downCount} down</span>}
                </div>
              </div>
              <button type="button" onClick={onRefresh} className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs text-gray-600 border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors">
                <RefreshCw className="w-3.5 h-3.5" /> Refresh
              </button>
            </div>

            <div className="rounded-lg border border-blue-200 bg-blue-50/80 p-4 flex gap-3 text-sm text-blue-900">
              <Info className="w-5 h-5 shrink-0 mt-0.5" />
              <div>
                <p className="font-medium">Scope of this monitor</p>
                <p className="mt-1 text-xs text-blue-800/90 leading-relaxed">
                  Only the Experience BFF <code className="text-[11px]">GET /health</code> probe and the tenant audit log
                  are wired here. Downstream service cards (identity, clinical stacks, Kafka, Redis, etc.) are{" "}
                  <strong>not</strong> aggregated by this BFF—use your runtime platform (mesh, k8s, APM) for those signals.
                  Audit rows are <strong>not</strong> raw application error logs.
                </p>
              </div>
            </div>

            {/* Service Status Grid */}
            <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-3">
              {services.map((svc) => {
                const si = STATUS_ICON[svc.status];
                const Icon = si.icon;
                return (
                  <div key={svc.code} className={`bg-white rounded-lg border border-gray-200 p-4 ${svc.status === "down" ? "border-red-300" : svc.status === "degraded" ? "border-amber-300" : ""}`}>
                    <div className="flex items-center justify-between mb-2">
                      <div className={`w-8 h-8 rounded-lg ${si.bg} flex items-center justify-center`}>
                        <Icon className={`w-4 h-4 ${si.color}`} />
                      </div>
                      <span className="text-xs text-gray-400">{svc.latency}ms</span>
                    </div>
                    <p className="text-sm font-medium text-gray-900">{svc.name}</p>
                    <div className="flex items-center justify-between mt-1">
                      <span className="text-[10px] text-gray-400">v{svc.version}</span>
                      <span className="text-[10px] text-gray-400">Up {svc.uptime}</span>
                    </div>
                    <p className="text-[10px] text-gray-400 mt-1">Checked {svc.lastCheck}</p>
                  </div>
                );
              })}
            </div>

            {/* Infrastructure Metrics */}
            <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
              <div className="px-5 py-4 border-b border-gray-200 flex items-center gap-2">
                <Cpu className="w-4 h-4 text-gray-500" />
                <h3 className="font-medium text-gray-900">Infrastructure Metrics</h3>
              </div>
              <div className="p-5 text-sm text-gray-600">
                Connection pools, Kafka lag, CPU, and disk for dependency services are{" "}
                <strong>not published</strong> on the Experience BFF. Export them from your hosting environment or
                observability stack.
              </div>
            </div>

            {/* Recent Audit */}
            <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
              <div className="px-5 py-4 border-b border-gray-200 flex items-center gap-2">
                <AlertTriangle className="w-4 h-4 text-amber-500" />
                <h3 className="font-medium text-gray-900">Recent audit activity</h3>
                <span className="text-xs text-gray-400 ml-auto">Admin audit log (newest first)</span>
              </div>
              {auditQ.isError ? (
                <div className="p-5 text-sm text-red-600">Could not load audit log.</div>
              ) : auditRows.length === 0 ? (
                <div className="p-8 text-center text-sm text-gray-500">No audit entries for this tenant.</div>
              ) : (
                <div className="divide-y divide-gray-100">
                  {auditRows.map((err) => (
                    <div key={err.id} className="px-5 py-3 hover:bg-gray-50 transition-colors">
                      <div className="flex items-center gap-2 mb-1">
                        <span
                          className={`px-1.5 py-0.5 rounded text-[10px] font-mono font-bold ${
                            err.level === "ERROR"
                              ? "bg-red-100 text-red-700"
                              : err.level === "WARN"
                                ? "bg-amber-100 text-amber-700"
                                : "bg-gray-100 text-gray-700"
                          }`}
                        >
                          {err.level}
                        </span>
                        <span className="text-xs font-medium text-gray-700">{err.service}</span>
                        <span className="text-[10px] text-gray-400">{err.timestamp}</span>
                      </div>
                      <p className="text-xs text-gray-600">{err.message}</p>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
