'use client';

import {
  Bed, Users, Package, DollarSign, AlertTriangle, TrendingUp,
  Clock, Activity, CheckCircle2, ArrowUpRight, ArrowDownRight,
  UserX, FlaskConical, HeartPulse, CalendarX, ListChecks,
} from 'lucide-react';

// ─── Mock Data ───

const METRICS = {
  beds: { total: 120, occupied: 98, available: 18, cleaning: 4, occupancy: 82 },
  queues: { waiting: 34, inService: 12, avgWait: 23, completed: 87, longestWait: 58 },
  staff: { onShift: 28, offShift: 14, onBreak: 3, overtime: 2 },
  stock: { lowItems: 5, expiringSoon: 8, pendingOrders: 3, criticalOut: 1 },
  billing: { unbilled: 12, pendingClaims: 7, todayRevenue: 45200, outstanding: 128500 },
  patients: {
    lostToFollowUp: 14,
    criticalResults: 6,
    overdueTasks: 9,
    pendingDischarges: 5,
    readmissions30d: 3,
    missedAppointments: 11,
    activeCarePlans: 42,
    avgLOS: 4.2,
  },
};

const ALERTS = [
  { id: 1, type: 'critical', message: 'ICU at 95% capacity - consider diversion', time: '5m ago' },
  { id: 2, type: 'critical', message: '6 critical lab results pending review', time: '8m ago' },
  { id: 3, type: 'warning', message: 'Paracetamol IV below reorder level (12 units)', time: '15m ago' },
  { id: 4, type: 'warning', message: '3 patients waiting >45min in General OPD', time: '20m ago' },
  { id: 5, type: 'critical', message: 'Blood bank: O- stock critical (2 units)', time: '30m ago' },
  { id: 6, type: 'warning', message: '14 patients lost to follow-up this month', time: '1h ago' },
];

const ACTIVITY_FEED = [
  { id: 1, action: 'Patient admitted', detail: 'Bed 4B, Medical Ward', actor: 'Sr. Moyo', time: '2m ago' },
  { id: 2, action: 'Critical result flagged', detail: 'K+ 6.8 mEq/L - Bed 2C ICU', actor: 'Lab', time: '5m ago' },
  { id: 3, action: 'Stock received', detail: 'PO-20260406-0012 (Surgical supplies)', actor: 'Pharmacy', time: '8m ago' },
  { id: 4, action: 'Discharge completed', detail: 'Bed 2A, Pediatrics', actor: 'Dr. Nkomo', time: '12m ago' },
  { id: 5, action: 'Missed appointment', detail: 'HIV Clinic - 3 no-shows today', actor: 'System', time: '25m ago' },
  { id: 6, action: 'Shift started', detail: 'Night shift - 14 staff checked in', actor: 'System', time: '30m ago' },
];

const WARD_CAPACITY = [
  { name: 'Medical Ward', occupied: 24, total: 28 },
  { name: 'Surgical Ward', occupied: 18, total: 22 },
  { name: 'ICU', occupied: 9, total: 10 },
  { name: 'Pediatrics', occupied: 14, total: 20 },
  { name: 'Maternity', occupied: 16, total: 20 },
  { name: 'Emergency', occupied: 17, total: 20 },
];

const QUEUE_PERFORMANCE = [
  { name: 'General OPD', waiting: 12, avgWait: 28, inService: 4 },
  { name: 'ANC Clinic', waiting: 5, avgWait: 15, inService: 2 },
  { name: 'HIV Clinic', waiting: 8, avgWait: 35, inService: 3 },
  { name: 'Pharmacy', waiting: 6, avgWait: 12, inService: 2 },
  { name: 'Lab Reception', waiting: 3, avgWait: 18, inService: 1 },
];

// ─── Sub-components ───

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
            ? <ArrowUpRight className="h-4 w-4 text-red-500" />
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

// ─── Main ───

export function WorkspaceDashboardPanel() {
  return (
    <div className="space-y-4">
      {/* Row 1: Operational KPIs */}
      <div>
        <h3 className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-2 px-1">Operations</h3>
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-3">
          <MetricCard icon={Bed} label="Bed Occupancy" value={`${METRICS.beds.occupancy}%`} sub={`${METRICS.beds.occupied}/${METRICS.beds.total} beds`} trend="up" color="text-amber-600" />
          <MetricCard icon={Users} label="Queue Load" value={`${METRICS.queues.waiting}`} sub={`${METRICS.queues.avgWait}min avg \u00B7 ${METRICS.queues.longestWait}min max`} trend="down" color="text-blue-600" />
          <MetricCard icon={Activity} label="Staff On Shift" value={`${METRICS.staff.onShift}`} sub={`${METRICS.staff.overtime} overtime`} color="text-green-600" />
          <MetricCard icon={Package} label="Stock Alerts" value={`${METRICS.stock.lowItems + METRICS.stock.criticalOut}`} sub={`${METRICS.stock.expiringSoon} expiring soon`} trend="up" color="text-red-600" />
          <MetricCard icon={DollarSign} label="Today Revenue" value={`R${(METRICS.billing.todayRevenue / 1000).toFixed(1)}k`} sub={`${METRICS.billing.unbilled} unbilled charges`} trend="up" color="text-emerald-600" />
        </div>
      </div>

      {/* Row 2: Patient Care KPIs */}
      <div>
        <h3 className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-2 px-1">Patient Care</h3>
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-3">
          <MetricCard icon={UserX} label="Lost to Follow-Up" value={`${METRICS.patients.lostToFollowUp}`} sub="This month - needs outreach" trend="up" color="text-orange-600" highlight />
          <MetricCard icon={FlaskConical} label="Critical Results" value={`${METRICS.patients.criticalResults}`} sub="Pending clinician review" trend="up" color="text-red-600" highlight />
          <MetricCard icon={CalendarX} label="Missed Appointments" value={`${METRICS.patients.missedAppointments}`} sub="Today - across all clinics" color="text-amber-600" />
          <MetricCard icon={ListChecks} label="Active Care Plans" value={`${METRICS.patients.activeCarePlans}`} sub={`${METRICS.patients.overdueTasks} overdue tasks`} color="text-indigo-600" />
          <MetricCard icon={HeartPulse} label="Avg Length of Stay" value={`${METRICS.patients.avgLOS}d`} sub={`${METRICS.patients.readmissions30d} readmissions (30d)`} color="text-purple-600" />
        </div>
      </div>

      {/* Active Alerts */}
      <div className="bg-white border border-gray-200 rounded-lg">
        <div className="px-4 pt-4 pb-2 flex items-center gap-2">
          <AlertTriangle className="h-5 w-5 text-amber-500" />
          <h3 className="text-base font-semibold">Active Alerts</h3>
          <span className="ml-auto px-2 py-0.5 rounded-full bg-red-100 text-red-700 text-xs font-medium">
            {ALERTS.filter(a => a.type === 'critical').length} critical
          </span>
        </div>
        <div className="px-4 pb-4">
          <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-2">
            {ALERTS.map(alert => (
              <div
                key={alert.id}
                className={`text-sm p-3 rounded-lg border ${
                  alert.type === 'critical'
                    ? 'border-red-200 bg-red-50'
                    : 'border-amber-200 bg-amber-50'
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

      {/* Bottom 3-column grid */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-3">
        {/* Ward Capacity */}
        <div className="bg-white border border-gray-200 rounded-lg flex flex-col">
          <div className="px-4 pt-4 pb-2 flex items-center gap-2">
            <Bed className="h-4 w-4 text-gray-400" />
            <h3 className="text-base font-semibold">Ward Capacity</h3>
          </div>
          <div className="flex-1 px-4 pb-4 space-y-3">
            {WARD_CAPACITY.map(ward => {
              const pct = Math.round((ward.occupied / ward.total) * 100);
              return (
                <div key={ward.name} className="space-y-1">
                  <div className="flex justify-between text-sm">
                    <span className="font-medium">{ward.name}</span>
                    <span className="text-gray-400 text-xs">{ward.occupied}/{ward.total} ({pct}%)</span>
                  </div>
                  <div className="w-full bg-gray-100 rounded-full h-2">
                    <div
                      className={`h-2 rounded-full ${pct > 90 ? 'bg-red-500' : pct > 75 ? 'bg-amber-500' : 'bg-green-500'}`}
                      style={{ width: `${pct}%` }}
                    />
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        {/* Queue Performance */}
        <div className="bg-white border border-gray-200 rounded-lg flex flex-col">
          <div className="px-4 pt-4 pb-2 flex items-center gap-2">
            <Users className="h-4 w-4 text-gray-400" />
            <h3 className="text-base font-semibold">Queue Performance</h3>
          </div>
          <div className="flex-1 px-4 pb-4 space-y-3">
            {QUEUE_PERFORMANCE.map(q => (
              <div key={q.name} className="flex items-center justify-between text-sm p-2 rounded-lg bg-gray-50">
                <div>
                  <span className="font-medium">{q.name}</span>
                  <div className="flex items-center gap-2 mt-0.5">
                    <span className="text-xs text-gray-500">{q.waiting} waiting</span>
                    <span className="text-xs text-gray-400">&middot;</span>
                    <span className="text-xs text-gray-500">{q.inService} serving</span>
                  </div>
                </div>
                <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${
                  q.avgWait > 30 ? 'bg-red-100 text-red-700' : q.avgWait > 20 ? 'bg-gray-100 text-gray-600' : 'bg-green-100 text-green-700'
                }`}>
                  {q.avgWait}min
                </span>
              </div>
            ))}
          </div>
        </div>

        {/* Activity Feed */}
        <div className="bg-white border border-gray-200 rounded-lg flex flex-col">
          <div className="px-4 pt-4 pb-2 flex items-center gap-2">
            <Activity className="h-4 w-4 text-gray-400" />
            <h3 className="text-base font-semibold">Recent Activity</h3>
          </div>
          <div className="flex-1 px-4 pb-4 space-y-1">
            {ACTIVITY_FEED.map(item => (
              <div key={item.id} className="flex items-start gap-3 text-sm p-2.5 rounded-lg hover:bg-gray-50">
                <CheckCircle2 className="h-4 w-4 mt-0.5 text-green-500 shrink-0" />
                <div className="min-w-0 flex-1">
                  <p className="font-medium">{item.action}</p>
                  <p className="text-gray-500 truncate text-xs">{item.detail} &middot; {item.actor}</p>
                </div>
                <span className="text-gray-400 whitespace-nowrap text-xs">{item.time}</span>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
