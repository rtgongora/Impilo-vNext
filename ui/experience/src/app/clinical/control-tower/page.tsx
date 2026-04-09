"use client";

/**
 * Control Tower — Facility operations dashboard from live Experience BFF data.
 * Route: /clinical/control-tower | pageTitle: "Control Tower"
 */

import { useMemo } from "react";
import Link from "next/link";
import {
  BarChart3,
  Loader2,
  Users,
  Clock,
  BedDouble,
  UserCheck,
  AlertTriangle,
  RefreshCw,
} from "lucide-react";
import { useQueryClient } from "@tanstack/react-query";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import {
  buildOperationalAlerts,
  buildQueuePerformanceByType,
  buildQueueRows,
  buildWardUtilization,
  facilityOpsKeys,
  useFacilityActiveShiftCount,
  useFacilityBeds,
  useFacilityQueueStats,
  useFacilityQueueWaiting,
  useFacilityWards,
} from "@/hooks/queries/useFacilityOperations";

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
  const facility = useFacilityStore((s) => s.facility);
  const fid = facility?.id;
  const queryClient = useQueryClient();

  const wardsQ = useFacilityWards(fid);
  const bedsQ = useFacilityBeds(fid);
  const queueQ = useFacilityQueueWaiting(fid);
  const statsQ = useFacilityQueueStats(fid);
  const shiftsQ = useFacilityActiveShiftCount(fid);

  const wards = wardsQ.data?.data ?? [];
  const beds = bedsQ.data?.data ?? [];
  const queueEntries = queueQ.data?.data ?? [];
  const queueStats = statsQ.data?.data;

  const queueRows = useMemo(() => buildQueueRows(queueEntries), [queueEntries]);
  const bedUtil = useMemo(() => buildWardUtilization(wards, beds), [wards, beds]);
  const queueByType = useMemo(() => buildQueuePerformanceByType(queueEntries), [queueEntries]);

  const alerts = useMemo(
    () =>
      buildOperationalAlerts({
        wards,
        beds,
        queueStats,
        queueEntriesWaiting: queueEntries,
      }),
    [wards, beds, queueStats, queueEntries]
  );

  const statCards = useMemo(() => {
    const occupied = beds.filter((b) => b.attributes.status === "OCCUPIED").length;
    const total = beds.length;
    const occPct = total > 0 ? Math.round((occupied / total) * 100) : 0;
    const avgMin =
      queueStats && queueStats.avgWaitSeconds > 0
        ? Math.max(1, Math.round(queueStats.avgWaitSeconds / 60))
        : 0;
    const waitLabel = queueStats && queueStats.waiting > 0 ? `${avgMin} min` : "—";

    return [
      {
        label: "Patients in beds",
        value: String(occupied),
        footnote: total > 0 ? `${occupied} occupied of ${total} configured beds` : "No bed rows for facility",
        icon: Users,
        color: "bg-blue-500",
      },
      {
        label: "Avg wait (waiting, today)",
        value: waitLabel,
        footnote:
          queueStats && queueStats.waiting > 0
            ? `${queueStats.waiting} waiting (today's queue snapshot)`
            : "Queue stats when patients are waiting",
        icon: Clock,
        color: "bg-amber-500",
      },
      {
        label: "Bed occupancy",
        value: total > 0 ? `${occPct}%` : "—",
        footnote: total > 0 ? `${occupied} / ${total}` : "Configure wards and beds in admin",
        icon: BedDouble,
        color: "bg-purple-500",
      },
      {
        label: "Active shifts (roster week)",
        value: String(shiftsQ.activeShiftCount),
        footnote: "Distinct staff with ACTIVE shift and no end time in current ISO week",
        icon: UserCheck,
        color: "bg-green-500",
      },
    ];
  }, [beds, queueStats, shiftsQ.activeShiftCount]);

  const loading =
    !!fid &&
    (wardsQ.isLoading || bedsQ.isLoading || queueQ.isLoading || statsQ.isLoading || shiftsQ.isLoading);

  const onRefresh = () => {
    if (!fid) return;
    void queryClient.invalidateQueries({ queryKey: facilityOpsKeys.all(fid) });
    void queryClient.invalidateQueries({ queryKey: ["staffing", "roster-week", fid] });
  };

  return (
    <AppLayout>
      <PageShell title="Control Tower" subtitle="Facility operations dashboard (live BFF data)">
        {!fid ? (
          <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
            <BarChart3 className="w-10 h-10 text-gray-300 mx-auto mb-3" />
            <p className="text-gray-600 text-sm">Select a facility to load control tower metrics.</p>
            <Link href="/home" className="mt-2 inline-block text-sm text-blue-600 hover:text-blue-800">
              Go to Home →
            </Link>
          </div>
        ) : loading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading dashboard…</span>
          </div>
        ) : (
          <div className="space-y-6">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <BarChart3 className="w-5 h-5 text-rose-600" />
                <h2 className="text-lg font-semibold text-gray-900">Facility overview</h2>
                <span className="text-xs text-gray-500">({facility.name})</span>
              </div>
              <button
                type="button"
                onClick={onRefresh}
                className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs text-gray-600 border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors"
              >
                <RefreshCw className="w-3.5 h-3.5" /> Refresh
              </button>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
              {statCards.map((stat) => {
                const Icon = stat.icon;
                return (
                  <div key={stat.label} className="bg-white rounded-lg border border-gray-200 p-5">
                    <div className="flex items-center justify-between mb-3">
                      <div className={`w-10 h-10 rounded-lg ${stat.color} flex items-center justify-center`}>
                        <Icon className="w-5 h-5 text-white" />
                      </div>
                    </div>
                    <p className="text-2xl font-bold text-gray-900">{stat.value}</p>
                    <p className="text-xs text-gray-500 mt-1">{stat.label}</p>
                    <p className="text-[10px] text-gray-400 mt-2 leading-snug">{stat.footnote}</p>
                  </div>
                );
              })}
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
              <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
                <div className="px-5 py-4 border-b border-gray-200 flex items-center justify-between">
                  <h3 className="font-medium text-gray-900">Queue — waiting</h3>
                  <span className="text-xs text-gray-400">{queueRows.length} entries (page)</span>
                </div>
                <div className="divide-y divide-gray-100">
                  {queueRows.length === 0 ? (
                    <div className="px-5 py-8 text-center text-sm text-gray-500">No WAITING queue entries.</div>
                  ) : (
                    queueRows.map((item) => (
                      <div
                        key={item.id}
                        className="px-5 py-3 flex items-center justify-between hover:bg-gray-50 transition-colors"
                      >
                        <div>
                          <p className="text-sm font-medium text-gray-900">{item.patientName}</p>
                          <p className="text-xs text-gray-500">
                            {item.type} &middot; Waiting {item.waitTime}
                          </p>
                        </div>
                        <div className="flex items-center gap-2">
                          <span
                            className={`px-2 py-0.5 rounded-full text-xs font-medium ${PRIORITY_STYLES[item.priority]}`}
                          >
                            {item.priority}
                          </span>
                          {item.assignedTo ? (
                            <span className="text-xs text-green-600">{item.assignedTo}</span>
                          ) : (
                            <span className="text-xs text-amber-600">Unassigned</span>
                          )}
                        </div>
                      </div>
                    ))
                  )}
                </div>
              </div>

              <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
                <div className="px-5 py-4 border-b border-gray-200">
                  <h3 className="font-medium text-gray-900">Bed utilization by ward</h3>
                </div>
                <div className="p-5 space-y-4">
                  {bedUtil.length === 0 ? (
                    <p className="text-sm text-gray-500">No ward or bed data for this facility.</p>
                  ) : (
                    bedUtil.map((ward) => {
                      const occupancyPct = ward.total > 0 ? Math.round((ward.occupied / ward.total) * 100) : 0;
                      return (
                        <div key={ward.ward}>
                          <div className="flex items-center justify-between mb-1">
                            <span className="text-sm text-gray-700">{ward.ward}</span>
                            <span className="text-xs text-gray-500">
                              {ward.occupied}/{ward.total} ({occupancyPct}%)
                            </span>
                          </div>
                          <div className="w-full bg-gray-100 rounded-full h-3 flex overflow-hidden">
                            <div
                              className="bg-blue-500 h-3"
                              style={{
                                width: ward.total > 0 ? `${(ward.occupied / ward.total) * 100}%` : "0%",
                              }}
                            />
                            <div
                              className="bg-amber-400 h-3"
                              style={{
                                width: ward.total > 0 ? `${(ward.cleaning / ward.total) * 100}%` : "0%",
                              }}
                            />
                          </div>
                          <div className="flex items-center gap-3 mt-1 text-[10px] text-gray-400">
                            <span>{ward.available} available</span>
                            {ward.cleaning > 0 && <span>{ward.cleaning} cleaning</span>}
                          </div>
                        </div>
                      );
                    })
                  )}
                </div>
              </div>
            </div>

            <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
              <div className="px-5 py-4 border-b border-gray-200">
                <h3 className="font-medium text-gray-900">Waiting load by queue type</h3>
                <p className="text-xs text-gray-500 mt-1">Derived from the same WAITING page as the queue list.</p>
              </div>
              <div className="p-5 space-y-3">
                {queueByType.length === 0 ? (
                  <p className="text-sm text-gray-500">No waiting entries to group.</p>
                ) : (
                  queueByType.map((q) => (
                    <div key={q.name} className="flex items-center justify-between text-sm p-2 rounded-lg bg-gray-50">
                      <div>
                        <span className="font-medium">{q.name}</span>
                        <div className="text-xs text-gray-500 mt-0.5">{q.waiting} waiting</div>
                      </div>
                      <span
                        className={`px-2 py-0.5 rounded-full text-xs font-medium ${
                          q.avgWait > 30
                            ? "bg-red-100 text-red-700"
                            : q.avgWait > 20
                              ? "bg-gray-100 text-gray-600"
                              : "bg-green-100 text-green-700"
                        }`}
                      >
                        ~{q.avgWait} min avg
                      </span>
                    </div>
                  ))
                )}
              </div>
            </div>

            <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
              <div className="px-5 py-4 border-b border-gray-200 flex items-center gap-2">
                <AlertTriangle className="w-4 h-4 text-amber-500" />
                <h3 className="font-medium text-gray-900">Alerts &amp; thresholds</h3>
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
