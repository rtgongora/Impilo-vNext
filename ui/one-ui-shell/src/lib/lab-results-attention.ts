/**
 * Derive a single “needs attention” count from the mobile provider lab results payload.
 * BFF: GET /internal/v1/mobile/provider/labs/results — shape may be JSON:API rows or a plain array from OROS.
 */

function readBoolish(value: unknown): boolean | undefined {
  if (value === true) return true;
  if (value === false) return false;
  if (typeof value === "string" && value.trim()) return value !== "false" && value !== "0";
  return undefined;
}

function isAcknowledged(attrs: Record<string, unknown>): boolean {
  const direct = readBoolish(attrs.acknowledged);
  if (direct === true) return true;
  const at = attrs.acknowledged_at ?? attrs.acknowledgedAt;
  if (typeof at === "string" && at.trim().length > 0) return true;
  return false;
}

function resultRows(attrs: Record<string, unknown>): unknown[] {
  const raw = attrs.results ?? attrs.result_values ?? attrs.resultValues;
  return Array.isArray(raw) ? raw : [];
}

function rowIsAbnormalOrCritical(row: unknown): boolean {
  if (!row || typeof row !== "object") return false;
  const r = row as Record<string, unknown>;
  if (readBoolish(r.is_abnormal) === true || readBoolish(r.isAbnormal) === true) return true;
  if (readBoolish(r.is_critical) === true || readBoolish(r.isCritical) === true) return true;
  const interp = String(r.interpretation ?? r.flag ?? "").toUpperCase();
  return interp === "CRITICAL" || interp === "ABNORMAL";
}

function isStatUrgency(attrs: Record<string, unknown>): boolean {
  const u = String(attrs.urgency ?? attrs.Urgency ?? "").toUpperCase();
  return u === "STAT";
}

function toAttributesRecord(row: unknown): Record<string, unknown> | null {
  if (!row || typeof row !== "object") return null;
  const rec = row as Record<string, unknown>;
  const nested = rec.attributes;
  if (nested && typeof nested === "object" && !Array.isArray(nested)) {
    return nested as Record<string, unknown>;
  }
  return rec;
}

function extractDataArray(payload: unknown): unknown[] {
  if (payload == null) return [];
  if (Array.isArray(payload)) return payload;
  if (typeof payload !== "object") return [];
  const root = payload as Record<string, unknown>;
  const d = root.data;
  if (Array.isArray(d)) return d;
  if (d && typeof d === "object" && Array.isArray((d as Record<string, unknown>).data)) {
    return (d as { data: unknown[] }).data;
  }
  return [];
}

/**
 * Count lab result rows that still need clinician attention: not acknowledged, and either
 * flagged abnormal/critical on any parameter, or STAT urgency (until acknowledged).
 */
export function countUnacknowledgedAbnormalLabResults(payload: unknown): number {
  const rows = extractDataArray(payload);
  let n = 0;
  for (const row of rows) {
    const attrs = toAttributesRecord(row);
    if (!attrs) continue;
    if (isAcknowledged(attrs)) continue;
    const rowsNeedingRead = resultRows(attrs);
    const abnormal = rowsNeedingRead.some(rowIsAbnormalOrCritical);
    if (abnormal || isStatUrgency(attrs)) n += 1;
  }
  return n;
}
