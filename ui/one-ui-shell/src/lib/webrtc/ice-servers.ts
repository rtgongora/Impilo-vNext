import { apiClient, type ApiResponse } from "@/lib/api-client";
import type { IceServerConfig } from "@/lib/webrtc/types";

const FALLBACK_ICE: RTCIceServer[] = [
  { urls: "stun:stun.l.google.com:19302" },
  { urls: "turn:localhost:3478", username: "impilo", credential: "impilo-turn-secret" },
];

let cached: RTCIceServer[] | null = null;

/** Warm ICE config while the session workspace loads. */
export function prefetchIceServers(): void {
  void fetchIceServers();
}

export async function fetchIceServers(): Promise<RTCIceServer[]> {
  if (cached) return cached;
  try {
    const res = await apiClient.get<ApiResponse<{ iceServers: IceServerConfig[] }>>(
      "/internal/v1/mobile/provider/telemedicine/rtc/ice-servers",
    );
    const servers = res.data?.iceServers ?? [];
    if (servers.length > 0) {
      const mapped: RTCIceServer[] = servers.map((s: IceServerConfig) => ({
        urls: s.urls,
        username: s.username,
        credential: s.credential,
      }));
      cached = mapped;
      return mapped;
    }
  } catch {
    // Dev fallback when BFF not rebuilt yet
  }
  return FALLBACK_ICE;
}
