'use client';

import { useMemo, useState } from 'react';
import { Loader2 } from 'lucide-react';
import { LiveDataSourceBadge } from '@/components/common/LiveDataSourceBadge';
import { NotLiveNotice } from '@/components/common/NotLiveNotice';
import { useStaffingRosterWeek } from '@/hooks/queries/useStaffing';
import { useShiftHandovers } from '@/hooks/queries/useInpatient';
import {
  Users, Clock, Calendar, UserCheck, Coffee,
  ArrowRightLeft, FileText, Shield, Sun, Moon,
} from 'lucide-react';

// ─── Types ───

type HRTab = 'roster' | 'shifts' | 'leave' | 'handover';

type ActiveShiftRow = {
  id: string;
  type: string;
  time: string;
  staffCount: number;
  coverage: number | null;
  departments: string[];
};

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
  // Live-only: render the real roster, with an honest empty state when absent.
  const displayRoster = liveRoster;
  const dataSource =
    liveRoster.length > 0 ? 'live' : preferLive && rosterQ.isLoading ? 'mixed' : 'demo';
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
  // Live-only: real active-shift aggregates, honest empty state when absent.
  const displayActiveShifts: ActiveShiftRow[] = liveActiveShifts;

  // Pending SBAR shift handovers — real inpatient-service handover feed.
  const handoverQ = useShiftHandovers(facilityId ?? undefined, 'PENDING');
  const liveHandovers = useMemo(() => {
    const raw = handoverQ.data?.data;
    const arr = Array.isArray(raw) ? raw : [];
    return arr.map((row, index) => {
      const r = row as Record<string, unknown>;
      return {
        id: String(r.id ?? index),
        status: String(r.status ?? 'PENDING'),
        outgoingStaff: r.outgoing_staff ? String(r.outgoing_staff) : '—',
        submittedAt: r.submitted_at ? String(r.submitted_at) : null,
      };
    });
  }, [handoverQ.data]);
  const handoversLive = preferLive && liveHandovers.length > 0;

  const tabs: { key: HRTab; label: string; icon: React.ComponentType<{ className?: string }>; badge?: number }[] = [
    { key: 'roster', label: 'Staff Roster', icon: Users },
    { key: 'shifts', label: 'Active Shifts', icon: Clock },
    { key: 'leave', label: 'Leave', icon: Calendar },
    { key: 'handover', label: 'Handover', icon: ArrowRightLeft, badge: liveHandovers.length > 0 ? liveHandovers.length : undefined },
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
          <p className="text-lg font-bold" title="Facility-wide leave view is not yet available">—</p>
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
          {displayRoster.length === 0 && (
            <div className="text-center py-8 text-muted-foreground text-sm">
              No staff on the live roster for this facility this week.
            </div>
          )}
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
          {displayActiveShifts.length === 0 && (
            <div className="text-center py-8 text-muted-foreground text-sm">
              No active shifts. The live roster feed has no active shifts to aggregate for this facility.
            </div>
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

      {/* Leave Tab — no facility-scoped leave endpoint (hr-payroll leave API is employee-scoped) */}
      {activeTab === 'leave' && (
        <div className="space-y-2 max-h-[420px] overflow-auto">
          <NotLiveNotice>
            <span className="font-semibold">Facility-wide leave view is not yet available.</span>{' '}
            The hr-payroll leave API is employee-scoped, not facility-wide, so there is no
            facility-level leave feed to display here.
          </NotLiveNotice>
        </div>
      )}

      {/* Handover Tab */}
      {activeTab === 'handover' && handoversLive && (
        <div className="space-y-3">
          {liveHandovers.map(ho => (
            <div key={ho.id} className="bg-card border border-border border-l-4 border-l-amber-500 rounded-lg py-4 px-4">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-sm font-semibold">Shift handover</p>
                  <p className="text-xs text-muted-foreground">Outgoing: {ho.outgoingStaff}</p>
                  {ho.submittedAt && (
                    <p className="text-xs text-muted-foreground">Submitted {ho.submittedAt.slice(0, 16).replace('T', ' ')}</p>
                  )}
                </div>
                <span className="px-2 py-0.5 rounded border border-border text-xs">{ho.status}</span>
              </div>
              <div className="flex gap-2 mt-3">
                <button className="inline-flex items-center gap-1 px-3 py-1.5 text-xs border border-border rounded hover:bg-background">
                  <Shield className="h-3 w-3" />Accept Takeover
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {activeTab === 'handover' && !handoversLive && (
        <div className="space-y-3">
          <div className="text-center py-8 text-muted-foreground text-sm">
            No pending handovers for this facility.
          </div>
        </div>
      )}
    </div>
  );
}
