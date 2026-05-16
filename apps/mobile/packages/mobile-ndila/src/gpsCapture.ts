/**
 * GPS capture helpers.
 *
 * Works with either the React Native `Geolocation` module (when injected by
 * the consumer app) or a web/Expo-style `navigator.geolocation` fallback.
 * The SDK is intentionally provider-agnostic — apps inject the geolocation
 * implementation that matches their runtime, so this package stays
 * platform-neutral.
 */

import type { NdilaCoordinate } from "./types";

export interface GpsCaptureResult {
  coordinate: NdilaCoordinate;
  accuracyMeters?: number;
  altitudeMeters?: number;
  speedMps?: number;
  headingDegrees?: number;
  capturedAt: string;
}

export interface GeolocationLike {
  getCurrentPosition(
    onSuccess: (pos: { coords: { latitude: number; longitude: number; accuracy?: number; altitude?: number | null; speed?: number | null; heading?: number | null } }) => void,
    onError: (err: { code: number; message: string }) => void,
    options?: { enableHighAccuracy?: boolean; timeout?: number; maximumAge?: number },
  ): void;
}

let geolocationImpl: GeolocationLike | null =
  typeof navigator !== "undefined" && "geolocation" in navigator
    ? (navigator.geolocation as unknown as GeolocationLike)
    : null;

export function configureGeolocation(impl: GeolocationLike): void {
  geolocationImpl = impl;
}

export function captureCurrentLocation(options?: { highAccuracy?: boolean; timeoutMs?: number }): Promise<GpsCaptureResult> {
  return new Promise((resolve, reject) => {
    if (!geolocationImpl) {
      reject(new Error("Geolocation not configured. Call configureGeolocation() first."));
      return;
    }
    geolocationImpl.getCurrentPosition(
      (pos) => resolve({
        coordinate: { latitude: pos.coords.latitude, longitude: pos.coords.longitude },
        accuracyMeters: pos.coords.accuracy,
        altitudeMeters: pos.coords.altitude ?? undefined,
        speedMps: pos.coords.speed ?? undefined,
        headingDegrees: pos.coords.heading ?? undefined,
        capturedAt: new Date().toISOString(),
      }),
      (err) => reject(new Error(err.message || `GEOLOCATION_ERROR_${err.code}`)),
      {
        enableHighAccuracy: options?.highAccuracy ?? true,
        timeout: options?.timeoutMs ?? 15_000,
        maximumAge: 0,
      },
    );
  });
}

/** Haversine distance (metres). Useful for nearby/within-range checks offline. */
export function haversineMeters(a: NdilaCoordinate, b: NdilaCoordinate): number {
  const R = 6_371_000;
  const toRad = (d: number) => (d * Math.PI) / 180;
  const dLat = toRad(b.latitude - a.latitude);
  const dLng = toRad(b.longitude - a.longitude);
  const lat1 = toRad(a.latitude);
  const lat2 = toRad(b.latitude);
  const h =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.asin(Math.min(1, Math.sqrt(h)));
}
