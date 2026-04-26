import type { PatientResource } from "@/hooks/queries/usePatients";
import type { QueueEntryResource } from "@/hooks/queries/useQueue";
import { usePrivacyDisplayStore } from "@/hooks/usePrivacyDisplayStore";
import { maskName, maskDob, displayCpid } from "@/lib/pii-mask";

export const QUEUE_PRIORITY_STYLES: Record<string, { label: string; className: string }> = {
  EMERGENCY: { label: "Emergency", className: "bg-red-100 text-red-700" },
  URGENT: { label: "Urgent", className: "bg-orange-100 text-orange-700" },
  NORMAL: { label: "Standard", className: "bg-amber-100 text-amber-700" },
  LOW: { label: "Low", className: "bg-green-100 text-green-700" },
  "1": { label: "Emergency", className: "bg-red-100 text-red-700" },
  "2": { label: "Urgent", className: "bg-orange-100 text-orange-700" },
  "3": { label: "Standard", className: "bg-amber-100 text-amber-700" },
  "4": { label: "Non-urgent", className: "bg-green-100 text-green-700" },
  "5": { label: "Routine", className: "bg-slate-100 text-slate-700" },
};

export const QUEUE_TRIAGE_STYLES: Record<string, string> = {
  RED: "bg-red-500 text-white",
  ORANGE: "bg-orange-500 text-white",
  YELLOW: "bg-yellow-400 text-black",
  GREEN: "bg-green-500 text-white",
  BLUE: "bg-impilo-500 text-white",
};

export const QUEUE_STATUS_STYLES: Record<string, string> = {
  WAITING: "bg-yellow-100 text-yellow-700",
  CALLED: "bg-impilo-100 text-impilo-600",
  IN_PROGRESS: "bg-green-100 text-green-700",
  IN_SERVICE: "bg-green-100 text-green-700",
  SEEN: "bg-green-100 text-green-700",
  COMPLETED: "bg-slate-100 text-slate-600",
  PAUSED: "bg-amber-100 text-amber-700",
  NO_SHOW: "bg-red-100 text-red-700",
  TRANSFERRED: "bg-purple-100 text-purple-700",
  CANCELLED: "bg-slate-100 text-slate-500",
  SCHEDULED: "bg-impilo-100 text-impilo-600",
  CONFIRMED: "bg-green-100 text-green-700",
};

type QueueEntryAttributes = QueueEntryResource["attributes"] & Record<string, unknown>;

function readString(source: Record<string, unknown>, keys: string[]) {
  for (const key of keys) {
    const value = source[key];
    if (typeof value === "string" && value.trim()) return value;
  }
  return "";
}

function readNumber(source: Record<string, unknown>, keys: string[]) {
  for (const key of keys) {
    const value = source[key];
    if (typeof value === "number" && Number.isFinite(value)) return value;
    if (typeof value === "string" && value.trim() && !Number.isNaN(Number(value))) {
      return Number(value);
    }
  }
  return null;
}

export function getQueuePatientId(entry: QueueEntryResource) {
  return readString(entry.attributes as QueueEntryAttributes, ["patientId", "patient_id"]);
}

export function getQueuePatientName(entry: QueueEntryResource) {
  const attrs = entry.attributes as QueueEntryAttributes;
  return (
    readString(attrs, ["patientName", "patient_name", "displayName", "display_name"]) ||
    getQueuePatientId(entry) ||
    "Unknown patient"
  );
}

export function getQueueQueuedAt(entry: QueueEntryResource) {
  return readString(entry.attributes as QueueEntryAttributes, [
    "queuedAt",
    "queued_at",
    "arrivalTime",
    "arrival_time",
    "createdAt",
    "created_at",
  ]);
}

export function getQueueStatus(entry: QueueEntryResource) {
  return readString(entry.attributes as QueueEntryAttributes, ["status"]) || "WAITING";
}

export function getQueuePriority(entry: QueueEntryResource) {
  const attrs = entry.attributes as QueueEntryAttributes;
  return readNumber(attrs, ["priority"]) ?? readString(attrs, ["priority"]);
}

export function getQueueTriageCategory(entry: QueueEntryResource) {
  return readString(entry.attributes as QueueEntryAttributes, ["triageCategory", "triage_category"]);
}

export function getQueueReason(entry: QueueEntryResource) {
  return readString(entry.attributes as QueueEntryAttributes, ["reason", "notes"]);
}

export function getQueuePriorityMeta(priority: unknown) {
  const key = String(priority ?? "");
  return QUEUE_PRIORITY_STYLES[key] ?? {
    label: key || "Unassigned",
    className: "bg-slate-100 text-slate-700",
  };
}

export function formatQueueWaitTime(value: string) {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "-";
  const diff = Date.now() - date.getTime();
  const mins = Math.max(Math.floor(diff / 60000), 0);
  if (mins < 60) return `${mins}m`;
  const hours = Math.floor(mins / 60);
  return `${hours}h ${mins % 60}m`;
}

export function formatQueueDateTime(value: string, options?: Intl.DateTimeFormatOptions) {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "-";
  return date.toLocaleString([], options);
}

export function getAppointmentPatientId(appointment: {
  attributes: Record<string, unknown>;
}) {
  return readString(appointment.attributes, ["patientId", "patient_id"]);
}

export function getAppointmentStatus(appointment: { attributes: Record<string, unknown> }) {
  return readString(appointment.attributes, ["appointmentStatus", "appointment_status", "status"]) || "SCHEDULED";
}

export function getAppointmentType(appointment: { attributes: Record<string, unknown> }) {
  return readString(appointment.attributes, ["appointmentType", "appointment_type"]) || "General";
}

export function getAppointmentTime(appointment: { attributes: Record<string, unknown> }) {
  return readString(appointment.attributes, ["appointmentTime", "appointment_time", "scheduledAt", "scheduled_at"]);
}

export function getAppointmentProvider(appointment: { attributes: Record<string, unknown> }) {
  return readString(appointment.attributes, ["providerName", "provider_name"]) || "Unassigned";
}

export function getAppointmentReason(appointment: { attributes: Record<string, unknown> }) {
  return readString(appointment.attributes, ["reason", "notes"]) || "No scheduling note added yet.";
}

export function getPatientDisplayName(patient: PatientResource) {
  const level = usePrivacyDisplayStore.getState().level;
  return maskName(patient.attributes.displayName, level) || patient.id;
}

export function getPatientQueueSummary(patient: PatientResource) {
  const level = usePrivacyDisplayStore.getState().level;
  return [maskDob(patient.attributes.dateOfBirth, level), patient.attributes.gender, displayCpid(patient.attributes.cpid)]
    .filter(Boolean)
    .join(" | ");
}
