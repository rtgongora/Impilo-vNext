/**
 * Booking & appointment BFF contract — mirrors booking-service / Experience BFF shapes.
 * Booking = transaction container; Appointment = confirmed scheduled event.
 */

export type BookingTargetType =
  | "PRACTITIONER"
  | "WORKSPACE"
  | "FACILITY_SERVICE"
  | "SPECIALTY_POOL"
  | "RESOURCE"
  | "ON_CALL";

export type BookingChannel = "PHYSICAL" | "VIRTUAL" | "HOME" | "COMMUNITY" | "HYBRID";

export interface BookingRecord {
  id: string;
  bookingNumber?: string;
  clientId?: string;
  bookingType: string;
  bookingStatus: string;
  facilityId?: string;
  facilityName?: string;
  providerId?: string;
  providerName?: string;
  targetType?: string;
  targetRef?: string;
  preferredStartTime?: string;
  preferredEndTime?: string;
  preferredChannel?: string;
  reasonForBooking?: string;
  consentStatus?: string;
  agreementStatus?: string;
  authorisationStatus?: string;
  paymentStatus?: string;
  approvalStatus?: string;
  linkedAppointmentId?: string;
  serviceId?: string;
  serviceName?: string;
}

export interface AppointmentRecord {
  id: string;
  appointmentNumber?: string;
  bookingId?: string;
  clientId?: string;
  providerId?: string;
  providerName?: string;
  facilityId?: string;
  facilityName?: string;
  appointmentType: string;
  status: string;
  startTime?: string;
  endTime?: string;
  channel?: string;
  encounterId?: string;
  teleconsultSessionId?: string;
  reason?: string;
}

export interface CreateBookingPayload {
  clientId?: string;
  bookingType: string;
  serviceId?: string;
  serviceName?: string;
  facilityId: string;
  providerId?: string;
  targetType?: BookingTargetType;
  targetRef?: string;
  preferredStartTime?: string;
  preferredEndTime?: string;
  preferredChannel?: BookingChannel;
  reasonForBooking?: string;
  clinicalPriority?: string;
  paymentAcknowledged?: boolean;
}

export interface BookingMvumoStatus {
  consentStatus?: string;
  agreementStatus?: string;
  authorisationStatus?: string;
  paymentStatus?: string;
  overallReady?: boolean;
  pendingTypes?: string[];
}

export interface BookingFacilityConfig {
  facilityId: string;
  requireMvumoConsent?: boolean;
  requireMvumoAgreement?: boolean;
  requireMvumoAuthorisation?: boolean;
  requirePaymentBeforeConfirm?: boolean;
  defaultBookingChannel?: BookingChannel;
  availabilityWindowDays?: number;
}

function readAttrs(row: Record<string, unknown>): Record<string, unknown> {
  return row.attributes && typeof row.attributes === "object"
    ? (row.attributes as Record<string, unknown>)
    : row;
}

/** Build POST /internal/v1/bookings body from wizard state. */
export function toBookingRequest(payload: CreateBookingPayload): CreateBookingPayload {
  return {
    clientId: payload.clientId,
    bookingType: payload.bookingType,
    serviceId: payload.serviceId,
    serviceName: payload.serviceName,
    facilityId: payload.facilityId,
    providerId: payload.providerId,
    targetType: payload.targetType,
    targetRef: payload.targetRef,
    preferredStartTime: payload.preferredStartTime,
    preferredEndTime: payload.preferredEndTime,
    preferredChannel: payload.preferredChannel,
    reasonForBooking: payload.reasonForBooking?.trim() || undefined,
    clinicalPriority: payload.clinicalPriority,
  };
}

export function normalizeBooking(row: unknown, index = 0): BookingRecord | null {
  if (!row || typeof row !== "object") return null;
  const o = row as Record<string, unknown>;
  const attrs = readAttrs(o);

  const id = String(o.id ?? attrs.id ?? `booking-${index}`);
  const bookingStatus = String(
    attrs.bookingStatus ?? attrs.booking_status ?? attrs.status ?? "REQUESTED",
  );

  return {
    id,
    bookingNumber: (attrs.bookingNumber ?? attrs.booking_number) as string | undefined,
    clientId: (attrs.clientId ?? attrs.client_id) as string | undefined,
    bookingType: String(attrs.bookingType ?? attrs.booking_type ?? attrs.type ?? "CONSULTATION"),
    bookingStatus,
    facilityId: (attrs.facilityId ?? attrs.facility_id) as string | undefined,
    facilityName: (attrs.facilityName ?? attrs.facility_name) as string | undefined,
    providerId: (attrs.providerId ?? attrs.provider_id) as string | undefined,
    providerName: (attrs.providerName ?? attrs.provider_name) as string | undefined,
    targetType: (attrs.targetType ?? attrs.target_type) as string | undefined,
    targetRef: (attrs.targetRef ?? attrs.target_ref) as string | undefined,
    preferredStartTime: (attrs.preferredStartTime ??
      attrs.preferred_start_time ??
      attrs.scheduledAt ??
      attrs.scheduled_at) as string | undefined,
    preferredEndTime: (attrs.preferredEndTime ?? attrs.preferred_end_time) as string | undefined,
    preferredChannel: (attrs.preferredChannel ?? attrs.preferred_channel ?? attrs.channel) as
      | string
      | undefined,
    reasonForBooking: (attrs.reasonForBooking ?? attrs.reason_for_booking ?? attrs.reason) as
      | string
      | undefined,
    consentStatus: (attrs.consentStatus ?? attrs.consent_status) as string | undefined,
    agreementStatus: (attrs.agreementStatus ?? attrs.agreement_status) as string | undefined,
    authorisationStatus: (attrs.authorisationStatus ?? attrs.authorisation_status) as
      | string
      | undefined,
    paymentStatus: (attrs.paymentStatus ?? attrs.payment_status) as string | undefined,
    approvalStatus: (attrs.approvalStatus ?? attrs.approval_status) as string | undefined,
    linkedAppointmentId: (attrs.linkedAppointmentId ??
      attrs.linked_appointment_id ??
      attrs.appointmentId ??
      attrs.appointment_id) as string | undefined,
    serviceId: (attrs.serviceId ?? attrs.service_id) as string | undefined,
    serviceName: (attrs.serviceName ?? attrs.service_name) as string | undefined,
  };
}

export function normalizeBookingList(payload: unknown): BookingRecord[] {
  const root = payload as { data?: unknown } | null | undefined;
  const raw = root?.data;
  const arr: unknown[] = Array.isArray(raw) ? raw : raw && typeof raw === "object" ? [raw] : [];
  return arr
    .map((row, i) => normalizeBooking(row, i))
    .filter((row): row is BookingRecord => row !== null);
}

export function normalizeAppointment(row: unknown, index = 0): AppointmentRecord | null {
  if (!row || typeof row !== "object") return null;
  const o = row as Record<string, unknown>;
  const attrs = readAttrs(o);

  const id = String(o.id ?? attrs.id ?? `apt-${index}`);
  const status = String(attrs.status ?? "SCHEDULED");
  const startTime = (attrs.startTime ??
    attrs.start_time ??
    attrs.scheduledAt ??
    attrs.scheduled_at) as string | undefined;

  return {
    id,
    appointmentNumber: (attrs.appointmentNumber ?? attrs.appointment_number) as string | undefined,
    bookingId: (attrs.bookingId ?? attrs.booking_id) as string | undefined,
    clientId: (attrs.clientId ?? attrs.client_id ?? attrs.patientId ?? attrs.patient_id) as
      | string
      | undefined,
    providerId: (attrs.providerId ?? attrs.provider_id) as string | undefined,
    providerName: (attrs.providerName ?? attrs.provider_name) as string | undefined,
    facilityId: (attrs.facilityId ?? attrs.facility_id) as string | undefined,
    facilityName: (attrs.facilityName ?? attrs.facility_name) as string | undefined,
    appointmentType: String(
      attrs.appointmentType ?? attrs.appointment_type ?? attrs.type ?? "CONSULTATION",
    ),
    status,
    startTime,
    endTime: (attrs.endTime ?? attrs.end_time ?? attrs.end_at) as string | undefined,
    channel: (attrs.channel ?? attrs.preferredChannel) as string | undefined,
    encounterId: (attrs.encounterId ?? attrs.encounter_id) as string | undefined,
    teleconsultSessionId: (attrs.teleconsultSessionId ??
      attrs.teleconsult_session_id ??
      attrs.sessionId ??
      attrs.session_id) as string | undefined,
    reason: (attrs.reason ?? attrs.notes) as string | undefined,
  };
}

export function normalizeAppointmentList(payload: unknown): AppointmentRecord[] {
  const root = payload as { data?: unknown } | null | undefined;
  const raw = root?.data;
  const arr: unknown[] = Array.isArray(raw) ? raw : raw && typeof raw === "object" ? [raw] : [];
  return arr
    .map((row, i) => normalizeAppointment(row, i))
    .filter((row): row is AppointmentRecord => row !== null);
}

export function normalizeBookingMvumoStatus(payload: unknown): BookingMvumoStatus {
  const root = payload as { data?: Record<string, unknown> } | null | undefined;
  const data = root?.data ?? {};
  return {
    consentStatus: String(data.consentStatus ?? data.consent_status ?? "UNKNOWN"),
    agreementStatus: String(data.agreementStatus ?? data.agreement_status ?? "UNKNOWN"),
    authorisationStatus: String(
      data.authorisationStatus ?? data.authorisation_status ?? "UNKNOWN",
    ),
    paymentStatus: String(data.paymentStatus ?? data.payment_status ?? "UNKNOWN"),
    overallReady: Boolean(data.overallReady ?? data.overall_ready ?? false),
    pendingTypes: Array.isArray(data.pendingTypes)
      ? (data.pendingTypes as string[])
      : Array.isArray(data.pending_types)
        ? (data.pending_types as string[])
        : undefined,
  };
}

export const BOOKING_STATUS_STYLES: Record<string, string> = {
  DRAFT: "bg-neutral-100 text-muted-foreground",
  REQUESTED: "bg-amber-100 text-warning-foreground",
  PENDING_TRIAGE: "bg-orange-100 text-orange-700",
  PENDING_CONSENT: "bg-purple-100 text-warning-foreground",
  PENDING_AGREEMENT: "bg-purple-100 text-warning-foreground",
  PENDING_AUTHORISATION: "bg-purple-100 text-warning-foreground",
  PENDING_PAYMENT: "bg-yellow-100 text-yellow-800",
  PENDING_APPROVAL: "bg-amber-100 text-warning-foreground",
  RESERVED: "bg-blue-100 text-primary-hover",
  CONFIRMED: "bg-green-100 text-green-700",
  WAITLISTED: "bg-neutral-100 text-foreground",
  CANCELLED: "bg-neutral-100 text-muted-foreground",
  REJECTED: "bg-red-100 text-danger",
  EXPIRED: "bg-neutral-100 text-muted-foreground",
  FULFILLED: "bg-emerald-100 text-primary-hover",
};

export const APPOINTMENT_STATUS_STYLES: Record<string, string> = {
  SCHEDULED: "bg-primary-soft text-primary-hover",
  CONFIRMED: "bg-green-100 text-green-700",
  REMINDED: "bg-blue-100 text-primary-hover",
  CHECKED_IN: "bg-emerald-100 text-primary-hover",
  IN_PROGRESS: "bg-primary-soft text-primary-hover",
  COMPLETED: "bg-purple-100 text-warning-foreground",
  CANCELLED: "bg-neutral-100 text-muted-foreground",
  NO_SHOW: "bg-red-100 text-danger",
  RESCHEDULED: "bg-amber-100 text-warning-foreground",
};

export const CONFIRMED_APPOINTMENT_STATUSES = new Set([
  "SCHEDULED",
  "CONFIRMED",
  "REMINDED",
  "CHECKED_IN",
  "IN_PROGRESS",
]);

export const TERMINAL_BOOKING_STATUSES = new Set([
  "CANCELLED",
  "REJECTED",
  "EXPIRED",
  "FULFILLED",
]);
