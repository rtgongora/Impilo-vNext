'use client';

import { useState } from 'react';
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

const ACTIVE_SHIFTS = [
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
    off_shift: { label: 'Off Shift', color: 'bg-gray-400' },
    on_break: { label: 'On Break', color: 'bg-amber-500' },
    on_leave: { label: 'On Leave', color: 'bg-impilo-400' },
  };
  const cfg = map[status] || { label: status, color: 'bg-gray-400' };
  return (
    <div className="flex items-center gap-1.5">
      <div className={`h-2 w-2 rounded-full ${cfg.color}`} />
      <span className="text-xs text-gray-500">{cfg.label}</span>
    </div>
  );
}

function getRoleColor(role: string) {
  const map: Record<string, string> = {
    doctor: 'bg-impilo-100 text-impilo-600',
    specialist: 'bg-purple-100 text-purple-700',
    nurse: 'bg-green-100 text-green-700',
    pharmacist: 'bg-amber-100 text-amber-700',
    lab_tech: 'bg-red-100 text-red-700',
  };
  return map[role] || 'bg-gray-100 text-gray-700';
}

// ─── Component ───

export function HRShiftsPanel() {
  const [activeTab, setActiveTab] = useState<HRTab>('roster');
  const onShift = STAFF_ROSTER.filter(s => s.status === 'on_shift').length;
  const onBreak = STAFF_ROSTER.filter(s => s.status === 'on_break').length;
  const overtime = STAFF_ROSTER.filter(s => s.overtime).length;

  const tabs: { key: HRTab; label: string; icon: React.ComponentType<{ className?: string }>; badge?: number }[] = [
    { key: 'roster', label: 'Staff Roster', icon: Users },
    { key: 'shifts', label: 'Active Shifts', icon: Clock },
    { key: 'leave', label: 'Leave', icon: Calendar },
    { key: 'handover', label: 'Handover', icon: ArrowRightLeft, badge: PENDING_HANDOVERS.length > 0 ? PENDING_HANDOVERS.length : undefined },
  ];

  return (
    <div className="space-y-3">
      {/* Summary */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        <div className="bg-white border border-gray-200 rounded-lg pt-3 pb-2 px-3">
          <div className="flex items-center gap-2"><UserCheck className="h-4 w-4 text-green-500" /><span className="text-xs text-gray-500">On Shift</span></div>
          <p className="text-lg font-bold">{onShift}</p>
        </div>
        <div className="bg-white border border-gray-200 rounded-lg pt-3 pb-2 px-3">
          <div className="flex items-center gap-2"><Coffee className="h-4 w-4 text-amber-500" /><span className="text-xs text-gray-500">On Break</span></div>
          <p className="text-lg font-bold">{onBreak}</p>
        </div>
        <div className="bg-white border border-gray-200 rounded-lg pt-3 pb-2 px-3">
          <div className="flex items-center gap-2"><Clock className="h-4 w-4 text-red-500" /><span className="text-xs text-gray-500">Overtime</span></div>
          <p className="text-lg font-bold text-red-600">{overtime}</p>
        </div>
        <div className="bg-white border border-gray-200 rounded-lg pt-3 pb-2 px-3">
          <div className="flex items-center gap-2"><Calendar className="h-4 w-4 text-impilo-400" /><span className="text-xs text-gray-500">Leave Pending</span></div>
          <p className="text-lg font-bold">{LEAVE_REQUESTS.filter(l => l.status === 'pending').length}</p>
        </div>
      </div>

      {/* Tabs */}
      <div className="flex gap-1 border-b border-gray-200">
        {tabs.map(tab => {
          const Icon = tab.icon;
          return (
            <button
              key={tab.key}
              onClick={() => setActiveTab(tab.key)}
              className={`inline-flex items-center gap-1.5 px-3 py-2 text-xs font-medium border-b-2 transition-colors ${
                activeTab === tab.key ? 'border-impilo-500 text-impilo-500' : 'border-transparent text-gray-500 hover:text-gray-700'
              }`}
            >
              <Icon className="h-3.5 w-3.5" />
              {tab.label}
              {tab.badge && (
                <span className="ml-1 px-1.5 py-0.5 rounded-full bg-gray-100 text-[10px] font-medium">{tab.badge}</span>
              )}
            </button>
          );
        })}
      </div>

      {/* Roster Tab */}
      {activeTab === 'roster' && (
        <div className="space-y-1 max-h-[420px] overflow-auto">
          {STAFF_ROSTER.map(staff => (
            <div key={staff.id} className="flex items-center justify-between p-3 rounded-lg border border-gray-200 hover:bg-gray-50 transition-colors">
              <div className="flex items-center gap-3">
                <div className={`h-9 w-9 rounded-full flex items-center justify-center text-xs font-semibold ${getRoleColor(staff.role)}`}>
                  {staff.name.split(' ').map(n => n[0]).join('').slice(0, 2)}
                </div>
                <div>
                  <p className="text-sm font-medium">{staff.name}</p>
                  <p className="text-xs text-gray-500">{staff.department} &middot; {staff.hours}</p>
                </div>
              </div>
              <div className="flex items-center gap-3">
                {staff.overtime && <span className="px-2 py-0.5 rounded bg-red-100 text-red-700 text-[10px] font-medium">OT</span>}
                <span className="px-2 py-0.5 rounded border border-gray-200 text-[10px] capitalize">{staff.role.replace('_', ' ')}</span>
                {getStaffStatusBadge(staff.status)}
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Shifts Tab */}
      {activeTab === 'shifts' && (
        <div className="space-y-3">
          {ACTIVE_SHIFTS.map(shift => {
            const ShiftIcon = shift.type.includes('Night') ? Moon : shift.type.includes('Admin') ? FileText : Sun;
            return (
              <div key={shift.id} className="bg-white border border-gray-200 rounded-lg py-4 px-4">
                <div className="flex items-center justify-between mb-2">
                  <div className="flex items-center gap-2">
                    <ShiftIcon className="h-4 w-4 text-gray-400" />
                    <span className="font-semibold text-sm">{shift.type}</span>
                    <span className="text-xs text-gray-500">{shift.time}</span>
                  </div>
                  <span className={`px-2 py-0.5 rounded text-xs font-medium ${shift.coverage >= 90 ? 'bg-green-100 text-green-700' : 'bg-amber-100 text-amber-700'}`}>
                    {shift.coverage}% coverage
                  </span>
                </div>
                <div className="flex items-center justify-between text-xs text-gray-500 mb-2">
                  <span>{shift.staffCount} staff</span>
                  <span>{shift.departments.join(', ')}</span>
                </div>
                <div className="w-full bg-gray-100 rounded-full h-1.5">
                  <div
                    className={`h-1.5 rounded-full ${shift.coverage < 85 ? 'bg-amber-500' : 'bg-green-500'}`}
                    style={{ width: `${shift.coverage}%` }}
                  />
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Leave Tab */}
      {activeTab === 'leave' && (
        <div className="space-y-2 max-h-[420px] overflow-auto">
          {LEAVE_REQUESTS.map(req => (
            <div key={req.id} className="bg-white border border-gray-200 rounded-lg py-3 px-4">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-sm font-medium">{req.name}</p>
                  <p className="text-xs text-gray-500">{req.type} &middot; {req.department}</p>
                  <p className="text-xs text-gray-500">{req.from} &rarr; {req.to} ({req.days} days)</p>
                </div>
                <div className="flex items-center gap-2">
                  <span className={`px-2 py-0.5 rounded text-xs font-medium capitalize ${req.status === 'approved' ? 'bg-green-100 text-green-700' : 'bg-amber-100 text-amber-700'}`}>
                    {req.status}
                  </span>
                  {req.status === 'pending' && (
                    <div className="flex gap-1">
                      <button className="px-2 py-1 text-xs border border-gray-200 rounded hover:bg-gray-100">Approve</button>
                      <button className="px-2 py-1 text-xs text-red-500 hover:bg-red-50 rounded">Decline</button>
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
          {PENDING_HANDOVERS.map(ho => (
            <div key={ho.id} className="bg-white border border-gray-200 border-l-4 border-l-amber-500 rounded-lg py-4 px-4">
              <div className="flex items-center justify-between mb-2">
                <div>
                  <p className="text-sm font-semibold">{ho.department} Handover</p>
                  <p className="text-xs text-gray-500">{ho.from} &rarr; {ho.to}</p>
                </div>
                <span className="px-2 py-0.5 rounded border border-gray-200 text-xs">Due {ho.dueAt}</span>
              </div>
              <div className="flex items-center gap-4 text-xs text-gray-500">
                <span>{ho.patients} patients</span>
                {ho.criticalNotes > 0 && (
                  <span className="text-red-500 flex items-center gap-1">
                    <AlertTriangle className="h-3 w-3" />{ho.criticalNotes} critical note{ho.criticalNotes > 1 ? 's' : ''}
                  </span>
                )}
              </div>
              <div className="flex gap-2 mt-3">
                <button className="inline-flex items-center gap-1 px-3 py-1.5 text-xs bg-impilo-500 text-white rounded hover:bg-impilo-600">
                  <FileText className="h-3 w-3" />View Notes
                </button>
                <button className="inline-flex items-center gap-1 px-3 py-1.5 text-xs border border-gray-200 rounded hover:bg-gray-50">
                  <Shield className="h-3 w-3" />Complete Handover
                </button>
              </div>
            </div>
          ))}
          {PENDING_HANDOVERS.length === 0 && (
            <div className="text-center py-8 text-gray-400 text-sm">No pending handovers</div>
          )}
        </div>
      )}
    </div>
  );
}
