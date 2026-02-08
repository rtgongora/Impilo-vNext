/**
 * MUSHEX Payer Portal — Session Store
 *
 * Zustand store for payer session state. Used by apiClient to inject
 * trust headers on every outbound request through Envoy ext_authz -> TSHEPO.
 */

import { create } from "zustand";

interface SessionState {
  tenantId: string;
  actorId: string;
  actorType: string;
  accessToken: string;
  purposeOfUse: string;
  facilityId: string;

  setSession: (data: {
    tenantId: string;
    actorId: string;
    actorType: string;
    accessToken: string;
    purposeOfUse?: string;
    facilityId?: string;
  }) => void;

  clearSession: () => void;
}

export const useSessionStore = create<SessionState>((set) => ({
  tenantId: "",
  actorId: "",
  actorType: "CITIZEN",
  accessToken: "",
  purposeOfUse: "PAYMENT",
  facilityId: "",

  setSession: (data) =>
    set({
      tenantId: data.tenantId,
      actorId: data.actorId,
      actorType: data.actorType,
      accessToken: data.accessToken,
      purposeOfUse: data.purposeOfUse ?? "PAYMENT",
      facilityId: data.facilityId ?? "",
    }),

  clearSession: () =>
    set({
      tenantId: "",
      actorId: "",
      actorType: "CITIZEN",
      accessToken: "",
      purposeOfUse: "PAYMENT",
      facilityId: "",
    }),
}));
