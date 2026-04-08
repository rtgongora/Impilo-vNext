/**
 * Facility Work operational subcontexts surfaced on shift + scheduling cluster pages.
 * Each entry maps to a real in-app route (shift-guarded unless noted).
 */

import type { FacilityWorkSubcontext } from "@/lib/operational-context";

/** Subcontexts with explicit next-step routes in this cluster (no labels without behavior). */
export const FACILITY_WORK_CLUSTER_SUBCONTEXTS = [
  "front_desk",
  "triage",
  "consultation",
  "nursing_station",
  "pharmacy",
  "laboratory",
  "imaging",
  "billing",
  "ward",
  "supervisor",
] as const satisfies readonly FacilityWorkSubcontext[];

export type FacilityWorkClusterSubcontext = (typeof FACILITY_WORK_CLUSTER_SUBCONTEXTS)[number];

export function isFacilityWorkClusterSubcontext(value: string): value is FacilityWorkClusterSubcontext {
  return (FACILITY_WORK_CLUSTER_SUBCONTEXTS as readonly string[]).includes(value);
}

export interface FacilityWorkSubcontextNav {
  shortLabel: string;
  href: string;
  nextCue: string;
}

export const FACILITY_WORK_SUBCONTEXT_NAV: Record<FacilityWorkClusterSubcontext, FacilityWorkSubcontextNav> = {
  front_desk: {
    shortLabel: "Front desk",
    href: "/queue/walk-in",
    nextCue: "Register walk-ins and route into the queue",
  },
  triage: {
    shortLabel: "Triage",
    href: "/queue/triage",
    nextCue: "Run triage queue and acuity decisions",
  },
  consultation: {
    shortLabel: "Consultation",
    href: "/telemedicine",
    nextCue: "Telemedicine hub and consult sessions",
  },
  nursing_station: {
    shortLabel: "Nursing station",
    href: "/queue/waiting",
    nextCue: "Waiting room and in-progress nursing handoffs",
  },
  pharmacy: {
    shortLabel: "Pharmacy",
    href: "/pharmacy",
    nextCue: "Dispensing, stock, and prescription queues",
  },
  laboratory: {
    shortLabel: "Laboratory",
    href: "/queue/search",
    nextCue: "Find the patient, then open chart → orders/results",
  },
  imaging: {
    shortLabel: "Imaging",
    href: "/queue/search",
    nextCue: "Locate the patient for imaging orders and results review",
  },
  billing: {
    shortLabel: "Billing",
    href: "/finance",
    nextCue: "Revenue workspace (FINANCE role required for some lanes)",
  },
  ward: {
    shortLabel: "Ward",
    href: "/beds",
    nextCue: "Bed board and ward-level capacity",
  },
  supervisor: {
    shortLabel: "Supervisor",
    href: "/queue",
    nextCue: "Queue command view across lanes",
  },
};
