/**
 * Citizen appointment BFF contract — mirrors CitizenAppointmentController body shape.
 */

export interface CitizenAppointmentRecord {
  id: string;
  facilityId?: string;
  facilityName?: string;
  appointmentType: string;
  status: string;
  scheduledAt?: string;
  reason?: string;
  providerName?: string;
}

export interface RequestCitizenAppointmentPayload {
  facilityId: string;
  appointmentType: string;
  preferredDate: string;
  reason?: string;
}

export function toCitizenAppointmentRequest(
  payload: RequestCitizenAppointmentPayload,
): RequestCitizenAppointmentPayload {
  return {
    facilityId: payload.facilityId,
    appointmentType: payload.appointmentType,
    preferredDate: payload.preferredDate,
    reason: payload.reason?.trim() || undefined,
  };
}

/** Normalize mobile/citizen BFF rows (camelCase or JSON:API) into a stable UI shape. */
export function normalizeCitizenAppointment(row: unknown, index = 0): CitizenAppointmentRecord | null {
  if (!row || typeof row !== "object") return null;
  const o = row as Record<string, unknown>;
  const attrs =
    o.attributes && typeof o.attributes === "object"
      ? (o.attributes as Record<string, unknown>)
      : o;

  const id = String(o.id ?? attrs.id ?? `apt-${index}`);
  const appointmentType = String(
    attrs.appointmentType ?? attrs.appointment_type ?? attrs.type ?? "GENERAL",
  );
  const status = String(attrs.status ?? "SCHEDULED");
  const scheduledAt =
    (attrs.scheduledAt ?? attrs.scheduled_at ?? attrs.preferredDate ?? attrs.preferred_date) as
      | string
      | undefined;

  return {
    id,
    facilityId: (attrs.facilityId ?? attrs.facility_id) as string | undefined,
    facilityName: (attrs.facilityName ?? attrs.facility_name) as string | undefined,
    appointmentType,
    status,
    scheduledAt,
    reason: (attrs.reason ?? attrs.notes) as string | undefined,
    providerName: (attrs.providerName ?? attrs.provider_name) as string | undefined,
  };
}

export function normalizeCitizenAppointmentList(payload: unknown): CitizenAppointmentRecord[] {
  const root = payload as { data?: unknown } | null | undefined;
  const raw = root?.data;
  const arr: unknown[] = Array.isArray(raw) ? raw : raw && typeof raw === "object" ? [raw] : [];
  return arr
    .map((row, i) => normalizeCitizenAppointment(row, i))
    .filter((row): row is CitizenAppointmentRecord => row !== null);
}
