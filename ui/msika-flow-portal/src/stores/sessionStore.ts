import { create } from "zustand";

interface SessionState {
  tenantId: string;
  actorId: string;
  actorType: string;
  accessToken: string;
  purposeOfUse: string;
  facilityId: string;
  patientCpid: string;

  setSession: (data: {
    tenantId: string;
    actorId: string;
    actorType?: string;
    accessToken: string;
    purposeOfUse?: string;
    facilityId?: string;
    patientCpid?: string;
  }) => void;

  clearSession: () => void;
}

export const useSessionStore = create<SessionState>((set) => ({
  tenantId: "",
  actorId: "",
  actorType: "PATIENT",
  accessToken: "",
  purposeOfUse: "TREATMENT",
  facilityId: "",
  patientCpid: "",

  setSession: (data) =>
    set({
      tenantId: data.tenantId,
      actorId: data.actorId,
      actorType: data.actorType ?? "PATIENT",
      accessToken: data.accessToken,
      purposeOfUse: data.purposeOfUse ?? "TREATMENT",
      facilityId: data.facilityId ?? "",
      patientCpid: data.patientCpid ?? "",
    }),

  clearSession: () =>
    set({
      tenantId: "",
      actorId: "",
      actorType: "PATIENT",
      accessToken: "",
      purposeOfUse: "TREATMENT",
      facilityId: "",
      patientCpid: "",
    }),
}));
