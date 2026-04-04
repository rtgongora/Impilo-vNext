"use client";

/**
 * Scheduling — Provider appointment management.
 * Route: /scheduling | pageTitle: "Scheduling"
 *
 * Lovable-aligned: appointment list with status badges, create form
 * with type/date/time/patient fields, confirm/cancel actions.
 * Uses the real SchedulingController BFF API with TUSO booking bridge.
 */

import { useState, useEffect, useRef } from "react";
import Link from "next/link";
import {
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
  AlertCircle,
  Search,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { usePatients, type PatientResource } from "@/hooks/queries/usePatients";
import { apiClient, type ApiResponse } from "@/lib/api-client";

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
  SCHEDULED: { label: "Scheduled", className: "bg-blue-100 text-blue-700" },
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

export default function SchedulingPage() {
  const { user } = useAuthStore();
  const facility = useFacilityStore((s) => s.facility);
  const [activeTab, setActiveTab] = useState<TabFilter>("all");
  const [appointments, setAppointments] = useState<AppointmentResource[]>([]);
  const [isLoading, setIsLoading] = useState(true);
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

  const [form, setForm] = useState({
    appointment_type: "OPD",
    date: "",
    time: "09:00",
    reason: "",
    notes: "",
  });

  useEffect(() => {
    fetchAppointments();
  }, [facility?.id, activeTab]);

  async function fetchAppointments() {
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
  }

  async function handleCreate() {
    if (!facility || !selectedPatient || !form.date) return;
    setCreating(true);
    try {
      const scheduledAt = `${form.date}T${form.time}:00Z`;
      const endAt = new Date(new Date(scheduledAt).getTime() + 30 * 60000).toISOString();
      await apiClient.post("/internal/v1/appointments", {
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
      await apiClient.post(`/internal/v1/appointments/${id}/confirm`);
      fetchAppointments();
    } catch { /* handled */ } finally { setActionPending(null); }
  }

  async function handleCancel(id: string) {
    setActionPending(id);
    try {
      await apiClient.post(`/internal/v1/appointments/${id}/cancel`, { reason: "Cancelled by provider" });
      fetchAppointments();
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

        {/* Tabs + Create */}
        <div className="flex items-center justify-between mb-5">
          <div className="flex gap-1 border-b border-gray-200">
            {tabs.map((tab) => (
              <button
                key={tab.key}
                onClick={() => setActiveTab(tab.key)}
                className={`px-4 py-2.5 text-sm font-medium border-b-2 transition-colors ${
                  activeTab === tab.key
                    ? "border-blue-600 text-blue-600"
                    : "border-transparent text-gray-500 hover:text-gray-700"
                }`}
              >
                {tab.label}
              </button>
            ))}
          </div>
          <button
            onClick={() => setShowCreate((v) => !v)}
            className="inline-flex items-center gap-1.5 px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 transition-colors"
          >
            <Plus className="w-4 h-4" />
            New Appointment
          </button>
        </div>

        {/* Create Form */}
        {showCreate && (
          <div className="bg-white rounded-lg border border-gray-200 p-5 mb-5 space-y-4">
            <h3 className="text-sm font-medium text-gray-900 flex items-center gap-2">
              <CalendarDays className="w-4 h-4 text-blue-500" />
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
                    className="w-full pl-10 pr-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                  />
                </div>
                {selectedPatient && (
                  <div className="mt-1 flex items-center gap-2 text-xs text-green-700 bg-green-50 px-2 py-1 rounded">
                    <User className="w-3 h-3" />
                    {selectedPatient.attributes.displayName} — CPID: {selectedPatient.attributes.cpid}
                  </div>
                )}
                {showPatientDropdown && patients.length > 0 && !selectedPatient && (
                  <div className="absolute z-20 mt-1 w-full bg-white border border-gray-200 rounded-lg shadow-lg max-h-48 overflow-y-auto">
                    {patients.map((p) => (
                      <button
                        key={p.id}
                        onClick={() => handleSelectPatient(p)}
                        className="w-full flex items-center gap-2 px-3 py-2 text-left hover:bg-blue-50 transition-colors"
                      >
                        <User className="w-4 h-4 text-gray-400" />
                        <div>
                          <p className="text-sm font-medium text-gray-900">{p.attributes.displayName}</p>
                          <p className="text-xs text-gray-500">CPID: {p.attributes.cpid} · {p.attributes.gender} · DOB: {p.attributes.dateOfBirth}</p>
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
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
                  {APPOINTMENT_TYPES.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
                </select>
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-600 mb-1">Date *</label>
                <input type="date" required value={form.date} onChange={(e) => setForm({ ...form, date: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-600 mb-1">Resource (optional)</label>
                <select value={selectedResourceId} onChange={(e) => setSelectedResourceId(e.target.value)}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
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
                            ? "bg-blue-600 text-white border-blue-600"
                            : slot.available
                              ? "bg-white text-gray-700 border-gray-200 hover:bg-blue-50 hover:border-blue-300"
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
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
                  {TIME_SLOTS.map((t) => <option key={t} value={t}>{t}</option>)}
                </select>
              </div>
            )}

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-xs font-medium text-gray-600 mb-1">Reason</label>
                <input type="text" value={form.reason} onChange={(e) => setForm({ ...form, reason: e.target.value })}
                  placeholder="e.g. Follow-up diabetes check"
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-600 mb-1">Notes</label>
                <input type="text" value={form.notes} onChange={(e) => setForm({ ...form, notes: e.target.value })}
                  placeholder="Additional scheduling notes"
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
              </div>
            </div>
            <div className="flex gap-3">
              <button onClick={() => setShowCreate(false)}
                className="flex-1 py-2 bg-gray-100 text-gray-700 text-sm font-medium rounded-lg hover:bg-gray-200 transition-colors">
                Cancel
              </button>
              <button onClick={handleCreate} disabled={creating || !selectedPatient || !form.date}
                className="flex-1 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 disabled:opacity-50 flex items-center justify-center gap-2 transition-colors">
                {creating ? <><Loader2 className="w-4 h-4 animate-spin" /> Scheduling...</> : <><Save className="w-4 h-4" /> Schedule</>}
              </button>
            </div>
          </div>
        )}

        {/* Appointment List */}
        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
          </div>
        ) : appointments.length === 0 ? (
          <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
            <CalendarDays className="w-10 h-10 text-gray-300 mx-auto mb-3" />
            <p className="text-gray-400 text-sm">No appointments found</p>
          </div>
        ) : (
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
                      <div className="w-10 h-10 rounded-lg bg-blue-50 flex items-center justify-center">
                        {a.appointment_type === "TELECONSULT" ? (
                          <Video className="w-5 h-5 text-blue-600" />
                        ) : (
                          <CalendarDays className="w-5 h-5 text-blue-600" />
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
                            <Link href={`/ehr/${a.patient_id}`} className="flex items-center gap-1 text-blue-600 hover:text-blue-800">
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
                      </div>
                    </div>

                    {/* Actions */}
                    {isActionable && (
                      <div className="flex gap-2 shrink-0">
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
        )}
      </PageShell>
    </AppLayout>
  );
}
