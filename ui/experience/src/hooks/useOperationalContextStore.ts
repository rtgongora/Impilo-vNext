/**
 * Operational context — persisted top-level mode (single login, many hats).
 *
 * Session keys:
 *   exp:operational_mode
 *   exp:facility_work_subcontext
 *   exp:registry_admin_subtype
 *   exp:organization_admin_surface
 */

import { create } from "zustand";
import {
  type FacilityWorkSubcontext,
  type OperationalAuthPrincipal,
  type OperationalMode,
  type OrganizationAdminSurface,
  type RegistryAdminSubtype,
  isOperationalMode,
  isOrganizationAdminSurface,
  isRegistryAdminSubtype,
  suggestDefaultOperationalMode,
} from "@/lib/operational-context";

const KEY_MODE = "exp:operational_mode";
const KEY_FACILITY_SUB = "exp:facility_work_subcontext";
const KEY_REGISTRY_SUB = "exp:registry_admin_subtype";
const KEY_ORG_SURFACE = "exp:organization_admin_surface";

const FACILITY_SUB_VALUES: FacilityWorkSubcontext[] = [
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
];

function isFacilityWorkSubcontext(value: string): value is FacilityWorkSubcontext {
  return (FACILITY_SUB_VALUES as string[]).includes(value);
}

function loadOperationalMode(): OperationalMode {
  if (typeof window === "undefined") return "my_life";
  const raw = sessionStorage.getItem(KEY_MODE);
  if (raw && isOperationalMode(raw)) return raw;
  return "my_life";
}

function loadFacilityWorkSubcontext(): FacilityWorkSubcontext | null {
  if (typeof window === "undefined") return null;
  const raw = sessionStorage.getItem(KEY_FACILITY_SUB);
  if (raw && isFacilityWorkSubcontext(raw)) return raw;
  return null;
}

function loadRegistryAdminSubtype(): RegistryAdminSubtype | null {
  if (typeof window === "undefined") return null;
  const raw = sessionStorage.getItem(KEY_REGISTRY_SUB);
  if (raw && isRegistryAdminSubtype(raw)) return raw;
  return null;
}

function loadOrganizationAdminSurface(): OrganizationAdminSurface | null {
  if (typeof window === "undefined") return null;
  const raw = sessionStorage.getItem(KEY_ORG_SURFACE);
  if (raw && isOrganizationAdminSurface(raw)) return raw;
  return null;
}

interface OperationalContextState {
  operationalMode: OperationalMode;
  facilityWorkSubcontext: FacilityWorkSubcontext | null;
  registryAdminSubtype: RegistryAdminSubtype | null;
  organizationAdminSurface: OrganizationAdminSurface | null;
  setOperationalMode: (mode: OperationalMode) => void;
  setFacilityWorkSubcontext: (sub: FacilityWorkSubcontext | null) => void;
  setRegistryAdminSubtype: (sub: RegistryAdminSubtype | null) => void;
  setOrganizationAdminSurface: (surface: OrganizationAdminSurface | null) => void;
  /** After auth hydration: set suggested default only when no remembered mode. */
  ensureDefaultFromUser: (user: OperationalAuthPrincipal | null) => void;
  /** Re-read session (e.g. after external tab sync). */
  rehydrateFromSession: () => void;
  reset: () => void;
}

export const useOperationalContextStore = create<OperationalContextState>((set) => ({
  operationalMode: loadOperationalMode(),
  facilityWorkSubcontext: loadFacilityWorkSubcontext(),
  registryAdminSubtype: loadRegistryAdminSubtype(),
  organizationAdminSurface: loadOrganizationAdminSurface(),

  setOperationalMode: (mode) => {
    if (typeof window !== "undefined") {
      sessionStorage.setItem(KEY_MODE, mode);
    }
    set({ operationalMode: mode });
  },

  setFacilityWorkSubcontext: (sub) => {
    if (typeof window !== "undefined") {
      if (sub === null) sessionStorage.removeItem(KEY_FACILITY_SUB);
      else sessionStorage.setItem(KEY_FACILITY_SUB, sub);
    }
    set({ facilityWorkSubcontext: sub });
  },

  setRegistryAdminSubtype: (sub) => {
    if (typeof window !== "undefined") {
      if (sub === null) sessionStorage.removeItem(KEY_REGISTRY_SUB);
      else sessionStorage.setItem(KEY_REGISTRY_SUB, sub);
    }
    set({ registryAdminSubtype: sub });
  },

  setOrganizationAdminSurface: (surface) => {
    if (typeof window !== "undefined") {
      if (surface === null) sessionStorage.removeItem(KEY_ORG_SURFACE);
      else sessionStorage.setItem(KEY_ORG_SURFACE, surface);
    }
    set({ organizationAdminSurface: surface });
  },

  ensureDefaultFromUser: (user) => {
    if (typeof window === "undefined") return;
    const raw = sessionStorage.getItem(KEY_MODE);
    if (raw && isOperationalMode(raw)) {
      set({ operationalMode: raw });
      return;
    }
    const suggested = suggestDefaultOperationalMode(user);
    sessionStorage.setItem(KEY_MODE, suggested);
    set({ operationalMode: suggested });
  },

  rehydrateFromSession: () => {
    set({
      operationalMode: loadOperationalMode(),
      facilityWorkSubcontext: loadFacilityWorkSubcontext(),
      registryAdminSubtype: loadRegistryAdminSubtype(),
      organizationAdminSurface: loadOrganizationAdminSurface(),
    });
  },

  reset: () => {
    if (typeof window !== "undefined") {
      sessionStorage.removeItem(KEY_MODE);
      sessionStorage.removeItem(KEY_FACILITY_SUB);
      sessionStorage.removeItem(KEY_REGISTRY_SUB);
      sessionStorage.removeItem(KEY_ORG_SURFACE);
    }
    set({
      operationalMode: "my_life",
      facilityWorkSubcontext: null,
      registryAdminSubtype: null,
      organizationAdminSurface: null,
    });
  },
}));
