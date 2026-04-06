"use client";

/**
 * Integration Status — Upstream/downstream system health and sync monitoring.
 * Route: /admin/integration-status | pageTitle: "Integration Status"
 */

import { useState } from "react";
import { Globe, Loader2, RefreshCw, CheckCircle2, AlertTriangle, XCircle, Plug, ArrowUpDown, Zap } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";

interface IntegrationSystem {
  id: string;
  name: string;
  type: "upstream" | "downstream";
  description: string;
  status: "connected" | "degraded" | "disconnected";
  lastSync: string;
  lastSyncStatus: "success" | "partial" | "failed";
  errorCount24h: number;
  recordsSynced24h: number;
  avgLatency: number;
  protocol: string;
}

const MOCK_INTEGRATIONS: IntegrationSystem[] = [
  { id: "int-1", name: "DHIS2", type: "downstream", description: "District Health Information System — aggregate reporting", status: "connected", lastSync: "2026-04-06 06:00", lastSyncStatus: "success", errorCount24h: 0, recordsSynced24h: 1250, avgLatency: 340, protocol: "REST API" },
  { id: "int-2", name: "LMIS (ZINL)", type: "downstream", description: "Logistics Management — stock levels and consumption", status: "connected", lastSync: "2026-04-06 08:00", lastSyncStatus: "success", errorCount24h: 2, recordsSynced24h: 450, avgLatency: 520, protocol: "REST API" },
  { id: "int-3", name: "MOSIP", type: "upstream", description: "National ID verification and biometric matching", status: "connected", lastSync: "2026-04-06 09:30", lastSyncStatus: "success", errorCount24h: 0, recordsSynced24h: 85, avgLatency: 180, protocol: "REST + ABIS" },
  { id: "int-4", name: "ZINARA", type: "upstream", description: "Zimbabwe National Administration — citizen registry", status: "degraded", lastSync: "2026-04-06 07:15", lastSyncStatus: "partial", errorCount24h: 12, recordsSynced24h: 30, avgLatency: 2100, protocol: "SOAP/XML" },
  { id: "int-5", name: "NHIS (PSMAS)", type: "downstream", description: "National Health Insurance claims submission", status: "connected", lastSync: "2026-04-05 23:00", lastSyncStatus: "success", errorCount24h: 1, recordsSynced24h: 320, avgLatency: 450, protocol: "HL7 FHIR" },
  { id: "int-6", name: "OpenHIE SHR", type: "downstream", description: "Shared Health Record — clinical document exchange", status: "connected", lastSync: "2026-04-06 09:45", lastSyncStatus: "success", errorCount24h: 0, recordsSynced24h: 890, avgLatency: 120, protocol: "HL7 FHIR" },
  { id: "int-7", name: "MPI (OpenCR)", type: "upstream", description: "Master Patient Index — patient matching", status: "connected", lastSync: "2026-04-06 09:50", lastSyncStatus: "success", errorCount24h: 0, recordsSynced24h: 145, avgLatency: 95, protocol: "HL7 FHIR" },
  { id: "int-8", name: "Payment Gateway", type: "downstream", description: "EcoCash/OneMoney payment processing", status: "disconnected", lastSync: "2026-04-06 08:22", lastSyncStatus: "failed", errorCount24h: 45, recordsSynced24h: 0, avgLatency: 0, protocol: "REST API" },
  { id: "int-9", name: "SMS Gateway (Twilio)", type: "downstream", description: "Patient notifications and appointment reminders", status: "connected", lastSync: "2026-04-06 09:55", lastSyncStatus: "success", errorCount24h: 3, recordsSynced24h: 2100, avgLatency: 200, protocol: "REST API" },
  { id: "int-10", name: "Laboratory (Biotics)", type: "upstream", description: "Lab results feed from Biotics LIS", status: "connected", lastSync: "2026-04-06 09:48", lastSyncStatus: "success", errorCount24h: 1, recordsSynced24h: 560, avgLatency: 280, protocol: "HL7v2 ADT/ORU" },
];

const STATUS_CONFIG: Record<string, { icon: React.ElementType; color: string; bg: string; label: string }> = {
  connected: { icon: CheckCircle2, color: "text-green-600", bg: "bg-green-100", label: "Connected" },
  degraded: { icon: AlertTriangle, color: "text-amber-600", bg: "bg-amber-100", label: "Degraded" },
  disconnected: { icon: XCircle, color: "text-red-600", bg: "bg-red-100", label: "Disconnected" },
};

const SYNC_STYLES: Record<string, string> = {
  success: "text-green-600",
  partial: "text-amber-600",
  failed: "text-red-600",
};

export default function IntegrationStatusPage() {
  const { data, isLoading } = useQuery({
    queryKey: ["integration-status"],
    queryFn: async () => ({ data: MOCK_INTEGRATIONS }),
    refetchInterval: 60000,
  });

  const integrations = data?.data ?? [];
  const [filterType, setFilterType] = useState<string>("all");
  const [testingId, setTestingId] = useState<string | null>(null);

  const filtered = filterType === "all" ? integrations : integrations.filter((i) => i.type === filterType);

  const connectedCount = integrations.filter((i) => i.status === "connected").length;
  const degradedCount = integrations.filter((i) => i.status === "degraded").length;
  const disconnectedCount = integrations.filter((i) => i.status === "disconnected").length;

  function testConnection(id: string) {
    setTestingId(id);
    setTimeout(() => setTestingId(null), 2000);
  }

  return (
    <AppLayout>
      <PageShell title="Integration Status" subtitle="External system connectivity and sync health">
        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading integration status...</span>
          </div>
        ) : (
          <div className="space-y-6">
            {/* Header */}
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Globe className="w-5 h-5 text-teal-600" />
                <h2 className="text-lg font-semibold text-gray-900">Integration Health</h2>
              </div>
              <div className="flex items-center gap-3">
                <select value={filterType} onChange={(e) => setFilterType(e.target.value)} className="rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
                  <option value="all">All Systems</option>
                  <option value="upstream">Upstream</option>
                  <option value="downstream">Downstream</option>
                </select>
              </div>
            </div>

            {/* Summary */}
            <div className="grid grid-cols-3 gap-4">
              <div className="bg-green-50 rounded-lg p-4 flex items-center gap-3">
                <CheckCircle2 className="w-8 h-8 text-green-600" />
                <div>
                  <p className="text-2xl font-bold text-green-700">{connectedCount}</p>
                  <p className="text-xs text-green-600">Connected</p>
                </div>
              </div>
              <div className="bg-amber-50 rounded-lg p-4 flex items-center gap-3">
                <AlertTriangle className="w-8 h-8 text-amber-600" />
                <div>
                  <p className="text-2xl font-bold text-amber-700">{degradedCount}</p>
                  <p className="text-xs text-amber-600">Degraded</p>
                </div>
              </div>
              <div className="bg-red-50 rounded-lg p-4 flex items-center gap-3">
                <XCircle className="w-8 h-8 text-red-600" />
                <div>
                  <p className="text-2xl font-bold text-red-700">{disconnectedCount}</p>
                  <p className="text-xs text-red-600">Disconnected</p>
                </div>
              </div>
            </div>

            {/* Integration Table */}
            <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-gray-200 bg-gray-50">
                      <th className="text-left px-4 py-3 font-medium text-gray-600">System</th>
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Status</th>
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Last Sync</th>
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Errors (24h)</th>
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Records (24h)</th>
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Latency</th>
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filtered.map((sys) => {
                      const sc = STATUS_CONFIG[sys.status];
                      const StatusIcon = sc.icon;
                      return (
                        <tr key={sys.id} className={`border-b border-gray-100 hover:bg-gray-50 transition-colors ${sys.status === "disconnected" ? "bg-red-50/30" : ""}`}>
                          <td className="px-4 py-3">
                            <div className="flex items-center gap-2">
                              <span className={`px-1.5 py-0.5 rounded text-[10px] font-medium ${sys.type === "upstream" ? "bg-blue-100 text-blue-700" : "bg-purple-100 text-purple-700"}`}>
                                {sys.type === "upstream" ? "UP" : "DOWN"}
                              </span>
                              <div>
                                <p className="font-medium text-gray-900">{sys.name}</p>
                                <p className="text-[10px] text-gray-400">{sys.description}</p>
                                <p className="text-[10px] text-gray-400">{sys.protocol}</p>
                              </div>
                            </div>
                          </td>
                          <td className="px-4 py-3">
                            <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium ${sc.bg} ${sc.color}`}>
                              <StatusIcon className="w-3 h-3" /> {sc.label}
                            </span>
                          </td>
                          <td className="px-4 py-3">
                            <p className="text-xs text-gray-700">{sys.lastSync}</p>
                            <p className={`text-[10px] font-medium ${SYNC_STYLES[sys.lastSyncStatus]}`}>{sys.lastSyncStatus}</p>
                          </td>
                          <td className="px-4 py-3">
                            <span className={`text-sm font-medium ${sys.errorCount24h > 10 ? "text-red-600" : sys.errorCount24h > 0 ? "text-amber-600" : "text-green-600"}`}>
                              {sys.errorCount24h}
                            </span>
                          </td>
                          <td className="px-4 py-3 text-gray-700">{sys.recordsSynced24h.toLocaleString()}</td>
                          <td className="px-4 py-3">
                            <span className={`text-xs ${sys.avgLatency > 1000 ? "text-red-600" : sys.avgLatency > 500 ? "text-amber-600" : "text-green-600"}`}>
                              {sys.avgLatency}ms
                            </span>
                          </td>
                          <td className="px-4 py-3">
                            <div className="flex items-center gap-1">
                              <button
                                onClick={() => testConnection(sys.id)}
                                disabled={testingId === sys.id}
                                className="inline-flex items-center gap-1 px-2 py-1 text-xs text-blue-600 border border-blue-200 rounded hover:bg-blue-50 disabled:opacity-50 transition-colors"
                              >
                                {testingId === sys.id ? <Loader2 className="w-3 h-3 animate-spin" /> : <Zap className="w-3 h-3" />}
                                Test
                              </button>
                              {sys.errorCount24h > 0 && (
                                <button className="inline-flex items-center gap-1 px-2 py-1 text-xs text-amber-600 border border-amber-200 rounded hover:bg-amber-50 transition-colors">
                                  <RefreshCw className="w-3 h-3" /> Retry
                                </button>
                              )}
                            </div>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
