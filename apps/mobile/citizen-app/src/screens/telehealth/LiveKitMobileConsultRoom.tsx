import React from "react";
import { AdaptiveSessionRoomNative, normalizeLiveKitServerUrl } from "@impilo/mobile-session";

/** @deprecated Import `normalizeLiveKitServerUrl` from `@impilo/mobile-session` instead. */
export { normalizeLiveKitServerUrl };

export interface LiveKitMobileConsultRoomProps {
  serverUrl?: string;
  token?: string;
  videoEnabled: boolean;
  micMuted: boolean;
  onConnected?: () => void;
  onDisconnected?: () => void;
  onError?: (message: string) => void;
}

/**
 * @deprecated Thin alias kept for backwards compatibility. Use
 * `AdaptiveSessionRoomNative` from `@impilo/mobile-session` (shared across the
 * citizen and provider apps) instead.
 */
export function LiveKitMobileConsultRoom(props: LiveKitMobileConsultRoomProps) {
  return (
    <AdaptiveSessionRoomNative
      {...props}
      layout="consult"
      unavailableMessage="Live media is blocked until Impilo returns a governed LiveKit server URL and scoped token."
      invalidEndpointMessage="Continue with secure chat/notes and request support."
    />
  );
}
