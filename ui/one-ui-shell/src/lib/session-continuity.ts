import { useShellStore } from "@/hooks/useShellStore";
import { type FacilityContext, useFacilityStore } from "@/hooks/useFacilityStore";
import { useOperationalContextStore } from "@/hooks/useOperationalContextStore";
import { type ShiftContext, useShiftStore } from "@/hooks/useShiftStore";
import { useWorkModeStore } from "@/hooks/useWorkModeStore";
import { type WorkspaceContext, useWorkspaceStore } from "@/hooks/useWorkspaceStore";
import { OPERATIONAL_CONTEXT_SESSION_KEYS } from "@/lib/operational-context";

export const EXPERIENCE_CONTINUITY_SESSION_KEYS = [
  "exp:facility",
  "exp:workspace",
  "exp:shift",
  "exp:work_mode",
  "exp:work_mode_context",
  "exp:purpose_of_use",
  ...OPERATIONAL_CONTEXT_SESSION_KEYS,
] as const;

export function hasPersistedExperienceContinuity(): boolean {
  if (typeof window === "undefined") return false;
  return EXPERIENCE_CONTINUITY_SESSION_KEYS.some((key) => sessionStorage.getItem(key) !== null);
}

export function resetExperienceContinuity(): void {
  useShiftStore.getState().endShift();
  useWorkspaceStore.getState().clearWorkspace();
  useFacilityStore.getState().clearFacility();
  useOperationalContextStore.getState().reset();
  useWorkModeStore.getState().reset();
  useShellStore.getState().clearSessionShellState();

  if (typeof window !== "undefined") {
    sessionStorage.removeItem("exp:purpose_of_use");
  }
}

function parseStoredJson<T>(key: string): T | null {
  if (typeof window === "undefined") return null;
  const raw = sessionStorage.getItem(key);
  if (!raw) return null;

  try {
    return JSON.parse(raw) as T;
  } catch {
    sessionStorage.removeItem(key);
    return null;
  }
}

export interface HydratedExperienceContinuity {
  facility: FacilityContext | null;
  workspace: WorkspaceContext | null;
  shift: ShiftContext | null;
}

export function loadHydratedExperienceContinuity(): HydratedExperienceContinuity {
  const facility = parseStoredJson<FacilityContext>("exp:facility");
  const storedWorkspace = parseStoredJson<WorkspaceContext>("exp:workspace");
  const storedShift = parseStoredJson<ShiftContext>("exp:shift");

  const workspace =
    facility && storedWorkspace?.facilityId === facility.id ? storedWorkspace : null;
  if (!workspace && storedWorkspace && typeof window !== "undefined") {
    sessionStorage.removeItem("exp:workspace");
  }

  const shift =
    facility &&
    workspace &&
    storedShift?.facilityId === facility.id &&
    storedShift.workspaceId === workspace.id
      ? storedShift
      : null;
  if (!shift && storedShift && typeof window !== "undefined") {
    sessionStorage.removeItem("exp:shift");
  }

  return { facility, workspace, shift };
}
