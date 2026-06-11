"use client";

import { useRouter } from "next/navigation";
import { Phone, PhoneOff, Video } from "lucide-react";
import { useIncomingCallStore } from "@/hooks/useIncomingCallStore";
import { apiClient } from "@/lib/api-client";
import { SHELL_TASKBAR_HEIGHT_PX } from "@/lib/shell/app-registry";

export function IncomingCallOverlay() {
  const router = useRouter();
  const invite = useIncomingCallStore((s) => s.invite);
  const clearInvite = useIncomingCallStore((s) => s.clearInvite);

  if (!invite) return null;

  const activeInvite = invite;
  const isVideo = activeInvite.callType === "video";

  async function handleDecline() {
    try {
      await apiClient.post(`/internal/v1/telemedicine/calls/${activeInvite.sessionId}/decline`, {
        patientId: activeInvite.patientId,
        targetUserId: activeInvite.targetUserId,
      });
    } catch {
      // best effort
    }
    clearInvite();
  }

  async function handleAccept() {
    const { sessionId, callType, from: callerPeerId, patientId, targetUserId } = activeInvite;
    try {
      await apiClient.post(`/internal/v1/telemedicine/calls/${sessionId}/accept`, {
        patientId,
        targetUserId,
      });
    } catch {
      // best effort
    }
    clearInvite();
    const peerQuery = callerPeerId ? `&peerId=${encodeURIComponent(callerPeerId)}` : "";
    const dest = `/telemedicine/session/${sessionId}?autoAnswer=1&callType=${callType ?? "audio"}${peerQuery}`;
    const onSession =
      typeof window !== "undefined" &&
      window.location.pathname.endsWith(`/telemedicine/session/${sessionId}`);
    if (onSession) {
      router.replace(dest, { scroll: false });
    } else {
      router.push(dest);
    }
  }

  return (
    <div
      className="fixed inset-x-0 z-[300] flex justify-center px-4 pointer-events-none"
      style={{ bottom: `calc(${SHELL_TASKBAR_HEIGHT_PX}px + 1rem)` }}
      role="dialog"
      aria-label="Incoming telemedicine call"
    >
      <div className="pointer-events-auto w-full max-w-md overflow-hidden rounded-2xl border border-sky-200 bg-white shadow-2xl ring-1 ring-black/5 animate-in slide-in-from-bottom-4">
        <div className="bg-gradient-to-r from-sky-600 to-impilo-600 px-5 py-4 text-white">
          <p className="text-xs font-semibold uppercase tracking-wider opacity-90">Incoming call</p>
          <p className="mt-1 text-lg font-semibold">{activeInvite.callerName ?? "Care team"}</p>
          <p className="text-sm opacity-90">{isVideo ? "Video teleconsult" : "Audio teleconsult"}</p>
        </div>
        <div className="flex items-center justify-center gap-4 px-5 py-5">
          <button
            type="button"
            onClick={() => void handleDecline()}
            className="flex h-14 w-14 items-center justify-center rounded-full bg-red-500 text-white shadow-lg transition hover:bg-red-600"
            aria-label="Decline call"
          >
            <PhoneOff className="h-6 w-6" />
          </button>
          <button
            type="button"
            onClick={() => void handleAccept()}
            className="flex h-16 w-16 items-center justify-center rounded-full bg-green-500 text-white shadow-lg transition hover:bg-green-600"
            aria-label="Accept call"
          >
            {isVideo ? <Video className="h-7 w-7" /> : <Phone className="h-7 w-7" />}
          </button>
        </div>
      </div>
    </div>
  );
}
