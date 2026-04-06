"use client";

/**
 * Roster — Staff roster management with weekly grid view and shift assignment.
 * Route: /scheduling/roster | pageTitle: "Staff Roster"
 */

import { useState } from "react";
import { Calendar, Loader2, ChevronLeft, ChevronRight, Send, Users } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";

type ShiftType = "Day" | "Night" | "Off" | "Leave" | "On-Call" | "";

interface StaffMember {
  id: string;
  name: string;
  role: string;
  department: string;
}

interface RosterEntry {
  staffId: string;
  shifts: Record<string, ShiftType>;
}

const DAYS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];
const DATES = ["2026-04-06", "2026-04-07", "2026-04-08", "2026-04-09", "2026-04-10", "2026-04-11", "2026-04-12"];

const MOCK_STAFF: StaffMember[] = [
  { id: "s-1", name: "Dr. M. Ndlovu", role: "Physician", department: "Internal Medicine" },
  { id: "s-2", name: "Dr. T. Moyo", role: "Physician", department: "Surgery" },
  { id: "s-3", name: "Sr. N. Phiri", role: "Nurse", department: "General Ward" },
  { id: "s-4", name: "Sr. A. Mlambo", role: "Nurse", department: "ICU" },
  { id: "s-5", name: "Sr. R. Chigumba", role: "Nurse", department: "Maternity" },
  { id: "s-6", name: "Dr. S. Chirwa", role: "Physician", department: "Psychiatry" },
  { id: "s-7", name: "Mr. K. Sibanda", role: "Physiotherapist", department: "Rehabilitation" },
  { id: "s-8", name: "Ms. L. Banda", role: "Dietitian", department: "Clinical Nutrition" },
];

const MOCK_ROSTER: RosterEntry[] = [
  { staffId: "s-1", shifts: { "2026-04-06": "Day", "2026-04-07": "Day", "2026-04-08": "Day", "2026-04-09": "Off", "2026-04-10": "Day", "2026-04-11": "Off", "2026-04-12": "Off" } },
  { staffId: "s-2", shifts: { "2026-04-06": "Day", "2026-04-07": "Night", "2026-04-08": "Off", "2026-04-09": "Day", "2026-04-10": "Day", "2026-04-11": "On-Call", "2026-04-12": "Off" } },
  { staffId: "s-3", shifts: { "2026-04-06": "Night", "2026-04-07": "Night", "2026-04-08": "Off", "2026-04-09": "Off", "2026-04-10": "Day", "2026-04-11": "Day", "2026-04-12": "Day" } },
  { staffId: "s-4", shifts: { "2026-04-06": "Day", "2026-04-07": "Day", "2026-04-08": "Night", "2026-04-09": "Night", "2026-04-10": "Off", "2026-04-11": "Off", "2026-04-12": "Day" } },
  { staffId: "s-5", shifts: { "2026-04-06": "Day", "2026-04-07": "Day", "2026-04-08": "Day", "2026-04-09": "Day", "2026-04-10": "Day", "2026-04-11": "Off", "2026-04-12": "Off" } },
  { staffId: "s-6", shifts: { "2026-04-06": "Day", "2026-04-07": "Off", "2026-04-08": "Day", "2026-04-09": "Day", "2026-04-10": "Off", "2026-04-11": "Leave", "2026-04-12": "Leave" } },
  { staffId: "s-7", shifts: { "2026-04-06": "Day", "2026-04-07": "Day", "2026-04-08": "Day", "2026-04-09": "Off", "2026-04-10": "Day", "2026-04-11": "Off", "2026-04-12": "Off" } },
  { staffId: "s-8", shifts: { "2026-04-06": "Day", "2026-04-07": "Day", "2026-04-08": "Off", "2026-04-09": "Day", "2026-04-10": "Day", "2026-04-11": "Off", "2026-04-12": "Off" } },
];

const SHIFT_STYLES: Record<ShiftType, string> = {
  Day: "bg-blue-100 text-blue-700 border-blue-200",
  Night: "bg-indigo-100 text-indigo-700 border-indigo-200",
  Off: "bg-gray-50 text-gray-400 border-gray-200",
  Leave: "bg-amber-100 text-amber-700 border-amber-200",
  "On-Call": "bg-purple-100 text-purple-700 border-purple-200",
  "": "bg-white text-gray-300 border-dashed border-gray-200",
};

const SHIFT_OPTIONS: ShiftType[] = ["Day", "Night", "Off", "Leave", "On-Call"];

export default function RosterPage() {
  const { data, isLoading } = useQuery({
    queryKey: ["roster"],
    queryFn: async () => ({ staff: MOCK_STAFF, roster: MOCK_ROSTER }),
  });

  const staff = data?.staff ?? [];
  const roster = data?.roster ?? [];
  const [weekOffset, setWeekOffset] = useState(0);
  const [editingCell, setEditingCell] = useState<string | null>(null);
  const [published, setPublished] = useState(false);

  // Coverage calculation
  const coverageByDay: Record<string, number> = {};
  DATES.forEach((date) => {
    coverageByDay[date] = roster.filter((r) => r.shifts[date] === "Day" || r.shifts[date] === "Night").length;
  });

  return (
    <AppLayout>
      <PageShell title="Staff Roster" subtitle="Weekly roster management and shift assignment">
        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading roster...</span>
          </div>
        ) : (
          <div className="space-y-6">
            {/* Header */}
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Calendar className="w-5 h-5 text-cyan-600" />
                <h2 className="text-lg font-semibold text-gray-900">Weekly Roster</h2>
              </div>
              <div className="flex items-center gap-3">
                <div className="flex items-center gap-1 bg-white border border-gray-200 rounded-lg">
                  <button onClick={() => setWeekOffset((p) => p - 1)} className="p-2 hover:bg-gray-50 rounded-l-lg transition-colors">
                    <ChevronLeft className="w-4 h-4 text-gray-600" />
                  </button>
                  <span className="px-3 text-sm font-medium text-gray-700">Week of 6 Apr 2026</span>
                  <button onClick={() => setWeekOffset((p) => p + 1)} className="p-2 hover:bg-gray-50 rounded-r-lg transition-colors">
                    <ChevronRight className="w-4 h-4 text-gray-600" />
                  </button>
                </div>
                <button
                  onClick={() => setPublished(true)}
                  disabled={published}
                  className="inline-flex items-center gap-1.5 px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                >
                  <Send className="w-4 h-4" />
                  {published ? "Published" : "Publish Roster"}
                </button>
              </div>
            </div>

            {/* Shift Legend */}
            <div className="flex items-center gap-3 flex-wrap">
              {SHIFT_OPTIONS.map((s) => (
                <span key={s} className={`px-2.5 py-1 rounded border text-xs font-medium ${SHIFT_STYLES[s]}`}>{s}</span>
              ))}
            </div>

            {/* Roster Grid */}
            <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-gray-200 bg-gray-50">
                      <th className="text-left px-4 py-3 font-medium text-gray-600 w-48 sticky left-0 bg-gray-50 z-10">Staff</th>
                      {DAYS.map((day, i) => (
                        <th key={day} className="text-center px-3 py-3 font-medium text-gray-600 min-w-[100px]">
                          <p>{day}</p>
                          <p className="text-[10px] text-gray-400 font-normal">{DATES[i]?.slice(5)}</p>
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {staff.map((s) => {
                      const entry = roster.find((r) => r.staffId === s.id);
                      return (
                        <tr key={s.id} className="border-b border-gray-100 hover:bg-gray-50/50">
                          <td className="px-4 py-3 sticky left-0 bg-white z-10">
                            <p className="font-medium text-gray-900 text-xs">{s.name}</p>
                            <p className="text-[10px] text-gray-400">{s.role} &middot; {s.department}</p>
                          </td>
                          {DATES.map((date) => {
                            const shift = entry?.shifts[date] || "";
                            const cellKey = `${s.id}-${date}`;
                            const isEditing = editingCell === cellKey;

                            return (
                              <td key={date} className="px-1 py-2 text-center">
                                {isEditing ? (
                                  <select
                                    autoFocus
                                    value={shift}
                                    onChange={() => setEditingCell(null)}
                                    onBlur={() => setEditingCell(null)}
                                    className="w-full rounded border border-blue-300 px-1 py-1 text-xs focus:outline-none focus:ring-2 focus:ring-blue-500"
                                  >
                                    <option value="">—</option>
                                    {SHIFT_OPTIONS.map((opt) => <option key={opt}>{opt}</option>)}
                                  </select>
                                ) : (
                                  <button
                                    onClick={() => setEditingCell(cellKey)}
                                    className={`w-full px-2 py-1.5 rounded border text-xs font-medium transition-colors ${SHIFT_STYLES[shift as ShiftType] || SHIFT_STYLES[""]}`}
                                  >
                                    {shift || "—"}
                                  </button>
                                )}
                              </td>
                            );
                          })}
                        </tr>
                      );
                    })}
                    {/* Coverage Row */}
                    <tr className="bg-gray-50 border-t-2 border-gray-300">
                      <td className="px-4 py-3 sticky left-0 bg-gray-50 z-10">
                        <p className="font-medium text-gray-700 text-xs flex items-center gap-1"><Users className="w-3 h-3" /> Coverage</p>
                      </td>
                      {DATES.map((date) => {
                        const count = coverageByDay[date] || 0;
                        const isLow = count < 4;
                        return (
                          <td key={date} className="px-1 py-3 text-center">
                            <span className={`text-sm font-bold ${isLow ? "text-red-600" : "text-green-600"}`}>
                              {count}
                            </span>
                            <p className="text-[10px] text-gray-400">staff</p>
                          </td>
                        );
                      })}
                    </tr>
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
