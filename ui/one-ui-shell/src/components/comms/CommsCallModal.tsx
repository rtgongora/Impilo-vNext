"use client";

import { useState } from "react";
import { PhoneOff, Loader2, ShieldAlert } from "lucide-react";
import { AdaptiveSessionRoom } from "@/components/session/AdaptiveSessionRoom";
import type { CallResponse } from "@/hooks/queries/useComms";

/**
 * 1:1 call surface for the Comms Hub. Media runs over the repo's canonical self-hosted LiveKit via
 * the rtc-gateway token returned by khuluma-service (no custom WebRTC). When media is unavailable
 * (gateway/LiveKit unconfigured or consent-gated) the call still shows its lifecycle honestly and
 * offers to hang up — the audio/video simply does not connect.
 */
export function CommsCallModal({
  call,
  direction,
  onEnd,
}: {
  call: CallResponse;
  direction: "outgoing" | "incoming";
  onEnd: () => void;
}) {
  const [mediaError, setMediaError] = useState<string | null>(call.mediaError);
  const hasMedia = call.mediaAvailable && !!call.accessToken && !!call.roomUrl;
  const isVideo = call.callType === "VIDEO";

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4"
      role="dialog"
      aria-modal="true"
      aria-label="Active call"
    >
      <div className="flex w-full max-w-2xl flex-col gap-3 rounded-lg bg-neutral-950 p-4 text-white shadow-xl">
        <div className="flex items-center justify-between">
          <div>
            <p className="text-sm font-semibold">
              {isVideo ? "Video" : "Audio"} call · {direction === "incoming" ? "Incoming" : "Calling"}
            </p>
            <p className="text-xs text-neutral-400">Status: {call.status}</p>
          </div>
          <button
            type="button"
            onClick={onEnd}
            className="inline-flex items-center gap-2 rounded-md bg-red-600 px-3 py-1.5 text-sm font-medium hover:bg-red-500"
          >
            <PhoneOff className="h-4 w-4" /> End
          </button>
        </div>

        <div className="aspect-video w-full overflow-hidden rounded-md bg-neutral-900">
          {hasMedia ? (
            <AdaptiveSessionRoom
              layout="consult"
              controls={{ microphone: true, camera: true, leave: true }}
              serverUrl={call.roomUrl ?? ""}
              token={call.accessToken ?? ""}
              videoEnabled={isVideo}
              onError={(m) => setMediaError(m)}
            />
          ) : (
            <div className="flex h-full w-full flex-col items-center justify-center gap-2 px-4 text-center text-neutral-300">
              {mediaError ? (
                <>
                  <ShieldAlert className="h-8 w-8 text-amber-400" />
                  <span className="text-xs">
                    Media unavailable — the call is connected for signalling but audio/video did not start.
                  </span>
                  <span className="text-[10px] text-neutral-500">{mediaError}</span>
                </>
              ) : (
                <>
                  <Loader2 className="h-8 w-8 animate-spin text-emerald-400" />
                  <span className="text-xs">Connecting governed media…</span>
                </>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
