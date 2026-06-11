"use client";

import { useEffect } from "react";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useIncomingCallStore } from "@/hooks/useIncomingCallStore";
import { apiClient, type ApiResponse } from "@/lib/api-client";
import { TelemedicinePresenceClient, type PresenceMessage } from "@/lib/webrtc/presence-client";
import type { IncomingCallInvite } from "@/lib/webrtc/types";

/**
 * Keeps a user-scoped presence socket open so incoming telemedicine call invites can be pushed in real time.
 */
export function useTelemedicinePresence() {
  const user = useAuthStore((s) => s.user);
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const setInvite = useIncomingCallStore((s) => s.setInvite);

  useEffect(() => {
    if (!isAuthenticated || !user?.id) return;

    let client: TelemedicinePresenceClient | null = null;
    let pollTimer: ReturnType<typeof setInterval> | null = null;
    let cancelled = false;

    async function start() {
      let patientId: string | undefined;
      let cpid: string | undefined;

      if (user?.actorType === "CITIZEN" && user.email) {
        try {
          const res = await apiClient.get<
            ApiResponse<{ patientId?: string; cpid?: string }>
          >(`/internal/v1/telemedicine/citizen/profile?email=${encodeURIComponent(user.email)}`);
          patientId = res.data?.patientId;
          cpid = res.data?.cpid;
        } catch {
          // profile optional
        }
      }

      if (cancelled) return;

      const onMessage = (msg: PresenceMessage) => {
        if (msg.type === "call-invite") {
          setInvite(msg as IncomingCallInvite);
        }
      };

      client = new TelemedicinePresenceClient(user!.id, onMessage);
      client.connect(patientId, cpid);

      const pollPending = async () => {
        if (cancelled) return;
        try {
          if (patientId) {
            const patientRes = await apiClient.get<ApiResponse<Record<string, unknown>>>(
              `/internal/v1/telemedicine/calls/pending?patientId=${encodeURIComponent(patientId)}`,
            );
            const pending = patientRes.data;
            if (pending?.sessionId) {
              setInvite({
                type: "call-invite",
                sessionId: String(pending.sessionId),
                patientId,
                from: pending.callerId ? String(pending.callerId) : undefined,
                callerName: pending.callerName ? String(pending.callerName) : undefined,
                callType: (pending.callType as "audio" | "video") ?? "audio",
              });
              return;
            }
          }
          const userRes = await apiClient.get<ApiResponse<Record<string, unknown>>>(
            `/internal/v1/telemedicine/calls/pending-user?userId=${encodeURIComponent(user!.id)}`,
          );
          const pending = userRes.data;
          if (pending?.sessionId) {
            setInvite({
              type: "call-invite",
              sessionId: String(pending.sessionId),
              targetUserId: user!.id,
              from: pending.callerId ? String(pending.callerId) : undefined,
              callerName: pending.callerName ? String(pending.callerName) : undefined,
              callType: (pending.callType as "audio" | "video") ?? "audio",
            });
          }
        } catch {
          // optional fallback when push delivery is missed
        }
      };

      void pollPending();
      pollTimer = setInterval(() => void pollPending(), 2500);
    }

    void start();

    return () => {
      cancelled = true;
      if (pollTimer) clearInterval(pollTimer);
      client?.close();
    };
  }, [isAuthenticated, setInvite, user?.actorType, user?.email, user?.id]);
}
