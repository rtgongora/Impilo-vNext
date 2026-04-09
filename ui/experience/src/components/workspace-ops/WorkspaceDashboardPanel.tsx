'use client';

import {
  Bed, Users, AlertTriangle, Activity,
  ArrowUpRight, ArrowDownRight, Loader2,
} from 'lucide-react';
import { useMemo } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useFacilityStore } from '@/hooks/useFacilityStore';
import {
  buildOperationalAlerts,
  buildQueuePerformanceByType,
  buildQueueRows,
  facilityOpsKeys,
  useFacilityActiveShiftCount,
  useFacilityBeds,
  useFacilityQueueStats,
  useFacilityQueueWaiting,
  useFacilityWards,
} from '@/hooks/queries/useFacilityOperations';

function MetricCard({ icon: Icon, label, value, sub, trend, color, highlight }: {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  value: string;
  sub: string;
  trend?: 'up' | 'down';
  color?: string;
  highlight?: boolean;
}) {
  return (
    <div className={`bg-white border rounded-lg ${highlight ? 'border-orange-200' : 'border-gray-200'}`}>
      <div className="pt-4 pb-3 px-4">
        <div className="flex items-center justify-between mb-1.5">
          <Icon className={`h-5 w-5 ${color || 'text-gray-400'}`} />
          {trend && (trend === 'up'
            ? <ArrowUpRight className="h-4 w-4 text-amber-500" />
            : <ArrowDownRight className="h-4 w-4 text-green-500" />
          )}
        </div>
        <p className="text-2xl font-bold leading-tight">{value}</p>
        <p className="text-xs text-gray-500 mt-0.5 leading-snug">{sub}</p>
        <p className="text-[10px] text-gray-400 mt-1 uppercase tracking-wide">{label}</p>
      </div>
    </div>
  );
}

export interface WorkspaceDashboardPanelProps {
  /** When omitted, uses selected facility from the facility store. */
  facilityId?: string | null;
}

export function WorkspaceDashboardPanel({ facilityId: facilityIdProp }: WorkspaceDashboardPanelProps) {
  const storeFacilityId = useFacilityStore((s) => s.facility?.id);
  const fid = facilityIdProp ?? storeFacilityId ?? undefined;
  const queryClient = useQueryClient();

  const wardsQ = useFacilityWards(fid);
  const bedsQ = useFacilityBeds(fid);
  const queueQ = useFacilityQueueWaiting(fid, 60);
  const statsQ = useFacilityQueueStats(fid);
  const shiftsQ = useFacilityActiveShiftCount(fid);

  const wards = wardsQ.data?.data ?? [];
  const beds = bedsQ.data?.data ?? [];
  const queueEntries = queueQ.data?.data ?? [];
  const queueStats = statsQ.data?.data;

  const queueRows = useMemo(() => buildQueueRows(queueEntries), [queueEntries]);
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

  const bedOcc = useMemo(() => {
    const occ = beds.filter((b) => b.attributes.status === 'OCCUPIED').length;
    const total = beds.length;
    const pct = total > 0 ? Math.round((occ / total) * 100) : 0;
    return { occ, total, pct };
  }, [beds]);

  const avgWaitMin =
    queueStats && queueStats.avgWaitSeconds > 0
      ? Math.max(1, Math.round(queueStats.avgWaitSeconds / 60))
      : 0;

  const loading =
    !!fid &&
    (wardsQ.isLoading || bedsQ.isLoading || queueQ.isLoading || statsQ.isLoading || shiftsQ.isLoading);

  const refresh = () => {
    if (!fid) return;
    void queryClient.invalidateQueries({ queryKey: facilityOpsKeys.all(fid) });
    void queryClient.invalidateQueries({ queryKey: ['staffing', 'roster-week', fid] });
  };

  if (!fid) {
    return (
      <div className="text-center py-10 px-4 text-sm text-gray-500 border border-dashed border-gray-200 rounded-lg">
        Select a facility to load live workspace operations metrics.
      </div>
    );
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center py-16 text-gray-500 text-sm gap-2">
        <Loader2 className="h-5 w-5 animate-spin" /> Loading operations…
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex justify-end">
        <button
          type="button"
          onClick={refresh}
          className="text-xs text-blue-600 hover:text-blue-800"
        >
          Refresh data
        </button>
      </div>

      <div>
        <h3 className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-2 px-1">Operations (live)</h3>
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-3">
          <MetricCard
            icon={Bed}
            label="Bed occupancy"
            value={bedOcc.total > 0 ? `${bedOcc.pct}%` : '—'}
            sub={bedOcc.total > 0 ? `${bedOcc.occ}/${bedOcc.total} beds` : 'No beds for facility'}
            trend={bedOcc.pct >= 85 ? 'up' : 'down'}
            color="text-amber-600"
          />
          <MetricCard
            icon={Users}
            label="Queue waiting"
            value={String(queueStats?.waiting ?? queueRows.length)}
            sub={
              queueStats && queueStats.waiting > 0 && avgWaitMin > 0
                ? `~${avgWaitMin} min avg (today)`
                : 'Today’s queue snapshot'
            }
            color="text-blue-600"
          />
          <MetricCard
            icon={Activity}
            label="Active shifts"
            value={String(shiftsQ.activeShiftCount)}
            sub="ACTIVE with no end time (roster week)"
            color="text-green-600"
          />
        </div>
      </div>

      <div className="rounded-lg border border-gray-200 bg-slate-50/80 p-4 text-sm text-slate-700">
        <p className="font-medium text-slate-800">Extended KPIs</p>
        <p className="mt-1 text-xs text-slate-600 leading-relaxed">
          Supply, billing, lab, and longitudinal patient-quality metrics are not published on the Experience BFF in this
          release. This dashboard intentionally shows only capacity, queue, and staffing signals that are backed by
          live APIs.
        </p>
      </div>

      <div className="bg-white border border-gray-200 rounded-lg">
        <div className="px-4 pt-4 pb-2 flex items-center gap-2">
          <AlertTriangle className="h-5 w-5 text-amber-500" />
          <h3 className="text-base font-semibold">Active alerts</h3>
          <span className="ml-auto px-2 py-0.5 rounded-full bg-red-100 text-red-700 text-xs font-medium">
            {alerts.filter((a) => a.type === 'critical').length} critical
          </span>
        </div>
        <div className="px-4 pb-4">
          <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-2">
            {alerts.map((alert) => (
              <div
                key={alert.id}
                className={`text-sm p-3 rounded-lg border ${
                  alert.type === 'critical'
                    ? 'border-red-200 bg-red-50'
                    : alert.type === 'warning'
                      ? 'border-amber-200 bg-amber-50'
                      : 'border-blue-200 bg-blue-50'
                }`}
              >
                <div className="flex items-start justify-between gap-2">
                  <span className="font-medium">{alert.message}</span>
                  <span className="text-gray-400 whitespace-nowrap text-xs">{alert.time}</span>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-3">
        <div className="bg-white border border-gray-200 rounded-lg flex flex-col">
          <div className="px-4 pt-4 pb-2 flex items-center gap-2">
            <Bed className="h-4 w-4 text-gray-400" />
            <h3 className="text-base font-semibold">Ward capacity</h3>
          </div>
          <div className="flex-1 px-4 pb-4 space-y-3">
            {wards.length === 0 ? (
              <p className="text-sm text-gray-500">No wards configured.</p>
            ) : (
              wards.map((w) => {
                const a = w.attributes;
                const total = Number(a.totalBeds ?? 0);
                const occ = Number(a.occupiedBeds ?? 0);
                const pct = total > 0 ? Math.round((occ / total) * 100) : 0;
                return (
                  <div key={w.id} className="space-y-1">
                    <div className="flex justify-between text-sm">
                      <span className="font-medium">{a.name}</span>
                      <span className="text-gray-400 text-xs">
                        {occ}/{total} ({pct}%)
                      </span>
                    </div>
                    <div className="w-full bg-gray-100 rounded-full h-2">
                      <div
                        className={`h-2 rounded-full ${pct > 90 ? 'bg-red-500' : pct > 75 ? 'bg-amber-500' : 'bg-green-500'}`}
                        style={{ width: `${pct}%` }}
                      />
                    </div>
                  </div>
                );
              })
            )}
          </div>
        </div>

        <div className="bg-white border border-gray-200 rounded-lg flex flex-col">
          <div className="px-4 pt-4 pb-2 flex items-center gap-2">
            <Users className="h-4 w-4 text-gray-400" />
            <h3 className="text-base font-semibold">Queue types (waiting)</h3>
          </div>
          <div className="flex-1 px-4 pb-4 space-y-3">
            {queueByType.length === 0 ? (
              <p className="text-sm text-gray-500">No waiting entries.</p>
            ) : (
              queueByType.map((q) => (
                <div key={q.name} className="flex items-center justify-between text-sm p-2 rounded-lg bg-gray-50">
                  <div>
                    <span className="font-medium">{q.name}</span>
                    <div className="flex items-center gap-2 mt-0.5">
                      <span className="text-xs text-gray-500">{q.waiting} waiting</span>
                    </div>
                  </div>
                  <span
                    className={`px-2 py-0.5 rounded-full text-xs font-medium ${
                      q.avgWait > 30 ? 'bg-red-100 text-red-700' : q.avgWait > 20 ? 'bg-gray-100 text-gray-600' : 'bg-green-100 text-green-700'
                    }`}
                  >
                    ~{q.avgWait} min
                  </span>
                </div>
              ))
            )}
          </div>
        </div>
      </div>

      <div className="bg-white border border-gray-200 rounded-lg flex flex-col">
        <div className="px-4 pt-4 pb-2 flex items-center gap-2">
          <Activity className="h-4 w-4 text-gray-400" />
          <h3 className="text-base font-semibold">Activity</h3>
        </div>
        <div className="px-4 pb-4 text-sm text-gray-500">
          A chronological activity stream is not yet backed by an audit or clinical-events feed on the Experience BFF.
        </div>
      </div>
    </div>
  );
}
