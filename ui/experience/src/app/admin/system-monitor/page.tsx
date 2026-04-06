"use client";

/**
 * System Monitor — System health dashboard with service status and metrics.
 * Route: /admin/system-monitor | pageTitle: "System Monitor"
 */

import { useState } from "react";
import { Monitor, Loader2, RefreshCw, CheckCircle2, AlertTriangle, XCircle, Database, Cpu, HardDrive, Wifi } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";

interface ServiceStatus {
  name: string;
  code: string;
  status: "healthy" | "degraded" | "down";
  latency: number;
  uptime: string;
  version: string;
  lastCheck: string;
}

interface SystemMetric {
  label: string;
  value: string;
  max: string;
  percentage: number;
  status: "good" | "warning" | "critical";
}

interface ErrorEntry {
  id: string;
  timestamp: string;
  service: string;
  level: "ERROR" | "WARN";
  message: string;
}

const MOCK_SERVICES: ServiceStatus[] = [
  { name: "TSHEPO (Auth)", code: "tshepo", status: "healthy", latency: 12, uptime: "99.99%", version: "1.4.2", lastCheck: "10s ago" },
  { name: "VITO (Identity)", code: "vito", status: "healthy", latency: 18, uptime: "99.97%", version: "1.3.1", lastCheck: "10s ago" },
  { name: "VARAPI (Registry)", code: "varapi", status: "healthy", latency: 15, uptime: "99.98%", version: "1.2.0", lastCheck: "10s ago" },
  { name: "TUSO (Clinical)", code: "tuso", status: "healthy", latency: 22, uptime: "99.95%", version: "1.5.0", lastCheck: "10s ago" },
  { name: "ZIBO (Finance)", code: "zibo", status: "degraded", latency: 145, uptime: "99.82%", version: "1.1.3", lastCheck: "10s ago" },
  { name: "PCT (Claims)", code: "pct", status: "healthy", latency: 28, uptime: "99.96%", version: "1.0.4", lastCheck: "10s ago" },
  { name: "OROS (Orders)", code: "oros", status: "healthy", latency: 20, uptime: "99.94%", version: "1.2.1", lastCheck: "10s ago" },
  { name: "HAPI FHIR (SHR)", code: "hapi", status: "healthy", latency: 35, uptime: "99.91%", version: "7.4.0", lastCheck: "10s ago" },
  { name: "Keycloak (IAM)", code: "keycloak", status: "healthy", latency: 25, uptime: "99.99%", version: "25.0.2", lastCheck: "10s ago" },
  { name: "Envoy (Gateway)", code: "envoy", status: "healthy", latency: 3, uptime: "99.99%", version: "1.31.2", lastCheck: "10s ago" },
  { name: "Kafka (Events)", code: "kafka", status: "healthy", latency: 8, uptime: "99.98%", version: "3.7.1", lastCheck: "10s ago" },
  { name: "Redis (Cache)", code: "redis", status: "healthy", latency: 2, uptime: "99.99%", version: "7.2.4", lastCheck: "10s ago" },
];

const MOCK_METRICS: SystemMetric[] = [
  { label: "DB Connection Pool", value: "42", max: "100", percentage: 42, status: "good" },
  { label: "Kafka Consumer Lag", value: "124", max: "10000", percentage: 1.2, status: "good" },
  { label: "Redis Cache Hit Rate", value: "94.2%", max: "100%", percentage: 94.2, status: "good" },
  { label: "CPU Usage (Avg)", value: "38%", max: "100%", percentage: 38, status: "good" },
  { label: "Memory Usage (Avg)", value: "62%", max: "100%", percentage: 62, status: "warning" },
  { label: "Disk Usage", value: "71%", max: "100%", percentage: 71, status: "warning" },
];

const MOCK_ERRORS: ErrorEntry[] = [
  { id: "e-1", timestamp: "2026-04-06 09:42:15", service: "ZIBO", level: "WARN", message: "Slow query detected: claims_adjudication_batch took 3.2s (threshold: 2s)" },
  { id: "e-2", timestamp: "2026-04-06 09:38:02", service: "ZIBO", level: "ERROR", message: "Connection timeout to external payment gateway after 30s — retrying (attempt 2/3)" },
  { id: "e-3", timestamp: "2026-04-06 09:22:45", service: "TUSO", level: "WARN", message: "FHIR bundle validation warning: 2 resources missing optional coding system" },
  { id: "e-4", timestamp: "2026-04-06 08:55:10", service: "HAPI", level: "WARN", message: "Reindex job completed with 3 skipped resources (invalid references)" },
  { id: "e-5", timestamp: "2026-04-06 08:12:30", service: "Kafka", level: "WARN", message: "Consumer group rebalance triggered — 2 partitions reassigned" },
];

const STATUS_ICON: Record<string, { icon: React.ElementType; color: string; bg: string }> = {
  healthy: { icon: CheckCircle2, color: "text-green-600", bg: "bg-green-100" },
  degraded: { icon: AlertTriangle, color: "text-amber-600", bg: "bg-amber-100" },
  down: { icon: XCircle, color: "text-red-600", bg: "bg-red-100" },
};

const METRIC_COLOR: Record<string, string> = {
  good: "bg-green-500",
  warning: "bg-amber-500",
  critical: "bg-red-500",
};

export default function SystemMonitorPage() {
  const { data, isLoading } = useQuery({
    queryKey: ["system-monitor"],
    queryFn: async () => ({ services: MOCK_SERVICES, metrics: MOCK_METRICS, errors: MOCK_ERRORS }),
    refetchInterval: 30000,
  });

  const services = data?.services ?? [];
  const metrics = data?.metrics ?? [];
  const errors = data?.errors ?? [];

  const healthyCount = services.filter((s) => s.status === "healthy").length;
  const degradedCount = services.filter((s) => s.status === "degraded").length;
  const downCount = services.filter((s) => s.status === "down").length;

  return (
    <AppLayout>
      <PageShell title="System Monitor" subtitle="Service health, metrics, and error tracking">
        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading system status...</span>
          </div>
        ) : (
          <div className="space-y-6">
            {/* Header */}
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Monitor className="w-5 h-5 text-indigo-600" />
                <h2 className="text-lg font-semibold text-gray-900">System Health</h2>
                <div className="flex items-center gap-2 ml-4">
                  <span className="flex items-center gap-1 text-xs text-green-600"><CheckCircle2 className="w-3 h-3" /> {healthyCount} healthy</span>
                  {degradedCount > 0 && <span className="flex items-center gap-1 text-xs text-amber-600"><AlertTriangle className="w-3 h-3" /> {degradedCount} degraded</span>}
                  {downCount > 0 && <span className="flex items-center gap-1 text-xs text-red-600"><XCircle className="w-3 h-3" /> {downCount} down</span>}
                </div>
              </div>
              <button className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs text-gray-600 border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors">
                <RefreshCw className="w-3.5 h-3.5" /> Refresh
              </button>
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
              <div className="grid grid-cols-2 md:grid-cols-3 gap-4 p-5">
                {metrics.map((m) => (
                  <div key={m.label}>
                    <div className="flex items-center justify-between mb-1">
                      <span className="text-xs text-gray-600">{m.label}</span>
                      <span className="text-xs font-medium text-gray-900">{m.value}</span>
                    </div>
                    <div className="w-full bg-gray-100 rounded-full h-2.5">
                      <div className={`h-2.5 rounded-full ${METRIC_COLOR[m.status]}`} style={{ width: `${Math.min(100, m.percentage)}%` }} />
                    </div>
                  </div>
                ))}
              </div>
            </div>

            {/* Recent Errors */}
            <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
              <div className="px-5 py-4 border-b border-gray-200 flex items-center gap-2">
                <AlertTriangle className="w-4 h-4 text-amber-500" />
                <h3 className="font-medium text-gray-900">Recent Errors & Warnings</h3>
              </div>
              <div className="divide-y divide-gray-100">
                {errors.map((err) => (
                  <div key={err.id} className="px-5 py-3 hover:bg-gray-50 transition-colors">
                    <div className="flex items-center gap-2 mb-1">
                      <span className={`px-1.5 py-0.5 rounded text-[10px] font-mono font-bold ${err.level === "ERROR" ? "bg-red-100 text-red-700" : "bg-amber-100 text-amber-700"}`}>{err.level}</span>
                      <span className="text-xs font-medium text-gray-700">{err.service}</span>
                      <span className="text-[10px] text-gray-400">{err.timestamp}</span>
                    </div>
                    <p className="text-xs text-gray-600">{err.message}</p>
                  </div>
                ))}
              </div>
            </div>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
