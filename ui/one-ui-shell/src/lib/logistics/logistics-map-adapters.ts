import type { OpsMapMarker } from "@/lib/ndila/geo-map-markers";

type Row = Record<string, unknown>;

function readNumber(row: Row, ...keys: string[]): number | undefined {
  for (const key of keys) {
    const value = row[key];
    if (typeof value === "number" && Number.isFinite(value)) return value;
    if (typeof value === "string" && value.trim() !== "") {
      const parsed = Number(value);
      if (Number.isFinite(parsed)) return parsed;
    }
  }
  return undefined;
}

function readLabel(row: Row, ...keys: string[]): string {
  for (const key of keys) {
    const value = row[key];
    if (typeof value === "string" && value.trim()) return value;
  }
  return "asset";
}

/**
 * Normalize Nhume / dispatch fleet or delivery rows into shared map markers.
 */
export function rowToOpsMapMarker(
  row: Row,
  options: { idPrefix: string; kind: "delivery" | "fleet" | "courier" | "task" },
): OpsMapMarker | null {
  const latitude = readNumber(
    row,
    "latitude",
    "lat",
    "last_latitude",
    "destination_latitude",
    "destination_lat",
    "pickup_latitude",
    "pickup_lat",
    "current_latitude",
  );
  const longitude = readNumber(
    row,
    "longitude",
    "lng",
    "lon",
    "last_longitude",
    "destination_longitude",
    "destination_lon",
    "pickup_longitude",
    "pickup_lon",
    "current_longitude",
  );

  const id = String(
    row.id ??
      row.delivery_id ??
      row.task_id ??
      row.asset_id ??
      row.courier_id ??
      row.reference ??
      `${options.idPrefix}-${readLabel(row, "reference", "registration", "label", "name")}`,
  );

  const label =
    options.kind === "delivery"
      ? readLabel(row, "reference", "delivery_id", "destination_label", "status")
      : readLabel(row, "registration", "label", "name", "asset_id", "courier_id");

  const status = typeof row.status === "string" ? row.status : undefined;

  if (latitude == null || longitude == null) {
    return { id: `${options.idPrefix}:${id}`, label, status };
  }

  return {
    id: `${options.idPrefix}:${id}`,
    label,
    latitude,
    longitude,
    status,
  };
}

export function rowsToOpsMapMarkers(
  rows: unknown[],
  options: { idPrefix: string; kind: "delivery" | "fleet" | "courier" | "task" },
): OpsMapMarker[] {
  if (!Array.isArray(rows)) return [];
  return rows
    .filter((row): row is Row => typeof row === "object" && row !== null)
    .map((row) => rowToOpsMapMarker(row, options))
    .filter((marker): marker is OpsMapMarker => marker !== null);
}

export function mergeLogisticsMarkers(...groups: OpsMapMarker[][]): OpsMapMarker[] {
  const seen = new Set<string>();
  const merged: OpsMapMarker[] = [];
  for (const group of groups) {
    for (const marker of group) {
      if (seen.has(marker.id)) continue;
      seen.add(marker.id);
      merged.push(marker);
    }
  }
  return merged;
}

export function extractCollection(payload: unknown): Row[] {
  if (Array.isArray(payload)) {
    return payload.filter((row): row is Row => typeof row === "object" && row !== null);
  }
  if (typeof payload !== "object" || payload === null) return [];
  const record = payload as Row;
  if (Array.isArray(record.data)) {
    return record.data.filter((row): row is Row => typeof row === "object" && row !== null);
  }
  if (Array.isArray(record.items)) {
    return record.items.filter((row): row is Row => typeof row === "object" && row !== null);
  }
  return [];
}
