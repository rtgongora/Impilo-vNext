/**
 * Ndila TypeScript client
 * -----------------------
 * Thin, typed wrapper over the canonical Ndila API.
 * Every Ndila UI/mobile component should go through this client — never call
 * `/api/v1/maps/*` or `/api/v1/ndila/*` directly. The client funnels through
 * `apiClient`, which injects the canonical v1.2 trust headers (X-Tenant-ID,
 * X-Correlation-ID, X-Actor-ID, X-Purpose-Of-Use, ...) and handles
 * authentication / refresh.
 *
 * Doctrine:
 *   - All paths use `/api/v1/ndila/...` (canonical).
 *   - Components never hardcode a map provider; the backend's
 *     `NdilaProviderPolicyService` decides on every request.
 *   - The client is fallback-safe: when the backend is unreachable the
 *     consumer should render the offline notice rather than fail hard.
 */

import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface NdilaCoordinate {
  latitude: number;
  longitude: number;
}

export interface NdilaGeocodeResult {
  name?: string;
  latitude: number;
  longitude: number;
  address?: string;
  confidence?: number;
  provider?: string;
  providerPlaceId?: string;
  verificationStatus?: string;
}

export interface NdilaGeocodeResponse {
  success: boolean;
  denialReason?: string;
  results: NdilaGeocodeResult[];
  provider?: string;
  fallbackUsed?: boolean;
  servedFromCache?: boolean;
  policyDecisionId?: string;
}

export interface NdilaValidateLocationResponse {
  valid: boolean;
  plausible: boolean;
  errors: string[];
  warnings: string[];
  confidence: number;
}

export type NdilaTravelMode =
  | "WALKING" | "CYCLING" | "MOTORBIKE" | "CAR" | "TRUCK"
  | "AMBULANCE" | "PUBLIC_TRANSPORT" | "DRONE" | "ROBOT"
  | "AUTONOMOUS_VEHICLE" | "BOAT" | "OTHER";

export interface NdilaRouteRequest {
  origin: NdilaCoordinate;
  destination: NdilaCoordinate;
  mode?: NdilaTravelMode;
  optimizeFor?: "FASTEST" | "SHORTEST" | "SAFEST";
  avoid?: string[];
  includeGeometry?: boolean;
}

export interface NdilaRouteResponse {
  success: boolean;
  denialReason?: string;
  distanceMeters?: number;
  durationSeconds?: number;
  mode?: NdilaTravelMode;
  provider?: string;
  providerMode?: string;
  fallbackUsed?: boolean;
  servedFromCache?: boolean;
  polyline?: string;
  warnings?: string[];
  confidence?: number;
  policyDecisionId?: string;
}

export interface NdilaTileConfig {
  tileUrlTemplate: string;
  attribution: string;
  minZoom: number;
  maxZoom: number;
  supportsOffline: boolean;
  provider: string;
}

export interface NdilaLocation {
  id: string;
  tenantId?: string;
  ownerService?: string;
  ownerEntityType?: string;
  ownerEntityId?: string;
  name: string;
  locationType: string;
  latitude?: number;
  longitude?: number;
  district?: string;
  province?: string;
  country?: string;
  verificationStatus?: string;
  sensitivityLevel?: "PUBLIC" | "INTERNAL" | "RESTRICTED" | "HIGHLY_RESTRICTED";
  active?: boolean;
}

export interface NdilaSpatialIntelligenceSignal {
  type: string;
  count?: number;
  severity?: "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";
  trend?: string;
  proximity?: string;
}

export interface NdilaAiContextPacket {
  success?: boolean;
  denialReason?: string;
  contextPacketId?: string;
  summary?: string;
  signals?: NdilaSpatialIntelligenceSignal[];
  recommendedActions?: string[];
  confidence?: number;
  restrictions?: string[];
  explainability?: string[];
}

// ── Geocoding ──────────────────────────────────────────────────────────────
export async function ndilaGeocode(query: string, opts?: {
  country?: string; province?: string; district?: string; purposeOfUse?: string;
}): Promise<NdilaGeocodeResponse> {
  const envelope = await apiClient.post<ApiResponse<NdilaGeocodeResponse>>("/internal/v1/ndila/geocode", {
    query, ...opts,
  });
  return envelope.data ?? { success: false, results: [], denialReason: "Ndila geocode empty" };
}

export async function ndilaReverseGeocode(point: NdilaCoordinate, country?: string)
    : Promise<NdilaGeocodeResponse> {
  const envelope = await apiClient.post<ApiResponse<NdilaGeocodeResponse>>("/internal/v1/ndila/reverse-geocode", {
    latitude: point.latitude, longitude: point.longitude, country,
  });
  return envelope.data ?? { success: false, results: [], denialReason: "Ndila reverse-geocode empty" };
}

export async function ndilaValidateLocation(point: NdilaCoordinate,
                                            accuracyMeters?: number, country?: string)
    : Promise<NdilaValidateLocationResponse> {
  return apiClient.post<NdilaValidateLocationResponse>("/api/v1/ndila/validate-location", {
    latitude: point.latitude, longitude: point.longitude, accuracyMeters, country,
  });
}

// ── Locations ──────────────────────────────────────────────────────────────
export async function ndilaCreateLocation(req: Record<string, unknown>): Promise<NdilaLocation> {
  return apiClient.post<NdilaLocation>("/api/v1/ndila/locations", req);
}

export async function ndilaListLocationsByOwner(ownerService: string, ownerEntityId: string)
    : Promise<NdilaLocation[]> {
  return apiClient.get<NdilaLocation[]>(
      `/api/v1/ndila/locations/by-owner/${encodeURIComponent(ownerService)}/${encodeURIComponent(ownerEntityId)}`);
}

// ── Routing ────────────────────────────────────────────────────────────────
export async function ndilaRoute(req: NdilaRouteRequest): Promise<NdilaRouteResponse> {
  return apiClient.post<NdilaRouteResponse>("/api/v1/ndila/routes", req);
}

export async function ndilaEta(req: Omit<NdilaRouteRequest, "includeGeometry">)
    : Promise<NdilaRouteResponse> {
  return apiClient.post<NdilaRouteResponse>("/api/v1/ndila/eta", req);
}

// ── Tiles ──────────────────────────────────────────────────────────────────
export async function ndilaTileConfig(): Promise<NdilaTileConfig> {
  const envelope = await apiClient.get<ApiResponse<NdilaTileConfig>>("/internal/v1/ndila/tiles/config");
  return envelope.data ?? {
    provider: "PREVIEW_SOVEREIGN",
    tileUrlTemplate: "/internal/v1/ndila/tiles/{z}/{x}/{y}.png",
    attribution: "Impilo Ndila",
    maxZoom: 17,
    supportsOffline: true,
  };
}

// ── Spatial search ─────────────────────────────────────────────────────────
export async function ndilaNearby(req: {
  origin: NdilaCoordinate; radiusMeters: number; entityTypes?: string[]; limit?: number;
}): Promise<{ items: NdilaLocation[]; total?: number }> {
  return apiClient.post("/internal/v1/ndila/spatial/nearby", req);
}

// ── Intelligence ───────────────────────────────────────────────────────────
export async function ndilaAiContextPacket(req: {
  question: string;
  areaScope?: { level: "WARD"|"DISTRICT"|"PROVINCE"|"NATIONAL"|"FACILITY"|"CUSTOM"; id?: string };
  purposeOfUse?: string;
  includeRecommendations?: boolean;
}): Promise<NdilaAiContextPacket> {
  return apiClient.post<NdilaAiContextPacket>("/api/v1/ndila/intelligence/ai-context-packet", req);
}

// ── Tracking ───────────────────────────────────────────────────────────────
export interface NdilaTrackingEventPayload {
  latitude: number; longitude: number;
  accuracyMeters?: number; eventTimestamp?: string; telemetrySource?: string;
}

export async function ndilaPostTrackingEvent(assetId: string, body: NdilaTrackingEventPayload) {
  return apiClient.post(`/api/v1/ndila/tracking/assets/${encodeURIComponent(assetId)}/location`, body);
}

/**
 * Helper used by the offline-aware UI components: callers should treat
 * NetworkError / 5xx as "offline" and switch to local-queue mode rather than
 * surfacing a hard failure.
 */
export function isLikelyOffline(err: unknown): boolean {
  if (!err) return false;
  const e = err as { status?: number; message?: string };
  if (typeof e.message === "string" && /failed to fetch|network/i.test(e.message)) return true;
  if (typeof e.status === "number" && (e.status === 0 || e.status >= 500)) return true;
  return false;
}
