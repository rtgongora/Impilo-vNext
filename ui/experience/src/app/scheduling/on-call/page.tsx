"use client";

/**
 * On-Call — On-call schedule with calendar view, rotation, and swap management.
 * Route: /scheduling/on-call | pageTitle: "On-Call Schedule"
 */

import { useState } from "react";
import { Phone, Loader2, ChevronLeft, ChevronRight, ArrowRightLeft, User, Clock, AlertTriangle } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";

interface OnCallAssignment {
  id: string;
  date: string;
  dayOfWeek: string;
  specialty: string;
  primaryProvider: string;
  primaryPhone: string;
  backupProvider: string;
  backupPhone: string;
  shift: "Day" | "Night" | "24hr";
}

interface SwapRequest {
  id: string;
  requestor: string;
  requestee: string;
  originalDate: string;
  swapDate: string;
  status: "Pending" | "Approved" | "Declined";
  specialty: string;
}

const MOCK_SCHEDULE: OnCallAssignment[] = [
  { id: "oc-1", date: "2026-04-06", dayOfWeek: "Mon", specialty: "Internal Medicine", primaryProvider: "Dr. M. Ndlovu", primaryPhone: "+263 77 123 4567", backupProvider: "Dr. B. Ncube", backupPhone: "+263 77 890 1234", shift: "24hr" },
  { id: "oc-2", date: "2026-04-06", dayOfWeek: "Mon", specialty: "Surgery", primaryProvider: "Dr. T. Moyo", primaryPhone: "+263 77 345 6789", backupProvider: "Dr. L. Mupfumi", backupPhone: "+263 77 567 1234", shift: "24hr" },
  { id: "oc-3", date: "2026-04-06", dayOfWeek: "Mon", specialty: "Paediatrics", primaryProvider: "Dr. P. Zulu", primaryPhone: "+263 77 456 7890", backupProvider: "Dr. F. Mwale", backupPhone: "+263 77 678 2345", shift: "24hr" },
  { id: "oc-4", date: "2026-04-06", dayOfWeek: "Mon", specialty: "Obstetrics", primaryProvider: "Dr. K. Dlamini", primaryPhone: "+263 77 567 8901", backupProvider: "Dr. G. Sithole", backupPhone: "+263 77 789 3456", shift: "24hr" },
  { id: "oc-5", date: "2026-04-07", dayOfWeek: "Tue", specialty: "Internal Medicine", primaryProvider: "Dr. B. Ncube", primaryPhone: "+263 77 890 1234", backupProvider: "Dr. M. Ndlovu", backupPhone: "+263 77 123 4567", shift: "24hr" },
  { id: "oc-6", date: "2026-04-07", dayOfWeek: "Tue", specialty: "Surgery", primaryProvider: "Dr. L. Mupfumi", primaryPhone: "+263 77 567 1234", backupProvider: "Dr. T. Moyo", backupPhone: "+263 77 345 6789", shift: "24hr" },
  { id: "oc-7", date: "2026-04-08", dayOfWeek: "Wed", specialty: "Internal Medicine", primaryProvider: "Dr. M. Ndlovu", primaryPhone: "+263 77 123 4567", backupProvider: "Dr. B. Ncube", backupPhone: "+263 77 890 1234", shift: "24hr" },
  { id: "oc-8", date: "2026-04-09", dayOfWeek: "Thu", specialty: "Internal Medicine", primaryProvider: "Dr. B. Ncube", primaryPhone: "+263 77 890 1234", backupProvider: "Dr. M. Ndlovu", backupPhone: "+263 77 123 4567", shift: "24hr" },
];

const MOCK_SWAPS: SwapRequest[] = [
  { id: "sw-1", requestor: "Dr. M. Ndlovu", requestee: "Dr. B. Ncube", originalDate: "2026-04-10", swapDate: "2026-04-12", status: "Pending", specialty: "Internal Medicine" },
  { id: "sw-2", requestor: "Dr. T. Moyo", requestee: "Dr. L. Mupfumi", originalDate: "2026-04-08", swapDate: "2026-04-09", status: "Approved", specialty: "Surgery" },
];

const SPECIALTY_COLORS: Record<string, string> = {
  "Internal Medicine": "bg-blue-100 text-blue-700",
  "Surgery": "bg-red-100 text-red-700",
  "Paediatrics": "bg-green-100 text-green-700",
  "Obstetrics": "bg-pink-100 text-pink-700",
};

export default function OnCallPage() {
  const { data, isLoading } = useQuery({
    queryKey: ["on-call"],
    queryFn: async () => ({ schedule: MOCK_SCHEDULE, swaps: MOCK_SWAPS }),
  });

  const schedule = data?.schedule ?? [];
  const swaps = data?.swaps ?? [];
  const [selectedDate, setSelectedDate] = useState("2026-04-06");
  const [viewMode, setViewMode] = useState<"calendar" | "list">("calendar");

  const dates = [...new Set(schedule.map((s) => s.date))].sort();
  const todaySchedule = schedule.filter((s) => s.date === selectedDate);
  const pendingSwaps = swaps.filter((s) => s.status === "Pending");

  return (
    <AppLayout>
      <PageShell title="On-Call Schedule" subtitle="On-call assignments, rotations, and swap management">
        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading on-call schedule...</span>
          </div>
        ) : (
          <div className="space-y-6">
            {/* Header */}
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Phone className="w-5 h-5 text-green-600" />
                <h2 className="text-lg font-semibold text-gray-900">On-Call Schedule</h2>
              </div>
              <div className="flex items-center gap-2">
                <div className="flex border border-gray-300 rounded-lg overflow-hidden">
                  <button onClick={() => setViewMode("calendar")} className={`px-3 py-1.5 text-xs ${viewMode === "calendar" ? "bg-blue-600 text-white" : "bg-white text-gray-600 hover:bg-gray-50"}`}>Calendar</button>
                  <button onClick={() => setViewMode("list")} className={`px-3 py-1.5 text-xs ${viewMode === "list" ? "bg-blue-600 text-white" : "bg-white text-gray-600 hover:bg-gray-50"}`}>List</button>
                </div>
                <button className="inline-flex items-center gap-1.5 px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 transition-colors">
                  <ArrowRightLeft className="w-4 h-4" /> Request Swap
                </button>
              </div>
            </div>

            {/* Swap Requests Alert */}
            {pendingSwaps.length > 0 && (
              <div className="bg-amber-50 border border-amber-200 rounded-lg p-4">
                <div className="flex items-center gap-2 mb-2">
                  <ArrowRightLeft className="w-4 h-4 text-amber-600" />
                  <h3 className="text-sm font-medium text-amber-800">{pendingSwaps.length} Pending Swap Request{pendingSwaps.length > 1 ? "s" : ""}</h3>
                </div>
                {pendingSwaps.map((sw) => (
                  <div key={sw.id} className="flex items-center justify-between bg-white rounded-lg p-3 mt-2">
                    <div className="text-xs text-gray-600">
                      <span className="font-medium">{sw.requestor}</span> wants to swap <span className="font-medium">{sw.originalDate}</span> with <span className="font-medium">{sw.requestee}</span> on <span className="font-medium">{sw.swapDate}</span>
                    </div>
                    <div className="flex items-center gap-2">
                      <button className="px-2.5 py-1 text-xs text-green-600 border border-green-200 rounded hover:bg-green-50 transition-colors">Approve</button>
                      <button className="px-2.5 py-1 text-xs text-red-600 border border-red-200 rounded hover:bg-red-50 transition-colors">Decline</button>
                    </div>
                  </div>
                ))}
              </div>
            )}

            {/* Date Selection */}
            <div className="flex items-center gap-2 overflow-x-auto pb-2">
              {dates.map((date) => {
                const entry = schedule.find((s) => s.date === date);
                const isSelected = date === selectedDate;
                return (
                  <button
                    key={date}
                    onClick={() => setSelectedDate(date)}
                    className={`flex flex-col items-center px-4 py-2 rounded-lg border transition-colors shrink-0 ${isSelected ? "border-blue-300 bg-blue-50 text-blue-700" : "border-gray-200 bg-white text-gray-600 hover:bg-gray-50"}`}
                  >
                    <span className="text-xs font-medium">{entry?.dayOfWeek || ""}</span>
                    <span className="text-sm font-bold">{date.slice(8)}</span>
                    <span className="text-[10px] text-gray-400">{date.slice(5, 7)}/{date.slice(0, 4)}</span>
                  </button>
                );
              })}
            </div>

            {/* On-Call Cards */}
            {todaySchedule.length === 0 ? (
              <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
                <Phone className="w-10 h-10 text-gray-300 mx-auto mb-3" />
                <p className="text-gray-400 text-sm">No on-call assignments for this date</p>
              </div>
            ) : (
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {todaySchedule.map((oc) => (
                  <div key={oc.id} className="bg-white rounded-lg border border-gray-200 p-5">
                    <div className="flex items-center justify-between mb-4">
                      <span className={`px-2.5 py-1 rounded-full text-xs font-medium ${SPECIALTY_COLORS[oc.specialty] || "bg-gray-100 text-gray-700"}`}>
                        {oc.specialty}
                      </span>
                      <span className="text-xs text-gray-400 flex items-center gap-1">
                        <Clock className="w-3 h-3" /> {oc.shift} shift
                      </span>
                    </div>
                    {/* Primary */}
                    <div className="mb-3">
                      <p className="text-[10px] font-medium text-gray-400 uppercase tracking-wide mb-1">Primary</p>
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-2">
                          <div className="w-8 h-8 rounded-full bg-blue-100 text-blue-700 flex items-center justify-center text-xs font-semibold">
                            {oc.primaryProvider.split(" ").map((w) => w[0]).slice(0, 2).join("")}
                          </div>
                          <span className="text-sm font-medium text-gray-900">{oc.primaryProvider}</span>
                        </div>
                        <a href={`tel:${oc.primaryPhone}`} className="inline-flex items-center gap-1 text-xs text-blue-600 hover:text-blue-700">
                          <Phone className="w-3 h-3" /> {oc.primaryPhone}
                        </a>
                      </div>
                    </div>
                    {/* Backup */}
                    <div className="pt-3 border-t border-gray-100">
                      <p className="text-[10px] font-medium text-gray-400 uppercase tracking-wide mb-1">Backup / Escalation</p>
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-2">
                          <div className="w-8 h-8 rounded-full bg-gray-100 text-gray-600 flex items-center justify-center text-xs font-semibold">
                            {oc.backupProvider.split(" ").map((w) => w[0]).slice(0, 2).join("")}
                          </div>
                          <span className="text-sm text-gray-700">{oc.backupProvider}</span>
                        </div>
                        <a href={`tel:${oc.backupPhone}`} className="inline-flex items-center gap-1 text-xs text-gray-500 hover:text-blue-600">
                          <Phone className="w-3 h-3" /> {oc.backupPhone}
                        </a>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            )}

            {/* All Swap Requests */}
            <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
              <div className="px-5 py-4 border-b border-gray-200 flex items-center gap-2">
                <ArrowRightLeft className="w-4 h-4 text-gray-500" />
                <h3 className="font-medium text-gray-900">Swap Requests</h3>
              </div>
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-gray-200 bg-gray-50">
                    <th className="text-left px-4 py-3 font-medium text-gray-600">Requestor</th>
                    <th className="text-left px-4 py-3 font-medium text-gray-600">Original Date</th>
                    <th className="text-left px-4 py-3 font-medium text-gray-600">Swap With</th>
                    <th className="text-left px-4 py-3 font-medium text-gray-600">Swap Date</th>
                    <th className="text-left px-4 py-3 font-medium text-gray-600">Status</th>
                  </tr>
                </thead>
                <tbody>
                  {swaps.map((sw) => (
                    <tr key={sw.id} className="border-b border-gray-100 hover:bg-gray-50 transition-colors">
                      <td className="px-4 py-3 text-gray-900">{sw.requestor}</td>
                      <td className="px-4 py-3 text-gray-700">{sw.originalDate}</td>
                      <td className="px-4 py-3 text-gray-900">{sw.requestee}</td>
                      <td className="px-4 py-3 text-gray-700">{sw.swapDate}</td>
                      <td className="px-4 py-3">
                        <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${sw.status === "Approved" ? "bg-green-100 text-green-700" : sw.status === "Pending" ? "bg-amber-100 text-amber-700" : "bg-red-100 text-red-700"}`}>
                          {sw.status}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
