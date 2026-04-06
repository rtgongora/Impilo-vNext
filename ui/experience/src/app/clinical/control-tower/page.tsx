"use client";

/**
 * Control Tower — Real-time facility operations dashboard with stat cards and panels.
 * Route: /clinical/control-tower | pageTitle: "Control Tower"
 */

import { useState } from "react";
import { BarChart3, Loader2, Users, Clock, BedDouble, UserCheck, AlertTriangle, RefreshCw, ArrowUp, ArrowDown } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";

interface StatCard {
  label: string;
  value: string | number;
  change: number;
  changeLabel: string;
  icon: React.ElementType;
  color: string;
}

interface QueueItem {
  id: string;
  patientName: string;
  type: string;
  waitTime: string;
  priority: "Urgent" | "Normal" | "Low";
  assignedTo: string | null;
}

interface BedStatus {
  ward: string;
  total: number;
  occupied: number;
  available: number;
  cleaning: number;
}

interface Alert {
  id: string;
  type: "critical" | "warning" | "info";
  message: string;
  time: string;
}

const MOCK_STATS: StatCard[] = [
  { label: "Patients in Facility", value: 142, change: 8, changeLabel: "from yesterday", icon: Users, color: "bg-blue-500" },
  { label: "Avg Wait Time", value: "23 min", change: -5, changeLabel: "from last hour", icon: Clock, color: "bg-amber-500" },
  { label: "Bed Occupancy", value: "78%", change: 3, changeLabel: "from yesterday", icon: BedDouble, color: "bg-purple-500" },
  { label: "Staff on Shift", value: 34, change: 0, changeLabel: "full coverage", icon: UserCheck, color: "bg-green-500" },
];

const MOCK_QUEUE: QueueItem[] = [
  { id: "q-1", patientName: "T. Masuku", type: "Walk-in", waitTime: "45 min", priority: "Urgent", assignedTo: null },
  { id: "q-2", patientName: "R. Dube", type: "Scheduled", waitTime: "12 min", priority: "Normal", assignedTo: "Dr. Ndlovu" },
  { id: "q-3", patientName: "N. Chikwanha", type: "Walk-in", waitTime: "38 min", priority: "Normal", assignedTo: null },
  { id: "q-4", patientName: "S. Mutsago", type: "Referral", waitTime: "5 min", priority: "Urgent", assignedTo: "Dr. Moyo" },
  { id: "q-5", patientName: "P. Zimuto", type: "Follow-up", waitTime: "20 min", priority: "Low", assignedTo: "Sr. Phiri" },
];

const MOCK_BEDS: BedStatus[] = [
  { ward: "General Medical", total: 40, occupied: 32, available: 6, cleaning: 2 },
  { ward: "Surgical", total: 30, occupied: 25, available: 4, cleaning: 1 },
  { ward: "Paediatric", total: 20, occupied: 14, available: 5, cleaning: 1 },
  { ward: "Maternity", total: 25, occupied: 20, available: 3, cleaning: 2 },
  { ward: "ICU", total: 8, occupied: 7, available: 1, cleaning: 0 },
];

const MOCK_ALERTS: Alert[] = [
  { id: "a-1", type: "critical", message: "ICU bed capacity at 87% — only 1 bed available", time: "5 min ago" },
  { id: "a-2", type: "warning", message: "Average wait time exceeding 30-minute threshold in OPD", time: "12 min ago" },
  { id: "a-3", type: "warning", message: "3 patients waiting over 40 minutes without assignment", time: "18 min ago" },
  { id: "a-4", type: "info", message: "Night shift handover completed — 34 staff checked in", time: "1 hr ago" },
  { id: "a-5", type: "info", message: "Lab system connection restored after 5-minute outage", time: "2 hr ago" },
];

const PRIORITY_STYLES: Record<string, string> = {
  Urgent: "bg-red-100 text-red-700",
  Normal: "bg-blue-100 text-blue-700",
  Low: "bg-gray-100 text-gray-600",
};

const ALERT_STYLES: Record<string, string> = {
  critical: "border-l-red-500 bg-red-50",
  warning: "border-l-amber-500 bg-amber-50",
  info: "border-l-blue-500 bg-blue-50",
};

export default function ControlTowerPage() {
  const { data, isLoading } = useQuery({
    queryKey: ["control-tower"],
    queryFn: async () => ({ stats: MOCK_STATS, queue: MOCK_QUEUE, beds: MOCK_BEDS, alerts: MOCK_ALERTS }),
    refetchInterval: 30000,
  });

  const stats = data?.stats ?? MOCK_STATS;
  const queue = data?.queue ?? [];
  const beds = data?.beds ?? [];
  const alerts = data?.alerts ?? [];

  return (
    <AppLayout>
      <PageShell title="Control Tower" subtitle="Real-time facility operations dashboard">
        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading dashboard...</span>
          </div>
        ) : (
          <div className="space-y-6">
            {/* Header */}
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <BarChart3 className="w-5 h-5 text-rose-600" />
                <h2 className="text-lg font-semibold text-gray-900">Facility Overview</h2>
              </div>
              <button className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs text-gray-600 border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors">
                <RefreshCw className="w-3.5 h-3.5" /> Refresh
              </button>
            </div>

            {/* Stat Cards */}
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
              {stats.map((stat) => {
                const Icon = stat.icon;
                return (
                  <div key={stat.label} className="bg-white rounded-lg border border-gray-200 p-5">
                    <div className="flex items-center justify-between mb-3">
                      <div className={`w-10 h-10 rounded-lg ${stat.color} flex items-center justify-center`}>
                        <Icon className="w-5 h-5 text-white" />
                      </div>
                      {stat.change !== 0 && (
                        <span className={`flex items-center gap-0.5 text-xs font-medium ${stat.change > 0 ? "text-green-600" : "text-red-600"}`}>
                          {stat.change > 0 ? <ArrowUp className="w-3 h-3" /> : <ArrowDown className="w-3 h-3" />}
                          {Math.abs(stat.change)}
                        </span>
                      )}
                    </div>
                    <p className="text-2xl font-bold text-gray-900">{stat.value}</p>
                    <p className="text-xs text-gray-500 mt-1">{stat.label}</p>
                  </div>
                );
              })}
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
              {/* Queue Status */}
              <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
                <div className="px-5 py-4 border-b border-gray-200 flex items-center justify-between">
                  <h3 className="font-medium text-gray-900">Queue Status</h3>
                  <span className="text-xs text-gray-400">{queue.length} patients</span>
                </div>
                <div className="divide-y divide-gray-100">
                  {queue.map((item) => (
                    <div key={item.id} className="px-5 py-3 flex items-center justify-between hover:bg-gray-50 transition-colors">
                      <div>
                        <p className="text-sm font-medium text-gray-900">{item.patientName}</p>
                        <p className="text-xs text-gray-500">{item.type} &middot; Waiting {item.waitTime}</p>
                      </div>
                      <div className="flex items-center gap-2">
                        <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${PRIORITY_STYLES[item.priority]}`}>{item.priority}</span>
                        {item.assignedTo ? (
                          <span className="text-xs text-green-600">{item.assignedTo}</span>
                        ) : (
                          <span className="text-xs text-amber-600">Unassigned</span>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              </div>

              {/* Bed Utilization */}
              <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
                <div className="px-5 py-4 border-b border-gray-200">
                  <h3 className="font-medium text-gray-900">Bed Utilization</h3>
                </div>
                <div className="p-5 space-y-4">
                  {beds.map((ward) => {
                    const occupancyPct = Math.round((ward.occupied / ward.total) * 100);
                    return (
                      <div key={ward.ward}>
                        <div className="flex items-center justify-between mb-1">
                          <span className="text-sm text-gray-700">{ward.ward}</span>
                          <span className="text-xs text-gray-500">{ward.occupied}/{ward.total} ({occupancyPct}%)</span>
                        </div>
                        <div className="w-full bg-gray-100 rounded-full h-3 flex overflow-hidden">
                          <div className="bg-blue-500 h-3" style={{ width: `${(ward.occupied / ward.total) * 100}%` }} />
                          <div className="bg-amber-400 h-3" style={{ width: `${(ward.cleaning / ward.total) * 100}%` }} />
                        </div>
                        <div className="flex items-center gap-3 mt-1 text-[10px] text-gray-400">
                          <span>{ward.available} available</span>
                          {ward.cleaning > 0 && <span>{ward.cleaning} cleaning</span>}
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
            </div>

            {/* Alerts & Escalations */}
            <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
              <div className="px-5 py-4 border-b border-gray-200 flex items-center gap-2">
                <AlertTriangle className="w-4 h-4 text-amber-500" />
                <h3 className="font-medium text-gray-900">Alerts & Escalations</h3>
              </div>
              <div className="divide-y divide-gray-100">
                {alerts.map((alert) => (
                  <div key={alert.id} className={`px-5 py-3 border-l-4 ${ALERT_STYLES[alert.type]}`}>
                    <div className="flex items-center justify-between">
                      <p className="text-sm text-gray-800">{alert.message}</p>
                      <span className="text-xs text-gray-400 shrink-0 ml-4">{alert.time}</span>
                    </div>
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
