"use client";

/**
 * Roster — Weekly view built from recorded shifts (Experience BFF staffing API).
 * Route: /scheduling/roster | pageTitle: "Staff Roster"
 */

import { useMemo, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { ArrowLeft, Calendar, Loader2, ChevronLeft, ChevronRight, Users, Info } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { FacilityWorkClusterRibbon } from "@/components/experience/FacilityWorkClusterRibbon";
import { OrganizationPlaneContextBar } from "@/components/experience/OrganizationPlaneContextBar";
import { PageShell } from "@/components/PageShell";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useWorkspaceStore } from "@/hooks/useWorkspaceStore";
import { useStaffingRosterWeek, type StaffingShiftResource } from "@/hooks/queries/useStaffing";

type CellState = "active" | "worked" | "";

const DAYS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];

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

function weekDayStrings(weekStartMonday: Date): string[] {
  return Array.from({ length: 7 }, (_, i) => formatISODate(addDays(weekStartMonday, i)));
}

function shiftOverlapsCalendarDay(
  startedAt: string,
  endedAt: string | null,
  dayYmd: string,
  status: string
): boolean {
  const day0 = Date.parse(`${dayYmd}T00:00:00.000Z`);
  const day1 = day0 + 86400000;
  const s = Date.parse(startedAt);
  if (Number.isNaN(s)) return false;
  const e = endedAt ? Date.parse(endedAt) : status === "ACTIVE" ? Number.POSITIVE_INFINITY : day1;
  if (endedAt && Number.isNaN(e)) return false;
  return s < day1 && e > day0;
}

function cellStateForShift(attrs: StaffingShiftResource["attributes"], dayYmd: string): CellState | null {
  if (!shiftOverlapsCalendarDay(attrs.started_at, attrs.ended_at, dayYmd, attrs.status)) return null;
  if (attrs.status === "ACTIVE" && !attrs.ended_at) return "active";
  return "worked";
}

const CELL_STYLES: Record<CellState, string> = {
  active: "bg-emerald-100 text-emerald-800 border-emerald-200",
  worked: "bg-sky-100 text-sky-800 border-sky-200",
  "": "bg-white text-gray-300 border-dashed border-gray-200",
};

const CELL_LABELS: Record<CellState, string> = {
  active: "On duty",
  worked: "Worked",
  "": "—",
};

interface RosterRow {
  userId: string;
  displayName: string;
  days: Record<string, CellState>;
}

export default function RosterPage() {
  const searchParams = useSearchParams();
  const fromOrgAdmin = searchParams.get("from") === "organization-admin";
  const facility = useFacilityStore((s) => s.facility);
  const workspace = useWorkspaceStore((s) => s.workspace);

  const [weekOffset, setWeekOffset] = useState(0);
  const baseMonday = useMemo(() => getMonday(new Date()), []);
  const weekStart = useMemo(() => addDays(baseMonday, weekOffset * 7), [baseMonday, weekOffset]);
  const weekStartISO = formatISODate(weekStart);
  const dateColumns = useMemo(() => weekDayStrings(weekStart), [weekStart]);

  const { data, isLoading, isError } = useStaffingRosterWeek({
    facilityId: facility?.id,
    workspaceId: workspace?.id,
    weekStartISO,
  });

  const shifts = useMemo(() => data?.data ?? [], [data?.data]);

  const rows: RosterRow[] = useMemo(() => {
    const byUser = new Map<string, { displayName: string; days: Record<string, CellState> }>();
    for (const s of shifts) {
      const attrs = s.attributes;
      const uid = attrs.user_id;
      if (!byUser.has(uid)) {
        byUser.set(uid, { displayName: attrs.staff_display_name || uid, days: {} });
      }
      const entry = byUser.get(uid)!;
      if (attrs.staff_display_name) entry.displayName = attrs.staff_display_name;
      for (const day of dateColumns) {
        const next = cellStateForShift(attrs, day);
        if (!next) continue;
        const cur = entry.days[day] ?? "";
        if (next === "active") entry.days[day] = "active";
        else if (cur !== "active") entry.days[day] = "worked";
      }
    }
    return Array.from(byUser.entries())
      .map(([userId, v]) => ({ userId, displayName: v.displayName, days: v.days }))
      .sort((a, b) => a.displayName.localeCompare(b.displayName));
  }, [shifts, dateColumns]);

  const coverageByDay: Record<string, number> = {};
  for (const day of dateColumns) {
    coverageByDay[day] = rows.filter((r) => r.days[day] === "active" || r.days[day] === "worked").length;
  }

  const weekLabel = `Week of ${weekStart.toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" })}`;

  return (
    <AppLayout>
      <PageShell title="Staff Roster" subtitle="Weekly roster from recorded shifts at the selected facility">
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
            Select a facility to load the roster. Organization operators can pick a facility from the facility hub before opening
            staffing surfaces.
          </div>
        ) : null}

        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading roster...</span>
          </div>
        ) : isError ? (
          <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800">
            Could not load roster data. Check that the Experience BFF is running and migrations are applied.
          </div>
        ) : (
          <div className="space-y-6">
            <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
              <div className="flex items-center gap-2">
                <Calendar className="w-5 h-5 text-cyan-600" />
                <h2 className="text-lg font-semibold text-gray-900">Weekly Roster</h2>
              </div>
              <div className="flex flex-wrap items-center gap-3">
                <div className="flex items-center gap-1 bg-white border border-gray-200 rounded-lg">
                  <button
                    type="button"
                    onClick={() => setWeekOffset((p) => p - 1)}
                    className="p-2 hover:bg-gray-50 rounded-l-lg transition-colors"
                  >
                    <ChevronLeft className="w-4 h-4 text-gray-600" />
                  </button>
                  <span className="px-3 text-sm font-medium text-gray-700">{weekLabel}</span>
                  <button
                    type="button"
                    onClick={() => setWeekOffset((p) => p + 1)}
                    className="p-2 hover:bg-gray-50 rounded-r-lg transition-colors"
                  >
                    <ChevronRight className="w-4 h-4 text-gray-600" />
                  </button>
                </div>
                <Link
                  href="/shift"
                  className="inline-flex items-center gap-1.5 px-3 py-2 text-sm font-medium text-sky-700 border border-sky-200 rounded-lg hover:bg-sky-50 transition-colors"
                >
                  Shift clock
                </Link>
              </div>
            </div>

            <div className="flex items-start gap-2 rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-xs text-slate-700">
              <Info className="w-4 h-4 shrink-0 mt-0.5 text-slate-500" />
              <p>
                Cells reflect <span className="font-medium">real shift records</span> from the BFF (overlap with each calendar day).
                Planning labels such as Day/Night are not stored yet; use shift start/end for ground truth.{" "}
                {workspace?.id ? (
                  <span className="text-slate-600">Including shifts for this workspace or facility-wide (no workspace).</span>
                ) : null}
              </p>
            </div>

            <div className="flex items-center gap-3 flex-wrap text-xs">
              <span className={`px-2.5 py-1 rounded border font-medium ${CELL_STYLES.active}`}>On duty</span>
              <span className={`px-2.5 py-1 rounded border font-medium ${CELL_STYLES.worked}`}>Worked</span>
              <span className={`px-2.5 py-1 rounded border font-medium ${CELL_STYLES[""]}`}>No overlap</span>
            </div>

            <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-gray-200 bg-gray-50">
                      <th className="text-left px-4 py-3 font-medium text-gray-600 w-48 sticky left-0 bg-gray-50 z-10">
                        Staff
                      </th>
                      {DAYS.map((day, i) => (
                        <th key={day} className="text-center px-3 py-3 font-medium text-gray-600 min-w-[100px]">
                          <p>{day}</p>
                          <p className="text-[10px] text-gray-400 font-normal">{dateColumns[i]?.slice(5)}</p>
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {rows.length === 0 ? (
                      <tr>
                        <td colSpan={8} className="px-4 py-10 text-center text-sm text-gray-500">
                          No shift records overlap this week for this facility
                          {workspace?.id ? " and workspace filter" : ""}. Start a shift from the shift page to populate this grid.
                        </td>
                      </tr>
                    ) : (
                      rows.map((row) => (
                        <tr key={row.userId} className="border-b border-gray-100 hover:bg-gray-50/50">
                          <td className="px-4 py-3 sticky left-0 bg-white z-10">
                            <p className="font-medium text-gray-900 text-xs">{row.displayName}</p>
                            <p className="text-[10px] text-gray-400">{row.userId}</p>
                          </td>
                          {dateColumns.map((date) => {
                            const cell = row.days[date] ?? "";
                            return (
                              <td key={date} className="px-1 py-2 text-center">
                                <div
                                  className={`w-full px-2 py-1.5 rounded border text-xs font-medium ${CELL_STYLES[cell]}`}
                                  title={CELL_LABELS[cell]}
                                >
                                  {CELL_LABELS[cell]}
                                </div>
                              </td>
                            );
                          })}
                        </tr>
                      ))
                    )}
                    <tr className="bg-gray-50 border-t-2 border-gray-300">
                      <td className="px-4 py-3 sticky left-0 bg-gray-50 z-10">
                        <p className="font-medium text-gray-700 text-xs flex items-center gap-1">
                          <Users className="w-3 h-3" /> Coverage
                        </p>
                      </td>
                      {dateColumns.map((date) => {
                        const count = coverageByDay[date] || 0;
                        const isLow = count < 2;
                        return (
                          <td key={date} className="px-1 py-3 text-center">
                            <span className={`text-sm font-bold ${isLow ? "text-red-600" : "text-green-600"}`}>{count}</span>
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
