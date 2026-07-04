/**
 * Normalizes a governed RTC server URL (as returned by the BFF / rtc-gateway)
 * into a LiveKit websocket server URL.
 *
 * - Strips browser room paths (`/rooms/<name>...`) so a shareable room link can
 *   be reused as a server endpoint.
 * - Rewrites `https://` → `wss://` and `http://` → `ws://`.
 * - Returns an empty string for missing/blank input.
 */
export function normalizeLiveKitServerUrl(value: string | undefined | null): string {
  const raw = String(value ?? "").trim();
  if (!raw) return "";

  const withoutRoomPath = raw.replace(/\/rooms\/[^/?#]+.*$/i, "");
  if (withoutRoomPath.startsWith("https://")) {
    return `wss://${withoutRoomPath.slice("https://".length)}`;
  }
  if (withoutRoomPath.startsWith("http://")) {
    return `ws://${withoutRoomPath.slice("http://".length)}`;
  }
  return withoutRoomPath;
}

/** True when the URL is a LiveKit-compatible websocket endpoint. */
export function isSupportedLiveKitServerUrl(url: string): boolean {
  return url.startsWith("ws://") || url.startsWith("wss://");
}
