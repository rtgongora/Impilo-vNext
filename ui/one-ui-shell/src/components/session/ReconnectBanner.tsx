"use client";

import { useEffect, useRef } from "react";
import { useConnectionState } from "@livekit/components-react";
import { ConnectionState } from "livekit-client";
import { WifiOff } from "lucide-react";

/**
 * Reconnect banner — surfaces degraded transport states inside the room.
 * The "disconnected" banner only appears after the room was connected once,
 * so the normal join handshake never flashes a false alarm.
 */
export function ReconnectBanner() {
  const connectionState = useConnectionState();
  const hasConnectedRef = useRef(false);

  useEffect(() => {
    if (connectionState === ConnectionState.Connected) {
      hasConnectedRef.current = true;
    }
  }, [connectionState]);

  if (
    connectionState === ConnectionState.Reconnecting ||
    connectionState === ConnectionState.SignalReconnecting
  ) {
    return (
      <div
        data-testid="session-reconnect-banner"
        className="flex items-center gap-2 bg-amber-950/70 px-2 py-1 text-[10px] text-amber-200"
      >
        <WifiOff className="h-3 w-3" />
        <span>Reconnecting to governed media… your session will resume automatically.</span>
      </div>
    );
  }

  if (connectionState === ConnectionState.Disconnected && hasConnectedRef.current) {
    return (
      <div
        data-testid="session-reconnect-banner"
        className="flex items-center gap-2 bg-red-950/70 px-2 py-1 text-[10px] text-red-200"
      >
        <WifiOff className="h-3 w-3" />
        <span>Disconnected from the session. Check your connection, then rejoin from the session page.</span>
      </div>
    );
  }

  return null;
}
