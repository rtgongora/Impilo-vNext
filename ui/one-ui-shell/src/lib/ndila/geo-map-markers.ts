import type { FacilityResource } from "@/hooks/queries/useFacilities";
import type { NdilaGeoMarker } from "@/components/ndila/NdilaMapLibre";

export interface OpsMapMarker {
  id: string;
  label: string;
  latitude?: number;
  longitude?: number;
  status?: string;
}

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

function readString(row: Row, ...keys: string[]): string {
  for (const key of keys) {
    const value = row[key];
    if (typeof value === "string" && value.trim()) return value;
  }
  return "";
}

export function rowToGeoMarker(
  row: Row,
  options: { id: string; label: string; markerType?: string; status?: string },
): NdilaGeoMarker | null {
  const latitude = readNumber(row, "latitude", "lat", "last_latitude", "destination_latitude", "pickup_latitude");
  const longitude = readNumber(row, "longitude", "lng", "lon", "last_longitude", "destination_longitude", "pickup_longitude");
  if (latitude == null || longitude == null) return null;
  return {
    id: options.id,
    label: options.label,
    latitude,
    longitude,
    markerType: options.markerType,
    status: options.status,
  };
}

export function opsMarkerToGeoMarker(marker: OpsMapMarker): NdilaGeoMarker | null {
  if (marker.latitude == null || marker.longitude == null) return null;
  return {
    id: marker.id,
    label: marker.label,
    latitude: marker.latitude,
    longitude: marker.longitude,
    status: marker.status,
  };
}

export function opsMarkersToGeoMarkers(markers: OpsMapMarker[]): NdilaGeoMarker[] {
  return markers
    .map((marker) => opsMarkerToGeoMarker(marker))
    .filter((marker): marker is NdilaGeoMarker => marker !== null);
}

export function facilityResourceToGeoMarker(facility: FacilityResource): NdilaGeoMarker | null {
  const attrs = facility.attributes;
  if (attrs.latitude == null || attrs.longitude == null) return null;
  return {
    id: facility.id,
    label: attrs.name,
    latitude: attrs.latitude,
    longitude: attrs.longitude,
    markerType: "facility",
    status: attrs.status,
  };
}

export function facilityRegulatoryRowToGeoMarker(row: {
  facilityId: string;
  name: string;
  latitude: number | null;
  longitude: number | null;
  regulatoryStatus?: string;
}): NdilaGeoMarker | null {
  if (row.latitude == null || row.longitude == null) return null;
  return {
    id: row.facilityId,
    label: row.name,
    latitude: row.latitude,
    longitude: row.longitude,
    markerType: "facility",
    status: row.regulatoryStatus,
  };
}

export function siteRegistryRowToGeoMarker(row: unknown): NdilaGeoMarker | null {
  if (!row || typeof row !== "object") return null;
  const record = row as Row;
  const latitude = readNumber(record, "latitude", "lat", "geoLatitude", "geo_latitude");
  const longitude = readNumber(record, "longitude", "lng", "lon", "geoLongitude", "geo_longitude");
  if (latitude == null || longitude == null) return null;
  const id = readString(record, "siteId", "site_id", "id") || `site-${latitude}-${longitude}`;
  const label = readString(record, "name", "siteName", "site_name") || id;
  return {
    id,
    label,
    latitude,
    longitude,
    markerType: "site",
    status: readString(record, "regulatoryStatus", "regulatory_status", "lifecycleStatus"),
  };
}

export function trackingRowsToRoute(rows: unknown[]): Array<{ latitude: number; longitude: number }> {
  if (!Array.isArray(rows)) return [];
  return rows
    .filter((row): row is Row => typeof row === "object" && row !== null)
    .map((row) => {
      const latitude = readNumber(row, "latitude", "lat");
      const longitude = readNumber(row, "longitude", "lng", "lon");
      if (latitude == null || longitude == null) return null;
      return { latitude, longitude };
    })
    .filter((point): point is { latitude: number; longitude: number } => point !== null);
}

export function computeMapCenter(
  markers: NdilaGeoMarker[],
  fallback: { latitude: number; longitude: number },
): { latitude: number; longitude: number } {
  if (markers.length === 0) return fallback;
  const latitude = markers.reduce((sum, marker) => sum + marker.latitude, 0) / markers.length;
  const longitude = markers.reduce((sum, marker) => sum + marker.longitude, 0) / markers.length;
  return { latitude, longitude };
}

export const DEFAULT_MAP_CENTER = { latitude: -17.8252, longitude: 31.0335 };
