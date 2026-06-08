import { ndilaGeocode } from "@/lib/ndila/ndila-client";
import type { OpsMapMarker } from "@/lib/ndila/geo-map-markers";

export interface EnrichableMapRow {
  id: string;
  label: string;
  latitude?: number | null;
  longitude?: number | null;
  district?: string | null;
  province?: string | null;
  status?: string;
}

export interface EnrichedOpsMapMarker extends OpsMapMarker {
  coordinateSource?: "RECORD" | "GEOCODED";
}

const geocodeCache = new Map<string, { latitude: number; longitude: number }>();

function hasCoordinates(row: EnrichableMapRow): boolean {
  return row.latitude != null && row.longitude != null
    && Number.isFinite(row.latitude) && Number.isFinite(row.longitude);
}

function cacheKey(district?: string | null, province?: string | null): string | null {
  const parts = [district?.trim(), province?.trim(), "Zimbabwe"].filter(Boolean);
  if (parts.length < 2) return null;
  return parts.join(", ");
}

export async function geocodeDistrictCentroid(
  district?: string | null,
  province?: string | null,
  purposeOfUse = "OPERATIONS_MANAGEMENT",
): Promise<{ latitude: number; longitude: number } | null> {
  const key = cacheKey(district, province);
  if (!key) return null;
  const cached = geocodeCache.get(key);
  if (cached) return cached;

  try {
    const resp = await ndilaGeocode(key, { country: "ZWE", purposeOfUse });
    const hit = resp.results?.[0];
    if (!resp.success || !hit) return null;
    const point = { latitude: hit.latitude, longitude: hit.longitude };
    geocodeCache.set(key, point);
    return point;
  } catch {
    return null;
  }
}

export async function enrichOpsMapMarkers(
  rows: EnrichableMapRow[],
  options?: { maxGeocode?: number; purposeOfUse?: string },
): Promise<EnrichedOpsMapMarker[]> {
  const maxGeocode = options?.maxGeocode ?? 25;
  let geocoded = 0;
  const out: EnrichedOpsMapMarker[] = [];

  for (const row of rows) {
    if (hasCoordinates(row)) {
      out.push({
        id: row.id,
        label: row.label,
        latitude: row.latitude as number,
        longitude: row.longitude as number,
        status: row.status,
        coordinateSource: "RECORD",
      });
      continue;
    }

    if (geocoded >= maxGeocode) continue;

    const approx = await geocodeDistrictCentroid(row.district, row.province, options?.purposeOfUse);
    if (!approx) continue;

    geocoded += 1;
    out.push({
      id: row.id,
      label: row.label,
      latitude: approx.latitude,
      longitude: approx.longitude,
      status: row.status ? `${row.status} · approx` : "approximate",
      coordinateSource: "GEOCODED",
    });
  }

  return out;
}
