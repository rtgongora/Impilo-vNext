/**
 * Shared helpers for the Khuluma communication hub. Envelope-tolerant reads + persona
 * resolution so every Khuluma surface treats real BFF data the same way. Khuluma is the
 * communication front door; Impilo Live (live-service) is the sovereign realtime stage.
 */

export function asRecord(v: unknown): Record<string, unknown> {
  return v && typeof v === "object" ? (v as Record<string, unknown>) : {};
}

/** Envelope-tolerant list unwrap ({data:[...]} | [...] | {data:{content:[...]}} → rows). */
export function rows(payload: unknown): Record<string, unknown>[] {
  if (Array.isArray(payload)) return (payload as unknown[]).map(asRecord);
  const obj = asRecord(payload);
  if (Array.isArray(obj.data)) return (obj.data as unknown[]).map(asRecord);
  const data = asRecord(obj.data);
  if (Array.isArray(data.content)) return (data.content as unknown[]).map(asRecord);
  if (Array.isArray(obj.content)) return (obj.content as unknown[]).map(asRecord);
  if (Array.isArray(obj.items)) return (obj.items as unknown[]).map(asRecord);
  return [];
}

export function readStr(r: Record<string, unknown>, ...keys: string[]): string {
  for (const k of keys) {
    const x = r[k];
    if (typeof x === "string" && x.length > 0) return x;
  }
  return "";
}

export function readNum(r: Record<string, unknown>, ...keys: string[]): number {
  for (const k of keys) {
    const x = r[k];
    if (typeof x === "number") return x;
    if (typeof x === "string" && x.trim()) {
      const n = Number(x);
      if (!Number.isNaN(n)) return n;
    }
  }
  return 0;
}

export function formatWhen(iso: string): string {
  if (!iso) return "";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString(undefined, { weekday: "short", hour: "2-digit", minute: "2-digit" });
}

/** Roles that make a person a "worker" — get the professional Khuluma persona. */
const WORK_ROLE_KEYS = ["CLINICAL", "OPERATIONS_AGGREGATE", "ADMIN", "FINANCE", "QUEUE"];

/**
 * Resolve the Khuluma persona from role membership. Any professional/ops role → "work"
 * (clinical inbox, escalations, on-call, channels); otherwise "life" (citizen/household).
 */
export function resolvePersona(matches: (roleKey: string) => boolean): "work" | "life" {
  return WORK_ROLE_KEYS.some((r) => matches(r)) ? "work" : "life";
}
