import { create } from "zustand";
import type { IncomingCallInvite } from "@/lib/webrtc/types";

interface IncomingCallState {
  invite: IncomingCallInvite | null;
  setInvite: (invite: IncomingCallInvite | null) => void;
  clearInvite: () => void;
}

export const useIncomingCallStore = create<IncomingCallState>((set) => ({
  invite: null,
  setInvite: (invite) => set({ invite }),
  clearInvite: () => set({ invite: null }),
}));
