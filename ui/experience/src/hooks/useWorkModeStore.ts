/**
 * Work Mode Store — tracks the user's active work context mode.
 *
 * Modes derive from the user's Keycloak roles and current selections:
 * - clinical: Direct patient care at a facility (CLINICIAN, NURSE)
 * - pharmacy: Dispensing/stock work (PHARMACIST)
 * - admin: System administration (SYSTEM_ADMIN, FACILITY_ADMIN, DEVELOPER)
 * - finance: Billing, payments, claims (FINANCE)
 * - oversight: Cross-facility oversight (SYSTEM_ADMIN without facility)
 * - general: Default for users without a specific mode
 *
 * Persists to sessionStorage. The mode is set explicitly when the user
 * selects their work context (workplace hub or sidebar navigation).
 */

import { create } from "zustand";

export type WorkMode = "clinical" | "pharmacy" | "admin" | "finance" | "oversight" | "general";

interface WorkModeState {
  mode: WorkMode;
  setMode: (mode: WorkMode) => void;
  /** Derive the best initial mode from user roles */
  deriveFromRoles: (roles: string[]) => void;
}

function loadMode(): WorkMode {
  if (typeof window === "undefined") return "general";
  return (sessionStorage.getItem("exp:work_mode") as WorkMode) || "general";
}

export const useWorkModeStore = create<WorkModeState>((set) => ({
  mode: loadMode(),

  setMode: (mode) => {
    sessionStorage.setItem("exp:work_mode", mode);
    set({ mode });
  },

  deriveFromRoles: (roles) => {
    let derived: WorkMode = "general";
    if (roles.includes("SYSTEM_ADMIN")) derived = "oversight";
    if (roles.includes("FACILITY_ADMIN")) derived = "admin";
    if (roles.includes("FINANCE")) derived = "finance";
    if (roles.includes("PHARMACIST")) derived = "pharmacy";
    if (roles.includes("CLINICIAN") || roles.includes("NURSE")) derived = "clinical";
    // Most specific role wins (clinical > pharmacy > finance > admin > oversight)
    sessionStorage.setItem("exp:work_mode", derived);
    set({ mode: derived });
  },
}));
