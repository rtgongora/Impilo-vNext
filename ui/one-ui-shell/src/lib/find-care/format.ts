/**
 * Honest presentation helpers for the find-care lane.
 *
 * The register never gives us a live "open now" or a per-facility telemedicine
 * flag, and distance/ETA are only present when the person shared a location and
 * the facility has coordinates. These helpers make the UI say "unknown" plainly
 * instead of inventing a reassuring-but-false value.
 */

import type { CareResult } from "@/lib/find-care/types";

/** Title-case a SCREAMING_SNAKE or lowercase register enum for display. */
export function humanizeEnum(value: string | null | undefined): string | null {
  if (!value) return null;
  return value
    .toLowerCase()
    .split(/[_\s]+/)
    .filter(Boolean)
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(" ");
}

/** "18 km" / "850 m", or null when distance is not known. */
export function formatDistance(distanceMeters: number | null | undefined): string | null {
  if (distanceMeters == null || !Number.isFinite(distanceMeters)) return null;
  if (distanceMeters < 1000) return `${Math.round(distanceMeters)} m`;
  const km = distanceMeters / 1000;
  return `${km < 10 ? km.toFixed(1) : Math.round(km)} km`;
}

/** "~25 min", or null when routing did not return a travel time (never invented). */
export function formatEta(etaMinutes: number | null | undefined): string | null {
  if (etaMinutes == null || !Number.isFinite(etaMinutes)) return null;
  const mins = Math.max(1, Math.round(etaMinutes));
  return `~${mins} min`;
}

/**
 * A combined distance line for a result, honest about missing pieces:
 *  - both known:      "18 km · ~25 min"
 *  - distance only:   "18 km" (routing/ETA not available)
 *  - nothing known:   null  → caller shows "distance unavailable — share your location"
 */
export function formatDistanceLine(result: Pick<CareResult, "distanceMeters" | "etaMinutes">): string | null {
  const dist = formatDistance(result.distanceMeters);
  const eta = formatEta(result.etaMinutes);
  if (dist && eta) return `${dist} · ${eta}`;
  return dist;
}

/** A Google Maps directions link built purely from coordinates the register holds. */
export function directionsUrl(
  lat: number | null | undefined,
  lng: number | null | undefined,
): string | null {
  if (lat == null || lng == null || !Number.isFinite(lat) || !Number.isFinite(lng)) return null;
  return `https://www.google.com/maps/dir/?api=1&destination=${lat},${lng}`;
}
