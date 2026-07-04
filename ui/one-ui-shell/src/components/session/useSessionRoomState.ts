"use client";

import { useEffect, useState } from "react";
import {
  useConnectionState,
  useParticipants,
  useRoomContext,
} from "@livekit/components-react";
import { ConnectionState, RoomEvent } from "livekit-client";

export interface SessionRoomState {
  connectionState: ConnectionState;
  isRecording: boolean;
  participantCount: number;
  canPublish: boolean;
}

/**
 * Room-state hook for slot content rendered inside an AdaptiveSessionRoom.
 * Must be called from a component under <LiveKitRoom> (i.e. via the
 * header/footer/overlay slots or children of AdaptiveSessionRoom).
 */
export function useSessionRoomState(): SessionRoomState {
  const room = useRoomContext();
  const connectionState = useConnectionState();
  const participants = useParticipants();
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

  return {
    connectionState,
    isRecording,
    participantCount: participants.length,
    canPublish: room?.localParticipant?.permissions?.canPublish ?? false,
  };
}
