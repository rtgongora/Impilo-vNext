/**
 * NdilaClient (mobile) — typed wrapper around the Ndila API for React Native /
 * Expo apps. Delegates all HTTP through `@impilo/mobile-api-client`, which
 * already handles trust headers, auth refresh, idempotency, retry and
 * step-up detection.
 */

import { apiClient, type ApiResponse } from "@impilo/mobile-api-client";
import type {
  NdilaCoordinate,
  NdilaNearbyResult,
  NdilaRoute,
  NdilaTileConfig,
  NdilaTrackingEvent,
  NdilaTravelMode,
} from "./types";

const path = (suffix: string) => `/api/v1/ndila/${suffix.replace(/^\//, "")}`;
const unwrap = <T>(p: Promise<ApiResponse<T>>): Promise<T> => p.then((r) => r.data);

export const ndilaMobile = {
  async geocode(query: string, opts?: { country?: string }) {
    return unwrap(apiClient.post<{
      success: boolean; denialReason?: string; results: NdilaNearbyResult[]; provider?: string;
    }>(path("geocode"), { query, ...opts }));
  },

  async reverseGeocode(point: NdilaCoordinate, country?: string) {
    return unwrap(apiClient.post<{
      success: boolean; results: NdilaNearbyResult[]; provider?: string;
    }>(path("reverse-geocode"), { latitude: point.latitude, longitude: point.longitude, country }));
  },

  async validateLocation(point: NdilaCoordinate, accuracyMeters?: number, country?: string) {
    return unwrap(apiClient.post<{
      valid: boolean; plausible: boolean; errors: string[]; warnings: string[]; confidence: number;
    }>(path("validate-location"), {
      latitude: point.latitude, longitude: point.longitude, accuracyMeters, country,
    }));
  },

  async route(req: {
    origin: NdilaCoordinate; destination: NdilaCoordinate;
    mode?: NdilaTravelMode; optimizeFor?: "FASTEST" | "SHORTEST" | "SAFEST";
    includeGeometry?: boolean;
  }) {
    return unwrap(apiClient.post<NdilaRoute>(path("routes"), req));
  },

  async nearby(req: {
    origin: NdilaCoordinate; radiusMeters: number;
    entityTypes?: string[]; limit?: number;
  }) {
    return unwrap(apiClient.post<{ items: NdilaNearbyResult[] }>(path("spatial/nearby"), req));
  },

  async tileConfig() {
    return unwrap(apiClient.get<NdilaTileConfig>(path("tiles/config")));
  },

  async postTrackingEvent(assetId: string, event: NdilaTrackingEvent) {
    return unwrap(apiClient.post(
      path(`tracking/assets/${encodeURIComponent(assetId)}/location`),
      event
    ));
  },

  async createLocation(payload: {
    ownerService: string; ownerEntityType: string; ownerEntityId?: string;
    name: string; locationType: string;
    latitude: number; longitude: number; accuracyMeters?: number;
    source?: string;
  }) {
    return unwrap(apiClient.post(path("locations"), payload));
  },
};

export type NdilaClient = typeof ndilaMobile;
