/**
 * Helpers for Impilo sovereign `ApiResponse<T>` envelopes returned through the BFF.
 * Avoids treating unknown JSON as fake KPIs — callers derive counts or rows explicitly.
 */

export function extractListFromEnvelope(raw: unknown): unknown[] {
  if (raw == null || typeof raw !== "object") return [];
  const root = raw as Record<string, unknown>;
  const data = root.data;
  if (Array.isArray(data)) return data;
  if (data && typeof data === "object") {
    const d = data as Record<string, unknown>;
    if (Array.isArray(d.items)) return d.items;
    if (Array.isArray(d.content)) return d.content as unknown[];
  }
  return [];
}

export function countEnvelopeList(raw: unknown): number {
  return extractListFromEnvelope(raw).length;
}
