/**
 * Normalizes optional WebSocket / push payloads for the shell tray.
 * Servers may send any JSON; we pick common keys then fall back to a short string preview.
 */

function pickString(obj: Record<string, unknown>, keys: string[]): string | undefined {
  for (const k of keys) {
    const v = obj[k];
    if (typeof v === "string" && v.trim()) return v.trim();
  }
  return undefined;
}

function asRecord(o: unknown): Record<string, unknown> | null {
  if (o && typeof o === "object" && !Array.isArray(o)) return o as Record<string, unknown>;
  return null;
}

/** Collect nested maps commonly used by gateways (FCM-style, generic event envelopes). */
function envelopeSources(root: Record<string, unknown>): Record<string, unknown>[] {
  const seen = new Set<Record<string, unknown>>();
  const out: Record<string, unknown>[] = [];
  const add = (o: Record<string, unknown> | null | undefined) => {
    if (!o || seen.has(o)) return;
    seen.add(o);
    out.push(o);
  };
  add(root);
  add(asRecord(root.data));
  add(asRecord(root.notification));
  add(asRecord(root.event));
  add(asRecord(root.alert));
  add(asRecord(root.payload));
  add(asRecord(root.attributes));
  const data = asRecord(root.data);
  if (data) {
    add(asRecord(data.notification));
    add(asRecord(data.event));
    add(asRecord(data.payload));
    add(asRecord(data.attributes));
  }
  return out;
}

function previewObject(obj: Record<string, unknown>, maxLen: number): string {
  try {
    const s = JSON.stringify(obj);
    if (s.length <= maxLen) return s;
    return `${s.slice(0, maxLen)}…`;
  } catch {
    return "";
  }
}

export interface NormalizedLiveNotification {
  title: string;
  body: string;
  severity: string;
}

/**
 * @param data Parsed JSON object from a WebSocket message (or shell event payload).
 */
export function normalizeLiveNotificationPayload(data: Record<string, unknown>): NormalizedLiveNotification {
  const sources = envelopeSources(data);

  const titleKeys = ["title", "subject", "summary", "headline", "name", "event", "type", "label", "alertTitle"];
  let title: string | undefined;
  for (const src of sources) {
    title = pickString(src, titleKeys);
    if (title) break;
  }
  title = title ?? "Notification";

  const bodyKeys = ["message", "body", "text", "detail", "description", "content", "reason", "alertBody", "msg"];
  let body = "";
  for (const src of sources) {
    body = pickString(src, bodyKeys) ?? "";
    if (body) break;
  }

  if (!body) {
    body = previewObject(data, 220);
  }

  const severityKeys = ["severity", "level", "priority", "classification", "urgency"];
  let severityRaw = "INFO";
  for (const src of sources) {
    const s = pickString(src, severityKeys);
    if (s) {
      severityRaw = s;
      break;
    }
  }
  const severity = severityRaw.toUpperCase();

  return { title, body: body || "(no message body)", severity };
}
