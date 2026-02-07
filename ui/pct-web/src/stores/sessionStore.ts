/**
 * PCT Web -- Session & Work Context Store
 *
 * Zustand store for operator session state. Used by apiClient to inject
 * trust headers on every outbound request through Envoy ext_authz -> TSHEPO.
 */

import { create } from "zustand";

export interface ActiveSession {
  shiftId: string;
  facilityId: string;
  facilityName: string;
  workspaceId: string;
  workspaceName: string;
  dutyMode: "CLINICAL" | "ADMIN" | "VIRTUAL";
  startedAt: string;
  actorId: string;
  actorDisplayName: string;
}

interface SessionState {
  tenantId: string;
  actorId: string;
  actorType: string;
  facilityId: string;
  workspaceId: string;
  shiftId: string;
  accessToken: string;
  purposeOfUse: string;
  activeSession: ActiveSession | null;

  setSession: (data: {
    tenantId: string;
    actorId: string;
    actorType: string;
    accessToken: string;
    purposeOfUse?: string;
  }) => void;

  setActiveSession: (session: ActiveSession | null) => void;

  clearSession: () => void;
}

export const useSessionStore = create<SessionState>((set) => ({
  tenantId: "",
  actorId: "",
  actorType: "CLINICIAN",
  facilityId: "",
  workspaceId: "",
  shiftId: "",
  accessToken: "",
  purposeOfUse: "TREATMENT",
  activeSession: null,

  setSession: (data) =>
    set({
      tenantId: data.tenantId,
      actorId: data.actorId,
      actorType: data.actorType,
      accessToken: data.accessToken,
      purposeOfUse: data.purposeOfUse ?? "TREATMENT",
    }),

  setActiveSession: (session) =>
    set({
      activeSession: session,
      facilityId: session?.facilityId ?? "",
      workspaceId: session?.workspaceId ?? "",
      shiftId: session?.shiftId ?? "",
    }),

  clearSession: () =>
    set({
      tenantId: "",
      actorId: "",
      actorType: "CLINICIAN",
      facilityId: "",
      workspaceId: "",
      shiftId: "",
      accessToken: "",
      purposeOfUse: "TREATMENT",
      activeSession: null,
    }),
}));
