/** Unwrap JSON:API / BFF list payloads into flat row records for product tables. */

export function unwrapCollection(payload: unknown): Record<string, unknown>[] {
  if (Array.isArray(payload)) {
    return payload.filter((row): row is Record<string, unknown> => !!row && typeof row === "object");
  }
  if (!payload || typeof payload !== "object") return [];
  const record = payload as Record<string, unknown>;
  if (Array.isArray(record.data)) {
    return record.data.map((row) => flattenRow(row));
  }
  for (const key of ["items", "content", "results", "reviews", "orders", "vendors", "entries", "rows"]) {
    const nested = record[key];
    if (Array.isArray(nested)) {
      return nested.map((row) => flattenRow(row));
    }
  }
  return [flattenRow(record)];
}

function flattenRow(row: unknown): Record<string, unknown> {
  if (!row || typeof row !== "object") return {};
  const record = row as Record<string, unknown>;
  const attrs = record.attributes;
  if (attrs && typeof attrs === "object" && !Array.isArray(attrs)) {
    return { id: record.id, ...(attrs as Record<string, unknown>) };
  }
  return record;
}

export function pickField(row: Record<string, unknown>, ...keys: string[]): string {
  for (const key of keys) {
    const value = row[key];
    if (value != null && value !== "") return String(value);
  }
  return "—";
}

export function rowId(row: Record<string, unknown>, index: number, ...keys: string[]): string {
  for (const key of keys) {
    const value = row[key];
    if (value != null && value !== "") return String(value);
  }
  if (row.id != null) return String(row.id);
  return String(index);
}
