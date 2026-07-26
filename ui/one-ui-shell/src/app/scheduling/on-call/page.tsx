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
  identityResolutionFailed,
  staffLabel,
  useCreateOnCallSwap,
  useOnCallSwaps,
  useOnCallWeek,
  usePatchOnCallSwap,
  type OnCallAssignmentResource,
  type OnCallSwapResource,
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
  "Internal Medicine": "bg-primary-soft text-primary",
  Surgery: "bg-red-100 text-danger",
  Paediatrics: "bg-green-100 text-green-700",
  Obstetrics: "bg-pink-100 text-pink-700",
};

/**
 * A swap row carries no worker reference — vashandi keys swaps on ids, deliberately, because two
 * staff share a name. So an unresolved party falls back to the profile id in full: long, but it is
 * the identifier the request was actually made against, and it cannot be confused with a name.
 */
function swapPartyLabel(displayName: string | null | undefined, profileId: string | null): string {
  const name = displayName?.trim();
  if (name) return name;
  return profileId ?? "—";
}

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
  const {
    data: swapsRes,
    isLoading: swapsLoading,
    isError: swapsError,
  } = useOnCallSwaps(facility?.id);
  const patchSwap = usePatchOnCallSwap();
  const createSwap = useCreateOnCallSwap();

  const schedule: OnCallAssignmentResource[] = useMemo(() => weekRes?.data ?? [], [weekRes?.data]);
  const swaps = useMemo(() => swapsRes?.data ?? [], [swapsRes?.data]);
  // Names absent because the registries did not answer is a different fact from names absent
  // because these people have no registry record, and only the first one is a fault to report.
  const namesUnavailable =
    identityResolutionFailed(weekRes?.meta) || identityResolutionFailed(swapsRes?.meta);

  const dates = useMemo(() => {
    const set = new Set(schedule.map((s) => s.attributes.assignment_date));
    return [...set].sort();
  }, [schedule]);

  const [selectedDate, setSelectedDate] = useState<string>("");
  const [viewMode, setViewMode] = useState<"calendar" | "list">("calendar");
  const [showSwapForm, setShowSwapForm] = useState(false);
  // A swap is raised against a rostered shift and a person, not against typed names: two staff
  // share a name, and approving a swap changes who is clinically accountable for that window.
  const [swapForm, setSwapForm] = useState({
    requesting_shift_id: "",
    requested_profile_id: "",
    reason: "",
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
    if (!facility?.id || !swapForm.requesting_shift_id || !swapForm.requested_profile_id) {
      return;
    }
    await createSwap.mutateAsync({
      requesting_shift_id: swapForm.requesting_shift_id,
      requested_profile_id: swapForm.requested_profile_id,
      reason: swapForm.reason || null,
    });
    setSwapForm({ requesting_shift_id: "", requested_profile_id: "", reason: "" });
    setShowSwapForm(false);
  }

  // Only shifts actually on this week's rota can be swapped, and only people actually rostered can
  // be asked — the picker is built from the rota rather than from free text.
  const rosteredShifts = schedule.flatMap((s) =>
    [
      s.attributes.primary_shift_id
        ? {
            shiftId: s.attributes.primary_shift_id,
            profileId: s.attributes.primary_workforce_profile_id,
            // The person, however they can be named: registry name first, worker reference
            // otherwise. Picking a colleague by raw profile UUID is how the wrong person gets
            // asked to cover a night.
            personLabel:
              staffLabel(s.attributes.primary_display_name, s.attributes.primary_staff_reference) ??
              "Unnamed colleague",
            label: `${s.attributes.assignment_date} · ${s.attributes.specialty} · first call`,
          }
        : null,
      s.attributes.backup_shift_id
        ? {
            shiftId: s.attributes.backup_shift_id,
            profileId: s.attributes.backup_workforce_profile_id,
            personLabel:
              staffLabel(s.attributes.backup_display_name, s.attributes.backup_staff_reference) ??
              "Unnamed colleague",
            label: `${s.attributes.assignment_date} · ${s.attributes.specialty} · second call`,
          }
        : null,
    ].filter((v): v is NonNullable<typeof v> => Boolean(v))
  );

  const selectedShiftOwner = rosteredShifts.find(
    (r) => r.shiftId === swapForm.requesting_shift_id
  )?.profileId;

  const loading = weekLoading || swapsLoading;

  return (
    <AppLayout>
      <PageShell title="On-Call Schedule" subtitle="On-call assignments and swap requests for the selected facility">
        <OrganizationPlaneContextBar />
        {fromOrgAdmin && (
          <div className="mb-4">
            <Link
              href="/organization-admin/staffing?from=organization-admin"
              className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground transition-colors"
            >
              <ArrowLeft className="h-4 w-4" /> Back to staffing hub
            </Link>
          </div>
        )}
        <FacilityWorkClusterRibbon shiftExpected={false} />

        {!facility?.id ? (
          <div className="rounded-lg border border-warning/35 bg-warning-soft px-4 py-3 text-sm text-warning-foreground">
            Select a facility to load on-call assignments.
          </div>
        ) : null}

        {namesUnavailable ? (
          <div
            role="status"
            className="mb-4 rounded-lg border border-warning/35 bg-warning-soft px-4 py-3 text-sm text-warning-foreground"
          >
            Staff names could not be looked up just now — the rota below shows worker references
            instead. These are the people rostered; only their names are missing.
          </div>
        ) : null}

        {swapsError ? (
          <div
            role="alert"
            className="mb-4 rounded-lg border border-danger/28 bg-danger-soft px-4 py-3 text-sm text-red-800"
          >
            Swap requests could not be loaded. Any pending swap for this week is not shown here — do
            not read this as &ldquo;no swaps requested&rdquo;.
          </div>
        ) : null}

        <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
          <div className="flex items-center gap-1 bg-card border border-border rounded-lg">
            <button
              type="button"
              onClick={() => setWeekOffset((p) => p - 1)}
              className="p-2 hover:bg-background rounded-l-lg transition-colors"
            >
              <ChevronLeft className="w-4 h-4 text-muted-foreground" />
            </button>
            <span className="px-3 text-xs font-medium text-foreground">{weekLabel}</span>
            <button
              type="button"
              onClick={() => setWeekOffset((p) => p + 1)}
              className="p-2 hover:bg-background rounded-r-lg transition-colors"
            >
              <ChevronRight className="w-4 h-4 text-muted-foreground" />
            </button>
          </div>
        </div>

        {loading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
            <span className="ml-2 text-sm text-muted-foreground">Loading on-call schedule...</span>
          </div>
        ) : weekError ? (
          <div className="rounded-lg border border-danger/28 bg-danger-soft px-4 py-3 text-sm text-red-800">
            Could not load on-call data. Ensure the BFF is running and migration V29 is applied.
          </div>
        ) : (
          <div className="space-y-6">
            <div className="flex items-center justify-between flex-wrap gap-3">
              <div className="flex items-center gap-2">
                <Phone className="w-5 h-5 text-green-600" />
                <h2 className="text-lg font-semibold text-foreground">On-Call Schedule</h2>
              </div>
              <div className="flex items-center gap-2 flex-wrap">
                <div className="flex border border-border rounded-lg overflow-hidden">
                  <button
                    type="button"
                    onClick={() => setViewMode("calendar")}
                    className={`px-3 py-1.5 text-xs ${viewMode === "calendar" ? "bg-primary text-white" : "bg-card text-muted-foreground hover:bg-background"}`}
                  >
                    Calendar
                  </button>
                  <button
                    type="button"
                    onClick={() => setViewMode("list")}
                    className={`px-3 py-1.5 text-xs ${viewMode === "list" ? "bg-primary text-white" : "bg-card text-muted-foreground hover:bg-background"}`}
                  >
                    List
                  </button>
                </div>
                <button
                  type="button"
                  onClick={() => setShowSwapForm((v) => !v)}
                  className="inline-flex items-center gap-1.5 px-4 py-2 bg-primary text-white text-sm font-medium rounded-lg hover:bg-primary-hover transition-colors"
                >
                  <ArrowRightLeft className="w-4 h-4" /> Request Swap
                </button>
              </div>
            </div>

            {showSwapForm && facility?.id ? (
              <div className="rounded-lg border border-border bg-card p-4 space-y-3 text-sm">
                <h3 className="font-medium text-foreground">New swap request</h3>
                <div className="grid grid-cols-1 gap-3">
                  <label className="block">
                    <span className="text-xs text-muted-foreground">Shift to be covered</span>
                    <select
                      className="mt-1 w-full border border-border rounded-md px-2 py-1.5"
                      value={swapForm.requesting_shift_id}
                      onChange={(e) =>
                        setSwapForm((f) => ({ ...f, requesting_shift_id: e.target.value, requested_profile_id: "" }))
                      }
                    >
                      <option value="">Select a rostered shift…</option>
                      {rosteredShifts.map((r) => (
                        <option key={r.shiftId} value={r.shiftId}>
                          {r.label} · {r.personLabel}
                        </option>
                      ))}
                    </select>
                  </label>
                  <label className="block">
                    <span className="text-xs text-muted-foreground">Ask which colleague to cover it</span>
                    <select
                      className="mt-1 w-full border border-border rounded-md px-2 py-1.5"
                      value={swapForm.requested_profile_id}
                      onChange={(e) => setSwapForm((f) => ({ ...f, requested_profile_id: e.target.value }))}
                      disabled={!swapForm.requesting_shift_id}
                    >
                      <option value="">Select a colleague on this week&apos;s rota…</option>
                      {[...new Map(
                        rosteredShifts
                          .filter((r) => r.profileId && r.profileId !== selectedShiftOwner)
                          .map((r) => [r.profileId, r])
                      ).values()].map((r) => (
                        <option key={r.profileId!} value={r.profileId!}>
                          {r.personLabel}
                        </option>
                      ))}
                    </select>
                    <span className="mt-1 block text-[11px] text-muted-foreground">
                      The person already on that shift is not offered — a swap with oneself changes nothing.
                    </span>
                  </label>
                  <label className="block">
                    <span className="text-xs text-muted-foreground">Reason (optional)</span>
                    <input
                      className="mt-1 w-full border border-border rounded-md px-2 py-1.5"
                      value={swapForm.reason}
                      onChange={(e) => setSwapForm((f) => ({ ...f, reason: e.target.value }))}
                    />
                  </label>
                </div>
                <div className="flex gap-2">
                  <button
                    type="button"
                    onClick={() => setShowSwapForm(false)}
                    className="px-3 py-1.5 text-xs border border-border rounded-md hover:bg-background"
                  >
                    Cancel
                  </button>
                  <button
                    type="button"
                    disabled={createSwap.isPending}
                    onClick={() => void submitSwapRequest()}
                    className="px-3 py-1.5 text-xs bg-primary text-white rounded-md hover:bg-primary-hover disabled:opacity-50"
                  >
                    {createSwap.isPending ? "Submitting…" : "Submit request"}
                  </button>
                </div>
              </div>
            ) : null}

            {pendingSwaps.length > 0 && (
              <div className="bg-warning-soft border border-warning/35 rounded-lg p-4">
                <div className="flex items-center gap-2 mb-2">
                  <ArrowRightLeft className="w-4 h-4 text-amber-600" />
                  <h3 className="text-sm font-medium text-warning-foreground">
                    {pendingSwaps.length} Pending Swap Request{pendingSwaps.length > 1 ? "s" : ""}
                  </h3>
                </div>
                {pendingSwaps.map((sw) => {
                  const a = sw.attributes;
                  return (
                    <div key={sw.id} className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2 bg-card rounded-lg p-3 mt-2">
                      <div className="text-xs text-muted-foreground">
                        <span className="font-medium">{swapPartyLabel(a.requesting_display_name, a.requesting_workforce_profile_id)}</span> asks{" "}
                        <span className="font-medium">{swapPartyLabel(a.requested_display_name, a.requested_workforce_profile_id)}</span> to cover their shift
                        {a.reason ? <> — {a.reason}</> : null}
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
                          className="px-2.5 py-1 text-xs text-red-600 border border-danger/28 rounded hover:bg-danger-soft transition-colors disabled:opacity-50"
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
                  <p className="text-sm text-muted-foreground">No assignment dates in this week.</p>
                ) : (
                  dates.map((date) => {
                    const isSelected = date === selectedDate;
                    return (
                      <button
                        key={date}
                        type="button"
                        onClick={() => setSelectedDate(date)}
                        className={`flex flex-col items-center px-4 py-2 rounded-lg border transition-colors shrink-0 ${
                          isSelected ? "border-primary/25 bg-primary-soft text-primary" : "border-border bg-card text-muted-foreground hover:bg-background"
                        }`}
                      >
                        <span className="text-xs font-medium">{dayShortLabel(date)}</span>
                        <span className="text-sm font-bold">{date.slice(8)}</span>
                        <span className="text-[10px] text-muted-foreground">
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
                <div className="bg-card rounded-lg border border-border p-12 text-center">
                  <Phone className="w-10 h-10 text-muted-foreground mx-auto mb-3" />
                  <p className="text-muted-foreground text-sm">No on-call assignments for this date</p>
                </div>
              ) : (
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  {todaySchedule.map((oc) => renderAssignmentCard(oc))}
                </div>
              ))}

            {viewMode === "list" && (
              <div className="bg-card rounded-lg border border-border overflow-hidden">
                <div className="px-5 py-4 border-b border-border">
                  <h3 className="font-medium text-foreground">All assignments this week</h3>
                </div>
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-border bg-background">
                      <th className="text-left px-4 py-3 font-medium text-muted-foreground">Date</th>
                      <th className="text-left px-4 py-3 font-medium text-muted-foreground">Specialty</th>
                      <th className="text-left px-4 py-3 font-medium text-muted-foreground">Primary</th>
                      <th className="text-left px-4 py-3 font-medium text-muted-foreground">Backup</th>
                      <th className="text-left px-4 py-3 font-medium text-muted-foreground">Shift</th>
                    </tr>
                  </thead>
                  <tbody>
                    {schedule.length === 0 ? (
                      <tr>
                        <td colSpan={5} className="px-4 py-8 text-center text-muted-foreground">
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
                            <tr key={oc.id} className="border-b border-border hover:bg-background">
                              <td className="px-4 py-3 text-foreground">{o.assignment_date}</td>
                              <td className="px-4 py-3 text-foreground">{o.specialty}</td>
                              <td className="px-4 py-3 text-foreground">
                                {staffLabel(o.primary_display_name, o.primary_staff_reference) ?? "—"}
                              </td>
                              <td className="px-4 py-3 text-foreground">
                                {o.backup_workforce_profile_id ? (
                                  staffLabel(o.backup_display_name, o.backup_staff_reference) ?? "—"
                                ) : (
                                  <span className="text-amber-700">No second on call</span>
                                )}
                              </td>
                              <td className="px-4 py-3 text-muted-foreground">{toShiftLabel(o.shift_kind ?? "")}</td>
                            </tr>
                          );
                        })
                    )}
                  </tbody>
                </table>
              </div>
            )}

            <div className="bg-card rounded-lg border border-border overflow-hidden">
              <div className="px-5 py-4 border-b border-border flex items-center gap-2">
                <ArrowRightLeft className="w-4 h-4 text-muted-foreground" />
                <h3 className="font-medium text-foreground">Swap Requests</h3>
              </div>
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-border bg-background">
                    <th className="text-left px-4 py-3 font-medium text-muted-foreground">Requestor</th>
                    <th className="text-left px-4 py-3 font-medium text-muted-foreground">Original Date</th>
                    <th className="text-left px-4 py-3 font-medium text-muted-foreground">Swap With</th>
                    <th className="text-left px-4 py-3 font-medium text-muted-foreground">Swap Date</th>
                    <th className="text-left px-4 py-3 font-medium text-muted-foreground">Status</th>
                  </tr>
                </thead>
                <tbody>
                  {swaps.length === 0 ? (
                    <tr>
                      <td colSpan={5} className="px-4 py-8 text-center text-muted-foreground">
                        No swap requests yet.
                      </td>
                    </tr>
                  ) : (
                    swaps.map((sw) => {
                      const a = sw.attributes;
                      const ui = mapUiSwapStatus(a.status);
                      return (
                        <tr key={sw.id} className="border-b border-border hover:bg-background transition-colors">
                          <td className="px-4 py-3 text-foreground">
                            {swapPartyLabel(a.requesting_display_name, a.requesting_workforce_profile_id)}
                          </td>
                          <td className="px-4 py-3 text-muted-foreground">{a.created_at?.slice(0, 10) ?? "—"}</td>
                          <td className="px-4 py-3 text-foreground">
                            {swapPartyLabel(a.requested_display_name, a.requested_workforce_profile_id)}
                          </td>
                          <td className="px-4 py-3 text-muted-foreground">{a.reason ?? "—"}</td>
                          <td className="px-4 py-3">
                            <span
                              className={`px-2 py-0.5 rounded-full text-xs font-medium ${
                                ui === "Approved"
                                  ? "bg-green-100 text-green-700"
                                  : ui === "Pending"
                                    ? "bg-amber-100 text-warning-foreground"
                                    : "bg-red-100 text-danger"
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
  const primaryLabel = staffLabel(o.primary_display_name, o.primary_staff_reference);
  const backupLabel = staffLabel(o.backup_display_name, o.backup_staff_reference);
  return (
    <div key={oc.id} className="bg-card rounded-lg border border-border p-5">
      <div className="flex items-center justify-between mb-4">
        <span
          className={`px-2.5 py-1 rounded-full text-xs font-medium ${SPECIALTY_COLORS[o.specialty] || "bg-neutral-100 text-foreground"}`}
        >
          {o.specialty}
        </span>
        <span className="text-xs text-muted-foreground flex items-center gap-1">
          <Clock className="w-3 h-3" /> {toShiftLabel(o.shift_kind ?? "")}
        </span>
      </div>
      <div className="mb-3">
        <p className="text-[10px] font-medium text-muted-foreground uppercase tracking-wide mb-1">Primary</p>
        <div className="flex items-center justify-between gap-2 flex-wrap">
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-full bg-primary-soft text-primary flex items-center justify-center text-xs font-semibold">
              {(primaryLabel ?? "—").slice(0, 2).toUpperCase()}
            </div>
            <div className="flex flex-col">
              <span className="text-sm font-medium text-foreground">
                {primaryLabel ?? "Not rostered"}
              </span>
              {o.primary_profession ? (
                <span className="text-[11px] text-muted-foreground">{o.primary_profession}</span>
              ) : null}
            </div>
          </div>
          {primaryPhone ? (
            <a href={`tel:${primaryPhone}`} className="inline-flex items-center gap-1 text-xs text-primary hover:text-primary">
              <Phone className="w-3 h-3" /> {primaryPhone}
            </a>
          ) : (
            <span className="text-xs text-muted-foreground">No phone on file</span>
          )}
        </div>
      </div>
      <div className="pt-3 border-t border-border">
        <p className="text-[10px] font-medium text-muted-foreground uppercase tracking-wide mb-1">Backup / Escalation</p>
        <div className="flex items-center justify-between gap-2 flex-wrap">
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-full bg-neutral-100 text-muted-foreground flex items-center justify-center text-xs font-semibold">
              {(backupLabel ?? "—").slice(0, 2).toUpperCase()}
            </div>
            {/* "Nobody is second on call tonight" is the most useful thing this card can say, and
                resolving names must never turn an uncovered night into a covered-looking one. */}
            <span className="text-sm text-foreground">
              {o.backup_workforce_profile_id ? (
                backupLabel ?? "Rostered — name unavailable"
              ) : (
                <span className="text-amber-700">No second on call</span>
              )}
            </span>
          </div>
          {backupPhone ? (
            <a href={`tel:${backupPhone}`} className="inline-flex items-center gap-1 text-xs text-muted-foreground hover:text-primary">
              <Phone className="w-3 h-3" /> {backupPhone}
            </a>
          ) : (
            <span className="text-xs text-muted-foreground">No phone on file</span>
          )}
        </div>
      </div>
    </div>
  );
}
