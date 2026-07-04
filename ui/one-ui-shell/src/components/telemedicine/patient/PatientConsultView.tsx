"use client";

/**
 * Patient consult view — the citizen-facing in-visit surface. The video is the
 * whole stage (consult layout, your own picture as a small picture-in-picture),
 * with only the controls a patient needs: microphone, camera, leave. A
 * low-bandwidth toggle switches the visit to audio only (a real bandwidth
 * drop, handled inside AdaptiveSessionRoom). Plain language everywhere —
 * no transport jargon.
 */

import { useState } from "react";
import { AdaptiveSessionRoom } from "@/components/session/AdaptiveSessionRoom";
import { LowBandwidthToggle } from "@/components/session/LowBandwidthToggle";
import { useSessionRoomState } from "@/components/session/useSessionRoomState";

export function PatientConsultView({
  roomUrl,
  token,
  startAudioOnly = false,
  onLeave,
}: {
  roomUrl: string;
  token: string;
  /** True when the granted media profile is AUDIO_ONLY (audio-first join). */
  startAudioOnly?: boolean;
  onLeave: () => void;
}) {
  const [audioOnly, setAudioOnly] = useState(startAudioOnly);

  return (
    <div className="space-y-2" data-testid="patient-consult-view">
      <div className="flex items-center justify-between">
        <p className="text-sm text-muted-foreground">
          You&apos;re in the visit. If the picture struggles, switch to audio only.
        </p>
        <LowBandwidthToggle audioOnly={audioOnly} onAudioOnlyChange={setAudioOnly} />
      </div>
      <div className="h-[calc(100vh-16rem)] min-h-[420px]">
        <AdaptiveSessionRoom
          layout="consult"
          localPreviewPolicy="pip"
          controls={{ microphone: true, camera: true, leave: true, settings: false }}
          serverUrl={roomUrl}
          token={token}
          videoEnabled={!audioOnly}
          audioOnly={audioOnly}
          onAudioOnlyChange={setAudioOnly}
          showRecordingBadge={false}
          headerSlot={<PlainRecordingNotice />}
          onDisconnected={onLeave}
        />
      </div>
    </div>
  );
}

/**
 * Plain-language recording notice — rendered inside the room (headerSlot) so
 * it can read the real LiveKit recording state. Replaces the clinical
 * "Recording" badge with wording a patient understands.
 */
function PlainRecordingNotice() {
  const { isRecording } = useSessionRoomState();
  if (!isRecording) return null;
  return (
    <div
      data-testid="patient-recording-notice"
      className="flex items-center gap-2 bg-red-950/70 px-2 py-1 text-[11px] text-red-200"
    >
      <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-red-500" />
      This visit is being recorded so your care team can review it later.
    </div>
  );
}
