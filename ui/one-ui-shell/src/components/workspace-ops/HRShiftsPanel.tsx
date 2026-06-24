'use client';

import { useMemo, useState } from 'react';
import { Loader2 } from 'lucide-react';
import { LiveDataSourceBadge } from '@/components/common/LiveDataSourceBadge';
import { NotLiveNotice } from '@/components/common/NotLiveNotice';
import { useStaffingRosterWeek } from '@/hooks/queries/useStaffing';
import {
  Users, Clock, Calendar, UserCheck, Coffee,
  AlertTriangle, ArrowRightLeft, FileText, Shield, Sun, Moon,
} from 'lucide-react';

// ─── Types ───

type HRTab = 'roster' | 'shifts' | 'leave' | 'handover';

// ─── Mock Data ───

const STAFF_ROSTER = [
  { id: '1', name: 'Dr. T. Nkomo', role: 'doctor', department: 'Emergency', status: 'on_shift', shiftType: 'day', hours: '07:00-19:00', overtime: false },
  { id: '2', name: 'Sr. P. Moyo', role: 'nurse', department: 'Medical Ward', status: 'on_shift', shiftType: 'day', hours: '07:00-19:00', overtime: false },
  { id: '3', name: 'Dr. A. Sibanda', role: 'specialist', department: 'ICU', status: 'on_shift', shiftType: 'day', hours: '08:00-17:00', overtime: true },
  { id: '4', name: 'N. Dube', role: 'nurse', department: 'Surgical Ward', status: 'on_break', shiftType: 'day', hours: '07:00-19:00', overtime: false },
  { id: '5', name: 'Dr. K. Mhlanga', role: 'doctor', department: 'Pediatrics', status: 'on_shift', shiftType: 'day', hours: '08:00-20:00', overtime: false },
  { id: '6', name: 'T. Ncube', role: 'pharmacist', department: 'Pharmacy', status: 'on_shift', shiftType: 'day', hours: '08:00-17:00', overtime: false },
  { id: '7', name: 'J. Maposa', role: 'lab_tech', department: 'Laboratory', status: 'off_shift', shiftType: 'night', hours: '19:00-07:00', overtime: false },
  { id: '8', name: 'Dr. R. Zulu', role: 'doctor', department: 'Emergency', status: 'off_shift', shiftType: 'night', hours: '19:00-07:00', overtime: false },
];

type ActiveShiftRow = {
  id: string;
  type: string;
  time: string;
  staffCount: number;
  coverage: number | null;
  departments: string[];
};

const ACTIVE_SHIFTS: ActiveShiftRow[] = [
  { id: 'SH-001', type: 'Day Shift', time: '07:00 - 19:00', staffCount: 18, coverage: 95, departments: ['Emergency', 'Medical', 'Surgical', 'ICU'] },
  { id: 'SH-002', type: 'Night Shift', time: '19:00 - 07:00', staffCount: 12, coverage: 85, departments: ['Emergency', 'Medical', 'ICU'] },
  { id: 'SH-003', type: 'Admin Shift', time: '08:00 - 17:00', staffCount: 6, coverage: 100, departments: ['Admin', 'Finance', 'HR'] },
];

const LEAVE_REQUESTS = [
  { id: 'LV-001', name: 'Dr. M. Chikwanda', type: 'Annual Leave', from: '2026-04-10', to: '2026-04-17', days: 5, status: 'pending', department: 'Surgical' },
  { id: 'LV-002', name: 'Sr. L. Banda', type: 'Sick Leave', from: '2026-04-06', to: '2026-04-08', days: 2, status: 'approved', department: 'Maternity' },
  { id: 'LV-003', name: 'T. Phiri', type: 'Study Leave', from: '2026-04-14', to: '2026-04-18', days: 5, status: 'pending', department: 'Laboratory' },
];

const PENDING_HANDOVERS = [
  { id: 'HO-001', from: 'Dr. T. Nkomo', to: 'Dr. R. Zulu', department: 'Emergency', patients: 8, criticalNotes: 2, dueAt: '18:30' },
  { id: 'HO-002', from: 'Sr. P. Moyo', to: 'N. Tshabalala', department: 'Medical Ward', patients: 12, criticalNotes: 1, dueAt: '18:45' },
];

// ─── Helpers ───

function getStaffStatusBadge(status: string) {
  const map: Record<string, { label: string; color: string }> = {
    on_shift: { label: 'On Shift', color: 'bg-green-500' },
    off_shift: { label: 'Off Shift', color: 'bg-neutral-500' },
    on_break: { label: 'On Break', color: 'bg-amber-500' },
    on_leave: { label: 'On Leave', color: 'bg-impilo-400' },
  };
  const cfg = map[status] || { label: status, color: 'bg-neutral-500' };
  return (
    <div className="flex items-center gap-1.5">
      <div className={`h-2 w-2 rounded-full ${cfg.color}`} />
      <span className="text-xs text-muted-foreground">{cfg.label}</span>
    </div>
  );
}

function getRoleColor(role: string) {
  const map: Record<string, string> = {
    doctor: 'bg-primary-soft text-primary',
    specialist: 'bg-purple-100 text-warning-foreground',
    nurse: 'bg-green-100 text-green-700',
    pharmacist: 'bg-amber-100 text-warning-foreground',
    lab_tech: 'bg-red-100 text-danger',
  };
  return map[role] || 'bg-neutral-100 text-foreground';
}

// ─── Component ───

function weekStartIso(): string {
  const d = new Date();
  const day = d.getDay();
  const diff = d.getDate() - day + (day === 0 ? -6 : 1);
  d.setDate(diff);
  d.setHours(0, 0, 0, 0);
  return d.toISOString().slice(0, 10);
}

interface HRShiftsPanelProps {
  facilityId?: string | null;
}

export function HRShiftsPanel({ facilityId }: HRShiftsPanelProps) {
  const [activeTab, setActiveTab] = useState<HRTab>('roster');
  const [preferLive, setPreferLive] = useState(true);
  const rosterQ = useStaffingRosterWeek({
    facilityId: facilityId ?? undefined,
    weekStartISO: weekStartIso(),
  });
  const liveRoster = useMemo(() => {
    const rows = rosterQ.data?.data ?? [];
    return rows.map((shift, index) => ({
      id: shift.id ?? String(index),
      name: shift.attributes.staff_display_name,
      role: 'staff',
      department: shift.attributes.workspace_id ?? 'Workspace',
      status: shift.attributes.status === 'ACTIVE' ? 'on_shift' : 'off_shift',
      shiftType: 'day',
      hours: `${shift.attributes.started_at?.slice(11, 16) ?? '—'}-${shift.attributes.ended_at?.slice(11, 16) ?? '—'}`,
      overtime: false,
    }));
  }, [rosterQ.data]);
  const displayRoster = preferLive && liveRoster.length > 0 ? liveRoster : STAFF_ROSTER;
  const dataSource =
    preferLive && liveRoster.length > 0 ? 'live' : preferLive && rosterQ.isLoading ? 'mixed' : 'demo';
  const onShift = displayRoster.filter(s => s.status === 'on_shift').length;
  const onBreak = displayRoster.filter(s => s.status === 'on_break').length;
  const overtime = displayRoster.filter(s => s.overtime).length;

  // Active-shift summaries derived from the SAME live roster feed (no extra endpoint).
  // Coverage% is intentionally null when live — there is no establishment target to
  // compute it from, so we show real staff counts rather than a fabricated percentage.
  const liveActiveShifts = useMemo<ActiveShiftRow[]>(() => {
    const rows = rosterQ.data?.data ?? [];
    const groups = new Map<string, { staff: number; depts: Set<string>; start: string }>();
    for (const s of rows) {
      if (s.attributes.status !== 'ACTIVE') continue;
      const start = s.attributes.started_at?.slice(11, 16) ?? '—';
      const end = s.attributes.ended_at?.slice(11, 16) ?? '—';
      const key = `${start} - ${end}`;
      const g = groups.get(key) ?? { staff: 0, depts: new Set<string>(), start };
      g.staff += 1;
      if (s.attributes.workspace_id) g.depts.add(s.attributes.workspace_id);
      groups.set(key, g);
    }
    return Array.from(groups.entries()).map(([time, g]) => {
      const startHour = parseInt(g.start.slice(0, 2), 10);
      const type = Number.isNaN(startHour)
        ? 'Shift'
        : startHour >= 18 || startHour < 6
          ? 'Night Shift'
          : startHour < 8
            ? 'Day Shift'
            : 'Admin Shift';
      return { id: time, type, time, staffCount: g.staff, coverage: null, departments: Array.from(g.depts) };
    });
  }, [rosterQ.data]);
  const activeShiftsLive = preferLive && liveActiveShifts.length > 0;
  const displayActiveShifts: ActiveShiftRow[] = activeShiftsLive ? liveActiveShifts : ACTIVE_SHIFTS;

  const tabs: { key: HRTab; label: string; icon: React.ComponentType<{ className?: string }>; badge?: number }[] = [
    { key: 'roster', label: 'Staff Roster', icon: Users },
    { key: 'shifts', label: 'Active Shifts', icon: Clock },
    { key: 'leave', label: 'Leave', icon: Calendar },
    { key: 'handover', label: 'Handover', icon: ArrowRightLeft, badge: PENDING_HANDOVERS.length > 0 ? PENDING_HANDOVERS.length : undefined },
  ];

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <LiveDataSourceBadge source={dataSource} />
        <label className="inline-flex items-center gap-2 text-xs text-muted-foreground">
          <input
            type="checkbox"
            checked={preferLive}
            onChange={(e) => setPreferLive(e.target.checked)}
            className="rounded border-border"
          />
          Prefer live staffing roster
        </label>
        {preferLive && rosterQ.isLoading ? (
          <span className="inline-flex items-center gap-1 text-xs text-muted-foreground">
            <Loader2 className="h-3 w-3 animate-spin" /> Loading roster…
          </span>
        ) : null}
      </div>
      {/* Summary */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        <div className="bg-card border border-border rounded-lg pt-3 pb-2 px-3">
          <div className="flex items-center gap-2"><UserCheck className="h-4 w-4 text-green-500" /><span className="text-xs text-muted-foreground">On Shift</span></div>
          <p className="text-lg font-bold">{onShift}</p>
        </div>
        <div className="bg-card border border-border rounded-lg pt-3 pb-2 px-3">
          <div className="flex items-center gap-2"><Coffee className="h-4 w-4 text-amber-500" /><span className="text-xs text-muted-foreground">On Break</span></div>
          <p className="text-lg font-bold">{onBreak}</p>
        </div>
        <div className="bg-card border border-border rounded-lg pt-3 pb-2 px-3">
          <div className="flex items-center gap-2"><Clock className="h-4 w-4 text-red-500" /><span className="text-xs text-muted-foreground">Overtime</span></div>
          <p className="text-lg font-bold text-red-600">{overtime}</p>
        </div>
        <div className="bg-card border border-border rounded-lg pt-3 pb-2 px-3">
          <div className="flex items-center gap-2"><Calendar className="h-4 w-4 text-impilo-400" /><span className="text-xs text-muted-foreground">Leave Pending</span></div>
          <p className="text-lg font-bold">{LEAVE_REQUESTS.filter(l => l.status === 'pending').length}</p>
        </div>
      </div>

      {/* Tabs */}
      <div className="flex gap-1 border-b border-border">
        {tabs.map(tab => {
          const Icon = tab.icon;
          return (
            <button
              key={tab.key}
              onClick={() => setActiveTab(tab.key)}
              className={`inline-flex items-center gap-1.5 px-3 py-2 text-xs font-medium border-b-2 transition-colors ${
                activeTab === tab.key ? 'border-impilo-500 text-primary' : 'border-transparent text-muted-foreground hover:text-foreground'
              }`}
            >
              <Icon className="h-3.5 w-3.5" />
              {tab.label}
              {tab.badge && (
                <span className="ml-1 px-1.5 py-0.5 rounded-full bg-neutral-100 text-[10px] font-medium">{tab.badge}</span>
              )}
            </button>
          );
        })}
      </div>

      {/* Roster Tab */}
      {activeTab === 'roster' && (
        <div className="space-y-1 max-h-[420px] overflow-auto">
          {displayRoster.map(staff => (
            <div key={staff.id} className="flex items-center justify-between p-3 rounded-lg border border-border hover:bg-background transition-colors">
              <div className="flex items-center gap-3">
                <div className={`h-9 w-9 rounded-full flex items-center justify-center text-xs font-semibold ${getRoleColor(staff.role)}`}>
                  {staff.name.split(' ').map(n => n[0]).join('').slice(0, 2)}
                </div>
                <div>
                  <p className="text-sm font-medium">{staff.name}</p>
                  <p className="text-xs text-muted-foreground">{staff.department} &middot; {staff.hours}</p>
                </div>
              </div>
              <div className="flex items-center gap-3">
                {staff.overtime && <span className="px-2 py-0.5 rounded bg-red-100 text-danger text-[10px] font-medium">OT</span>}
                <span className="px-2 py-0.5 rounded border border-border text-[10px] capitalize">{staff.role.replace('_', ' ')}</span>
                {getStaffStatusBadge(staff.status)}
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Shifts Tab */}
      {activeTab === 'shifts' && (
        <div className="space-y-3">
          {!activeShiftsLive && (
            <NotLiveNotice>
              <span className="font-semibold">Not live yet.</span> Active-shift summaries
              are demo data — the live roster feed has no active shifts to aggregate.
            </NotLiveNotice>
          )}
          {displayActiveShifts.map(shift => {
            const ShiftIcon = shift.type.includes('Night') ? Moon : shift.type.includes('Admin') ? FileText : Sun;
            return (
              <div key={shift.id} className="bg-card border border-border rounded-lg py-4 px-4">
                <div className="flex items-center justify-between mb-2">
                  <div className="flex items-center gap-2">
                    <ShiftIcon className="h-4 w-4 text-muted-foreground" />
                    <span className="font-semibold text-sm">{shift.type}</span>
                    <span className="text-xs text-muted-foreground">{shift.time}</span>
                  </div>
                  {shift.coverage != null && (
                    <span className={`px-2 py-0.5 rounded text-xs font-medium ${shift.coverage >= 90 ? 'bg-green-100 text-green-700' : 'bg-amber-100 text-warning-foreground'}`}>
                      {shift.coverage}% coverage
                    </span>
                  )}
                </div>
                <div className="flex items-center justify-between text-xs text-muted-foreground mb-2">
                  <span>{shift.staffCount} staff</span>
                  <span>{shift.departments.join(', ')}</span>
                </div>
                {shift.coverage != null && (
                  <div className="w-full bg-neutral-100 rounded-full h-1.5">
                    <div
                      className={`h-1.5 rounded-full ${shift.coverage < 85 ? 'bg-amber-500' : 'bg-green-500'}`}
                      style={{ width: `${shift.coverage}%` }}
                    />
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}

      {/* Leave Tab */}
      {activeTab === 'leave' && (
        <div className="space-y-2 max-h-[420px] overflow-auto">
          <NotLiveNotice>
            <span className="font-semibold">Not live yet.</span> Leave requests are demo
            data — the hr-payroll leave API is employee-scoped, not facility-wide.
          </NotLiveNotice>
          {LEAVE_REQUESTS.map(req => (
            <div key={req.id} className="bg-card border border-border rounded-lg py-3 px-4">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-sm font-medium">{req.name}</p>
                  <p className="text-xs text-muted-foreground">{req.type} &middot; {req.department}</p>
                  <p className="text-xs text-muted-foreground">{req.from} &rarr; {req.to} ({req.days} days)</p>
                </div>
                <div className="flex items-center gap-2">
                  <span className={`px-2 py-0.5 rounded text-xs font-medium capitalize ${req.status === 'approved' ? 'bg-green-100 text-green-700' : 'bg-amber-100 text-warning-foreground'}`}>
                    {req.status}
                  </span>
                  {req.status === 'pending' && (
                    <div className="flex gap-1">
                      <button className="px-2 py-1 text-xs border border-border rounded hover:bg-neutral-100">Approve</button>
                      <button className="px-2 py-1 text-xs text-red-500 hover:bg-danger-soft rounded">Decline</button>
                    </div>
                  )}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Handover Tab */}
      {activeTab === 'handover' && (
        <div className="space-y-3">
          <NotLiveNotice>
            <span className="font-semibold">Not live yet.</span> Pending handovers are
            demo data — no handover-tracking endpoint exists.
          </NotLiveNotice>
          {PENDING_HANDOVERS.map(ho => (
            <div key={ho.id} className="bg-card border border-border border-l-4 border-l-amber-500 rounded-lg py-4 px-4">
              <div className="flex items-center justify-between mb-2">
                <div>
                  <p className="text-sm font-semibold">{ho.department} Handover</p>
                  <p className="text-xs text-muted-foreground">{ho.from} &rarr; {ho.to}</p>
                </div>
                <span className="px-2 py-0.5 rounded border border-border text-xs">Due {ho.dueAt}</span>
              </div>
              <div className="flex items-center gap-4 text-xs text-muted-foreground">
                <span>{ho.patients} patients</span>
                {ho.criticalNotes > 0 && (
                  <span className="text-red-500 flex items-center gap-1">
                    <AlertTriangle className="h-3 w-3" />{ho.criticalNotes} critical note{ho.criticalNotes > 1 ? 's' : ''}
                  </span>
                )}
              </div>
              <div className="flex gap-2 mt-3">
                <button className="inline-flex items-center gap-1 px-3 py-1.5 text-xs bg-primary text-white rounded hover:bg-primary-hover">
                  <FileText className="h-3 w-3" />View Notes
                </button>
                <button className="inline-flex items-center gap-1 px-3 py-1.5 text-xs border border-border rounded hover:bg-background">
                  <Shield className="h-3 w-3" />Complete Handover
                </button>
              </div>
            </div>
          ))}
          {PENDING_HANDOVERS.length === 0 && (
            <div className="text-center py-8 text-muted-foreground text-sm">No pending handovers</div>
          )}
        </div>
      )}
    </div>
  );
}
