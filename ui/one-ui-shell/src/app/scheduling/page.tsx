"use client";

/**
 * Scheduling — Provider appointment management.
 * Route: /scheduling | pageTitle: "Scheduling"
 *
 * Lovable-aligned: appointment list with status badges, create form
 * with type/date/time/patient fields, confirm/cancel actions.
 * Uses the real SchedulingController BFF API with TUSO booking bridge.
 */

import { useState, useEffect, useRef, useCallback } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import {
  ArrowLeft,
  CalendarDays,
  Plus,
  Loader2,
  Clock,
  CheckCircle2,
  XCircle,
  User,
  Building,
  Video,
  Save,
  Search,
  ChevronLeft,
  ChevronRight,
  List,
  CalendarRange,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { FacilityWorkClusterRibbon } from "@/components/experience/FacilityWorkClusterRibbon";
import { OrganizationPlaneContextBar } from "@/components/experience/OrganizationPlaneContextBar";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import {
  useCancelAppointment,
  useCheckInAppointment,
  useConfirmAppointment,
  useCreateAppointment,
} from "@/hooks/queries/useAppointments";
import { usePatients, type PatientResource } from "@/hooks/queries/usePatients";
import { apiClient, type ApiResponse } from "@/lib/api-client";
import { usePrivacyDisplayStore } from "@/hooks/usePrivacyDisplayStore";
import { maskName, maskDob, displayCpid } from "@/lib/pii-mask";
import { AppointmentMessagePanel } from "@/components/scheduling/AppointmentMessagePanel";

interface AppointmentResource {
  id: string;
  type: string;
  attributes: {
    patient_id: string;
    facility_id: string;
    provider_id: string | null;
    provider_name: string | null;
    appointment_type: string;
    status: string;
    scheduled_at: string;
    end_at: string | null;
    reason: string | null;
    notes: string | null;
    tuso_booking_id: string | null;
    created_at: string;
  };
}

const STATUS_BADGE: Record<string, { label: string; className: string }> = {
  SCHEDULED: { label: "Scheduled", className: "bg-impilo-100 text-impilo-600" },
  CONFIRMED: { label: "Confirmed", className: "bg-green-100 text-green-700" },
  CANCELLED: { label: "Cancelled", className: "bg-gray-100 text-gray-600" },
  COMPLETED: { label: "Completed", className: "bg-purple-100 text-purple-700" },
};

const APPOINTMENT_TYPES = [
  { value: "OPD", label: "Outpatient Visit" },
  { value: "FOLLOW_UP", label: "Follow-up" },
  { value: "PROCEDURE", label: "Procedure" },
  { value: "CONSULTATION", label: "Consultation" },
  { value: "TELECONSULT", label: "Teleconsult" },
  { value: "LAB", label: "Lab Work" },
  { value: "IMAGING", label: "Imaging" },
];

const TIME_SLOTS = [
  "08:00", "08:30", "09:00", "09:30", "10:00", "10:30", "11:00", "11:30",
  "12:00", "12:30", "13:00", "13:30", "14:00", "14:30", "15:00", "15:30",
  "16:00", "16:30",
];

type TabFilter = "all" | "SCHEDULED" | "CONFIRMED" | "today";
type ViewMode = "list" | "calendar";

function getWeekDays(weekStart: Date): Date[] {
  return Array.from({ length: 7 }, (_, i) => {
    const d = new Date(weekStart);
    d.setDate(d.getDate() + i);
    return d;
  });
}

function isSameDay(a: Date, b: Date): boolean {
  return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();
}

function getMonday(d: Date): Date {
  const day = d.getDay();
  const diff = d.getDate() - day + (day === 0 ? -6 : 1);
  const monday = new Date(d);
  monday.setDate(diff);
  monday.setHours(0, 0, 0, 0);
  return monday;
}

export default function SchedulingPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const fromOrgAdmin = searchParams.get("from") === "organization-admin";
  const { user } = useAuthStore();

  useEffect(() => {
    if (user?.actorType === "CITIZEN") {
      router.replace("/home/appointments");
    }
  }, [router, user?.actorType]);
  const facility = useFacilityStore((s) => s.facility);
  const createAppointment = useCreateAppointment();
  const confirmAppointment = useConfirmAppointment();
  const cancelAppointment = useCancelAppointment();
  const checkInAppointment = useCheckInAppointment({
    onCheckedIn: (meta) => {
      const { patient_id: patientId, journey_id: journeyId, core_transaction_id: transactionId } = meta;
      if (patientId && journeyId && transactionId) {
        const params = new URLSearchParams({ journey_id: journeyId, transaction_id: transactionId });
        router.push(`/ehr/${patientId}/encounters?${params.toString()}`);
      }
    },
  });
  const [viewMode, setViewMode] = useState<ViewMode>("list");
  const [activeTab, setActiveTab] = useState<TabFilter>("all");
  const [appointments, setAppointments] = useState<AppointmentResource[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [weekStart, setWeekStart] = useState<Date>(getMonday(new Date()));
  const [showCreate, setShowCreate] = useState(false);
  const [creating, setCreating] = useState(false);
  const [actionPending, setActionPending] = useState<string | null>(null);

  // Patient search state
  const [patientSearch, setPatientSearch] = useState("");
  const [debouncedSearch, setDebouncedSearch] = useState("");
  const [selectedPatient, setSelectedPatient] = useState<PatientResource | null>(null);
  const [showPatientDropdown, setShowPatientDropdown] = useState(false);
  const searchTimeout = useRef<NodeJS.Timeout | null>(null);

  const { data: searchResults } = usePatients(
    debouncedSearch.length >= 2 ? { search: debouncedSearch } : undefined
  );
  const patients = debouncedSearch.length >= 2 ? (searchResults?.data ?? []) : [];

  function handlePatientSearchChange(value: string) {
    setPatientSearch(value);
    setSelectedPatient(null);
    if (searchTimeout.current) clearTimeout(searchTimeout.current);
    searchTimeout.current = setTimeout(() => setDebouncedSearch(value), 300);
    setShowPatientDropdown(true);
  }

  function handleSelectPatient(patient: PatientResource) {
    setSelectedPatient(patient);
    setPatientSearch(patient.attributes.displayName);
    setShowPatientDropdown(false);
  }

  // Resource and availability state
  const [resources, setResources] = useState<Array<{ id: string; name: string; resourceType: string }>>([]);
  const [selectedResourceId, setSelectedResourceId] = useState("");
  const [availabilitySlots, setAvailabilitySlots] = useState<Array<{ time: string; available: boolean }>>([]);
  const [loadingAvailability, setLoadingAvailability] = useState(false);

  // Fetch facility resources when facility changes
  useEffect(() => {
    if (!facility?.id) return;
    apiClient.get<ApiResponse<Array<{ id: string; name: string; resourceType: string }>>>(
      `/internal/v1/appointments/resources?facility_id=${facility.id}`
    ).then((res) => {
      const data = res.data;
      if (Array.isArray(data)) setResources(data);
      else setResources([]);
    }).catch(() => setResources([]));
  }, [facility?.id]);

  const [form, setForm] = useState({
    appointment_type: "OPD",
    date: "",
    time: "09:00",
    reason: "",
    notes: "",
  });

  // Fetch availability when resource + date change
  useEffect(() => {
    if (!selectedResourceId || !form.date) {
      setAvailabilitySlots([]);
      return;
    }
    setLoadingAvailability(true);
    apiClient.get<ApiResponse<{ slots: Array<{ time: string; available: boolean }> }>>(
      `/internal/v1/appointments/availability?resource_id=${selectedResourceId}&date=${form.date}`
    ).then((res) => {
      setAvailabilitySlots((res.data as { slots: Array<{ time: string; available: boolean }> })?.slots ?? []);
    }).catch(() => setAvailabilitySlots([])).finally(() => setLoadingAvailability(false));
  }, [selectedResourceId, form.date]);

  const fetchAppointments = useCallback(async () => {
    setIsLoading(true);
    try {
      const params = new URLSearchParams();
      if (facility?.id) params.set("facility_id", facility.id);
      if (activeTab !== "all" && activeTab !== "today") params.set("status", activeTab);
      const res = await apiClient.get<ApiResponse<AppointmentResource[]>>(
        `/internal/v1/appointments?${params.toString()}`
      );
      let items = res.data ?? [];
      if (activeTab === "today") {
        const today = new Date().toISOString().split("T")[0];
        items = items.filter((a) => a.attributes.scheduled_at?.startsWith(today));
      }
      setAppointments(items);
    } catch {
      setAppointments([]);
    } finally {
      setIsLoading(false);
    }
  }, [activeTab, facility?.id]);

  useEffect(() => {
    void fetchAppointments();
  }, [fetchAppointments, viewMode, weekStart]);

  async function handleCreate() {
    if (!facility || !selectedPatient || !form.date) return;
    setCreating(true);
    try {
      const scheduledAt = `${form.date}T${form.time}:00Z`;
      const endAt = new Date(new Date(scheduledAt).getTime() + 30 * 60000).toISOString();
      await createAppointment.mutateAsync({
        patient_id: selectedPatient.id,
        facility_id: facility.id,
        provider_id: user?.id ?? null,
        provider_name: user?.displayName ?? user?.email ?? null,
        appointment_type: form.appointment_type,
        scheduled_at: scheduledAt,
        end_at: endAt,
        reason: form.reason || null,
        notes: form.notes || null,
        resource_id: selectedResourceId || null,
      });
      setForm({ appointment_type: "OPD", date: "", time: "09:00", reason: "", notes: "" });
      setSelectedPatient(null);
      setPatientSearch("");
      setSelectedResourceId("");
      setAvailabilitySlots([]);
      setShowCreate(false);
      fetchAppointments();
    } catch {
      // handled by UI
    } finally {
      setCreating(false);
    }
  }

  async function handleConfirm(id: string) {
    setActionPending(id);
    try {
      await confirmAppointment.mutateAsync(id);
      fetchAppointments();
    } catch { /* handled */ } finally { setActionPending(null); }
  }

  async function handleCancel(id: string) {
    setActionPending(id);
    try {
      await cancelAppointment.mutateAsync({ id, reason: "Cancelled by provider" });
      fetchAppointments();
    } catch { /* handled */ } finally { setActionPending(null); }
  }

  async function handleCheckIn(id: string, patientId?: string | null) {
    setActionPending(id);
    try {
      await checkInAppointment.mutateAsync(id);
      if (!patientId) {
        fetchAppointments();
      }
    } catch { /* handled */ } finally { setActionPending(null); }
  }

  const tabs: { key: TabFilter; label: string }[] = [
    { key: "all", label: "All" },
    { key: "today", label: "Today" },
    { key: "SCHEDULED", label: "Pending" },
    { key: "CONFIRMED", label: "Confirmed" },
  ];

  return (
    <AppLayout>
      <PageShell title="Scheduling" subtitle={facility ? `Appointments at ${facility.name}` : "Select a facility"}>
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

        <div className="mb-5 flex flex-wrap items-center gap-x-4 gap-y-2 text-sm text-slate-600">
          <span className="font-medium text-slate-700">Scheduling cluster</span>
          <Link
            href={fromOrgAdmin ? "/scheduling/roster?from=organization-admin" : "/scheduling/roster"}
            className="text-sky-700 hover:underline"
          >
            Roster
          </Link>
          <Link
            href={fromOrgAdmin ? "/scheduling/on-call?from=organization-admin" : "/scheduling/on-call"}
            className="text-sky-700 hover:underline"
          >
            On-call
          </Link>
          <Link
            href={fromOrgAdmin ? "/scheduling/noticeboard?from=organization-admin" : "/scheduling/noticeboard"}
            className="text-sky-700 hover:underline"
          >
            Noticeboard
          </Link>
          <Link href="/shift/active" className="text-sky-700 hover:underline">
            Active shift
          </Link>
        </div>

        {/* View Toggle + Create */}
        <div className="flex items-center justify-between mb-5">
          <div className="flex items-center gap-4">
            {/* View mode toggle */}
            <div className="flex bg-gray-100 rounded-lg p-0.5">
              <button onClick={() => setViewMode("list")}
                className={`px-3 py-1.5 text-xs font-medium rounded-md flex items-center gap-1.5 transition-colors ${
                  viewMode === "list" ? "bg-white text-gray-900 shadow-sm" : "text-gray-500 hover:text-gray-700"
                }`}>
                <List className="w-3.5 h-3.5" /> List
              </button>
              <button onClick={() => setViewMode("calendar")}
                className={`px-3 py-1.5 text-xs font-medium rounded-md flex items-center gap-1.5 transition-colors ${
                  viewMode === "calendar" ? "bg-white text-gray-900 shadow-sm" : "text-gray-500 hover:text-gray-700"
                }`}>
                <CalendarRange className="w-3.5 h-3.5" /> Calendar
              </button>
            </div>

            {/* List tabs (only shown in list mode) */}
            {viewMode === "list" && (
              <div className="flex gap-1 border-b border-gray-200">
                {tabs.map((tab) => (
                  <button
                    key={tab.key}
                    onClick={() => setActiveTab(tab.key)}
                    className={`px-4 py-2.5 text-sm font-medium border-b-2 transition-colors ${
                      activeTab === tab.key
                        ? "border-impilo-500 text-impilo-500"
                        : "border-transparent text-gray-500 hover:text-gray-700"
                    }`}
                  >
                    {tab.label}
                  </button>
                ))}
              </div>
            )}
          </div>
          <button
            onClick={() => setShowCreate((v) => !v)}
            className="inline-flex items-center gap-1.5 px-4 py-2 bg-impilo-500 text-white text-sm font-medium rounded-lg hover:bg-impilo-600 transition-colors"
          >
            <Plus className="w-4 h-4" />
            New Appointment
          </button>
        </div>

        {/* Create Form */}
        {showCreate && (
          <div className="bg-white rounded-lg border border-gray-200 p-5 mb-5 space-y-4">
            <h3 className="text-sm font-medium text-gray-900 flex items-center gap-2">
              <CalendarDays className="w-4 h-4 text-impilo-400" />
              Schedule Appointment
            </h3>
            <div className="grid grid-cols-2 gap-3">
              <div className="relative">
                <label className="block text-xs font-medium text-gray-600 mb-1">Patient *</label>
                <div className="relative">
                  <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
                  <input
                    type="text"
                    value={patientSearch}
                    onChange={(e) => handlePatientSearchChange(e.target.value)}
                    onFocus={() => patients.length > 0 && setShowPatientDropdown(true)}
                    placeholder="Search by name or CPID..."
                    className="w-full pl-10 pr-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400"
                  />
                </div>
                {selectedPatient && (
                  <div className="mt-1 flex items-center gap-2 text-xs text-green-700 bg-green-50 px-2 py-1 rounded">
                    <User className="w-3 h-3" />
                    {maskName(selectedPatient.attributes.displayName, usePrivacyDisplayStore.getState().level)} — CPID: {displayCpid(selectedPatient.attributes.cpid)}
                  </div>
                )}
                {showPatientDropdown && patients.length > 0 && !selectedPatient && (
                  <div className="absolute z-20 mt-1 w-full bg-white border border-gray-200 rounded-lg shadow-lg max-h-48 overflow-y-auto">
                    {patients.map((p) => (
                      <button
                        key={p.id}
                        onClick={() => handleSelectPatient(p)}
                        className="w-full flex items-center gap-2 px-3 py-2 text-left hover:bg-impilo-50 transition-colors"
                      >
                        <User className="w-4 h-4 text-gray-400" />
                        <div>
                          <p className="text-sm font-medium text-gray-900">{maskName(p.attributes.displayName, usePrivacyDisplayStore.getState().level)}</p>
                          <p className="text-xs text-gray-500">CPID: {displayCpid(p.attributes.cpid)} · {p.attributes.gender} · {maskDob(p.attributes.dateOfBirth, usePrivacyDisplayStore.getState().level)}</p>
                        </div>
                      </button>
                    ))}
                  </div>
                )}
                {showPatientDropdown && debouncedSearch.length >= 2 && patients.length === 0 && !selectedPatient && (
                  <div className="absolute z-20 mt-1 w-full bg-white border border-gray-200 rounded-lg shadow-lg p-3 text-center">
                    <p className="text-xs text-gray-500">No patients found for &ldquo;{debouncedSearch}&rdquo;</p>
                  </div>
                )}
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-600 mb-1">Type</label>
                <select value={form.appointment_type} onChange={(e) => setForm({ ...form, appointment_type: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400">
                  {APPOINTMENT_TYPES.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
                </select>
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-600 mb-1">Date *</label>
                <input type="date" required value={form.date} onChange={(e) => setForm({ ...form, date: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400" />
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-600 mb-1">Resource (optional)</label>
                <select value={selectedResourceId} onChange={(e) => setSelectedResourceId(e.target.value)}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400">
                  <option value="">No specific resource</option>
                  {resources.map((r) => (
                    <option key={r.id} value={r.id}>{r.name} ({r.resourceType})</option>
                  ))}
                </select>
              </div>
            </div>

            {/* Availability Grid — shown when resource + date selected */}
            {selectedResourceId && form.date && (
              <div>
                <label className="block text-xs font-medium text-gray-600 mb-2">
                  Available Time Slots
                  {loadingAvailability && <Loader2 className="w-3 h-3 animate-spin inline ml-2" />}
                </label>
                {availabilitySlots.length > 0 ? (
                  <div className="grid grid-cols-6 gap-1.5">
                    {availabilitySlots.map((slot) => (
                      <button
                        key={slot.time}
                        type="button"
                        disabled={!slot.available}
                        onClick={() => setForm({ ...form, time: slot.time })}
                        className={`px-2 py-1.5 text-xs font-medium rounded border transition-colors ${
                          form.time === slot.time
                            ? "bg-impilo-500 text-white border-impilo-500"
                            : slot.available
                              ? "bg-white text-gray-700 border-gray-200 hover:bg-impilo-50 hover:border-impilo-200"
                              : "bg-gray-100 text-gray-400 border-gray-100 cursor-not-allowed line-through"
                        }`}
                      >
                        {slot.time}
                      </button>
                    ))}
                  </div>
                ) : !loadingAvailability ? (
                  <p className="text-xs text-gray-400">Select a date and resource to see availability</p>
                ) : null}
              </div>
            )}

            {/* Fallback time selector when no resource selected */}
            {!selectedResourceId && (
              <div>
                <label className="block text-xs font-medium text-gray-600 mb-1">Time</label>
                <select value={form.time} onChange={(e) => setForm({ ...form, time: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400">
                  {TIME_SLOTS.map((t) => <option key={t} value={t}>{t}</option>)}
                </select>
              </div>
            )}

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-xs font-medium text-gray-600 mb-1">Reason</label>
                <input type="text" value={form.reason} onChange={(e) => setForm({ ...form, reason: e.target.value })}
                  placeholder="e.g. Follow-up diabetes check"
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400" />
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-600 mb-1">Notes</label>
                <input type="text" value={form.notes} onChange={(e) => setForm({ ...form, notes: e.target.value })}
                  placeholder="Additional scheduling notes"
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400" />
              </div>
            </div>
            <div className="flex gap-3">
              <button onClick={() => setShowCreate(false)}
                className="flex-1 py-2 bg-gray-100 text-gray-700 text-sm font-medium rounded-lg hover:bg-gray-200 transition-colors">
                Cancel
              </button>
              <button onClick={handleCreate} disabled={creating || !selectedPatient || !form.date}
                className="flex-1 py-2 bg-impilo-500 text-white text-sm font-medium rounded-lg hover:bg-impilo-600 disabled:opacity-50 flex items-center justify-center gap-2 transition-colors">
                {creating ? <><Loader2 className="w-4 h-4 animate-spin" /> Scheduling...</> : <><Save className="w-4 h-4" /> Schedule</>}
              </button>
            </div>
          </div>
        )}

        {/* Calendar View */}
        {viewMode === "calendar" && (
          <div className="space-y-4">
            {/* Week Navigation */}
            <div className="flex items-center justify-between">
              <button
                onClick={() => { const d = new Date(weekStart); d.setDate(d.getDate() - 7); setWeekStart(d); }}
                className="px-3 py-1.5 text-sm text-gray-600 bg-gray-100 rounded-lg hover:bg-gray-200 transition-colors flex items-center gap-1"
              >
                <ChevronLeft className="w-4 h-4" /> Previous
              </button>
              <h3 className="text-sm font-medium text-gray-900">
                {weekStart.toLocaleDateString("en-ZA", { month: "short", day: "numeric" })} — {
                  (() => { const end = new Date(weekStart); end.setDate(end.getDate() + 6); return end.toLocaleDateString("en-ZA", { month: "short", day: "numeric", year: "numeric" }); })()
                }
              </h3>
              <button
                onClick={() => { const d = new Date(weekStart); d.setDate(d.getDate() + 7); setWeekStart(d); }}
                className="px-3 py-1.5 text-sm text-gray-600 bg-gray-100 rounded-lg hover:bg-gray-200 transition-colors flex items-center gap-1"
              >
                Next <ChevronRight className="w-4 h-4" />
              </button>
            </div>

            {/* Week Grid */}
            <div className="grid grid-cols-7 gap-3">
              {getWeekDays(weekStart).map((date) => {
                const dayAppts = appointments.filter((a) => {
                  if (!a.attributes.scheduled_at) return false;
                  return isSameDay(new Date(a.attributes.scheduled_at), date);
                });
                const isToday = isSameDay(date, new Date());

                return (
                  <div key={date.toISOString()} className={`bg-white rounded-lg border ${isToday ? "border-impilo-400 ring-2 ring-blue-100" : "border-gray-200"}`}>
                    <div className={`px-3 py-2 border-b ${isToday ? "bg-impilo-50" : "bg-gray-50"}`}>
                      <div className={`text-xs font-medium ${isToday ? "text-impilo-600" : "text-gray-600"}`}>
                        {date.toLocaleDateString("en-ZA", { weekday: "short" })}
                      </div>
                      <div className={`text-lg font-semibold ${isToday ? "text-impilo-600" : "text-gray-900"}`}>
                        {date.getDate()}
                      </div>
                    </div>
                    <div className="p-2 min-h-[200px] max-h-[350px] overflow-y-auto space-y-1.5">
                      {dayAppts.length === 0 ? (
                        <p className="text-[10px] text-gray-400 text-center py-4">No appointments</p>
                      ) : (
                        dayAppts.map((appt) => {
                          const a = appt.attributes;
                          const status = STATUS_BADGE[a.status] ?? { label: a.status, className: "bg-gray-100 text-gray-600" };
                          const time = a.scheduled_at ? new Date(a.scheduled_at).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" }) : "";
                          const statusBorderColor =
                            a.status === "SCHEDULED" ? "border-l-blue-500" :
                            a.status === "CONFIRMED" ? "border-l-green-500" :
                            a.status === "CANCELLED" ? "border-l-gray-400" :
                            "border-l-purple-500";

                          return (
                            <div key={appt.id} className={`p-2 rounded border border-l-4 ${statusBorderColor} bg-white hover:bg-gray-50 transition-colors`}>
                              <div className="flex items-center gap-1 mb-0.5">
                                <Clock className="w-3 h-3 text-gray-400" />
                                <span className="text-xs font-medium text-gray-900">{time}</span>
                              </div>
                              <p className="text-xs text-gray-700 truncate">
                                {APPOINTMENT_TYPES.find((t) => t.value === a.appointment_type)?.label ?? a.appointment_type}
                              </p>
                              {a.provider_name && (
                                <p className="text-[10px] text-gray-500 truncate">{a.provider_name}</p>
                              )}
                              <span className={`inline-block mt-1 px-1.5 py-0.5 text-[10px] font-medium rounded-full ${status.className}`}>
                                {status.label}
                              </span>
                            </div>
                          );
                        })
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        )}

        {/* Appointment List (list mode only) */}
        {viewMode === "list" && isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
          </div>
        ) : viewMode === "list" && appointments.length === 0 ? (
          <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
            <CalendarDays className="w-10 h-10 text-gray-300 mx-auto mb-3" />
            <p className="text-gray-400 text-sm">No appointments found</p>
          </div>
        ) : viewMode === "list" ? (
          <div className="space-y-3">
            {appointments.map((appt) => {
              const a = appt.attributes;
              const status = STATUS_BADGE[a.status] ?? { label: a.status, className: "bg-gray-100 text-gray-600" };
              const isActionable = a.status === "SCHEDULED" || a.status === "CONFIRMED";
              const scheduledDate = a.scheduled_at ? new Date(a.scheduled_at) : null;

              return (
                <div key={appt.id} className="bg-white rounded-lg border border-gray-200 p-4">
                  <div className="flex items-start justify-between">
                    <div className="flex items-start gap-3">
                      <div className="w-10 h-10 rounded-lg bg-impilo-50 flex items-center justify-center">
                        {a.appointment_type === "TELECONSULT" ? (
                          <Video className="w-5 h-5 text-impilo-500" />
                        ) : (
                          <CalendarDays className="w-5 h-5 text-impilo-500" />
                        )}
                      </div>
                      <div>
                        <div className="flex items-center gap-2 mb-1">
                          <span className="text-sm font-medium text-gray-900">
                            {APPOINTMENT_TYPES.find((t) => t.value === a.appointment_type)?.label ?? a.appointment_type}
                          </span>
                          <span className={`px-2 py-0.5 text-xs font-medium rounded-full ${status.className}`}>
                            {status.label}
                          </span>
                          {a.tuso_booking_id && (
                            <span className="px-2 py-0.5 text-xs font-medium rounded-full bg-teal-100 text-teal-700">
                              Resource booked
                            </span>
                          )}
                        </div>
                        <div className="flex items-center gap-3 text-xs text-gray-500">
                          {scheduledDate && (
                            <span className="flex items-center gap-1">
                              <Clock className="w-3 h-3" />
                              {scheduledDate.toLocaleDateString()} {scheduledDate.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
                            </span>
                          )}
                          {a.patient_id && (
                            <Link href={`/ehr/${a.patient_id}`} className="flex items-center gap-1 text-impilo-500 hover:text-impilo-700">
                              <User className="w-3 h-3" />
                              Patient
                            </Link>
                          )}
                          {a.provider_name && (
                            <span className="flex items-center gap-1">
                              <Building className="w-3 h-3" />
                              {a.provider_name}
                            </span>
                          )}
                        </div>
                        {a.reason && (
                          <p className="text-xs text-gray-500 mt-1">{a.reason}</p>
                        )}
                        {isActionable && (
                          <AppointmentMessagePanel
                            appointmentId={appt.id}
                            patientLabel={a.patient_id ? `CPID ${displayCpid(a.patient_id)}` : undefined}
                          />
                        )}
                      </div>
                    </div>

                    {/* Actions */}
                    {isActionable && (
                      <div className="flex gap-2 shrink-0">
                        {(a.status === "SCHEDULED" || a.status === "CONFIRMED") && (
                          <button
                            onClick={() => handleCheckIn(appt.id, a.patient_id)}
                            disabled={actionPending === appt.id}
                            className="px-3 py-1.5 bg-emerald-100 text-emerald-700 text-xs font-medium rounded-lg hover:bg-emerald-200 disabled:opacity-50 transition-colors flex items-center gap-1"
                          >
                            <CheckCircle2 className="w-3 h-3" /> Check in
                          </button>
                        )}
                        {a.status === "SCHEDULED" && (
                          <button
                            onClick={() => handleConfirm(appt.id)}
                            disabled={actionPending === appt.id}
                            className="px-3 py-1.5 bg-green-100 text-green-700 text-xs font-medium rounded-lg hover:bg-green-200 disabled:opacity-50 transition-colors flex items-center gap-1"
                          >
                            <CheckCircle2 className="w-3 h-3" /> Confirm
                          </button>
                        )}
                        <button
                          onClick={() => handleCancel(appt.id)}
                          disabled={actionPending === appt.id}
                          className="px-3 py-1.5 bg-red-50 text-red-600 text-xs font-medium rounded-lg hover:bg-red-100 disabled:opacity-50 transition-colors flex items-center gap-1"
                        >
                          <XCircle className="w-3 h-3" /> Cancel
                        </button>
                      </div>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        ) : null}
      </PageShell>
    </AppLayout>
  );
}
