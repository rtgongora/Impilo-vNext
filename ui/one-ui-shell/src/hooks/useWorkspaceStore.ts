/**
 * Experience UI — Workspace Context Store (Zustand)
 *
 * Provider tree position: QueryClient > Auth > Facility > [WorkspaceProvider] > Shift > Router
 * Persistence key: exp:workspace (sessionStorage)
 */

import { create } from "zustand";

export interface WorkspaceContext {
  id: string;
  name: string;
  workspaceType: string;
  facilityId: string;
}

interface WorkspaceState {
  workspace: WorkspaceContext | null;
  hasWorkspace: boolean;
  setWorkspace: (workspace: WorkspaceContext) => void;
  clearWorkspace: () => void;
}

export const useWorkspaceStore = create<WorkspaceState>((set: (partial: Partial<WorkspaceState>) => void) => ({
  workspace: null,
  hasWorkspace: false,

  setWorkspace: (workspace: WorkspaceContext) => {
    if (typeof window !== "undefined") {
      sessionStorage.setItem("exp:workspace", JSON.stringify(workspace));
    }
    set({ workspace, hasWorkspace: true });
  },

  clearWorkspace: () => {
    if (typeof window !== "undefined") {
      sessionStorage.removeItem("exp:workspace");
    }
    set({ workspace: null, hasWorkspace: false });
  },
}));
