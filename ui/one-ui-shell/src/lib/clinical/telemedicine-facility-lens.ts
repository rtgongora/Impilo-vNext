import type { TelemedicineSession } from "@/hooks/queries/useTelemedicine";

/**
 * Facility-centric lens for telemedicine queues (receiving site vs remote host).
 * Uses `session.attributes.facility_id` compared to the clinician's selected workplace.
 */

export type TelemedicineFacilityLens = "all" | "hosted_here" | "remote_host" | "facility_unknown";

export type TelemedicineFacilityRole = "hosted_here" | "remote_host" | "facility_unknown";

export function classifyTelemedicineFacilityRole(
  session: TelemedicineSession,
  currentFacilityId: string | undefined,
): TelemedicineFacilityRole {
  const fid = session.attributes.facility_id;
  if (!fid?.trim() || !currentFacilityId?.trim()) return "facility_unknown";
  if (fid === currentFacilityId) return "hosted_here";
  return "remote_host";
}

export function partitionTelemedicineSessionsByFacilityHost(
  sessions: TelemedicineSession[],
  currentFacilityId: string | undefined,
): {
  hostedHere: TelemedicineSession[];
  remoteHost: TelemedicineSession[];
  unknownFacility: TelemedicineSession[];
} {
  const hostedHere: TelemedicineSession[] = [];
  const remoteHost: TelemedicineSession[] = [];
  const unknownFacility: TelemedicineSession[] = [];

  for (const s of sessions) {
    const role = classifyTelemedicineFacilityRole(s, currentFacilityId);
    if (role === "hosted_here") hostedHere.push(s);
    else if (role === "remote_host") remoteHost.push(s);
    else unknownFacility.push(s);
  }
  return { hostedHere, remoteHost, unknownFacility };
}

export function filterTelemedicineSessionsByLens(
  sessions: TelemedicineSession[],
  lens: TelemedicineFacilityLens,
  currentFacilityId: string | undefined,
): TelemedicineSession[] {
  if (lens === "all") return sessions;
  return sessions.filter((s) => classifyTelemedicineFacilityRole(s, currentFacilityId) === lens);
}
