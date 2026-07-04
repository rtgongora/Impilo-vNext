"use client";

import { useEffect, useState } from "react";
import { useRoomContext } from "@livekit/components-react";
import { RoomEvent } from "livekit-client";

/**
 * Recording badge — visible only while the room is actually being recorded
 * (LiveKit egress). Consent/recording governance lives server-side; this is
 * the honest client-side indicator.
 */
export function RecordingBadge() {
  const room = useRoomContext();
  const [isRecording, setIsRecording] = useState<boolean>(() => room?.isRecording ?? false);

  useEffect(() => {
    if (!room) return;
    setIsRecording(room.isRecording);
    const onRecordingStatusChanged = (recording: boolean) => setIsRecording(recording);
    room.on(RoomEvent.RecordingStatusChanged, onRecordingStatusChanged);
    return () => {
      room.off(RoomEvent.RecordingStatusChanged, onRecordingStatusChanged);
    };
  }, [room]);

  if (!isRecording) return null;

  return (
    <span
      data-testid="session-recording-badge"
      className="inline-flex items-center gap-1 rounded-full bg-red-950/70 px-2 py-0.5 text-[10px] font-medium text-red-200"
    >
      <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-red-500" />
      Recording
    </span>
  );
}
