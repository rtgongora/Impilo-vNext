import { create } from "zustand";

interface SessionState {
  tenantId: string;
  actorId: string;
  actorType: string;
  accessToken: string;
  purposeOfUse: string;
  facilityId: string;
  vendorId: string;
  setSession: (data: {
    tenantId: string;
    actorId: string;
    actorType?: string;
    accessToken: string;
    facilityId?: string;
    vendorId?: string;
  }) => void;
  clearSession: () => void;
}

export const useSessionStore = create<SessionState>((set) => ({
  tenantId: "",
  actorId: "",
  actorType: "VENDOR",
  accessToken: "",
  purposeOfUse: "OPERATIONS",
  facilityId: "",
  vendorId: "",
  setSession: (data) =>
    set({
      tenantId: data.tenantId,
      actorId: data.actorId,
      actorType: data.actorType ?? "VENDOR",
      accessToken: data.accessToken,
      purposeOfUse: "OPERATIONS",
      facilityId: data.facilityId ?? "",
      vendorId: data.vendorId ?? "",
    }),
  clearSession: () =>
    set({
      tenantId: "",
      actorId: "",
      actorType: "VENDOR",
      accessToken: "",
      purposeOfUse: "OPERATIONS",
      facilityId: "",
      vendorId: "",
    }),
}));
