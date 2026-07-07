/**
 * Facility Mode reads (IATG Journey E).
 *
 * Facility-admin appointments for a facility, from the fail-closed BFF proxy
 * GET /api/v1/facility-claim/appointments. The signed-in person administers a
 * facility ONLY if they hold an ACTIVE appointment for it — this is how the shell
 * gates Facility-Mode admin actions per facility, not by a global role. No one
 * administers a facility they have no appointment for.
 *
 * NB: the canonical active state emitted by tuso is "ACTIVE" (see
 * FacilityAdminAppointmentEntity.STATE_ACTIVE; approve() sets ACTIVE, not
 * APPROVED). The gate below MUST match that literal or Facility Mode never
 * activates for a legitimately-approved admin.
 */

import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

interface Envelope<T> {
  data: T;
  meta?: { request_id?: string; correlation_id?: string };
}

export interface FacilityAppointmentView {
  id?: number;
  facilityUuid?: string;
  personHealthId?: string;
  role?: string;
  appointedBy?: string;
  approvalState?: string;
  evidenceRef?: string;
  validFrom?: string | null;
  validTo?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

interface FacilityAppointmentsResponse {
  facilityUuid: string;
  appointments: FacilityAppointmentView[];
}

export function fetchFacilityAppointments(facilityUuid: string) {
  return apiClient.get<Envelope<FacilityAppointmentsResponse>>(
    `/api/v1/facility-claim/appointments?facilityUuid=${encodeURIComponent(facilityUuid)}`,
  );
}

export function useFacilityAppointments(facilityUuid: string | undefined) {
  return useQuery<Envelope<FacilityAppointmentsResponse>>({
    queryKey: ["facility-appointments", facilityUuid],
    queryFn: () => fetchFacilityAppointments(facilityUuid as string),
    enabled: !!facilityUuid,
  });
}

/** The canonical active appointment state emitted by tuso (never "APPROVED"). */
export const FACILITY_ADMIN_ACTIVE_STATE = "ACTIVE";

/** True when the person holds an ACTIVE facility-admin appointment for this facility. */
export function administersFacility(
  appointments: FacilityAppointmentView[] | undefined,
  personHealthId: string | undefined,
): boolean {
  if (!appointments || !personHealthId) return false;
  return appointments.some(
    (a) =>
      a.personHealthId === personHealthId &&
      (a.approvalState ?? "").toUpperCase() === FACILITY_ADMIN_ACTIVE_STATE,
  );
}

/** Appointments still awaiting approval (Facility Mode card 5). */
export function pendingAppointments(
  appointments: FacilityAppointmentView[] | undefined,
): FacilityAppointmentView[] {
  return (appointments ?? []).filter((a) => (a.approvalState ?? "").toUpperCase() === "PENDING");
}
