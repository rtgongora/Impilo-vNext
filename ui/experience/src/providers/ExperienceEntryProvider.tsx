"use client";

import {
  createContext,
  type ReactNode,
  useContext,
  useMemo,
} from "react";
import { usePathname, useRouter } from "next/navigation";
import { useAuthStore } from "@/hooks/useAuthStore";
import { type FacilityContext, useFacilityStore } from "@/hooks/useFacilityStore";
import { useOperationalContextStore } from "@/hooks/useOperationalContextStore";
import { useShiftStore } from "@/hooks/useShiftStore";
import { type WorkMode, useWorkModeStore } from "@/hooks/useWorkModeStore";
import { type WorkspaceContext, useWorkspaceStore } from "@/hooks/useWorkspaceStore";
import {
  buildAvailableOperationalModes,
  type FacilityWorkSubcontext,
  type OperationalMode,
  type RegistryAdminSubtype,
} from "@/lib/operational-context";
import { matchRouteDefinition, type RouteDefinition } from "@/lib/routes";

export type ExperienceEntryStage = "auth" | "facility" | "workspace" | "shift" | "ready";

interface SelectFacilityOptions {
  nextPath?: string;
  mode?: WorkMode;
  resetDownstream?: boolean;
}

interface SelectWorkspaceOptions {
  nextPath?: string;
  mode?: WorkMode;
  resetShift?: boolean;
}

interface ExperienceEntryContextValue {
  currentRoute: RouteDefinition | null;
  stage: ExperienceEntryStage;
  facility: FacilityContext | null;
  workspace: WorkspaceContext | null;
  shiftActive: boolean;
  workMode: WorkMode;
  /** Top-level operational context (single login, many hats). */
  operationalMode: OperationalMode;
  availableOperationalModes: OperationalMode[];
  setOperationalMode: (mode: OperationalMode) => void;
  facilityWorkSubcontext: FacilityWorkSubcontext | null;
  setFacilityWorkSubcontext: (sub: FacilityWorkSubcontext | null) => void;
  registryAdminSubtype: RegistryAdminSubtype | null;
  setRegistryAdminSubtype: (sub: RegistryAdminSubtype | null) => void;
  selectFacility: (facility: FacilityContext, options?: SelectFacilityOptions) => void;
  selectWorkspace: (workspace: WorkspaceContext, options?: SelectWorkspaceOptions) => void;
  enterMode: (mode: WorkMode, nextPath: string) => void;
  clearEntry: () => void;
}

const ExperienceEntryContext = createContext<ExperienceEntryContextValue | null>(null);

function deriveWorkModeFromWorkspace(workspaceType: string): WorkMode {
  if (workspaceType === "PHARMACY") return "pharmacy";
  return "clinical";
}

function getExperienceEntryStage(args: {
  isAuthenticated: boolean;
  facility: FacilityContext | null;
  workspace: WorkspaceContext | null;
  hasShift: boolean;
}): ExperienceEntryStage {
  if (!args.isAuthenticated) return "auth";
  if (!args.facility) return "facility";
  if (!args.workspace) return "workspace";
  if (!args.hasShift) return "shift";
  return "ready";
}

export function ExperienceEntryProvider({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const { isAuthenticated } = useAuthStore();
  const facility = useFacilityStore((s) => s.facility);
  const setFacility = useFacilityStore((s) => s.setFacility);
  const clearFacility = useFacilityStore((s) => s.clearFacility);
  const workspace = useWorkspaceStore((s) => s.workspace);
  const setWorkspace = useWorkspaceStore((s) => s.setWorkspace);
  const clearWorkspace = useWorkspaceStore((s) => s.clearWorkspace);
  const hasShift = useShiftStore((s) => s.hasShift);
  const endShift = useShiftStore((s) => s.endShift);
  const workMode = useWorkModeStore((s) => s.mode);
  const setWorkMode = useWorkModeStore((s) => s.setMode);

  const user = useAuthStore((s) => s.user);
  const operationalMode = useOperationalContextStore((s) => s.operationalMode);
  const setOperationalModeStore = useOperationalContextStore((s) => s.setOperationalMode);
  const facilityWorkSubcontext = useOperationalContextStore((s) => s.facilityWorkSubcontext);
  const setFacilityWorkSubcontext = useOperationalContextStore((s) => s.setFacilityWorkSubcontext);
  const registryAdminSubtype = useOperationalContextStore((s) => s.registryAdminSubtype);
  const setRegistryAdminSubtype = useOperationalContextStore((s) => s.setRegistryAdminSubtype);

  const availableOperationalModes = useMemo(
    () => buildAvailableOperationalModes(user),
    [user],
  );

  function setOperationalMode(next: OperationalMode) {
    if (!availableOperationalModes.includes(next)) return;
    setOperationalModeStore(next);
  }

  const currentRoute = matchRouteDefinition(pathname);
  const stage = getExperienceEntryStage({
    isAuthenticated,
    facility,
    workspace,
    hasShift,
  });

  function selectFacility(nextFacility: FacilityContext, options?: SelectFacilityOptions) {
    const resetDownstream = options?.resetDownstream ?? true;

    if (resetDownstream) {
      endShift();
      clearWorkspace();
    }

    setFacility(nextFacility);

    if (options?.mode) {
      setWorkMode(options.mode);
    }

    if (options?.nextPath) {
      router.push(options.nextPath);
    }
  }

  function selectWorkspace(nextWorkspace: WorkspaceContext, options?: SelectWorkspaceOptions) {
    const resetShift = options?.resetShift ?? true;

    if (resetShift) {
      endShift();
    }

    setWorkspace(nextWorkspace);
    setWorkMode(options?.mode ?? deriveWorkModeFromWorkspace(nextWorkspace.workspaceType));

    if (options?.nextPath) {
      router.push(options.nextPath);
    }
  }

  function enterMode(mode: WorkMode, nextPath: string) {
    setWorkMode(mode);
    router.push(nextPath);
  }

  function clearEntry() {
    endShift();
    clearWorkspace();
    clearFacility();
  }

  return (
    <ExperienceEntryContext.Provider
      value={{
        currentRoute,
        stage,
        facility,
        workspace,
        shiftActive: hasShift,
        workMode,
        operationalMode,
        availableOperationalModes,
        setOperationalMode,
        facilityWorkSubcontext,
        setFacilityWorkSubcontext,
        registryAdminSubtype,
        setRegistryAdminSubtype,
        selectFacility,
        selectWorkspace,
        enterMode,
        clearEntry,
      }}
    >
      {children}
    </ExperienceEntryContext.Provider>
  );
}

export function useExperienceEntry() {
  const context = useContext(ExperienceEntryContext);
  if (!context) {
    throw new Error("useExperienceEntry must be used within ExperienceEntryProvider");
  }

  return context;
}
