import { create } from "zustand";

interface SessionState {
  tenantId: string;
  actorId: string;
  actorType: string;
  accessToken: string;
  purposeOfUse: string;
  facilityId: string;
  setSession: (data: { tenantId: string; actorId: string; actorType?: string; accessToken: string; facilityId?: string }) => void;
  clearSession: () => void;
}

export const useSessionStore = create<SessionState>((set) => ({
  tenantId: "",
  actorId: "",
  actorType: "OPS",
  accessToken: "",
  purposeOfUse: "OPERATIONS",
  facilityId: "",
  setSession: (data) =>
    set({
      tenantId: data.tenantId,
      actorId: data.actorId,
      actorType: data.actorType ?? "OPS",
      accessToken: data.accessToken,
      purposeOfUse: "OPERATIONS",
      facilityId: data.facilityId ?? "",
    }),
  clearSession: () =>
    set({
      tenantId: "",
      actorId: "",
      actorType: "OPS",
      accessToken: "",
      purposeOfUse: "OPERATIONS",
      facilityId: "",
    }),
}));
