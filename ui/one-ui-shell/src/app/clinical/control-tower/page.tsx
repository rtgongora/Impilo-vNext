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
import { useClinicalWorklist } from "@/hooks/queries/useClinicalWorklist";
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
  Urgent: "bg-red-100 text-danger",
  Normal: "bg-primary-soft text-primary",
  Low: "bg-neutral-100 text-muted-foreground",
};

const ALERT_STYLES: Record<string, string> = {
  critical: "border-l-red-500 bg-danger-soft",
  warning: "border-l-amber-500 bg-warning-soft",
  info: "border-l-blue-500 bg-primary-soft",
};

const WORKLIST_PRIORITY: Record<string, string> = {
  URGENT: "bg-red-100 text-danger",
  EMERGENCY: "bg-red-100 text-danger",
  STAT: "bg-red-100 text-danger",
  HIGH: "bg-amber-100 text-warning-foreground",
  MEDIUM: "bg-primary-soft text-primary",
  ROUTINE: "bg-primary-soft text-primary",
  LOW: "bg-neutral-100 text-muted-foreground",
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
  const worklistQ = useClinicalWorklist({ facilityId: fid, size: 24 });

  const wards = useMemo(() => wardsQ.data?.data ?? [], [wardsQ.data?.data]);
  const beds = useMemo(() => bedsQ.data?.data ?? [], [bedsQ.data?.data]);
  const queueEntries = useMemo(() => queueQ.data?.data ?? [], [queueQ.data?.data]);
  const queueStats = statsQ.data?.data;

  const queueRows = useMemo(() => buildQueueRows(queueEntries), [queueEntries]);
  const bedUtil = useMemo(() => buildWardUtilization(wards, beds), [wards, beds]);
  const queueByType = useMemo(() => buildQueuePerformanceByType(queueEntries), [queueEntries]);
  const worklistItems = useMemo(() => worklistQ.data?.data?.items ?? [], [worklistQ.data?.data?.items]);
  const worklistSummary = worklistQ.data?.data?.summary;

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
        color: "bg-primary",
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
    (wardsQ.isLoading ||
      bedsQ.isLoading ||
      queueQ.isLoading ||
      statsQ.isLoading ||
      shiftsQ.isLoading ||
      worklistQ.isLoading);

  const onRefresh = () => {
    if (!fid) return;
    void queryClient.invalidateQueries({ queryKey: facilityOpsKeys.all(fid) });
    void queryClient.invalidateQueries({ queryKey: ["staffing", "roster-week", fid] });
  };

  return (
    <AppLayout>
      <PageShell title="Control Tower" subtitle="Facility operations dashboard (live BFF data)">
        {!fid ? (
          <div className="bg-card rounded-lg border border-border p-12 text-center">
            <BarChart3 className="w-10 h-10 text-muted-foreground mx-auto mb-3" />
            <p className="text-muted-foreground text-sm">Select a facility to load control tower metrics.</p>
            <Link href="/home" className="mt-2 inline-block text-sm text-primary hover:text-primary-hover">
              Go to Home →
            </Link>
          </div>
        ) : loading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
            <span className="ml-2 text-sm text-muted-foreground">Loading dashboard…</span>
          </div>
        ) : (
          <div className="space-y-6">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <BarChart3 className="w-5 h-5 text-rose-600" />
                <h2 className="text-lg font-semibold text-foreground">Facility overview</h2>
                <span className="text-xs text-muted-foreground">({facility.name})</span>
              </div>
              <button
                type="button"
                onClick={onRefresh}
                className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs text-muted-foreground border border-border rounded-lg hover:bg-background transition-colors"
              >
                <RefreshCw className="w-3.5 h-3.5" /> Refresh
              </button>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
              {statCards.map((stat) => {
                const Icon = stat.icon;
                return (
                  <div key={stat.label} className="bg-card rounded-lg border border-border p-5">
                    <div className="flex items-center justify-between mb-3">
                      <div className={`w-10 h-10 rounded-lg ${stat.color} flex items-center justify-center`}>
                        <Icon className="w-5 h-5 text-white" />
                      </div>
                    </div>
                    <p className="text-2xl font-bold text-foreground">{stat.value}</p>
                    <p className="text-xs text-muted-foreground mt-1">{stat.label}</p>
                    <p className="text-[10px] text-muted-foreground mt-2 leading-snug">{stat.footnote}</p>
                  </div>
                );
              })}
            </div>

            <div className="bg-card rounded-lg border border-border overflow-hidden">
              <div className="px-5 py-4 border-b border-border flex items-center justify-between">
                <div>
                  <h3 className="font-medium text-foreground">Clinician worklist inbox</h3>
                  <p className="text-xs text-muted-foreground mt-1">
                    Composed queue, referral, task, order, pharmacy, and telemedicine actions.
                  </p>
                </div>
                <div className="text-right">
                  <p className="text-sm font-semibold text-foreground">{worklistSummary?.total ?? 0} items</p>
                  <p className="text-xs text-muted-foreground">
                    {worklistSummary?.urgent ?? 0} urgent · {worklistSummary?.overdue ?? 0} overdue
                  </p>
                </div>
              </div>
              <div className="divide-y divide-gray-100">
                {worklistItems.length === 0 ? (
                  <div className="px-5 py-8 text-center text-sm text-muted-foreground">
                    No composed worklist items for this facility snapshot.
                  </div>
                ) : (
                  worklistItems.slice(0, 8).map((item) => {
                    const priority = String(item.priority ?? "MEDIUM").toUpperCase();
                    const priorityClass = WORKLIST_PRIORITY[priority] ?? "bg-neutral-100 text-muted-foreground";
                    const href = typeof item.href === "string" && item.href.length > 0 ? item.href : "/clinical";
                    return (
                      <div key={String(item.id)} className="px-5 py-3 flex items-center justify-between gap-3">
                        <div className="min-w-0">
                          <p className="text-sm font-medium text-foreground truncate">{String(item.title ?? "Clinical action")}</p>
                          <p className="text-xs text-muted-foreground truncate">
                            {String(item.kind ?? "ITEM")} · {String(item.status ?? "PENDING")} · {String(item.source ?? "unknown")}
                          </p>
                          {item.description ? (
                            <p className="text-xs text-muted-foreground mt-1 truncate">{String(item.description)}</p>
                          ) : null}
                        </div>
                        <div className="flex items-center gap-2 shrink-0">
                          <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${priorityClass}`}>
                            {priority}
                          </span>
                          <Link href={href} className="text-xs text-primary hover:text-primary-hover">
                            Open
                          </Link>
                        </div>
                      </div>
                    );
                  })
                )}
              </div>
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
              <div className="bg-card rounded-lg border border-border overflow-hidden">
                <div className="px-5 py-4 border-b border-border flex items-center justify-between">
                  <h3 className="font-medium text-foreground">Queue — waiting</h3>
                  <span className="text-xs text-muted-foreground">{queueRows.length} entries (page)</span>
                </div>
                <div className="divide-y divide-gray-100">
                  {queueRows.length === 0 ? (
                    <div className="px-5 py-8 text-center text-sm text-muted-foreground">No WAITING queue entries.</div>
                  ) : (
                    queueRows.map((item) => (
                      <div
                        key={item.id}
                        className="px-5 py-3 flex items-center justify-between hover:bg-background transition-colors"
                      >
                        <div>
                          <p className="text-sm font-medium text-foreground">{item.patientName}</p>
                          <p className="text-xs text-muted-foreground">
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

              <div className="bg-card rounded-lg border border-border overflow-hidden">
                <div className="px-5 py-4 border-b border-border">
                  <h3 className="font-medium text-foreground">Bed utilization by ward</h3>
                </div>
                <div className="p-5 space-y-4">
                  {bedUtil.length === 0 ? (
                    <p className="text-sm text-muted-foreground">No ward or bed data for this facility.</p>
                  ) : (
                    bedUtil.map((ward) => {
                      const occupancyPct = ward.total > 0 ? Math.round((ward.occupied / ward.total) * 100) : 0;
                      return (
                        <div key={ward.ward}>
                          <div className="flex items-center justify-between mb-1">
                            <span className="text-sm text-foreground">{ward.ward}</span>
                            <span className="text-xs text-muted-foreground">
                              {ward.occupied}/{ward.total} ({occupancyPct}%)
                            </span>
                          </div>
                          <div className="w-full bg-neutral-100 rounded-full h-3 flex overflow-hidden">
                            <div
                              className="bg-primary h-3"
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
                          <div className="flex items-center gap-3 mt-1 text-[10px] text-muted-foreground">
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

            <div className="bg-card rounded-lg border border-border overflow-hidden">
              <div className="px-5 py-4 border-b border-border">
                <h3 className="font-medium text-foreground">Waiting load by queue type</h3>
                <p className="text-xs text-muted-foreground mt-1">Derived from the same WAITING page as the queue list.</p>
              </div>
              <div className="p-5 space-y-3">
                {queueByType.length === 0 ? (
                  <p className="text-sm text-muted-foreground">No waiting entries to group.</p>
                ) : (
                  queueByType.map((q) => (
                    <div key={q.name} className="flex items-center justify-between text-sm p-2 rounded-lg bg-background">
                      <div>
                        <span className="font-medium">{q.name}</span>
                        <div className="text-xs text-muted-foreground mt-0.5">{q.waiting} waiting</div>
                      </div>
                      <span
                        className={`px-2 py-0.5 rounded-full text-xs font-medium ${
                          q.avgWait > 30
                            ? "bg-red-100 text-danger"
                            : q.avgWait > 20
                              ? "bg-neutral-100 text-muted-foreground"
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

            <div className="bg-card rounded-lg border border-border overflow-hidden">
              <div className="px-5 py-4 border-b border-border flex items-center gap-2">
                <AlertTriangle className="w-4 h-4 text-amber-500" />
                <h3 className="font-medium text-foreground">Alerts &amp; thresholds</h3>
              </div>
              <div className="divide-y divide-gray-100">
                {alerts.map((alert) => (
                  <div key={alert.id} className={`px-5 py-3 border-l-4 ${ALERT_STYLES[alert.type]}`}>
                    <div className="flex items-center justify-between">
                      <p className="text-sm text-foreground">{alert.message}</p>
                      <span className="text-xs text-muted-foreground shrink-0 ml-4">{alert.time}</span>
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
