/**
 * Work Mode Store — tracks the user's active work context mode.
 *
 * 9 modes covering the full Lovable WorkplaceSelectionHub parity:
 *
 * Facility-based:
 * - clinical: Direct patient care at a facility (CLINICIAN, NURSE)
 * - pharmacy: Dispensing/stock work (PHARMACIST)
 *
 * Non-facility:
 * - admin: System administration (SYSTEM_ADMIN, FACILITY_ADMIN, DEVELOPER)
 * - finance: Billing, payments, claims (FINANCE)
 * - oversight: Cross-facility oversight (SYSTEM_ADMIN without facility)
 *
 * License-based (independent of facility):
 * - independent_practice: Private practice under own license
 * - emergency_response: Emergency work under license authority
 * - community_outreach: Community health program work
 *
 * Default:
 * - general: Default for users without a specific mode
 *
 * Persists to sessionStorage. Mode set explicitly from workplace hub
 * or auto-synced from URL context.
 */

import { create } from "zustand";

export type WorkMode =
  | "clinical"
  | "pharmacy"
  | "admin"
  | "finance"
  | "oversight"
  | "independent_practice"
  | "emergency_response"
  | "community_outreach"
  | "general";

/** Metadata for the active work mode when license-based */
export interface WorkModeContext {
  licenseNumber?: string;
  licenseCategory?: string;
  practiceName?: string;
  programName?: string;
}

interface WorkModeState {
  mode: WorkMode;
  context: WorkModeContext;
  setMode: (mode: WorkMode, context?: WorkModeContext) => void;
  deriveFromRoles: (roles: string[]) => void;
}

function loadMode(): WorkMode {
  if (typeof window === "undefined") return "general";
  return (sessionStorage.getItem("exp:work_mode") as WorkMode) || "general";
}

function loadContext(): WorkModeContext {
  if (typeof window === "undefined") return {};
  try {
    const raw = sessionStorage.getItem("exp:work_mode_context");
    return raw ? JSON.parse(raw) : {};
  } catch {
    return {};
  }
}

export const useWorkModeStore = create<WorkModeState>((set) => ({
  mode: loadMode(),
  context: loadContext(),

  setMode: (mode, context) => {
    sessionStorage.setItem("exp:work_mode", mode);
    const ctx = context ?? {};
    sessionStorage.setItem("exp:work_mode_context", JSON.stringify(ctx));
    set({ mode, context: ctx });
  },

  deriveFromRoles: (roles) => {
    let derived: WorkMode = "general";
    if (roles.includes("SYSTEM_ADMIN")) derived = "oversight";
    if (roles.includes("FACILITY_ADMIN")) derived = "admin";
    if (roles.includes("FINANCE")) derived = "finance";
    if (roles.includes("PHARMACIST")) derived = "pharmacy";
    if (roles.includes("CLINICIAN") || roles.includes("NURSE")) derived = "clinical";
    sessionStorage.setItem("exp:work_mode", derived);
    sessionStorage.setItem("exp:work_mode_context", "{}");
    set({ mode: derived, context: {} });
  },
}));

/** Labels for display */
export const WORK_MODE_LABELS: Record<WorkMode, string> = {
  clinical: "Clinical",
  pharmacy: "Pharmacy",
  admin: "Administration",
  finance: "Finance",
  oversight: "Oversight",
  independent_practice: "Independent Practice",
  emergency_response: "Emergency Response",
  community_outreach: "Community Outreach",
  general: "General",
};
