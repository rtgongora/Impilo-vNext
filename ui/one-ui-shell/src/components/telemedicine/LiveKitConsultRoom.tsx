"use client";

import {
  AdaptiveSessionRoom,
  type AdaptiveSessionRoomProps,
} from "@/components/session/AdaptiveSessionRoom";

export { normalizeLiveKitServerUrl } from "@/lib/session/livekit-url";

export type LiveKitConsultRoomProps = Pick<
  AdaptiveSessionRoomProps,
  "serverUrl" | "token" | "videoEnabled" | "onConnected" | "onDisconnected" | "onError"
>;

/**
 * @deprecated Use AdaptiveSessionRoom from "@/components/session/AdaptiveSessionRoom"
 * with an explicit `layout` for the session mode. This alias keeps the legacy
 * grid behaviour for existing call sites.
 */
export function LiveKitConsultRoom(props: LiveKitConsultRoomProps) {
  return <AdaptiveSessionRoom layout="grid" {...props} />;
}
