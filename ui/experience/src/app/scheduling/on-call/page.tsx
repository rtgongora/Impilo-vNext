"use client";

/**
 * On-Call — Assignments and swap workflow backed by Experience BFF staffing tables.
 * Route: /scheduling/on-call | pageTitle: "On-Call Schedule"
 */

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import {
  ArrowLeft,
  Phone,
  Loader2,
  ChevronLeft,
  ChevronRight,
  ArrowRightLeft,
  Clock,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { FacilityWorkClusterRibbon } from "@/components/experience/FacilityWorkClusterRibbon";
import { OrganizationPlaneContextBar } from "@/components/experience/OrganizationPlaneContextBar";
import { PageShell } from "@/components/PageShell";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import {
  useCreateOnCallSwap,
  useOnCallSwaps,
  useOnCallWeek,
  usePatchOnCallSwap,
  type OnCallAssignmentResource,
} from "@/hooks/queries/useStaffing";

function getMonday(d: Date): Date {
  const day = d.getDay();
  const diff = d.getDate() - day + (day === 0 ? -6 : 1);
  const monday = new Date(d);
  monday.setDate(diff);
  monday.setHours(0, 0, 0, 0);
  return monday;
}

function formatISODate(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

function addDays(d: Date, n: number): Date {
  const x = new Date(d);
  x.setDate(x.getDate() + n);
  return x;
}

function dayShortLabel(ymd: string): string {
  const d = new Date(`${ymd}T12:00:00`);
  return d.toLocaleDateString("en-ZA", { weekday: "short" });
}

const SPECIALTY_COLORS: Record<string, string> = {
  "Internal Medicine": "bg-blue-100 text-blue-700",
  Surgery: "bg-red-100 text-red-700",
  Paediatrics: "bg-green-100 text-green-700",
  Obstetrics: "bg-pink-100 text-pink-700",
};

function mapUiSwapStatus(s: string): "Pending" | "Approved" | "Declined" {
  if (s === "APPROVED") return "Approved";
  if (s === "DECLINED") return "Declined";
  return "Pending";
}

function toShiftLabel(kind: string): string {
  if (kind === "Day" || kind === "Night" || kind === "24hr") return `${kind} shift`;
  return `${kind} shift`;
}

export default function OnCallPage() {
  const searchParams = useSearchParams();
  const fromOrgAdmin = searchParams.get("from") === "organization-admin";
  const facility = useFacilityStore((s) => s.facility);

  const [weekOffset, setWeekOffset] = useState(0);
  const baseMonday = useMemo(() => getMonday(new Date()), []);
  const weekStart = useMemo(() => addDays(baseMonday, weekOffset * 7), [baseMonday, weekOffset]);
  const weekStartISO = formatISODate(weekStart);

  const { data: weekRes, isLoading: weekLoading, isError: weekError } = useOnCallWeek({
    facilityId: facility?.id,
    weekStartISO,
  });
  const { data: swapsRes, isLoading: swapsLoading } = useOnCallSwaps(facility?.id);
  const patchSwap = usePatchOnCallSwap();
  const createSwap = useCreateOnCallSwap();

  const schedule: OnCallAssignmentResource[] = weekRes?.data ?? [];
  const swaps = swapsRes?.data ?? [];

  const dates = useMemo(() => {
    const set = new Set(schedule.map((s) => s.attributes.assignment_date));
    return [...set].sort();
  }, [schedule]);

  const [selectedDate, setSelectedDate] = useState<string>("");
  const [viewMode, setViewMode] = useState<"calendar" | "list">("calendar");
  const [showSwapForm, setShowSwapForm] = useState(false);
  const [swapForm, setSwapForm] = useState({
    requestor_name: "",
    requestee_name: "",
    original_date: "",
    swap_date: "",
    specialty: "",
  });

  useEffect(() => {
    if (dates.length === 0) {
      setSelectedDate("");
      return;
    }
    if (!selectedDate || !dates.includes(selectedDate)) {
      setSelectedDate(dates[0]!);
    }
  }, [dates, selectedDate]);

  const todaySchedule = schedule.filter((s) => s.attributes.assignment_date === selectedDate);
  const pendingSwaps = swaps.filter((s) => s.attributes.status === "PENDING");

  const weekLabel = `${weekStart.toLocaleDateString("en-ZA", { day: "numeric", month: "short" })} — ${addDays(weekStart, 6).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" })}`;

  async function submitSwapRequest() {
    if (!facility?.id || !swapForm.requestor_name || !swapForm.requestee_name || !swapForm.original_date || !swapForm.swap_date) {
      return;
    }
    await createSwap.mutateAsync({
      facility_id: facility.id,
      requestor_name: swapForm.requestor_name,
      requestee_name: swapForm.requestee_name,
      original_date: swapForm.original_date,
      swap_date: swapForm.swap_date,
      specialty: swapForm.specialty || null,
    });
    setSwapForm({ requestor_name: "", requestee_name: "", original_date: "", swap_date: "", specialty: "" });
    setShowSwapForm(false);
  }

  const loading = weekLoading || swapsLoading;

  return (
    <AppLayout>
      <PageShell title="On-Call Schedule" subtitle="On-call assignments and swap requests for the selected facility">
        <OrganizationPlaneContextBar />
        {fromOrgAdmin && (
          <div className="mb-4">
            <Link
              href="/organization-admin/staffing?from=organization-admin"
              className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 transition-colors"
            >
              <ArrowLeft className="h-4 w-4" /> Back to staffing hub
            </Link>
          </div>
        )}
        <FacilityWorkClusterRibbon shiftExpected={false} />

        {!facility?.id ? (
          <div className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">
            Select a facility to load on-call assignments.
          </div>
        ) : null}

        <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
          <div className="flex items-center gap-1 bg-white border border-gray-200 rounded-lg">
            <button
              type="button"
              onClick={() => setWeekOffset((p) => p - 1)}
              className="p-2 hover:bg-gray-50 rounded-l-lg transition-colors"
            >
              <ChevronLeft className="w-4 h-4 text-gray-600" />
            </button>
            <span className="px-3 text-xs font-medium text-gray-700">{weekLabel}</span>
            <button
              type="button"
              onClick={() => setWeekOffset((p) => p + 1)}
              className="p-2 hover:bg-gray-50 rounded-r-lg transition-colors"
            >
              <ChevronRight className="w-4 h-4 text-gray-600" />
            </button>
          </div>
        </div>

        {loading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading on-call schedule...</span>
          </div>
        ) : weekError ? (
          <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800">
            Could not load on-call data. Ensure the BFF is running and migration V29 is applied.
          </div>
        ) : (
          <div className="space-y-6">
            <div className="flex items-center justify-between flex-wrap gap-3">
              <div className="flex items-center gap-2">
                <Phone className="w-5 h-5 text-green-600" />
                <h2 className="text-lg font-semibold text-gray-900">On-Call Schedule</h2>
              </div>
              <div className="flex items-center gap-2 flex-wrap">
                <div className="flex border border-gray-300 rounded-lg overflow-hidden">
                  <button
                    type="button"
                    onClick={() => setViewMode("calendar")}
                    className={`px-3 py-1.5 text-xs ${viewMode === "calendar" ? "bg-blue-600 text-white" : "bg-white text-gray-600 hover:bg-gray-50"}`}
                  >
                    Calendar
                  </button>
                  <button
                    type="button"
                    onClick={() => setViewMode("list")}
                    className={`px-3 py-1.5 text-xs ${viewMode === "list" ? "bg-blue-600 text-white" : "bg-white text-gray-600 hover:bg-gray-50"}`}
                  >
                    List
                  </button>
                </div>
                <button
                  type="button"
                  onClick={() => setShowSwapForm((v) => !v)}
                  className="inline-flex items-center gap-1.5 px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 transition-colors"
                >
                  <ArrowRightLeft className="w-4 h-4" /> Request Swap
                </button>
              </div>
            </div>

            {showSwapForm && facility?.id ? (
              <div className="rounded-lg border border-gray-200 bg-white p-4 space-y-3 text-sm">
                <h3 className="font-medium text-gray-900">New swap request</h3>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  <label className="block">
                    <span className="text-xs text-gray-600">Requestor</span>
                    <input
                      className="mt-1 w-full border border-gray-300 rounded-md px-2 py-1.5"
                      value={swapForm.requestor_name}
                      onChange={(e) => setSwapForm((f) => ({ ...f, requestor_name: e.target.value }))}
                    />
                  </label>
                  <label className="block">
                    <span className="text-xs text-gray-600">Requestee</span>
                    <input
                      className="mt-1 w-full border border-gray-300 rounded-md px-2 py-1.5"
                      value={swapForm.requestee_name}
                      onChange={(e) => setSwapForm((f) => ({ ...f, requestee_name: e.target.value }))}
                    />
                  </label>
                  <label className="block">
                    <span className="text-xs text-gray-600">Original on-call date</span>
                    <input
                      type="date"
                      className="mt-1 w-full border border-gray-300 rounded-md px-2 py-1.5"
                      value={swapForm.original_date}
                      onChange={(e) => setSwapForm((f) => ({ ...f, original_date: e.target.value }))}
                    />
                  </label>
                  <label className="block">
                    <span className="text-xs text-gray-600">Swap to date</span>
                    <input
                      type="date"
                      className="mt-1 w-full border border-gray-300 rounded-md px-2 py-1.5"
                      value={swapForm.swap_date}
                      onChange={(e) => setSwapForm((f) => ({ ...f, swap_date: e.target.value }))}
                    />
                  </label>
                  <label className="block sm:col-span-2">
                    <span className="text-xs text-gray-600">Specialty (optional)</span>
                    <input
                      className="mt-1 w-full border border-gray-300 rounded-md px-2 py-1.5"
                      value={swapForm.specialty}
                      onChange={(e) => setSwapForm((f) => ({ ...f, specialty: e.target.value }))}
                    />
                  </label>
                </div>
                <div className="flex gap-2">
                  <button
                    type="button"
                    onClick={() => setShowSwapForm(false)}
                    className="px-3 py-1.5 text-xs border border-gray-200 rounded-md hover:bg-gray-50"
                  >
                    Cancel
                  </button>
                  <button
                    type="button"
                    disabled={createSwap.isPending}
                    onClick={() => void submitSwapRequest()}
                    className="px-3 py-1.5 text-xs bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-50"
                  >
                    {createSwap.isPending ? "Submitting…" : "Submit request"}
                  </button>
                </div>
              </div>
            ) : null}

            {pendingSwaps.length > 0 && (
              <div className="bg-amber-50 border border-amber-200 rounded-lg p-4">
                <div className="flex items-center gap-2 mb-2">
                  <ArrowRightLeft className="w-4 h-4 text-amber-600" />
                  <h3 className="text-sm font-medium text-amber-800">
                    {pendingSwaps.length} Pending Swap Request{pendingSwaps.length > 1 ? "s" : ""}
                  </h3>
                </div>
                {pendingSwaps.map((sw) => {
                  const a = sw.attributes;
                  return (
                    <div key={sw.id} className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2 bg-white rounded-lg p-3 mt-2">
                      <div className="text-xs text-gray-600">
                        <span className="font-medium">{a.requestor_name}</span> wants to swap{" "}
                        <span className="font-medium">{a.original_date}</span> with{" "}
                        <span className="font-medium">{a.requestee_name}</span> on{" "}
                        <span className="font-medium">{a.swap_date}</span>
                      </div>
                      <div className="flex items-center gap-2 shrink-0">
                        <button
                          type="button"
                          disabled={patchSwap.isPending}
                          onClick={() =>
                            facility?.id &&
                            patchSwap.mutate({ id: sw.id, facilityId: facility.id, status: "APPROVED" })
                          }
                          className="px-2.5 py-1 text-xs text-green-600 border border-green-200 rounded hover:bg-green-50 transition-colors disabled:opacity-50"
                        >
                          Approve
                        </button>
                        <button
                          type="button"
                          disabled={patchSwap.isPending}
                          onClick={() =>
                            facility?.id &&
                            patchSwap.mutate({ id: sw.id, facilityId: facility.id, status: "DECLINED" })
                          }
                          className="px-2.5 py-1 text-xs text-red-600 border border-red-200 rounded hover:bg-red-50 transition-colors disabled:opacity-50"
                        >
                          Decline
                        </button>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}

            {viewMode === "calendar" && (
              <div className="flex items-center gap-2 overflow-x-auto pb-2">
                {dates.length === 0 ? (
                  <p className="text-sm text-gray-500">No assignment dates in this week.</p>
                ) : (
                  dates.map((date) => {
                    const isSelected = date === selectedDate;
                    return (
                      <button
                        key={date}
                        type="button"
                        onClick={() => setSelectedDate(date)}
                        className={`flex flex-col items-center px-4 py-2 rounded-lg border transition-colors shrink-0 ${
                          isSelected ? "border-blue-300 bg-blue-50 text-blue-700" : "border-gray-200 bg-white text-gray-600 hover:bg-gray-50"
                        }`}
                      >
                        <span className="text-xs font-medium">{dayShortLabel(date)}</span>
                        <span className="text-sm font-bold">{date.slice(8)}</span>
                        <span className="text-[10px] text-gray-400">
                          {date.slice(5, 7)}/{date.slice(0, 4)}
                        </span>
                      </button>
                    );
                  })
                )}
              </div>
            )}

            {viewMode === "calendar" &&
              (todaySchedule.length === 0 ? (
                <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
                  <Phone className="w-10 h-10 text-gray-300 mx-auto mb-3" />
                  <p className="text-gray-400 text-sm">No on-call assignments for this date</p>
                </div>
              ) : (
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  {todaySchedule.map((oc) => renderAssignmentCard(oc))}
                </div>
              ))}

            {viewMode === "list" && (
              <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
                <div className="px-5 py-4 border-b border-gray-200">
                  <h3 className="font-medium text-gray-900">All assignments this week</h3>
                </div>
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-gray-200 bg-gray-50">
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Date</th>
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Specialty</th>
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Primary</th>
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Backup</th>
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Shift</th>
                    </tr>
                  </thead>
                  <tbody>
                    {schedule.length === 0 ? (
                      <tr>
                        <td colSpan={5} className="px-4 py-8 text-center text-gray-500">
                          No rows for this week.
                        </td>
                      </tr>
                    ) : (
                      schedule
                        .slice()
                        .sort((a, b) => a.attributes.assignment_date.localeCompare(b.attributes.assignment_date))
                        .map((oc) => {
                          const o = oc.attributes;
                          return (
                            <tr key={oc.id} className="border-b border-gray-100 hover:bg-gray-50">
                              <td className="px-4 py-3 text-gray-800">{o.assignment_date}</td>
                              <td className="px-4 py-3 text-gray-900">{o.specialty}</td>
                              <td className="px-4 py-3 text-gray-800">{o.primary_staff_name}</td>
                              <td className="px-4 py-3 text-gray-700">{o.backup_staff_name}</td>
                              <td className="px-4 py-3 text-gray-600">{toShiftLabel(o.shift_kind)}</td>
                            </tr>
                          );
                        })
                    )}
                  </tbody>
                </table>
              </div>
            )}

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
                  {swaps.length === 0 ? (
                    <tr>
                      <td colSpan={5} className="px-4 py-8 text-center text-gray-500">
                        No swap requests yet.
                      </td>
                    </tr>
                  ) : (
                    swaps.map((sw) => {
                      const a = sw.attributes;
                      const ui = mapUiSwapStatus(a.status);
                      return (
                        <tr key={sw.id} className="border-b border-gray-100 hover:bg-gray-50 transition-colors">
                          <td className="px-4 py-3 text-gray-900">{a.requestor_name}</td>
                          <td className="px-4 py-3 text-gray-700">{a.original_date}</td>
                          <td className="px-4 py-3 text-gray-900">{a.requestee_name}</td>
                          <td className="px-4 py-3 text-gray-700">{a.swap_date}</td>
                          <td className="px-4 py-3">
                            <span
                              className={`px-2 py-0.5 rounded-full text-xs font-medium ${
                                ui === "Approved"
                                  ? "bg-green-100 text-green-700"
                                  : ui === "Pending"
                                    ? "bg-amber-100 text-amber-700"
                                    : "bg-red-100 text-red-700"
                              }`}
                            >
                              {ui}
                            </span>
                          </td>
                        </tr>
                      );
                    })
                  )}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}

function renderAssignmentCard(oc: OnCallAssignmentResource) {
  const o = oc.attributes;
  const primaryPhone = o.primary_phone ?? "";
  const backupPhone = o.backup_phone ?? "";
  return (
    <div key={oc.id} className="bg-white rounded-lg border border-gray-200 p-5">
      <div className="flex items-center justify-between mb-4">
        <span
          className={`px-2.5 py-1 rounded-full text-xs font-medium ${SPECIALTY_COLORS[o.specialty] || "bg-gray-100 text-gray-700"}`}
        >
          {o.specialty}
        </span>
        <span className="text-xs text-gray-400 flex items-center gap-1">
          <Clock className="w-3 h-3" /> {toShiftLabel(o.shift_kind)}
        </span>
      </div>
      <div className="mb-3">
        <p className="text-[10px] font-medium text-gray-400 uppercase tracking-wide mb-1">Primary</p>
        <div className="flex items-center justify-between gap-2 flex-wrap">
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-full bg-blue-100 text-blue-700 flex items-center justify-center text-xs font-semibold">
              {o.primary_staff_name
                .split(" ")
                .map((w) => w[0])
                .slice(0, 2)
                .join("")}
            </div>
            <span className="text-sm font-medium text-gray-900">{o.primary_staff_name}</span>
          </div>
          {primaryPhone ? (
            <a href={`tel:${primaryPhone}`} className="inline-flex items-center gap-1 text-xs text-blue-600 hover:text-blue-700">
              <Phone className="w-3 h-3" /> {primaryPhone}
            </a>
          ) : (
            <span className="text-xs text-gray-400">No phone on file</span>
          )}
        </div>
      </div>
      <div className="pt-3 border-t border-gray-100">
        <p className="text-[10px] font-medium text-gray-400 uppercase tracking-wide mb-1">Backup / Escalation</p>
        <div className="flex items-center justify-between gap-2 flex-wrap">
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-full bg-gray-100 text-gray-600 flex items-center justify-center text-xs font-semibold">
              {o.backup_staff_name
                .split(" ")
                .map((w) => w[0])
                .slice(0, 2)
                .join("")}
            </div>
            <span className="text-sm text-gray-700">{o.backup_staff_name}</span>
          </div>
          {backupPhone ? (
            <a href={`tel:${backupPhone}`} className="inline-flex items-center gap-1 text-xs text-gray-500 hover:text-blue-600">
              <Phone className="w-3 h-3" /> {backupPhone}
            </a>
          ) : (
            <span className="text-xs text-gray-400">No phone on file</span>
          )}
        </div>
      </div>
    </div>
  );
}
