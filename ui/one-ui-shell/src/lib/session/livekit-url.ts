/**
 * Governed LiveKit endpoint helpers for the adaptive session suite.
 *
 * The RTC Gateway returns room URLs in several shapes (https room links,
 * ws/wss endpoints). Media components must always connect to a plain
 * ws(s):// signalling endpoint, so we normalize here in one place.
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

export function isSupportedLiveKitServerUrl(url: string): boolean {
  return url.startsWith("ws://") || url.startsWith("wss://");
}
